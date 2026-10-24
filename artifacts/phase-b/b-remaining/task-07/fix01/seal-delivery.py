from pathlib import Path
import hashlib,json,subprocess
O=Path('artifacts/phase-b/b-remaining/task-07');F=O/'fix01';W=Path('.superpowers/sdd/2026-10-23-ama-phase-b-remaining');A=W/'task-07-before-fix01-20261024'
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
old=json.loads((A/'manifest.json').read_text());custody=json.loads((F/'custody-initial-seal.json').read_text())
(O/'delivery-custody.json').write_text(json.dumps({'initialDelivery':{'path':str(A/'delivery-custody.json'),'sha256':sha(A/'delivery-custody.json'),'result':json.loads((A/'delivery-custody.json').read_text())},'fix01':custody,'fix01Evidence':str(F/'custody-initial-seal.json')},indent=2)+'\n')
backend=set(subprocess.check_output(['git','diff','--name-only','84b4b0bbf8212ec5606c3f8583a0989f10af681e','--','backend'],text=True).splitlines())
backend.update(subprocess.check_output(['git','ls-files','--others','--exclude-standard','backend'],text=True).splitlines())
assert all(p.endswith('.java') for p in backend)
metadata={str(O/p) for p in ['report.md','manifest.json','qualification-ledger.json','delivery-custody.json']}|{str(W/'task-07-report.md'),str(W/'task-07-fix01-report-20261024.md')}
raw={str(p) for p in F.rglob('*') if p.is_file() and not any(x in p.parts for x in ['classes','__pycache__'])}
owned=sorted(backend|metadata|raw|{str(F/'owned-paths.txt')});(F/'owned-paths.txt').write_text('\n'.join(owned)+'\n')
paths={x['path'] for x in old['entries']}|set(owned);paths.discard(str(O/'manifest.json'))
entries=[{'path':p,'bytes':Path(p).stat().st_size,'sha256':sha(p)} for p in sorted(paths)]
result={**{k:v for k,v in old.items() if k!='entries'},'fixBase':'84b4b0bbf8212ec5606c3f8583a0989f10af681e','deliveryHead':'containing fix commit; exact SHA returned in author handoff','initialManifest':{'path':str(A/'manifest.json'),'sha256':sha(A/'manifest.json')},'selectedTests':{'integration':141,'unit':254,'distinct':395,'failure':0,'error':0,'skip':0},'fix01OwnedPaths':owned,'entries':entries}
(O/'manifest.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({'manifestEntries':len(entries),'fixOwnedPaths':len(owned),'backendPaths':len(backend),'newRawPaths':len(raw),'reportSha256':sha(O/'report.md'),'fixReportSha256':sha(W/'task-07-fix01-report-20261024.md'),'manifestSha256':sha(O/'manifest.json')}))
