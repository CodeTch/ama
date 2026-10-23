from pathlib import Path
import json,re
root=Path(__file__).parent/'migration'
def read(p):return json.loads(p.read_text())
reports={}
for target in ['shared','behavior','fairheld','fairunregistered','overflow']:
 d=root/target;b=read(d/'before.json');a=read(d/'apply.json')
 for key in ['engine','checks','references']:assert b[key]==a[key],(target,key)
 for key in ['columns','indexes','constraints','keys']:
  field={'columns':'COLUMN_NAME','indexes':'INDEX_NAME','constraints':'CONSTRAINT_NAME','keys':'CONSTRAINT_NAME'}[key]
  name='reconcile_scan_seq' if key=='columns' else 'external_reconcile_scan'
  added=[x for x in a[key] if x[field]==name and x['TABLE_NAME']=='external_operation'];assert len(added)==1,(key,added)
  assert b[key]==[x for x in a[key] if x not in added],(target,key)
 c=next(x for x in a['columns'] if x['COLUMN_NAME']=='reconcile_scan_seq')
 for k,v in {'TABLE_NAME':'external_operation','ORDINAL_POSITION':'16','COLUMN_DEFAULT':None,'IS_NULLABLE':'NO','DATA_TYPE':'bigint','COLUMN_TYPE':'bigint','EXTRA':'auto_increment','CHARACTER_SET_NAME':None,'COLLATION_NAME':None,'NUMERIC_PRECISION':'19','NUMERIC_SCALE':'0'}.items():assert c[k]==v,(k,c[k])
 ix=next(x for x in a['indexes'] if x['INDEX_NAME']=='external_reconcile_scan')
 for k,v in {'NON_UNIQUE':'0','SEQ_IN_INDEX':'1','COLUMN_NAME':'reconcile_scan_seq','COLLATION':'A','INDEX_TYPE':'BTREE','IS_VISIBLE':'YES','SUB_PART':None,'EXPRESSION':None}.items():assert ix[k]==v,(k,ix[k])
 assert any(x['TABLE_NAME']=='durable_task' and x['INDEX_NAME']=='task_input_lookup' for x in a['indexes'])
 bt=b['tables'];at=json.loads(json.dumps(a['tables']))
 for row in at:
  if row['TABLE_NAME']=='external_operation':assert row['AUTO_INCREMENT'] is not None;row['AUTO_INCREMENT']=None
 assert bt==at,(target,'tables')
 for table,rows in a['showCreate'].items():
  ddl=rows[0]['Create Table']
  if table=='external_operation':
   assert ddl.count('  `reconcile_scan_seq` bigint NOT NULL AUTO_INCREMENT,\n')==1
   ddl=ddl.replace('  `reconcile_scan_seq` bigint NOT NULL AUTO_INCREMENT,\n','').replace('  UNIQUE KEY `external_reconcile_scan` (`reconcile_scan_seq`),\n','')
   ddl=re.sub(r' AUTO_INCREMENT=\d+','',ddl)
  assert ddl==b['showCreate'][table][0]['Create Table'],(target,table,'show')
 for table in b['data']:
  if table!='flyway_schema_history':assert b['data'][table]==a['data'][table],(target,table,'complete data')
 assert len(a['history'])==15 and a['history'][:14]==b['history'];assert a['history'][-1]['version']=='013.3' and a['history'][-1]['success']=='1'
 mapping=a['ordinalMap'];assert len(mapping)==b['data']['external_operation']['rows'];seq=[int(x['reconcile_scan_seq']) for x in mapping];assert all(x>0 for x in seq) and len(set(seq))==len(seq)
 assert read(d/'apply-migration.json')['migrationsExecuted']==1
 r=d/'rerun.json'
 if r.exists():
  rr=read(r)
  for key in a:
   if key!='ownConnections':assert a[key]==rr[key],(target,key,'rerun')
  assert read(d/'rerun-migration.json')['migrationsExecuted']==0
 reports[target]={'oldHistory14PreservedAllFields':True,'oldTables':len(b['tables']),'oldColumns':len(b['columns']),'allOldDataAndAllocatorsIdentical':True,'legacyOperations':len(mapping),'positiveUniqueOrdinalMapping':True,'newColumnExactOrdinal16':True,'onlyNewColumnAndUniqueIndexAndEngineAllocationDiffer':True,'Task6IndexPreserved':True,'firstApply':1,'normalRerun':0 if r.exists() else 'pending','elapsedNanos':read(d/'apply-migration.json')['elapsedNanos']}
(root/'preservation-PASS.json').write_text(json.dumps(reports,indent=2)+'\n');print(json.dumps(reports,indent=2))
