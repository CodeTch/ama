from pathlib import Path
import json,hashlib,subprocess,datetime,xml.etree.ElementTree as ET,shlex,re,difflib
r=Path('/home/tanren/ama/.worktrees/phase-b-completion');w=r/'.superpowers/sdd/2026-10-23-ama-phase-b-remaining';e=r/'artifacts/phase-b/b-remaining/task-05-fix-01'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
write=lambda name,data:(e/name).write_text(json.dumps(data,indent=2,ensure_ascii=False)+'\n')
product=['backend/adapters/src/main/java/ama/storage/LocalFileClient.java','backend/adapters/src/main/java/ama/storage/SourceReadLifetime.java','backend/adapters/src/main/java/ama/storage/s3/S3FileStorage.java','backend/adapters/src/main/java/ama/storage/s3/ScopedS3HttpClient.java','backend/adapters/src/main/java/ama/task/TranscriptImportRuntime.java','backend/worker/src/main/java/ama/worker/WorkerLoop.java','backend/worker/src/main/java/ama/worker/TranscriptWorkerRuntimeProvider.java']
tests=['backend/adapters/src/test/java/ama/storage/SourceReadLifetimeTest.java','backend/worker/src/test/java/ama/worker/WorkerShutdownTest.java']+[str(p.relative_to(r)) for p in sorted((r/'backend/integration-tests/src/test/java').glob('**/Task05Fix*.java'))]
owned=product+tests
write('explicit-source-paths.json',{'product':product,'test':tests,'all':owned})
final=json.loads((e/'final-all-units/source.json').read_text())
actual={str(p.relative_to(r)):sha(p) for p in r.glob('backend/**/*') if p.is_file() and ('src' in p.parts or p.name=='pom.xml') and 'target' not in p.parts}
assert final==actual,'source changed after final allunits'
write('final-source-seal.json',actual)
snap=e/'final-owned-source';snap.mkdir(exist_ok=True)
for name in owned:
 dest=snap/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes((r/name).read_bytes())

baseline=json.loads((e/'protected-old-evidence.json').read_text());changes=[]
for name,digest in baseline.items():
 if not (r/name).exists() or sha(r/name)!=digest:changes.append(name)
assert not changes,changes
write('old-evidence-verification.json',{'baseline':'protected-old-evidence.json','baselineSha256':sha(e/'protected-old-evidence.json'),'protectedFiles':len(baseline),'changed':changes,'utc':datetime.datetime.now(datetime.timezone.utc).isoformat()})

index=[];windows=[]
for directory in sorted(e.iterdir()):
 if not directory.is_dir() or not (directory/'command.json').exists():continue
 command=json.loads((directory/'command.json').read_text());suites=[];counts={'tests':0,'failures':0,'errors':0,'skipped':0}
 for path in sorted(directory.glob('*-TEST-*.xml')):
  node=ET.parse(path).getroot();suites.append({'name':node.get('name'),'cases':[t.get('name') for t in node.findall('testcase')]})
  for k in counts:counts[k]+=int(node.get(k,'0'))
 index.append({'run':directory.name,**command,**counts,'suites':suites,'qualification':'fresh runner-collected XML; compile/setup failures may have zero XML'})
 before=json.loads((directory/'source.json').read_text());names=set(before)|set(actual)
 changed=sorted(name for name in names if before.get(name)!=actual.get(name))
 windows.append({'run':directory.name,'changedFromFinal':changed,'productChangedFromFinal':[p for p in changed if '/src/main/' in p or p.endswith('/pom.xml')],'testChangedFromFinal':[p for p in changed if '/src/test/' in p]})
write('test-command-index.json',index);write('source-windows.json',windows)
selected=['green-s3-open','cv1-genuine-carriers-and-validator','s3-before-headers','final-affected-regressions','final-local-lifetime-custody']
seen=set()
for run in selected:
 row=next(x for x in index if x['run']==run);assert row['exit']==0 and not(row['failures'] or row['errors'] or row['skipped'])
 window=next(x for x in windows if x['run']==run);assert not window['productChangedFromFinal'],window
 for suite in row['suites']:
  for case in suite['cases']:
   identity=suite['name']+'#'+case;assert identity not in seen;seen.add(identity)
