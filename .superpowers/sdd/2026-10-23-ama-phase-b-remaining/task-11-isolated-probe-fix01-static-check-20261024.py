#!/usr/bin/env python3
"""Finite source-only check for the Task11 probe helper fix01; never imports it."""

import ast
import hashlib
import os
from pathlib import Path
import stat
import subprocess
import tempfile

ROOT = Path('/home/tanren/ama/.worktrees/phase-b-completion')
W = ROOT / '.superpowers/sdd/2026-10-23-ama-phase-b-remaining'
HELPER = W / 'task-11-isolated-ddl-probe-prepared-20261024.py'
source = HELPER.read_text()
tree = ast.parse(source, filename=str(HELPER))
compile(tree, str(HELPER), 'exec')
print('AST_PARSE_COMPILE_OK')

v007_path = 'backend/adapters/src/main/resources/db/migration/V007__identity_sessions.sql'
result = subprocess.run(['git', 'show', 'de1611a67ba483afe21f3b29bb69c2d32028efef:' + v007_path],
                        cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                        check=True, timeout=15)
assert result.stderr == b''
assert hashlib.sha256(result.stdout).hexdigest() == '859970b83fc5623b18340c2de793ea35429cc3c0da5cbd1365b4763a5a64780d'
v007_lines = result.stdout.decode().splitlines()
assert v007_lines[29].strip() == 'ALTER TABLE enterprise_identity'
assert v007_lines[30].strip() == 'MODIFY enterprise_id VARCHAR(255) COLLATE utf8mb4_0900_bin NOT NULL,'
print('V007_SEAL_AND_FINAL_KEY_RANGE_OK')

def assignment(name):
    found = [n.value for n in tree.body if isinstance(n, ast.Assign)
             and any(isinstance(t, ast.Name) and t.id == name for t in n.targets)]
    assert len(found) == 1
    return ast.literal_eval(found[0])

seals = assignment('BASE_SEALS')
assert seals['V007__identity_sessions.sql'] == hashlib.sha256(result.stdout).hexdigest()
stubs = assignment('STUBS')
identity_stub = [x for x in stubs if x.startswith('CREATE TABLE enterprise_identity')]
assert len(identity_stub) == 1
assert 'enterprise_id varchar(255) COLLATE utf8mb4_0900_bin NOT NULL' in identity_stub[0]
assert 'COLLATE utf8mb4_bin' not in identity_stub[0]
verify = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == 'verify_baseline')
assert 'V007__identity_sessions.sql' in ast.unparse(verify)
main = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == 'main')
main_text = ast.unparse(main)
assert "'enterprise_identity': ('enterprise_id', 'varchar(255)', 'utf8mb4_0900_bin')" in main_text
print('V007_STUB_EXPECTATION_SEAL_CHAIN_OK')

public_file = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == 'public_file')
reads = [n for n in ast.walk(public_file) if isinstance(n, ast.Call) and isinstance(n.func, ast.Attribute)
         and n.func.attr == 'read_bytes']
assert len(reads) == 1
assert 'data = path.read_bytes()' in ast.unparse(public_file)
assert 'sha(data)' in ast.unparse(public_file)
assert 'return data' in ast.unparse(public_file)
assert main_text.count('public_file(R1, R1_SHA)') == 1
assert 'r1_bytes = public_file(R1, R1_SHA)' in main_text
assert "r1_sql = r1_bytes.decode('ascii')" in main_text
assert "R1.read_text" not in main_text and "R1.read_bytes" not in main_text
print('SINGLE_VERIFIED_R1_BUFFER_OK')

guard = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == 'validate_private_parent')
guard_text = ast.unparse(guard)
assert 'root.resolve(strict=True) != root' in guard_text
assert 'private_parent.resolve(strict=True) != private_parent' in guard_text
assert 'private_parent.is_symlink()' in guard_text
assert 'stat.S_ISDIR(parent_stat.st_mode)' in guard_text
assert 'parent_stat.st_uid != euid' in guard_text
assert 'parent_stat.st_mode & 18' in guard_text  # ast.unparse renders 0o022 as decimal.
assert main_text.index("validate_private_parent(ROOT, ROOT / '.tools', os.geteuid())") < main_text.index('state_dir.mkdir(mode=448)')
assert main_text.index('state_dir.mkdir(mode=448)') < main_text.index('p.record()')
print('GUARD_BEFORE_FIRST_PRIVATE_WRITE_OK')

# Exercise only the extracted pure guard function on a new disposable W fixture.
module = ast.Module(body=[guard], type_ignores=[])
namespace = {'stat': stat, 'fail': lambda message: (_ for _ in ()).throw(RuntimeError(message))}
exec(compile(module, '<extracted-pure-path-guard>', 'exec'), namespace)
check = namespace['validate_private_parent']
with tempfile.TemporaryDirectory(prefix='task11-fix01-pathguard-', dir=W) as tmp:
    fixture_root = Path(tmp) / 'root'
    fixture_root.mkdir(mode=0o700)
    parent = fixture_root / '.tools'
    parent.mkdir(mode=0o755)
    os.chmod(parent, 0o755)
    check(fixture_root, parent, os.geteuid())
    print('GUARD_ACCEPTS_OWNER_0755_OK')
    os.chmod(parent, 0o775)
    try:
        check(fixture_root, parent, os.geteuid())
    except RuntimeError:
        print('GUARD_REJECTS_GROUP_WRITABLE_OK')
    else:
        raise AssertionError('group-writable parent accepted')
    os.chmod(parent, 0o755)
    try:
        check(fixture_root, parent, os.geteuid() + 1)
    except RuntimeError:
        print('GUARD_REJECTS_WRONG_OWNER_OK')
    else:
        raise AssertionError('wrong owner accepted')
    parent.rmdir()
    target = Path(tmp) / 'outside'
    target.mkdir()
    parent.symlink_to(target, target_is_directory=True)
    try:
        check(fixture_root, parent, os.geteuid())
    except RuntimeError:
        print('GUARD_REJECTS_SYMLINK_PARENT_OK')
    else:
        raise AssertionError('symlink parent accepted')
print('NO_HELPER_IMPORT_OR_EXECUTION_OK')
