"""Independent scoped B07 fix1 custody/log/XML audit. Never writes the author tree."""
from pathlib import Path
import subprocess,hashlib,json,re,xml.etree.ElementTree as ET
TREE=Path('/home/tanren/ama/.worktrees/phase-b-minutes-pure')
OUT=Path('/home/tanren/ama/.superpowers/sdd/2026-10-23-resume')
E=TREE/'artifacts/phase-b/minutes-pure-s1-fix1'
BASE='7e052a88fcd03d60523d013839701dd35a7f2b48'; HEAD='13b8f1d16f85787c12626faa41c7e511403f16f9'
def git(*args):return subprocess.check_output(['git','-C',str(TREE),*args])
def sha(b):return hashlib.sha256(b).hexdigest()
def blob(rev,p):return git('show',f'{rev}:{p}')
assert git('rev-parse','HEAD').decode().strip()==HEAD
before=git('status','--porcelain=v1');assert before==b''
paths=git('diff','--name-only',BASE,HEAD).decode().splitlines();assert len(paths)==79
package=(OUT/'task-B-07-fix1-review-package.diff').read_bytes();assert len(package)==857980 and sha(package)=='cefcf050122a844f16bbb05e6832764f7e5aa827f5daaa6fbe8c208f80f9d628'
offset=package.index(b'diff --git ');full=git('diff','--no-ext-diff','--full-index','--binary','-U10',BASE,HEAD)
assert package[offset:]==full,'full package diff differs'
sections=re.findall(rb'^diff --git a/(.+) b/(.+)$',full,re.M);assert len(sections)==79 and [a.decode() for a,b in sections]==paths
header=package[:offset].decode();start=header.index('[\n  {');finish=header.rindex('\n]')+2
mapping=json.loads(header[start:finish]);assert [x['path'] for x in mapping]==paths
records=[]
for m in mapping:
 data=(TREE/m['path']).read_bytes();assert data==blob(HEAD,m['path']);assert m['size']==len(data) and m['sha256']==sha(data);records.append(m)
assert (E/'delivery-paths.txt').read_text().splitlines()==paths
allow=(E/'payload-allowlist.txt').read_text().splitlines();sums=[x.split('  ',1) for x in (E/'SHA256SUMS').read_text().splitlines()]
assert len(allow)==77 and [n for _,n in sums]==allow
assert set(paths)-set(allow)=={'artifacts/phase-b/minutes-pure-s1-fix1/SHA256SUMS','artifacts/phase-b/minutes-pure-s1-fix1/payload-allowlist.txt'}
for digest,name in sums:assert sha((TREE/name).read_bytes())==digest
source=json.loads((E/'final-source-hashes.json').read_bytes());assert len(source)==16
for m in source:assert sha((TREE/m['path']).read_bytes())==m['sha256']
oldpaths=git('diff','--name-only','8da440d39e1b8e8dcdfc34722908d29b5aa3aa8a',BASE).decode().splitlines();baseline=json.loads((E/'baseline-273.json').read_bytes());assert [x['path'] for x in baseline]==oldpaths and len(oldpaths)==273
modified=[]
for m in baseline:
 historical=blob(BASE,m['path']);assert len(historical)==m['bytes'] and sha(historical)==m['sha256']
 if (TREE/m['path']).read_bytes()!=historical:modified.append(m['path'])
assert set(modified)==set(paths)&set(oldpaths) and len(modified)==15
assert all(n=='.gitattributes' or n.startswith('backend/') and n.endswith('.java') for n in modified)
oldpayload=[x.split('  ',1) for x in (TREE/'artifacts/phase-b/b07-pure-s1/SHA256SUMS').read_text().splitlines()];assert len(oldpayload)==271
for digest,n in oldpayload:assert sha(blob(BASE,n))==digest
assert sum(sha((TREE/n).read_bytes())==d for d,n in oldpayload)==256
custody=json.loads((E/'custody.json').read_bytes());assert len(custody)==6
for m in custody:assert sha(Path(m['source']).read_bytes())==m['sha256']==sha((E/m['path']).read_bytes())
for m in json.loads((E/'xml-hashes.json').read_bytes()):assert sha((TREE/m['path']).read_bytes())==m['sha256']
assert len(json.loads((E/'xml-hashes.json').read_bytes()))==22
summary={};cases={};red=[]
for folder in sorted((E/'junit').iterdir()):
 totals=[0,0,0,0];found=set();suite=[]
 for xml in sorted(folder.glob('*.xml')):
  raw=xml.read_bytes();root=ET.fromstring(raw);values=[int(root.get(k)) for k in ['tests','failures','errors','skipped']];actual=root.findall('testcase')
  assert values==[len(actual),sum(c.find('failure') is not None for c in actual),sum(c.find('error') is not None for c in actual),sum(c.find('skipped') is not None for c in actual)]
  for c in actual:
   key=(c.get('classname'),c.get('name'));assert key not in found;found.add(key)
   if c.find('failure') is not None:red.append({'round':folder.name,'class':key[0],'name':key[1],'type':c.find('failure').get('type'),'message':c.find('failure').get('message')})
  totals=[a+b for a,b in zip(totals,values)];suite.append({'name':root.get('name'),'counts':values,'sha256':sha(raw)})
 summary[folder.name]={'counts':totals,'suites':suite};cases[folder.name]=found
 saved=json.loads((E/(folder.name+'-summary.json')).read_bytes());assert [saved[k] for k in ['tests','failures','errors','skipped']]==totals
