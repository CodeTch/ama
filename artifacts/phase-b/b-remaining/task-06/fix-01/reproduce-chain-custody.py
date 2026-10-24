from pathlib import Path
import json,re,os,shlex,subprocess,collections,datetime
r=Path('artifacts/phase-b/b-remaining/task-06/fix-01')
accepted={}; owners=set(); meetings=set(); inherited={}
for p in r.glob('pipeline-*/accepted-*.json'):
 d=json.loads(p.read_text()); accepted[d['task']]={'receipt':str(p),'record':d};meetings.add(d['meeting']);owners.add(d['owner'])
for p in r.glob('pipeline-*/resource.json'):owners.add(json.loads(p.read_text())['owner'])
for pattern in ['inherited-metadata/*.json','inherited-queue/*.json','inherited-archive/*-fixture-closed.json']:
 for p in r.glob(pattern):
  d=json.loads(p.read_text());inherited[d['meeting']]={'owner':d['owner'],'receipt':str(p)}
for p in r.glob('inherited-custody/*.txt'):
 m=re.match(r'owner=([^\s]+) meeting=([0-9a-f-]{36})',p.read_text())
 if m:inherited[m[2]]={'owner':m[1],'receipt':str(p)}
 else:assert p.name.endswith('-lock-order.txt')
def vals(xs):
 assert xs and all(re.fullmatch('[A-Za-z0-9_-]+',s) for s in xs)
 return ','.join("'"+s+"'" for s in sorted(xs))
pipeline="""SELECT JSON_OBJECT('task',t.id,'meeting',t.subject_id,'currentOwner',m.owner_id,'input',t.input_id,'state',t.state,'output',t.output_id,'inputMeeting',i.meeting_id,'batch',i.batch_id,'batchMeeting',b.meeting_id,'acceptances',(SELECT COUNT(*) FROM idempotency_record r WHERE r.result_id=t.id AND r.operation='PROCESSING_IMPORT' AND r.http_status=202),'members',(SELECT COUNT(*) FROM input_batch_file f WHERE f.batch_id=i.batch_id),'files',(SELECT COUNT(*) FROM input_batch_file f JOIN file_object o ON o.id=f.file_id AND o.meeting_id=t.subject_id WHERE f.batch_id=i.batch_id),'rawChain',(SELECT COUNT(*) FROM transcript_revision r WHERE r.id=t.output_id AND r.producer_task_id=t.id AND r.processing_input_id=i.id AND r.batch_id=i.batch_id AND r.meeting_id=t.subject_id AND r.kind='RAW'),'unendedAttempts',(SELECT COUNT(*) FROM task_attempt a WHERE a.task_id=t.id AND a.ended_at IS NULL)) FROM durable_task t LEFT JOIN meeting m ON m.id=t.subject_id LEFT JOIN transcription_input i ON i.id=t.input_id LEFT JOIN input_batch b ON b.id=i.batch_id WHERE t.subject_kind='MEETING' AND (t.subject_id IN ("""+vals(meetings)+") OR m.owner_id IN ("+vals(owners)+")) ORDER BY t.id;"
control="SELECT JSON_OBJECT('task',t.id,'meeting',t.subject_id,'type',t.task_type,'pool',t.pool,'state',t.state,'inputKind',t.input_kind,'input',t.input_id,'outputKind',t.output_kind,'unendedAttempts',(SELECT COUNT(*) FROM task_attempt a WHERE a.task_id=t.id AND a.ended_at IS NULL)) FROM durable_task t WHERE t.subject_kind='MEETING' AND t.subject_id IN ("+vals(inherited)+") ORDER BY t.id;"
globalq="SELECT JSON_OBJECT('activeTasks',(SELECT COUNT(*) FROM durable_task WHERE state IN ('READY','RUNNING','RECONCILING')),'unendedAttempts',(SELECT COUNT(*) FROM task_attempt WHERE ended_at IS NULL),'scope','all database historical and controlled fixtures; read only');"
env=os.environ.copy()
for line in Path('.tools/phase-b-remaining/env.sh').read_text().splitlines():
 if line.startswith('export '):line=line[7:]
 if '=' in line and not line.startswith('#'):
  k,v=line.split('=',1);v=shlex.split(v);env[k]=v[0] if v else ''
env['MYSQL_PWD']=env['AMA_TEST_DATABASE_PASSWORD']
cmd=['docker','exec','-i','-e','MYSQL_PWD','eefdd9a984ee00fe6ca886168b29a6ce4f3b405467b8d3204240d4e1a8dc7502','mysql','-u',env['AMA_TEST_DATABASE_USERNAME'],'--batch','--raw','--skip-column-names','ama_b01_fixture']
def query(sql):
 x=subprocess.run(cmd,input=sql,text=True,capture_output=True,env=env);assert x.returncode==0,x.stderr
 return [json.loads(line) for line in x.stdout.splitlines()]
ps=query(pipeline);cs=query(control);gs=query(globalq)
assert set(accepted)<=set(d['task'] for d in ps)
for d in ps:
 d['evidence']=accepted.get(d['task'],{'scope':'derived current DB mapping; direct HTTP intake has no independent accepted JSON'})
assert all(d['state']=='SUCCEEDED' and d['acceptances']==1 and d['rawChain']==1 and d['members']==d['files'] and d['files']>0 and d['inputMeeting']==d['meeting']==d['batchMeeting'] and d['unendedAttempts']==0 for d in ps),json.dumps(ps)
for d in cs:d['evidence']=inherited[d['meeting']]
report={'observedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'mode':'SELECT only; no cleanup, cancellation or mutation','pipeline':ps,'inheritedControlFixtures':cs,'global':gs,'summary':{'pipelineTasks':len(ps),'independentAcceptedJson':len(accepted),'derivedDirectTasks':[d['task'] for d in ps if d['task'] not in accepted],'pipelineUnendedAttempts':sum(d['unendedAttempts'] for d in ps),'inheritedTasks':len(cs),'inheritedStates':dict(collections.Counter(d['state'] for d in cs)),'inheritedUnendedAttempts':sum(d['unendedAttempts'] for d in cs)}}
(r/'final-own-chain-custody.json').write_text(json.dumps(report,indent=2)+'\n')
(r/'final-own-chain-custody.sql').write_text(pipeline+'\n'+control+'\n'+globalq+'\n')
print(json.dumps(report['summary']));print(json.dumps(gs));print(json.dumps([d for d in ps if d['task'] not in accepted]))
