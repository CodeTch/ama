"""Explicit correction payload allowlist and exact-byte seal, excluding the two seal files."""
from pathlib import Path
import hashlib, subprocess, sys

TREE = Path(__file__).resolve().parents[3]
OUT = Path(__file__).resolve().parent
BASE = '7e052a88fcd03d60523d013839701dd35a7f2b48'
REPORT = 'docs/acceptance/b/minutes-pure-s1-fix1-report.md'
HELPER = 'backend/adapters/src/test/java/ama/config/json/OriginGraphFix1Cases.java'
EXCLUDED = {str((OUT / name).relative_to(TREE)) for name in ['payload-allowlist.txt', 'SHA256SUMS']}

def scope():
    changes = subprocess.check_output(['git', 'diff', '--name-only', BASE], cwd=TREE).decode().splitlines()
    files = {name for name in changes if name == '.gitattributes' or name.endswith('.java')}
    files.update([REPORT, HELPER])
    files.update(str(path.relative_to(TREE)) for path in OUT.rglob('*') if path.is_file())
    return sorted(files)

if len(sys.argv) > 1 and sys.argv[1] == 'verify':
    allow = (OUT / 'payload-allowlist.txt').read_text().splitlines()
    records = [line.split('  ', 1) for line in (OUT / 'SHA256SUMS').read_text().splitlines()]
    assert [name for _, name in records] == allow
    assert set(allow) == set(scope()) - EXCLUDED
    for expected, name in records:
        assert hashlib.sha256((TREE / name).read_bytes()).hexdigest() == expected, name
    staged = subprocess.check_output(['git', 'diff', '--cached', '--name-only', BASE], cwd=TREE).decode().splitlines()
    assert staged == (OUT / 'delivery-paths.txt').read_text().splitlines()
    assert set(staged) == set(allow) | EXCLUDED
    print(f'PASS: {len(allow)} exact payload SHA256; {len(staged)} staged correction paths; no unlisted payload')
else:
    delivery = OUT / 'delivery-paths.txt'
    delivery.touch(exist_ok=True)
    files = set(scope()) | EXCLUDED
    delivery.write_text(''.join(name + '\n' for name in sorted(files)))
    payloads = sorted(files - EXCLUDED)
    (OUT / 'payload-allowlist.txt').write_text(''.join(name + '\n' for name in payloads))
    (OUT / 'SHA256SUMS').write_text(''.join(hashlib.sha256((TREE / name).read_bytes()).hexdigest() + '  ' + name + '\n' for name in payloads))
    print(f'sealed {len(payloads)} payloads / {len(files)} correction paths')