assert summary['01-graph-red']['counts']==[8,5,0,0] and len(red)==5
assert summary['06-complete-owned']['counts']==[112,0,0,0] and len(summary['06-complete-owned']['suites'])==13
oldcases=set()
for xml in (TREE/'artifacts/phase-b/b07-pure-s1/junit/final-owned').glob('*.xml'):
 root=ET.parse(xml).getroot();assert all(int(root.get(k))==0 for k in ['failures','errors','skipped']);oldcases.update((c.get('classname'),c.get('name')) for c in root.findall('testcase'))
assert len(oldcases)==97 and oldcases<=cases['06-complete-owned'];assert len(cases['07-canonical-focused'])==25 and cases['07-canonical-focused']<=cases['06-complete-owned']
logs=[]
for f in sorted((E/'raw').glob('*.log')):
 data=f.read_bytes();s=data.decode('utf-8');logs.append({'path':str(f.relative_to(TREE)),'bytes':len(data),'sha256':sha(data),'builds':re.findall(r'BUILD (SUCCESS|FAILURE)',s),'testSummaries':re.findall(r'Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)',s),'compileError':'COMPILATION ERROR' in s,'warnings':[x for x in s.splitlines() if '[WARNING]' in x or 'deprecated' in x]})
assert len(logs)==12
assert logs[0]['builds']==['FAILURE'] and ['8','5','0','0'] in [list(x) for x in logs[0]['testSummaries']]
assert logs[1]['compileError'] and logs[2]['compileError']
assert logs[5]['builds']==['SUCCESS'] and logs[6]['builds']==['SUCCESS']
assert all(not l['warnings'] for l in logs[:7])
canonical=(E/'raw/07-canonical-focused-green.log').read_text();modules=[line for line in canonical.splitlines() if re.search(r'\.\.\.\s+SUCCESS \[',line)];assert len(modules)==8
assert 'AssertionError' in (E/'raw/09-integrity-audit.log').read_text()
assert 'PASS' in (E/'raw/12-red-source-custody.log').read_text()
for line in (E/'01-graph-red-source.sha256').read_text().splitlines():
 d,n=line.split('  ',1);assert sha((E/'red-source'/Path(n).name).read_bytes())==d
name='backend/adapters/src/test/java/ama/config/json/RawHashChainTest.java';current=(TREE/name).read_text();mark='class RawHashChainTest {\n';prefix=current.split(mark,1)[1].split(' @Test void fix1DeepRawBackedOriginsMeetExactDepthAndRejectOneMore()',1)[0];assert blob(BASE,name).decode().replace(mark,mark+prefix,1).encode()==(E/'red-source/RawHashChainTest.java').read_bytes()
assert git('status','--porcelain=v1')==before
result={'base':BASE,'head':HEAD,'packageBytes':len(package),'packageSha256':sha(package),'fullDiffExact':True,'paths':records,'payloadCount':len(allow),'sourceHashes':source,'oldBaselineCount':len(baseline),'oldModified':modified,'oldCurrentIdentical':273-len(modified),'oldPayloadsGit':len(oldpayload),'oldPayloadsCurrentIdentical':256,'custodyOriginals':6,'xmlCount':22,'junit':summary,'redFailures':red,'oldTests':97,'newTests':sorted(cases['06-complete-owned']-oldcases),'canonicalSubset':25,'canonicalModules':modules,'logs':logs,'authorTreeUnchanged':True}
(OUT/'task-B-07-fix1-review-audit.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({k:v for k,v in result.items() if k not in ['paths','sourceHashes','junit','logs','oldModified','canonicalModules']},indent=2))