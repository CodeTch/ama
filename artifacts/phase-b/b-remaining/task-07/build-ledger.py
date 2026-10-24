from pathlib import Path
import json,hashlib,subprocess,xml.etree.ElementTree as ET,difflib
O=Path('artifacts/phase-b/b-remaining/task-07');BASE='de1611a67ba483afe21f3b29bb69c2d32028efef'
prod=sorted([p for p in subprocess.check_output(['git','diff','--name-only',BASE,'--','backend'],text=True).splitlines() if '/src/main/' in p]+[p for p in subprocess.check_output(['git','ls-files','--others','--exclude-standard','backend'],text=True).splitlines() if '/src/main/' in p])
maintenance={'reconciliationMaintenanceSettlesTerminalDebtWithNoClaimableTasks','saturatedReconcilerStillMaintainsTerminalReservations'}
ledger=[];original=[]
for window in ['final-owned-integration','final-custom-replay-closure','final-maintenance-history-corrected','final-unit']:
 for p in sorted((O/window).glob('*-TEST-*.xml')):
  for c in ET.parse(p).getroot().findall('testcase'):
   if window=='final-owned-integration' and 'Task07Inherited' not in c.attrib['classname']:continue
   if window=='final-custom-replay-closure' and c.attrib['name'] in maintenance:continue
   assert not any(c.find(x) is not None for x in ['error','failure','skipped']),str(p)
   ledger.append({'kind':'unit' if window=='final-unit' else 'integration','class':c.attrib['classname'],'case':c.attrib['name'],'xml':str(p),'window':window})
keys=[(x['kind'],x['class'],x['case']) for x in ledger];assert len(keys)==len(set(keys));assert sum(x['kind']=='integration' for x in ledger)==120;assert sum(x['kind']=='unit' for x in ledger)==254
for p in sorted((O/'final-owned-integration').glob('*-TEST-*.xml')):
 for c in ET.parse(p).getroot().findall('testcase'):original.append({'class':c.attrib['classname'],'case':c.attrib['name'],'verdict':'error' if c.find('error') is not None else 'failure' if c.find('failure') is not None else 'pass','xml':str(p)})
windows=[{'window':p.parent.name,**json.loads(p.read_text())} for p in sorted(O.glob('*/receipt.json'))];proof={}
for window in ['final-owned-integration','final-custom-replay-closure','final-maintenance-history-corrected','final-unit']:
 hashes=json.loads((O/window/'source.json').read_text());assert all(hashes[x]==hashlib.sha256(Path(x).read_bytes()).hexdigest() for x in prod);proof[window]={'productionFiles':len(prod),'allProductionShaMatchFinal':True}
for p in Path('backend/integration-tests/src/test/java/ama/task').glob('Task07Inherited*.java'):assert json.loads((O/'final-owned-integration/source.json').read_text())[str(p)]==hashlib.sha256(p.read_bytes()).hexdigest()
for path in ['backend/integration-tests/src/test/java/ama/task/WorkerProcessIT.java','backend/integration-tests/src/test/java/ama/task/Task08InheritedFixture.java']:assert subprocess.check_output(['git','show',BASE+':'+path])==Path(path).read_bytes()
# Unit sources cannot refer to integration fixture helpers; production unchanged in every selected window.
for p in Path('backend').glob('*/src/test/java/**/*.java'):
 if '/integration-tests/' not in str(p):assert 'Task07' not in p.read_text(),str(p)
for old in ['final-owned-integration','final-custom-replay-closure']:
 diffs=[]
 for name in ['Task07PreparedExternalIT.java','Task07GenerationRetentionIT.java','Task07RecoveryBoundsIT.java','Task07ExternalWorkerIT.java']:
  path='backend/integration-tests/src/test/java/ama/task/'+name;before=(O/old/'source'/path).read_text();after=Path(path).read_text();diffs+=list(difflib.unified_diff(before.splitlines(True),after.splitlines(True),fromfile=old+'/'+path,tofile='final/'+path))
 (O/(old+'-helper-delta.diff')).write_text(''.join(diffs))
(O/'qualification-ledger.json').write_text(json.dumps({'base':BASE,'selected':ledger,'original116':original,'windows':windows,'productionFiles':prod,'dependencyProof':proof,'inheritedCopiesAllExactFinal':True,'unitDoesNotDependOnITFixtures':True,'unchangedBaseHelpers':['backend/integration-tests/src/test/java/ama/task/WorkerProcessIT.java','backend/integration-tests/src/test/java/ama/task/Task08InheritedFixture.java'],'replacementRule':'76 unchanged inherited cases carry from original116. All original custom40 replaced by final custom44; 42 passing final-custom-replay-closure cases carry, its two maintenance failures replaced by final-maintenance-history-corrected. Unit254 carries: production exact, no IT fixture dependency. No summing reruns.'},indent=2)+'\n')
print('Selected integration120 + unit254 =374 distinct cases')
