"""Expose the sibling harness's five demo destinations in the local signed provider."""
import http.client
import json
import re
import time
from urllib.parse import urlencode

LIBRARIES = {'kb-compliance', 'kb-investment', 'kb-market', 'kb-operations', 'kb-risk'}
PUBLISHER = '1'


def catalog_items(path, deadline):
    cursor = None
    for _ in range(32):
        query = {'limit': 100}
        if cursor is not None:
            query['cursor'] = cursor
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise TimeoutError('Harness catalog deadline exceeded')
        connection = http.client.HTTPConnection('127.0.0.1', 8792, timeout=min(2, remaining))
        try:
            # No delegation header: the harness serves its native catalog instead of
            # forwarding this request back to the signed provider.
            connection.request('GET', '/v1/knowledge/' + path + '?' + urlencode(query))
            response = connection.getresponse()
            raw = response.read(262145)
            if response.status != 200 or len(raw) > 262144:
                raise ValueError('Harness knowledge catalog unavailable')
            page = json.loads(raw)
        finally:
            connection.close()
        if not isinstance(page['items'], list) or len(page['items']) > 100:
            raise ValueError('Invalid harness catalog page')
        yield from page['items']
        following = page['nextCursor']
        if following is None:
            return
        if not isinstance(following, str) or not following.isdecimal() or int(following) <= int(cursor or 0):
            raise ValueError('Invalid harness catalog cursor')
        cursor = following
    raise ValueError('Harness catalog exceeds page limit')


class HarnessPublicationCatalog:
    def __init__(self, db):
        self.db = db
        self.refresh_after = 0
        # Only this provider connection overlays the external metadata. The harness
        # material picker already lists these IDs from its native catalog.
        db.executescript("""
            CREATE TEMP TABLE native_library(id TEXT PRIMARY KEY, name TEXT, root TEXT, description TEXT);
            CREATE TEMP TABLE native_folder(id TEXT PRIMARY KEY, library TEXT, parent TEXT, name TEXT, is_root INTEGER);
            CREATE TEMP VIEW library AS SELECT * FROM main.library UNION ALL SELECT id,name,root FROM native_library;
            CREATE TEMP VIEW folder AS SELECT * FROM main.folder UNION ALL SELECT * FROM native_folder;
            CREATE TEMP VIEW library_profile AS SELECT * FROM main.library_profile
                UNION ALL SELECT id,description,'具有该知识库阅读权限的成员' FROM native_library;
        """)

    def refresh_for(self, actor, path, body):
        destination = body.get('libraryId') if body else None
        uses_catalog = path.startswith(('/v1/knowledge/libraries', '/v1/knowledge/documents/'))
        if actor != PUBLISHER or not (uses_catalog or isinstance(destination, str) and destination in LIBRARIES):
            return
        now = time.monotonic()
        if now < self.refresh_after:
            return
        libraries, folders = [], []
        deadline = now + 3
        for library in catalog_items('libraries', deadline):
            key = library['id']
            if key not in LIBRARIES or library['status'] != 'ACTIVE':
                continue
            entries = list(catalog_items('libraries/' + key + '/folders', deadline))
            roots = [row['id'] for row in entries if row['parentFolderId'] is None]
            if len(roots) != 1:
                raise ValueError('Expected one harness library root')
            libraries.append((key, library['name'], roots[0], library['description']))
            parents = {row['id']: row['parentFolderId'] for row in entries}
            for row in entries:
                folder = row['id']
                if row['libraryId'] != key or not re.fullmatch(r'[A-Za-z0-9_-]{1,128}', folder):
                    raise ValueError('Invalid harness folder')
                ancestor = folder
                for _ in range(32):
                    if ancestor == roots[0]:
                        break
                    ancestor = parents[ancestor]
                else:
                    raise ValueError('Invalid harness folder hierarchy')
                folders.append((folder, key, parents[folder], row['name'], int(folder == roots[0])))

        self.db.execute('BEGIN IMMEDIATE')
        try:
            self.db.execute('DELETE FROM native_library')
            self.db.execute('DELETE FROM native_folder')
            self.db.executemany('INSERT INTO native_library VALUES(?,?,?,?)', libraries)
            self.db.executemany('INSERT INTO native_folder VALUES(?,?,?,?,?)', folders)
            if (self.db.execute('SELECT id FROM main.library INTERSECT SELECT id FROM native_library').fetchone()
                    or self.db.execute('SELECT id FROM main.folder INTERSECT SELECT id FROM native_folder').fetchone()):
                raise ValueError('Harness destination conflicts with a local destination')
            # Preserve subsequent ACL edits and grant versions on refresh.
            self.db.execute("""INSERT INTO main.grant_record(actor,library,folder,can_publish,can_read,version)
                SELECT ?,library,id,1,1,1 FROM native_folder WHERE true
                ON CONFLICT(actor,library,folder) DO NOTHING""", (PUBLISHER,))
            self.db.commit()
        except BaseException:
            self.db.rollback()
            raise
        self.refresh_after = time.monotonic() + 5
