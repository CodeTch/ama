from pathlib import Path
import hashlib,json,subprocess
O=Path('artifacts/phase-b/b-remaining/task-07');F=O/'fix02';W=Path('.superpowers/sdd/2026-10-23-ama-phase-b-remaining');A=W/'resume-fix02-before-records-20261024'
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
old=json.loads((A/O/'manifest.json').read_text());custody=json.loads((F/'custody-preseal.json').read_text())
(O/'delivery-custody.json').write_text(json.dumps({'previousFix01':{'path':str(A/O/'delivery-custody.json'),'sha256':sha(A/O/'delivery-custody.json'),'result':json.loads((A/O/'delivery-custody.json').read_text())},'fix02':custody,'fix02Evidence':str(F/'custody-preseal.json')},indent=2)+'\n')
backend=set(subprocess.check_output(['git','diff','--name-only','6cce4794dba473825d83fb9bccafc043cd611b5c','--','backend'],text=True).splitlines())
backend.update(subprocess.check_output(['git','ls-files','--others','--exclude-standard','backend'],text=True).splitlines())
assert len(backend)==5 and all(p.endswith('.java') for p in backend)
metadata={str(O/p) for p in ['report.md','manifest.json','qualification-ledger.json','delivery-custody.json']}|{str(W/'task-07-report.md'),str(W/'task-07-fix02-report-20261024.md')}
raw={str(p) for p in F.rglob('*') if p.is_file() and not any(x in p.parts for x in ['classes','__pycache__'])}
owned=sorted(backend|metadata|raw|{str(F/'owned-paths.txt')});(F/'owned-paths.txt').write_text('\n'.join(owned)+'\n')
paths={x['path'] for x in old['entries']}|set(owned);paths.discard(str(O/'manifest.json'))
entries=[{'path':p,'bytes':Path(p).stat().st_size,'sha256':sha(p)} for p in sorted(paths)]
result={**{k:v for k,v in old.items() if k!='entries'},'fixBase':'6cce4794dba473825d83fb9bccafc043cd611b5c','deliveryHead':'containing fix02 commit; exact SHA returned in author handoff','previousFix01Manifest':{'path':str(A/O/'manifest.json'),'sha256':sha(A/O/'manifest.json')},'selectedTests':{'integration':153,'unit':254,'distinct':407,'failure':0,'error':0,'skip':0,'composition':'151 PASS from full final-integration;2 exact later diagnostic replacements;254 units. Full final-integration command exit1/FAIL1/ERROR1 is preserved; those two causes remain UNKNOWN.'},'fix02OwnedPaths':owned,'entries':entries}
(O/'manifest.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({'manifestEntries':len(entries),'fixOwnedPaths':len(owned),'backendPaths':len(backend),'newRawPaths':len(raw),'sourceSealSha256':sha(F/'source-check.json'),'reportSha256':sha(O/'report.md'),'fixReportSha256':sha(W/'task-07-fix02-report-20261024.md'),'manifestSha256':sha(O/'manifest.json')}))
