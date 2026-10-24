from pathlib import Path
import json,hashlib,subprocess,xml.etree.ElementTree as ET
O=Path('artifacts/phase-b/b-remaining/task-07');F=O/'fix01';W=Path('.superpowers/sdd/2026-10-23-ama-phase-b-remaining');A=W/'task-07-before-fix01-20261024'
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
old=json.loads((A/'qualification-ledger.json').read_text());production={p:sha(p) for p in old['productionFiles']}
windows=[];selected=[]
for receipt in sorted(F.glob('*/receipt.json')):
 d=json.loads(receipt.read_text());cases=[]
 for xml in d['reports']:
  for case in ET.parse(xml).getroot().findall('testcase'):
   status='FAIL' if case.find('failure') is not None else 'ERROR' if case.find('error') is not None else 'SKIP' if case.find('skipped') is not None else 'PASS'
   cases.append({'class':case.attrib['classname'],'case':case.attrib['name'],'status':status,'xml':xml})
 windows.append({'window':'fix01/'+receipt.parent.name,'receipt':str(receipt),'receiptSha256':sha(receipt),**d,'cases':cases})
 if receipt.parent.name in ['final-integration','final-unit']:
  assert d['exit']==0 and all(c['status']=='PASS' for c in cases)
  kind='integration' if receipt.parent.name=='final-integration' else 'unit'
  selected.extend({'kind':kind,'window':'fix01/'+receipt.parent.name,**c} for c in cases)
keys={(x['class'],x['case']) for x in selected};assert len(keys)==len(selected)==395
oldkeys={(x['class'],x['case']) for x in old['selected']};assert oldkeys<=keys
new=[x for x in selected if (x['class'],x['case']) not in oldkeys];assert len(new)==21
current={str(p):sha(p) for p in Path('backend').rglob('*') if p.is_file() and '/target/' not in str(p) and p.suffix in ['.java','.sql']}
proof=[]
for name in ['final-integration','final-unit']:
 snapshot=json.loads((F/name/'source.json').read_text());assert snapshot==current
 for path,digest in production.items():assert snapshot[path]==digest
 exact=list((F/name/'source').rglob('*'));exact=[p for p in exact if p.is_file()]
 assert all(sha(p)==sha(Path(str(p.relative_to(F/name/'source')))) for p in exact)
 proof.append({'window':'fix01/'+name,'fullBackendJavaSqlHashMapExactCurrent':True,'all14ProductionExactCurrent':True,'exactCopiedSourceFiles':len(exact),'allCopiedBytesExactCurrent':True,'sourceManifestSha256':sha(F/name/'source.json')})
modified=subprocess.check_output(['git','diff','--name-only','84b4b0bbf8212ec5606c3f8583a0989f10af681e','--','backend'],text=True).splitlines()
result={'base':old['base'],'fixBase':'84b4b0bbf8212ec5606c3f8583a0989f10af681e','initialDelivery':{'ledger':str(A/'qualification-ledger.json'),'sha256':sha(A/'qualification-ledger.json'),'selected':old['selected'],'windows':old['windows'],'dependencyProof':old['dependencyProof'],'status':'Historical374 qualification superseded in full by corrected-source final windows; every original case key rerun.'},'original116':old['original116'],'selected':selected,'newCaseIdentities':new,'windows':windows,'productionFiles':production,'modifiedProductionFiles':[p for p in modified if '/src/main/' in p],'dependencyProof':proof,'replacementRule':'Select only fix01/final-integration141 and fix01/final-unit254. Prior windows preserve RED/GREEN/error/diagnostic lineage and are not added. Original374 all rerun; no retroactive production equivalence.'}
(O/'qualification-ledger.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({'selected':len(selected),'newCases':len(new),'windows':len(windows),'productionModified':len(result['modifiedProductionFiles']),'dependencyProof':proof}))
