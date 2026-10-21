from pathlib import Path
import hashlib,json,re,struct,xml.etree.ElementTree as ET,subprocess
ROOT=Path('/home/tanren/ama/.worktrees/phase-a')
TREE=ROOT.parent/'phase-b-minutes-pure'
OUT=ROOT/'.superpowers/sdd/2026-10-21-ama-phase-b'
A=TREE/'artifacts/phase-b/b07-pure-s1'
paths=json.loads((OUT/'task-B-07-pure-s1-delivery-paths.json').read_text())
def sha(b):return hashlib.sha256(b).hexdigest()
def enc(v):
 if v is None:return bytes([0])
 if isinstance(v,bool):return bytes([2 if v else 1])
 if isinstance(v,int):return bytes([3])+v.to_bytes(8,'big',signed=False)
 if isinstance(v,str):
  b=v.encode('utf-8','strict');return bytes([4])+len(b).to_bytes(8,'big')+b
 if isinstance(v,list):return bytes([5])+len(v).to_bytes(8,'big')+b''.join(map(enc,v))
 if isinstance(v,dict):
  keys=sorted(v,key=lambda x:x.encode('utf-8','strict'))
  return bytes([6])+len(keys).to_bytes(8,'big')+b''.join(enc(k)+enc(v[k]) for k in keys)
 raise ValueError(type(v))
def canon(v):
 if v is None:return b'null'
 if isinstance(v,bool):return b'true' if v else b'false'
 if isinstance(v,int):return str(v).encode()
 if isinstance(v,str):
  s='"'
  for c in v:
   n=ord(c)
   s+=('\\'+c) if c in ['"','\\'] else ('\\u%04x'%n) if n<32 else c
  return (s+'"').encode('utf-8','strict')
 if isinstance(v,list):return b'['+b','.join(canon(x) for x in v)+b']'
 return b'{'+b','.join(canon(k)+b':'+canon(v[k]) for k in sorted(v,key=lambda x:x.encode()))+b'}'
pkg=json.loads((OUT/'task-B-07-pure-s1-review-package.json').read_text())
package=[]
for f in pkg['files']:
 b=Path(f['path']).read_bytes()
 assert len(b)==f['size'] and sha(b)==f['sha256']
 package.append({'path':f['path'],'sha256':sha(b),'bytes':len(b)})
df=(OUT/'task-B-07-pure-s1-full.diff').read_text()
dp=re.findall(r'^diff --git a/(.+) b/\1$',df,re.M)
assert set(dp)==set(p['path'] for p in paths) and len(dp)==273
audited=[]
for p in paths:
 b=(TREE/p['path']).read_bytes();assert len(b)==p['size'] and sha(b)==p['sha256'],p['path']
 cls='FULLREAD' if p['path'].endswith(('.java','pom.xml')) or p['path'] in ['.gitattributes','docs/acceptance/b/task-B-07-pure-s1-report.md','artifacts/phase-b/b07-pure-s1/consumer-handbook.md','artifacts/phase-b/b07-pure-s1/independent_vectors.py','artifacts/phase-b/b07-pure-s1/verification-commands.txt','artifacts/phase-b/b07-pure-s1/meaningful-tdd-methods.txt','artifacts/phase-b/b07-pure-s1/observed-assertion-failures.txt'] else 'MACHINE_FULL_BYTES'
 audited.append({**p,'review':cls,'verified':True})
sums=[]
for line in (A/'SHA256SUMS').read_text().splitlines():
 h,path=line.split('  ',1);assert sha((TREE/path).read_bytes())==h,path;sums.append(path)
allow=(A/'payload-allowlist.txt').read_text().splitlines()
assert set(sums)==set(allow) and len(sums)==271
vectors=[]
for p in sorted((TREE/'backend/core/src/test/resources/ama/minutes/vectors').glob('*.json')):
 v=json.loads(p.read_bytes()); raw=enc(v);hx=p.with_suffix('.hex').read_text().strip();sh=p.with_suffix('.sha256').read_text().strip()
 assert raw.hex()==hx and sha(raw)==sh,p
 vectors.append({'path':str(p.relative_to(TREE)),'encodedBytes':len(raw),'sha256':sh})
for p in sorted((TREE/'backend/adapters/src/test/resources/ama/config').glob('*.hex')):
 raw=bytes.fromhex(p.read_text());assert sha(raw)==p.with_suffix('.sha256').read_text().strip()
rawjson=[]
for p in sorted((TREE/'backend/adapters/src/test/resources/ama/config').rglob('*.json')):
 raw=p.read_bytes(); v=json.loads(raw);assert canon(v)==raw,p
 rawjson.append({'path':str(p.relative_to(TREE)),'bytes':len(raw),'sha256':sha(raw)})
closure={}
for p in sorted((TREE/'backend/adapters/src/test/resources/ama/config/full').glob('*.json')):
 v=json.loads(p.read_bytes());identity=(v['artifactType'],v['version']);assert identity not in closure;closure[identity]=(v,sha(p.read_bytes()))
for identity,(v,h) in closure.items():
 slots=[d['slot'] for d in v['dependencies']];assert slots==sorted(set(slots))
 for d in v['dependencies']:
  r=d['ref'];assert closure[(r['artifactType'],r['version'])][1]==r['sha256']
