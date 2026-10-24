from pathlib import Path
import runpy,json,re,hashlib
root=Path.cwd();out=Path(__file__).parent
m=runpy.run_path('tools/checks/database_design.py');tables=m['TABLES'];by={t['name']:t for t in tables}
assert len(tables)==98 and sum(len(t['cols']) for t in tables)==732,'Current model must include reconcile ordinal'
assert sum(1+len(t['indexes']) for t in tables)==189,'Both Task6 and Task8 indexes required'
assert [c['name'] for c in by['external_operation']['cols']][-1]=='reconcile_scan_seq'
assert by['external_operation']['cols'][-1]['type']=='bigint'
assert any(i['name']=='task_input_lookup' and i['cols']==['input_kind','input_id','id'] and not i['unique'] for i in by['durable_task']['indexes'])
for path,expected in m['generated']().items():assert (root/path).read_text()==expected
base=root/'artifacts/phase-b/b-remaining/task-08-current-model-preservation/base-12ca543'
old=runpy.run_path(str(base/'tools/checks/database_design.py'));ob={t['name']:t for t in old['TABLES']}
for name,t in by.items():
 if name not in ['external_operation','durable_task']:assert t==ob[name],name
 else:
  from copy import deepcopy
  delta=deepcopy(t)
  if name=='durable_task':delta['indexes']=[i for i in delta['indexes'] if i['name']!='task_input_lookup']
  else:
   delta['cols']=delta['cols'][:-1];delta['indexes']=[i for i in delta['indexes'] if i['name']!='external_reconcile_scan'];delta['policy']=ob[name]['policy']
  assert delta==ob[name],name
for path,entities in [('docs/database/ama-schema-draft.sql',['durable_task','external_operation']),('docs/database/ama-data-dictionary.md',['durable_task','external_operation'])]:
 before=(base/path).read_text();after=(root/path).read_text()
 before=before.replace(old['VERSION'],m['VERSION'],1)
 for name in entities:
  pattern=(r'CREATE TABLE '+name+r' \(.*?\n\) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;' if path.endswith('.sql') else r'## '+name+r'\n.*?(?=\n## |\Z)')
  before,n=re.subn(pattern,'OWNED_ENTITY',before,flags=re.S);after,k=re.subn(pattern,'OWNED_ENTITY',after,flags=re.S);assert n==k==1
 assert before==after,'Outside authorized generated regions changed: '+path
path='docs/superpowers/specs/2026-10-21-ama-database-design.md';b=(base/path).read_bytes();a=(root/path).read_bytes();prefix=b.split('## 附录：完整 ER'.encode())[0];assert prefix in a;assert a.endswith(m['er']().encode());assert old['er']().replace('        bigint current_execution_no\n','        bigint current_execution_no\n        bigint reconcile_scan_seq\n',1)==m['er']()
assert m['auto_increment'](by['audit_event'],by['audit_event']['cols'][0])
assert m['auto_increment'](by['external_operation'],by['external_operation']['cols'][-1])
record={'version':m['VERSION'],'tables':98,'columns':732,'explicitIndexesIncluding98PK':189,'checks':sum(len(t['checks']) for t in tables),'foreignKeys':sum(len(t['fks']) for t in tables),'allOtherDefinitionsExact':True,'humanPrefixExactContiguousBytes':len(prefix),'EROnlyOperationFieldAdded':True,'oldVerifierNeverCalled':True}
(out/'current-model-PASS.json').write_text(json.dumps(record,indent=2)+'\n');print(json.dumps(record))
