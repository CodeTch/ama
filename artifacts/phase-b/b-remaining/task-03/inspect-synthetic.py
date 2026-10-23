import os,subprocess
query="SELECT t.id,t.state,t.input_id,t.subject_id,m.owner_id,a.attempt,a.ended_at FROM durable_task t JOIN meeting m ON m.id=t.subject_id LEFT JOIN task_attempt a ON a.task_id=t.id WHERE t.task_type='TRANSCRIPT_IMPORT_V1' AND t.pool='TRANSCRIPT_IMPORT' AND m.owner_id LIKE 'authority-%' ORDER BY t.created_at,t.id LIMIT 25"
env=os.environ.copy();env['MYSQL_PWD']=env['AMA_TEST_DATABASE_PASSWORD']
r=subprocess.run(['docker','exec','-e','MYSQL_PWD','ama-test-b01-ad9f1af29eff','mysql','-u',env['AMA_TEST_DATABASE_USERNAME'],'--database=ama_b01_fixture','--batch','--raw','-e',query],env=env,capture_output=True,text=True)
print(r.stdout);print(r.stderr);raise SystemExit(r.returncode)