suites=[];unique=set()
for p in sorted((A/'junit/final-owned').glob('*.xml')):
 e=ET.parse(p).getroot(); counts={k:int(e.get(k)) for k in ['tests','failures','errors','skipped']}
 cases=[(c.get('classname'),c.get('name')) for c in e.findall('testcase')]
 assert len(cases)==counts['tests'] and counts['failures']==counts['errors']==counts['skipped']==0
 for c in cases:assert c not in unique;unique.add(c)
 suites.append({'suite':e.get('name'),**counts})
canonical=ET.parse(A/'junit/canonical-runner/TEST-ama.config.json.RawHashChainTest.xml').getroot()
canonical_cases={(c.get('classname'),c.get('name')) for c in canonical.findall('testcase')}
assert len(unique)==97 and len(canonical_cases)==13 and canonical_cases<=unique
raws=[];observed=set();setups=[]
for p in sorted((A/'raw').glob('*.log')):
 s=p.read_text();fail=set(re.findall(r'^\[ERROR\] (ama\.[\w.$]+) -- Time elapsed:.*<<< FAILURE!',s,re.M))
 observed.update(fail)
 setup='COMPILATION ERROR' in s or 'Non-parseable POM' in s or 'Some problems were encountered while processing the POMs' in s
 if setup:setups.append(p.name[:2])
 raws.append({'path':str(p.relative_to(TREE)),'bytes':p.stat().st_size,'sha256':sha(p.read_bytes()),'buildSuccess':'BUILD SUCCESS' in s,'setupError':setup,'assertionFailures':sorted(fail),'summaries':re.findall(r'Tests run: \d+, Failures: \d+, Errors: \d+, Skipped: \d+',s),'warnings':[l for l in s.splitlines() if '[WARNING]' in l or 'deprecated API' in l]})
assert setups==['01','06','11','31','32','37','38','45','66'],setups
claims=set((A/'observed-assertion-failures.txt').read_text().splitlines());assert claims==observed,(claims-observed,observed-claims)
meaningful=set((A/'meaningful-tdd-methods.txt').read_text().splitlines());assert len(meaningful)==48 and meaningful<=observed and len(observed)==51
# Fixed projections versus already reviewed actual B00-1.3 metadata; no DB access.
metadata=json.loads((ROOT/'docs/acceptance/b/database-generation-addendum-metadata-evidence.json').read_text())['metadata']
source=(TREE/'backend/contracts/src/main/java/ama/contract/MinutesContracts.java').read_text()
tables=re.findall(r'^\s*[A-Z_]+\("([a-z_]+)","([^"]+)","([^"]+)","[^"]*"\)',source,re.M)
exceptions={'material_reference':{'state'},'material_revision':{'parse_state'},'parsed_document':{'state','error_code'}}
schema=[]
for table,spec,pk in tables:
 cols=[dict(name=w.split(':')[0],kind=w.split(':')[1].rstrip('?'),nullable=w.endswith('?')) for w in spec.split()]
 db=sorted([c for c in metadata['columns'] if c['table']==table and c['column'] not in exceptions.get(table,set())],key=lambda x:x['ordinal'])
 assert [c['name'] for c in cols]==[c['column'] for c in db],table
 for c,d in zip(cols,db):
  assert c['nullable']==(d['nullable']=='YES'),(table,c,d)
  if re.match(r'(bigint|int|smallint|tinyint)',d['type']):assert c['kind']=='UINT',(table,c,d)
  elif d['type'].startswith('datetime'):assert c['kind']=='TIMESTAMP',(table,c,d)
  elif d['type']=='date':assert c['kind']=='DATE',(table,c,d)
  else:assert c['kind'] in ['UUID','HASH','TEXT'],(table,c,d)
 actualpk=[x['column'] for x in sorted([x for x in metadata['indexes'] if x['table']==table and x['name']=='PRIMARY'],key=lambda x:x['seq'])]
 assert pk.split(',')==actualpk,(table,pk,actualpk)
 schema.append({'table':table,'columns':len(cols),'primaryKey':actualpk,'match':True})
assert len(schema)==32
r={'range':{'base':pkg['base'],'head':pkg['head']},'package':package,'paths':audited,'totalPaths':len(audited),'totalBytes':sum(p['size'] for p in paths),'manifestPayloads':len(sums),'vectors':vectors,'rawJson':rawjson,'closureNodes':len(closure),'finalSuites':suites,'finalUniqueTests':len(unique),'canonicalSubset':len(canonical_cases),'rawRuns':raws,'setupRounds':setups,'observedAssertions':len(observed),'meaningfulListedAssertions':len(meaningful),'fullRowSchema':schema}
(OUT/'task-B-07-pure-s1-review-audit.json').write_text(json.dumps(r,indent=2)+'\n')
print(json.dumps({k:v for k,v in r.items() if k in ['range','totalPaths','totalBytes','manifestPayloads','closureNodes','finalUniqueTests','canonicalSubset','setupRounds','observedAssertions','meaningfulListedAssertions']},indent=2))
print('vectors',len(vectors),'raw JSON',len(rawjson),'schema tables',len(schema),'raw rounds',len(raws))