assert len(seen)==16,len(seen)
write('nonoverlap-counts.json',{'selectedIntegrationRuns':selected,'distinctPassingIntegrationCases':len(seen),'caseIdentities':sorted(seen),'finalAllUnits':233,'focusedShutdownAlreadyIncludedInUnits':2,'fixtureMaintenanceSeparate':1,'qualification':'16 distinct integration cases on final product; tests span source-windows.json, no overlapping rerun sum'})

copies=[('backend/integration-tests/src/test/java/ama/storage/Task05TransportIT.java','backend/integration-tests/src/test/java/ama/storage/Task05FixTransportRegressionIT.java'),('backend/integration-tests/src/test/java/ama/storage/s3/Task05S3DeadlineIT.java','backend/integration-tests/src/test/java/ama/storage/s3/Task05FixS3DeadlineRegressionIT.java'),('backend/integration-tests/src/test/java/ama/task/Task05CancellationIT.java','backend/integration-tests/src/test/java/ama/task/Task05FixCancellationRegressionIT.java'),('backend/integration-tests/src/test/java/ama/storage/Task05NativeFixture.java','backend/integration-tests/src/test/java/ama/storage/Task05FixNativeFixture.java'),('backend/integration-tests/src/test/java/ama/storage/s3/WireProxy.java','backend/integration-tests/src/test/java/ama/storage/s3/Task05FixProxy.java')]
copy_manifest=[]
for original,copy in copies:
 out='forward-'+Path(copy).stem+'.diff'
 (e/out).write_text(''.join(difflib.unified_diff((r/original).read_text().splitlines(True),(r/copy).read_text().splitlines(True),fromfile=original,tofile=copy)))
 copy_manifest.append({'original':original,'originalSha256':sha(r/original),'copy':copy,'copySha256':sha(r/copy),'diff':out})
write('forward-copy-manifest.json',copy_manifest)
(e/'.gitattributes').write_text('* -text -whitespace\n')

secrets=[]
for file in [r/'.tools/phase-b-remaining/env.sh',r/'.tools/task05-5237a2db-e8d5-4cbe-b357-b35df5cfcba5/client.env']:
 for line in file.read_text().splitlines():
  for item in shlex.split(line,comments=True):
   if '=' in item:
    key,value=item.split('=',1)
    if any(word in key for word in ['PASSWORD','SECRET','ACCESS']) and len(value)>=8:secrets.append(value.encode())
secrets.append((r/'.tools/task05-5237a2db-e8d5-4cbe-b357-b35df5cfcba5/pki/keystore-password').read_bytes().strip())
scan=[p for p in e.rglob('*') if p.is_file() and p.name not in {'evidence-manifest.json','secret-scan.json'}]+[r/name for name in owned]
scan += [w/'task-05-fix-01-report.md',w/'task-05-report.md']+list(w.glob('task-05-fix-01-*.py'))
findings=[]
for path in scan:
 if not path.exists():continue
 data=path.read_bytes()
 if any(secret and secret in data for secret in secrets) or re.search(rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',data):findings.append(str(path.relative_to(r)))
assert not findings,findings
write('secret-scan.json',{'files':len([p for p in scan if p.exists()]),'privateValueCount':len(secrets),'findings':findings,'method':'exact private PASSWORD/SECRET/ACCESS values and PKI password plus private-key PEM patterns; no values emitted'})
manifest=[]
for p in sorted(e.rglob('*')):
 if p.is_file() and p.name!='evidence-manifest.json':manifest.append({'path':str(p.relative_to(r)),'bytes':p.stat().st_size,'sha256':sha(p)})
write('evidence-manifest.json',manifest)
print(json.dumps({'sourcePaths':len(owned),'protectedOldFiles':len(baseline),'finalUnits':233,'distinctFinalProductIntegration':len(seen),'secretFindings':len(findings),'evidenceFiles':len(manifest)}))
