import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

from harness_publication_catalog import HarnessPublicationCatalog

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'dev'))
from local_minutes import open_knowledge, prepare_knowledge


class HarnessCatalogTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        root = Path(directory.name)
        prepare_knowledge(root, {'owner': 'catalog-test', 'knowledgePort': 45723})
        self.provider = open_knowledge(json.loads((root / 'knowledge/provider.json').read_text()))
        self.addCleanup(self.provider.db.close)
        self.catalog = HarnessPublicationCatalog(self.provider.db)
        self.library = {'id': 'kb-risk', 'name': '风险库', 'description': '风险资料', 'status': 'ACTIVE'}
        self.child = {'id': 'limits', 'libraryId': 'kb-risk', 'parentFolderId': 'risk', 'name': '限额'}
        pages = {'libraries': [self.library, dict(self.library, id='unrelated')],
                 'libraries/kb-risk/folders': [self.child, dict(self.child, id='risk', parentFolderId=None)]}
        requests = patch('harness_publication_catalog.catalog_items', side_effect=lambda path, deadline: pages[path])
        self.requests = requests.start()
        self.addCleanup(requests.stop)

    def refresh(self, actor='1'):
        self.catalog.refresh_after = 0
        self.catalog.refresh_for(actor, '/v1/knowledge/libraries', None)

    def test_native_ids_are_authorized_without_duplicating_material_libraries(self):
        self.refresh('dev-member')
        self.requests.assert_not_called()
        self.refresh()
        db = self.provider.db
        self.assertEqual(self.provider.library_metadata('1', 'kb-risk')['rootFolderId'], 'risk')
        self.assertEqual(self.provider.grant('1', 'kb-risk', 'limits')['can_publish'], 1)
        self.assertFalse(db.execute("SELECT 1 FROM main.library WHERE id='kb-risk'").fetchone())
        self.assertFalse(db.execute("SELECT 1 FROM grant_record WHERE actor!='1' AND library='kb-risk'").fetchone())
        self.provider.administer('1', 'kb-risk', 'limits', False, True)
        self.library['name'] = '新名称'
        self.refresh()
        self.assertEqual(self.provider.library_metadata('1', 'kb-risk')['name'], '新名称')
        self.assertEqual(self.provider.grant('1', 'kb-risk', 'limits', read=True)['can_publish'], 0)

    def test_invalid_tree_does_not_install_partial_metadata_and_inactive_library_disappears(self):
        self.refresh()
        self.child['parentFolderId'] = 'limits'
        self.library['name'] = '未完成的更新'
        with self.assertRaises(ValueError):
            self.refresh()
        self.assertEqual(self.provider.library_metadata('1', 'kb-risk')['name'], '风险库')
        self.library['status'] = 'INACTIVE'
        self.refresh()
        self.assertIsNone(self.provider.db.execute("SELECT 1 FROM folder WHERE library='kb-risk'").fetchone())
