from pathlib import Path
import json,hashlib,subprocess,xml.etree.ElementTree as ET
O=Path('artifacts/phase-b/b-remaining/task-07');F=O/'fix02';W=Path('.superpowers/sdd/2026-10-23-ama-phase-b-remaining');A=W/'resume-fix02-before-records-20261024'
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def digest(data):return hashlib.sha256(data.encode()).hexdigest()
def outside_method(text,name):
 start=text.index('@Test void '+name+'(');body=text.index('{',start);i=body;depth=0;quote=None
 while i<len(text):
  ch=text[i]
  if quote:
   if ch=='\\':i+=2;continue
   if ch==quote:quote=None
  elif ch in ['"',"'"]:quote=ch
  elif text.startswith('//',i):
   i=text.index('\n',i);continue
  elif text.startswith('/*',i):
   i=text.index('*/',i)+2;continue
  elif ch=='{':depth+=1
  elif ch=='}':
   depth-=1
   if depth==0:return text[:start]+'<METHOD:'+name+'>'+text[i+1:]
  i+=1
 raise AssertionError('Unclosed method')
previous=A/O/'qualification-ledger.json';old=json.loads(previous.read_text());production={p:sha(p) for p in old['productionFiles']}
windows=[];selected=[];original_cases=[];replacements=[]
for receipt in sorted(F.glob('*/receipt.json')):
 d=json.loads(receipt.read_text());cases=[]
 for xml in d['reports']:
  for case in ET.parse(xml).getroot().findall('testcase'):
   status='FAIL' if case.find('failure') is not None else 'ERROR' if case.find('error') is not None else 'SKIP' if case.find('skipped') is not None else 'PASS'
   cases.append({'class':case.attrib['classname'],'case':case.attrib['name'],'status':status,'xml':xml})
 name=receipt.parent.name;windows.append({'window':'fix02/'+name,'receipt':str(receipt),'receiptSha256':sha(receipt),**d,'cases':cases})
 if name=='final-integration':
  original_cases=cases;chosen=[c for c in cases if c['status']=='PASS'];assert len(chosen)==151
  selected.extend({'kind':'integration','window':'fix02/'+name,**c} for c in chosen)
 elif name in ['inherited-diagnostic','final-unit']:
  assert d['exit']==0 and all(c['status']=='PASS' for c in cases)
  if name=='inherited-diagnostic':replacements=cases;assert len(cases)==2
  selected.extend({'kind':'unit' if name=='final-unit' else 'integration','window':'fix02/'+name,**c} for c in cases)
keys={(x['class'],x['case']) for x in selected};assert len(keys)==len(selected)==407
assert {(c['class'],c['case']) for c in original_cases if c['status']!='PASS'}=={(c['class'],c['case']) for c in replacements}
oldkeys={(x['class'],x['case']) for x in old['selected']};assert oldkeys<=keys
new=[x for x in selected if (x['class'],x['case']) not in oldkeys];assert len(new)==12
current={str(p):sha(p) for p in Path('backend').rglob('*') if p.is_file() and '/target/' not in str(p) and p.suffix in ['.java','.sql']}
changes={'backend/integration-tests/src/test/java/ama/task/Task07RecoveryBoundsIT.java':'transientSettlementFailureConsumesPositionThenRecoversOnTheNextPass','backend/integration-tests/src/test/java/ama/task/Task07InheritedB06QueueSeamsIT.java':'disabledExpiredCurrentExternalFactMustStillBeRetained'}
proof=[]
for name in ['final-integration','inherited-diagnostic','final-unit']:
 snapshot=json.loads((F/name/'source.json').read_text());different={p for p in current if snapshot.get(p)!=current[p]};assert different==(set(changes) if name=='final-integration' else set())
 for path,value in production.items():assert snapshot[path]==value
 exact=[p for p in (F/name/'source').rglob('*') if p.is_file()]
 assert all(sha(p)==sha(p.relative_to(F/name/'source')) for p in exact if str(p.relative_to(F/name/'source')) not in different)
 exceptions=[]
 for path in sorted(different):
  before=(F/name/'source'/path).read_text();after=Path(path).read_text();beforeOutside=outside_method(before,changes[path]);afterOutside=outside_method(after,changes[path]);assert beforeOutside==afterOutside
  exceptions.append({'path':path,'beforeSha256':snapshot[path],'currentSha256':current[path],'onlyChangedMethod':changes[path],'outsideMethodSha256':digest(beforeOutside),'outsideMethodByteEqual':True,'diagnosticOnly':True})
 proof.append({'window':'fix02/'+name,'fullBackendJavaSqlHashMapExactExceptExplicitMethods':True,'all14Task07ProductionExactCurrent':True,'exactCopiedSourceFiles':len(exact),'allOtherCopiedBytesExactCurrent':True,'methodExceptions':exceptions,'sourceManifestSha256':sha(F/name/'source.json')})
# This nested fixture is used by PreparedCompletionIT; it lies outside the changed method.
path='backend/integration-tests/src/test/java/ama/task/Task07InheritedB06QueueSeamsIT.java';marker='    static final class ProbeSource'
a=(F/'final-integration/source'/path).read_text().split(marker,1)[1];b=Path(path).read_text().split(marker,1)[1];assert a==b
(F/'source-dependency-proof.json').write_text(json.dumps({'windows':proof,'inheritedProbeSourceExactCurrent':True,'probeSourceTailSha256':digest(a),'selection':'151 passing integration cases + 2 explicitly replaced diagnostic cases +254 units. Two earlier causes remain UNKNOWN.'},indent=2)+'\n')
modified=subprocess.check_output(['git','diff','--name-only','6cce4794dba473825d83fb9bccafc043cd611b5c','--','backend'],text=True).splitlines()
result={'base':old['base'],'fixBase':'6cce4794dba473825d83fb9bccafc043cd611b5c','initialDelivery':old['initialDelivery'],'original116':old['original116'],'previousFix01':{'ledger':str(previous),'sha256':sha(previous),'selected':old['selected'],'windows':old['windows'],'dependencyProof':old['dependencyProof'],'status':'Historical395 qualification is replaced by407 selected actual cases; original raw is immutable.'},'selected':selected,'newCaseIdentities':new,'windows':windows,'productionFiles':production,'modifiedProductionFiles':[p for p in modified if '/src/main/' in p],'dependencyProof':proof,'replacementRule':'151 PASS from final-integration + 2 PASS from inherited-diagnostic replacing its one FAIL and one pre-business ERROR +254 final-unit. All407 selected identities unique. Original two causes UNKNOWN; later PASS does not establish flake root-cause closure. No production changes after first GREEN attempt.'}
(O/'qualification-ledger.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({'selected':len(selected),'newCases':len(new),'windows':len(windows),'replacementCases':[(c['class'],c['case']) for c in replacements]}))
