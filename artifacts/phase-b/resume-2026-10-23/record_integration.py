#!/usr/bin/env python3
import hashlib
import json
from pathlib import Path
import subprocess
import sys

repo, owner_base, owner_head, root_base, root_head = sys.argv[1:]
repo = Path(repo)
def git(*args):
    return subprocess.check_output(['git', *args], cwd=repo)
def blob(commit, path):
    return git('show', commit + ':' + path)
def sha(value):
    return hashlib.sha256(value).hexdigest()
def subsequence(needles, haystack):
    it = iter(haystack)
    return all(any(line == candidate for candidate in it) for line in needles)
owner_base, owner_head, root_base, root_head = [git('rev-parse', ref).decode().strip() for ref in (owner_base, owner_head, root_base, root_head)]
paths = git('diff', '--name-only', owner_base, owner_head).decode().splitlines()
root_paths = git('diff', '--name-only', root_base, root_head).decode().splitlines()
assert paths == root_paths, 'integrated path set differs'
exact = []
for path in paths:
    author = blob(owner_head, path)
    adopted = blob(root_head, path)
    if path == '.gitattributes':
        assert subsequence(author.splitlines(), adopted.splitlines()), 'lost author attributes'
        assert subsequence(blob(root_base, path).splitlines(), adopted.splitlines()), 'lost earlier root attributes'
    else:
        assert author == adopted, path
        exact.append(path)
old_sql = git('diff', '--name-only', '0676a21311f2b2193f73a5e096f4a5605ee9b41f', root_head, '--', 'backend/adapters/src/main/resources/db/migration', 'docs/superpowers/plans').decode().splitlines()
assert not old_sql, old_sql
result = {'ownerBase': owner_base, 'ownerHead': owner_head, 'rootBefore': root_base, 'rootAfter': root_head, 'changedPaths': len(paths), 'exactOwnerPayloads': len(exact), 'attributeRulesBothPreservedInOrder': True, 'protectedSqlAndPlansChanged': old_sql, 'testsRerun': False, 'scope': 'Exact reviewed correction adoption; attributes are the ordered union with prior root rules', 'pathHashes': [{'path': p, 'sha256': sha(blob(root_head, p))} for p in paths]}
print(json.dumps(result, ensure_ascii=False, indent=2))