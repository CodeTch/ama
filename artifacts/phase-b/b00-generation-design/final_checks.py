"""Read-only final ownership, empty-business-data and source/evidence checks."""
import hashlib,importlib.util,json,pathlib,subprocess
ROOT=pathlib.Path(__file__).resolve().parents[3];OUT=ROOT/'artifacts/phase-b/b00-generation-design'
p=json.loads((OUT/'fixture-initial.json').read_text());name=p['container']
i=json.loads(subprocess.check_output(['docker','inspect',name],text=True))[0]
assert i['Id']==p['id'] and i['Mounts']==p['mounts'] and i['Config']['Image']==p['image_reference']
spec=importlib.util.spec_from_file_location('design',ROOT/'tools/checks/database_design.py');d=importlib.util.module_from_spec(spec);spec.loader.exec_module(d)
for path,text in d.generated().items():assert (ROOT/path).read_text()==text,path
assert (ROOT/'docs/superpowers/specs/2026-10-21-ama-database-design.md').read_text().endswith(d.er())
expected=json.loads((ROOT/'docs/acceptance/b/database-generation-addendum-metadata-evidence.json').read_text())
assert hashlib.sha256(d.sql().encode()).hexdigest()==expected['ddl_sha256']
names=d.mysql(name,'SELECT TABLE_NAME FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY TABLE_NAME;').splitlines()
counts={}
for table in names:
    # Names originate from owned schema and must match fixed declared identifiers.
    assert table in {x['name'] for x in d.TABLES}
query=' UNION ALL '.join("SELECT '"+n+"',COUNT(*) FROM `"+n+'`' for n in names)+';'
for line in d.mysql(name,query).splitlines():
    n,v=line.split('\t');counts[n]=int(v)
assert {n:v for n,v in counts.items() if v}=={'task_outbox_sequence':1}
assert d.mysql(name,'SELECT @@innodb_use_native_aio;').strip()=='0'
assert pathlib.Path('/proc/sys/fs/aio-max-nr').read_text().strip()==p['aio_max_nr']
record={'container':name,'id':i['Id'],'image_reference':i['Config']['Image'],'image_id':i['Image'],'labels':i['Config']['Labels'],'mounts':i['Mounts'],'ports':i['NetworkSettings']['Ports'],'native_aio':0,'aio_max_nr':p['aio_max_nr'],'business_rows':0,'table_row_counts':counts,'generated_dictionary_DDL_ER_equal':True,'DDL_matches_actual_metadata_evidence_sha256':expected['ddl_sha256'],'container_retained_for_review':True,'scope':'read-only final checks, no cleanup/reset/other-owner operation'}
(OUT/'fixture-final.json').write_text(json.dumps(record,indent=2)+'\n')
print(json.dumps({'business_rows':0,'table_count':len(names),'DDL_dictionary_ER_equal':True,'owned_container_retained':name}))
