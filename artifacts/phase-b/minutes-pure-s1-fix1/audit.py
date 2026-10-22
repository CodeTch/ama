"""Read-only scope, evidence and independently encoded vector verification."""
from pathlib import Path
import hashlib, json, subprocess, xml.etree.ElementTree as ET

TREE = Path(__file__).resolve().parents[3]
OUT = Path(__file__).resolve().parent
BASE = '7e052a88fcd03d60523d013839701dd35a7f2b48'

def sha(data):
    return hashlib.sha256(data).hexdigest()

def enc(value):
    if value is None:
        return b'\x00'
    if isinstance(value, bool):
        return b'\x02' if value else b'\x01'
    if isinstance(value, int):
        return b'\x03' + value.to_bytes(8, 'big')
    if isinstance(value, str):
        raw = value.encode('utf-8', 'strict')
        return b'\x04' + len(raw).to_bytes(8, 'big') + raw
    if isinstance(value, list):
        return b'\x05' + len(value).to_bytes(8, 'big') + b''.join(enc(v) for v in value)
    return b'\x06' + len(value).to_bytes(8, 'big') + b''.join(enc(k) + enc(value[k]) for k in sorted(value, key=lambda k: k.encode('utf-8')))

baseline = json.loads((OUT / 'baseline-273.json').read_bytes())
preserved = []
modified = []
for record in baseline:
    name = record['path']
    git_bytes = subprocess.check_output(['git', 'show', f'{BASE}:{name}'], cwd=TREE)
    assert sha(git_bytes) == record['sha256']
    current = (TREE / name).read_bytes()
    if current == git_bytes:
        preserved.append(name)
    else:
        modified.append(name)
        assert name == '.gitattributes' or name.endswith('.java'), name
assert len(baseline) == 273
old_payloads = []
old_payloads_unchanged = []
for line in (TREE / 'artifacts/phase-b/b07-pure-s1/SHA256SUMS').read_text().splitlines():
    expected, name = line.split('  ', 1)
    assert sha(subprocess.check_output(['git', 'show', f'{BASE}:{name}'], cwd=TREE)) == expected, name
    if sha((TREE / name).read_bytes()) == expected:
        old_payloads_unchanged.append(name)
    else:
        assert name in modified
    old_payloads.append(name)
assert len(old_payloads) == 271
for record in json.loads((OUT / 'custody.json').read_bytes()):
    assert sha((OUT / record['path']).read_bytes()) == record['sha256']
    assert sha(Path(record['source']).read_bytes()) == record['sha256']

vectors = []
for source in sorted((TREE / 'backend/core/src/test/resources/ama/minutes/vectors').glob('*.json')):
    raw = enc(json.loads(source.read_bytes()))
    assert raw.hex() == source.with_suffix('.hex').read_text().strip()
    assert sha(raw) == source.with_suffix('.sha256').read_text().strip()
    vectors.append({'path': str(source.relative_to(TREE)), 'encodedBytes': len(raw), 'sha256': sha(raw)})
assert len(vectors) == 18

def cases(folder):
    found = set()
    for source in folder.glob('TEST-*.xml'):
        root = ET.parse(source).getroot()
        assert all(int(root.get(k)) == 0 for k in ['failures', 'errors', 'skipped'])
        for case in root.findall('testcase'):
            value = (case.get('classname'), case.get('name'))
            assert value not in found
            found.add(value)
    return found

old_cases = cases(TREE / 'artifacts/phase-b/b07-pure-s1/junit/final-owned')
final_cases = cases(OUT / 'junit/06-complete-owned')
canonical = cases(OUT / 'junit/07-canonical-focused')
assert len(old_cases) == 97 and len(final_cases) == 112 and old_cases <= final_cases
assert len(canonical) == 25 and canonical <= final_cases
red = json.loads((OUT / '01-graph-red-summary.json').read_bytes())
assert (red['tests'], red['failures'], red['errors'], red['skipped']) == (8, 5, 0, 0)
assert 'COMPILATION ERROR' in (OUT / 'raw/02-artifact-api-setup.log').read_text()
assert 'COMPILATION ERROR' in (OUT / 'raw/03-graph-artifact-green.log').read_text()
sources = subprocess.check_output(['git', 'diff', '--name-only', BASE], cwd=TREE).decode().splitlines()
sources += ['backend/adapters/src/test/java/ama/config/json/OriginGraphFix1Cases.java']
source_hashes = []
for name in sorted(set(sources)):
    if name == '.gitattributes' or name.startswith('backend/') and name.endswith('.java'):
        source_hashes.append({'path': name, 'sha256': sha((TREE / name).read_bytes())})
(OUT / 'final-source-hashes.json').write_text(json.dumps(source_hashes, indent=2) + '\n')
xml_hashes = [{'path': str(source.relative_to(TREE)), 'sha256': sha(source.read_bytes())} for source in sorted((OUT / 'junit').rglob('*.xml'))]
(OUT / 'xml-hashes.json').write_text(json.dumps(xml_hashes, indent=2) + '\n')
result = {'base': BASE, 'baselinePathsInGit': len(baseline), 'baselinePathsUnchangedNow': len(preserved), 'baselinePathsIntentionallyModified': modified, 'oldPayloadsPreservedInGit': len(old_payloads), 'oldPayloadsUnchangedNow': len(old_payloads_unchanged), 'custodyOriginalsUnchanged': 6, 'independentVectors': vectors, 'oldTests': len(old_cases), 'finalTests': len(final_cases), 'newTests': sorted(final_cases - old_cases), 'canonicalSubset': len(canonical), 'redAssertions': 5, 'setupFailureRounds': ['02', '03'], 'sourceHashes': len(source_hashes), 'xmlHashes': len(xml_hashes)}
(OUT / 'audit.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps({k: v for k, v in result.items() if k not in ['independentVectors', 'newTests', 'baselinePathsIntentionallyModified']}, indent=2))
