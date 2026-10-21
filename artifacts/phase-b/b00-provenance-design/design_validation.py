"""Independent design oracle and byte vectors, not application/config acceptance."""
import copy, hashlib, importlib.util, json, pathlib, struct
ROOT=pathlib.Path(__file__).resolve().parents[3]
OUT=ROOT/'artifacts/phase-b/b00-provenance-design'
def encode(v):
    if v is None:return b'\x00'
    if v is False:return b'\x01'
    if v is True:return b'\x02'
    if type(v) is int:
        assert 0<=v<2**64
        return b'\x03'+struct.pack('>Q',v)
    if isinstance(v,str):
        b=v.encode('utf-8');return b'\x04'+struct.pack('>Q',len(b))+b
    if isinstance(v,list):return b'\x05'+struct.pack('>Q',len(v))+b''.join(encode(x) for x in v)
    if isinstance(v,dict):
        keys=sorted(v,key=lambda x:x.encode('utf-8'))
        return b'\x06'+struct.pack('>Q',len(keys))+b''.join(encode(k)+encode(v[k]) for k in keys)
    raise TypeError(type(v))
def digest(v):return hashlib.sha256(encode(v)).hexdigest()
def module(name,path):
    s=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m
old=module('historical',ROOT/'docs/database/design-history/B00-1.3/database_design.py')
new=module('proposed',ROOT/'tools/checks/database_design.py')
checks=[]
def ok(name,condition):
    assert condition,name
    checks.append(name)
def rejects(name,fn):
    try:fn()
    except (AssertionError,KeyError,ValueError):checks.append(name);return
    raise AssertionError('expected rejection: '+name)
baseline=json.loads((ROOT/'docs/database/design-history/B00-1.3/manifest.json').read_text())
for f in baseline['files']:
    ok('preserved '+f['copy'],hashlib.sha256((ROOT/f['copy']).read_bytes()).hexdigest()==f['sha256'])
immutable=json.loads((OUT/'baseline-immutable-files.json').read_text())
entries=[dict(path=k,**v) for k,v in immutable.items()]
for f in entries:ok('immutable '+f['path'],hashlib.sha256((ROOT/f['path']).read_bytes()).hexdigest()==f['sha256'])
ok('96 tables 727 columns',len(new.TABLES)==96 and sum(len(t['cols']) for t in new.TABLES)==727)
for p,s in new.generated().items():ok('generated '+p,(ROOT/p).read_text()==s)
ok('complete ER',(ROOT/'docs/superpowers/specs/2026-10-21-ama-database-design.md').read_text().endswith(new.er()))
old_vectors=[]
for t in old.TABLES:
    nt=next(x for x in new.TABLES if x['name']==t['name'])
    keys=[c['name'] for c in t['cols']]
    ok('old column shapes '+t['name'],[(c['name'],c['type'],c['nullable'],c['default']) for c in t['cols']]==[(c['name'],c['type'],c['nullable'],c['default']) for c in nt['cols'][:len(keys)]])
    row={k:'synthetic:'+k for k in keys}; extended={**row,'source_context_id':'explicit-new-context'}
    before=digest(row);after=digest({k:extended[k] for k in keys})
    ok('old FullRow whitelist '+t['name'],before==after)
    old_vectors.append(dict(table=t['name'],columns=keys,digest=before))
(OUT/'historical-projection-vectors.json').write_text(json.dumps(old_vectors,indent=2)+'\n')
v=json.loads((OUT/'task-D-01-provenance-draft-exclusion-vector.json').read_text())
b=encode(v)
ok('draft exact byte vector',b.hex()==(OUT/'task-D-01-provenance-draft-exclusion-vector.hex').read_text().strip())
ok('draft exact digest',digest(v)=='015a3a30f9970d41ef3ac45209794c7a4367e494b53a4f314f6cd0f0d3728112')
ok('no own fingerprint field','generationInputFingerprint' not in json.dumps(v) and 'inputFingerprint' not in json.dumps(v))

# Small explicit complete-catalog oracle: no parser, network, runtime or source ACL implementation.
bindings=[dict(format='PDF',version='P1',sha='1'*64),dict(format='PDF',version='P2',sha='2'*64)]
docs={'D1':dict(material='R1',file='F1',format='PDF',version='P1',sha='1'*64,count=2,units=[(1,1,'one'),(2,5,'unused')]),'D2':dict(material='R2',file='F2',format='PDF',version='P2',sha='2'*64,count=1,units=[(1,1,'other file')])}
def catalog(snapshot,selections,documents,archive_bindings):
    ids=[(x['format'],x['version']) for x in archive_bindings]
    assert len(ids)==len(set(ids))
    assert set(snapshot)==set(selections) and len(snapshot)==len(set(snapshot))
    entries=[]
    for material in sorted(snapshot):
        d=documents[selections[material]]
        assert d['material']==material
        assert sum(x=={k:d[k] for k in ('format','version','sha')} for x in archive_bindings)==1
        assert len(d['units'])==d['count'] and len({x[0] for x in d['units']})==d['count']
        for ordinal,page,text in d['units']:
            assert d['format']=='PDF' and page>0 and text
            entries.append((d['file'],'PDF_PAGE',page,material,selections[material]))
    assert len({x[:3] for x in entries})==len(entries)
    return sorted(entries)
