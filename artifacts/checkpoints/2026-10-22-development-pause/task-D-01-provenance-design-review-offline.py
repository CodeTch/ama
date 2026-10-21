from pathlib import Path
import sys,json,hashlib,subprocess,re,ast,copy
sys.dont_write_bytecode=True
T=Path('/home/tanren/ama/.worktrees/phase-b-provenance-design')
S=Path('/home/tanren/ama/.worktrees/phase-a/.superpowers/sdd/2026-10-21-ama-phase-b')
A=T/'artifacts/phase-b/b00-provenance-design'
B='91774bd4eeca2c3f3233f0e6733b32c2abef28fc';H='214a63d978e4c04c0b3aec1c99d8d0ce3c093d9d'
def git(*a):return subprocess.check_output(['git','-C',str(T),*a])
def sha(b):return hashlib.sha256(b).hexdigest()
def read(p):return json.loads(p.read_bytes())
checks=[]
def ck(n,c):
 assert c,n
 checks.append(n)
def load(p):
 d={'__file__':str(p),'__name__':'offline'};exec(compile(p.read_text(),str(p),'exec'),d);return d
inv=read(S/'task-D-01-provenance-design-delivery-paths.json')
ck('HEAD/clean',git('rev-parse','HEAD').decode().strip()==H and git('status','--porcelain')==b'')
ck('all65 paths',git('diff','--name-only',B,H).decode().splitlines()==[x['path'] for x in inv] and len(inv)==65)
for x in inv:
 b=(T/x['path']).read_bytes();ck('custody '+x['path'],len(b)==x['size'] and sha(b)==x['sha256'] and b==git('show',H+':'+x['path']))
pkg=read(S/'task-D-01-provenance-design-review-package.json')
for x in pkg['files']:
 b=Path(x['path']).read_bytes();ck('package '+Path(x['path']).name,len(b)==x['size'] and sha(b)==x['sha256'])
ck('source supplement exact8',(S/'task-D-01-provenance-design-source.full').read_bytes()==b''.join(('\n===== FULL '+x['path']+' =====\n').encode()+(T/x['path']).read_bytes() for x in inv if x['kind']=='source'))
ck('full patch exact',(S/'task-D-01-provenance-design-full.diff').read_bytes().endswith(git('diff','--full-index',B,H)))
ck('report SHA',sha((S/'task-D-01-provenance-design-report.md').read_bytes())==pkg['sourceReportSha256'])
ck('diff check',subprocess.run(['git','-C',str(T),'diff','--check',B,H],capture_output=True).returncode==0)
for x in read(A/'final-manifest.json')['files']:
 b=(T/x['path']).read_bytes();ck('manifest '+x['path'],len(b)==x['bytes'] and sha(b)==x['sha256'])
for p,x in read(A/'baseline-immutable-files.json').items():
 b=(T/p).read_bytes();ck('BASE immutable '+p,b==git('show',B+':'+p) and sha(b)==x['sha256'])
for x in read(T/'docs/database/design-history/B00-1.3/manifest.json')['files']:
 b=(T/x['copy']).read_bytes();ck('history BASE '+x['source'],b==git('show',B+':'+x['source']) and sha(b)==x['sha256'] and len(b)==x['bytes'])
old=load(T/'docs/database/design-history/B00-1.3/database_design.py');new=load(T/'tools/checks/database_design.py')
O={t['name']:t for t in old['TABLES']};N={t['name']:t for t in new['TABLES']}
ck('exact3 added tables',set(N)-set(O)=={'source_context','source_context_material','parsed_document_profile'})
for name,t in O.items():
 n=copy.deepcopy(N[name])
 if name in ('minutes_element','evidence_link'):
  ck('nullable UUID '+name,n['cols'][-1]['name']=='source_context_id' and n['cols'][-1]['nullable'] and n['cols'][-1]['type']==new['ID'])
  n['cols'].pop();n['indexes'].pop();n['fks'].pop();n['policy']=t['policy']
 ck('all old semantics '+name,t==n)
for p,s in new['generated']().items():ck('generated '+p,(T/p).read_bytes()==s.encode())
ck('complete ER',(T/'docs/superpowers/specs/2026-10-21-ama-database-design.md').read_text().endswith(new['er']()))
meta=read(T/'docs/acceptance/b/database-material-provenance-addendum-metadata-evidence.json');actual=meta['metadata']
ck('DDL SHA',meta['ddl_sha256']==sha((T/'docs/database/ama-schema-draft.sql').read_bytes()))
# Execute ONLY the checker's pure metadata comparison block against the captured JSON.
source=(T/'tools/checks/database_design.py').read_text()
start=source.index("    assert {x['table'] for x in actual['tables']}")
end=source.index('    # Behavioral negative checks',start)
block='\n'.join(line[4:] for line in source[start:end].splitlines())
exec(compile(block,'offline metadata comparison','exec'),dict(new,actual=actual))
ck('all727 columns302 indexes114 checks206 FKs exact',True)
ck('counts',len(O)==93 and sum(len(t['cols']) for t in O.values())==709 and len(N)==96 and sum(len(t['cols']) for t in N.values())==727)
oldmeta=read(T/'docs/acceptance/b/database-generation-addendum-metadata-evidence.json')['metadata'];delta={}
for k,rows in oldmeta.items():
 f=lambda x:json.dumps(x,sort_keys=True)
 before=set(map(f,rows));after=set(map(f,actual[k]))
 ck('old metadata preserved '+k,before<=after);delta[k]=[json.loads(x) for x in sorted(after-before)]
