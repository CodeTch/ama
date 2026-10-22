"""Bounded local evidence capture; never sources or prints the private runtime environment."""
from pathlib import Path
import hashlib, json, shutil, subprocess, sys, xml.etree.ElementTree as ET

TREE = Path(__file__).resolve().parents[3]
OUT = Path(__file__).resolve().parent
BASE = '7e052a88fcd03d60523d013839701dd35a7f2b48'

def sha(data):
    return hashlib.sha256(data).hexdigest()

if sys.argv[1] == 'baseline':
    old = subprocess.check_output(['git', 'diff', '--name-only', '8da440d39e1b8e8dcdfc34722908d29b5aa3aa8a', BASE], cwd=TREE).decode().splitlines()
    assert len(old) == 273
    records = []
    for name in old:
        data = (TREE / name).read_bytes()
        assert data == subprocess.check_output(['git', 'show', f'{BASE}:{name}'], cwd=TREE)
        records.append({'path': name, 'bytes': len(data), 'sha256': sha(data)})
    (OUT / 'baseline-273.json').write_text(json.dumps(records, indent=2) + '\n')
    checkpoint = Path('/home/tanren/ama/artifacts/checkpoints/2026-10-22-development-pause')
    custody = []
    for name in ['task-B-07-pure-s1-fix1-brief.md', 'task-B-07-pure-s1-review.md', 'task-B-07-pure-s1-review-audit.py', 'task-B-07-pure-s1-review-audit.json', 'task-B-07-pure-s1-review-probe.java', 'task-B-07-pure-s1-review-probe.log']:
        source = checkpoint / name
        if not source.exists():
            source = Path('/home/tanren/ama/.worktrees/phase-a/.superpowers/sdd/2026-10-21-ama-phase-b') / name
        data = source.read_bytes()
        (OUT / 'custody').mkdir(exist_ok=True)
        (OUT / 'custody' / name).write_bytes(data)
        custody.append({'source': str(source), 'path': f'custody/{name}', 'bytes': len(data), 'sha256': sha(data)})
    audit = json.loads((OUT / 'custody/task-B-07-pure-s1-review-audit.json').read_bytes())
    assert audit['totalPaths'] == 273 and audit['finalUniqueTests'] == 97
    (OUT / 'custody.json').write_text(json.dumps(custody, indent=2) + '\n')
    print(f'baseline: {len(records)} exact Git/worktree paths; custody: {len(custody)} originals')
elif sys.argv[1] == 'red-source':
    # Reconstruct the test-only prefix over exact BASE and verify against hashes
    # actually captured immediately after round 01, before production changes.
    name = 'backend/adapters/src/test/java/ama/config/json/RawHashChainTest.java'
    current = (TREE / name).read_text()
    marker = 'class RawHashChainTest {\n'
    prefix = current.split(marker, 1)[1].split(' @Test void fix1DeepRawBackedOriginsMeetExactDepthAndRejectOneMore()', 1)[0]
    baseline = subprocess.check_output(['git', 'show', f'{BASE}:{name}'], cwd=TREE).decode()
    reconstructed = baseline.replace(marker, marker + prefix, 1).encode()
    hashes = {path: digest for digest, path in (line.split('  ', 1) for line in (OUT / '01-graph-red-source.sha256').read_text().splitlines())}
    assert sha(reconstructed) == hashes[name]
    destination = OUT / 'red-source'
    destination.mkdir(exist_ok=True)
    (destination / 'RawHashChainTest.java').write_bytes(reconstructed)
    helper = 'backend/adapters/src/test/java/ama/config/json/OriginGraphFix1Cases.java'
    helper_bytes = (TREE / helper).read_bytes()
    assert sha(helper_bytes) == hashes[helper]
    (destination / 'OriginGraphFix1Cases.java').write_bytes(helper_bytes)
    print('PASS: two exact round-01 test source snapshots match pre-correction captured SHA256')
elif sys.argv[1] == 'junit':
    label = sys.argv[2]
    destination = OUT / 'junit' / label
    destination.mkdir(parents=True, exist_ok=True)
    suites = []
    for module in ['contracts', 'core', 'adapters']:
        for source in (TREE / 'backend' / module / 'target/surefire-reports').glob('TEST-*.xml'):
            root = ET.parse(source).getroot()
            if root.get('name') not in sys.argv[3:]:
                continue
            shutil.copyfile(source, destination / source.name)
            suites.append({'suite': root.get('name'), **{key: int(root.get(key)) for key in ['tests', 'failures', 'errors', 'skipped']}, 'sha256': sha(source.read_bytes()), 'cases': [{'class': c.get('classname'), 'name': c.get('name'), 'failure': c.find('failure') is not None, 'error': c.find('error') is not None} for c in root.findall('testcase')]})
    summary = {key: sum(s[key] for s in suites) for key in ['tests', 'failures', 'errors', 'skipped']}
    summary.update(suites=len(suites), results=suites)
    (OUT / f'{label}-summary.json').write_text(json.dumps(summary, indent=2) + '\n')
    print(json.dumps({key: value for key, value in summary.items() if key != 'results'}))