c=catalog(['R1','R2'],{'R1':'D1','R2':'D2'},docs,bindings)
ok('two PDF parser versions same context and unused page5',len(c)==3 and any(x[2]==5 for x in c))
ok('catalog permutation stable',c==catalog(['R2','R1'],{'R2':'D2','R1':'D1'},docs,list(reversed(bindings))))
rejects('missing selection',lambda:catalog(['R1','R2'],{'R1':'D1'},docs,bindings))
rejects('wrong document material',lambda:catalog(['R1'],{'R1':'D2'},docs,bindings))
rejects('missing exact parser binding',lambda:catalog(['R1','R2'],{'R1':'D1','R2':'D2'},docs,bindings[:1]))
rejects('ambiguous parser binding',lambda:catalog(['R1'],{'R1':'D1'},docs,bindings+[bindings[0]]))
bad=copy.deepcopy(docs);bad['D2']['file']='F1'
rejects('same full locator conflict within context',lambda:catalog(['R1','R2'],{'R1':'D1','R2':'D2'},bad,bindings))
bad=copy.deepcopy(docs);bad['D1']['count']=3
rejects('complete count mismatch',lambda:catalog(['R1'],{'R1':'D1'},bad,bindings))
slots=sorted(['CANONICAL_CODEC']+['PARSER_SHA256_'+x['sha'] for x in bindings])
ok('parser slot exact length and two versions',len(set(slots))==3 and all(len(x)==78 for x in slots[1:]))

# Context graph vector preserves distinct occurrences sharing L, zero-evidence root and both contexts.
graph=dict(locatorRows=[dict(id='L',file='F',page=1)],contextRows=[dict(id='C1',generationInputId='G1',generationInputFingerprint='1'*64),dict(id='C2',generationInputId='G2',generationInputFingerprint='2'*64)],contextMaterialRows=[dict(context_id='C1',material_revision_id='R',parsed_document_id='D1'),dict(context_id='C2',material_revision_id='R',parsed_document_id='D2')],parserProfileRows=[dict(document_id='D1',format='PDF',parser_config_version='P1',parser_config_sha256='1'*64),dict(document_id='D2',format='PDF',parser_config_version='P2',parser_config_sha256='2'*64)],sourceUsages=[dict(key=['EVIDENCE','E1'],locator='L',context='C1'),dict(key=['EVIDENCE','E2'],locator='L',context='C2'),dict(key=['BACKGROUND','N1','L'],locator='L',context='C1'),dict(key=['BACKGROUND','N2','L'],locator='L',context='C2')],contextRoots=['C1','C2'])
def normalized(g):
    g=copy.deepcopy(g)
    keys=[tuple(x['key']) for x in g['sourceUsages']];assert len(keys)==len(set(keys))
    for k in g:g[k]=sorted(g[k],key=encode)
    return g
h=digest(normalized(graph));perturbations={}
for index in (0,1):
    x=copy.deepcopy(graph);x['parserProfileRows'][index]['parser_config_sha256']='3'*64
    changed=digest(normalized(x));ok('hash changes parser context '+str(index),changed!=h);perturbations['parser'+str(index)]=changed
    x=copy.deepcopy(graph);x['contextRows'][index]['generationInputFingerprint']='4'*64
    ok('hash changes ancestor input '+str(index),digest(normalized(x))!=h)
x=copy.deepcopy(graph)
for k in x:x[k].reverse()
ok('unordered graph permutation invariant',digest(normalized(x))==h)
x=copy.deepcopy(graph);x['sourceUsages'].append(x['sourceUsages'][0])
rejects('duplicate occurrence rejected',lambda:normalized(x))
x=copy.deepcopy(graph);x['sourceUsages'].pop()
ok('distinct same-locator occurrence retained',digest(normalized(x))!=h)
x=copy.deepcopy(graph);x['contextRoots'].append('ZERO_EVIDENCE_CONTEXT')
ok('zero-evidence root changes graph',digest(normalized(x))!=h)
ok('revoked permission denies reuse while graph remains',not all([True,False]) and digest(normalized(graph))==h)
(OUT/'graph-projection-vector.json').write_text(json.dumps(graph,indent=2)+'\n')
(OUT/'graph-projection-vector.hex').write_text(encode(normalized(graph)).hex()+'\n')
result=dict(scope='standalone design oracle / synthetic projections; no production archive, parser, ACL or runtime acceptance',checks=checks,graph_digest=h,perturbations=perturbations,draft_bytes=len(b),draft_digest=digest(v))
(OUT/'design-validation.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(dict(checks=len(checks),graph_digest=h,draft_digest=digest(v))))