# Independent encoder, separate from author oracle.
def enc(v):
 if v is None:return bytes([0])
 if isinstance(v,bool):return bytes([2 if v else 1])
 if isinstance(v,int):assert 0<=v<=2**63-1;return b'\x03'+v.to_bytes(8,'big')
 if isinstance(v,str):
  b=v.encode();return b'\x04'+len(b).to_bytes(8,'big')+b
 if isinstance(v,list):return b'\x05'+len(v).to_bytes(8,'big')+b''.join(map(enc,v))
 assert isinstance(v,dict)
 return b'\x06'+len(v).to_bytes(8,'big')+b''.join(enc(k)+enc(v[k]) for k in sorted(v,key=lambda x:x.encode()))
v=read(A/'task-D-01-provenance-draft-exclusion-vector.json');raw=enc(v)
ck('independent6871 byte vector',len(raw)==6871 and raw.hex()==(A/'task-D-01-provenance-draft-exclusion-vector.hex').read_text().strip() and sha(raw)=='015a3a30f9970d41ef3ac45209794c7a4367e494b53a4f314f6cd0f0d3728112')
ck('no own fingerprint fields','generationInputFingerprint' not in json.dumps(v) and 'inputFingerprint' not in json.dumps(v))
for x in read(A/'historical-projection-vectors.json'):
 cols=[c['name'] for c in O[x['table']]['cols']]
 ck('independent old vector '+x['table'],cols==x['columns'] and sha(enc({k:'synthetic:'+k for k in cols}))==x['digest'])
p=A/'design_validation.py';syntax=ast.parse(p.read_text());writes={}
class NoWrite(ast.NodeTransformer):
 def visit_Call(self,n):
  n=self.generic_visit(n)
  if isinstance(n.func,ast.Attribute) and n.func.attr=='write_text':return ast.copy_location(ast.Call(ast.Name('capture',ast.Load()),[n.func.value,*n.args],n.keywords),n)
  return n
def capture(p,s):writes[str(p)]=s;return len(s)
d={'__name__':'offline','__file__':str(p),'capture':capture}
exec(compile(ast.fix_missing_locations(NoWrite().visit(syntax)),str(p),'exec'),d)
ck('author250 pure assertions rerun without writes',len(d['checks'])==250)
for p,s in writes.items():ck('oracle exact '+Path(p).name,Path(p).read_text()==s)
logs={}
for name in ('mysql-raw.jsonl','probes-raw.jsonl','reopen-catalog-raw.jsonl'):
 rows=[json.loads(x) for x in (A/name).read_text().splitlines()]
 logs[name]=[dict(label=x.get('probe',x.get('label',x.get('query','')[:90])),exit=x['exit_code'],stdout_sha256=sha(x['stdout'].encode()),stderr=x['stderr'],boundary=x.get('database_boundary_witness',False)) for x in rows]
 if name=='mysql-raw.jsonl':
  for k,r in actual.items():
   match=[x for x in rows if x['exit_code']==0 and x['stdout'].startswith('{') and [json.loads(z) for z in x['stdout'].splitlines()]==r]
   ck('two raw metadata captures '+k,len(match)==2)
pr=read(A/'constraint-probes.json')['probes'];rp=read(A/'reopen-catalog-probes.json')
ck('24 probes6 APP witnesses',len(pr)==24 and sum(x['boundary'] for x in pr)==6 and all(x['expectation_met'] for x in pr))
ck('7 reopen probes',rp['checks']==7 and len(rp['results'])==7)
i=read(A/'fixture-initial.json')
ck('initial zero/AIO/resources',i['initial_tables']==0 and i['native_aio']==0 and i['mysql_version']=='8.4.6' and i['resources']==dict(NanoCpus=2000000000,Memory=1073741824,MemorySwap=1073741824,PidsLimit=256))
ck('source still clean',git('status','--porcelain')==b'')
result=dict(base=B,head=H,checks=checks,check_count=len(checks),paths=inv,metadata_counts=meta['counts'],metadata_delta=delta,raw_records=logs,draft_bytes=len(raw),draft_sha256=sha(raw),oracle_assertions=len(d['checks']),scope='OFFLINE ONLY; no DB/Docker/network/source writes. Recorded metadata comparison uses reviewed pure checker block; independent baseline delta and vector analyses also run.')
p=S/'task-D-01-provenance-design-review-offline.json'
assert not p.exists()
p.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(dict(checks=len(checks),sha256=sha(p.read_bytes()),metadata_counts=meta['counts'])))

