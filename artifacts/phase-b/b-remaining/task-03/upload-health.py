import os,subprocess,json,time,pathlib
q="SELECT UTC_TIMESTAMP(6) AS observed_at,1 AS connection_ok; SHOW GLOBAL STATUS WHERE Variable_name IN ('Threads_connected','Threads_running','Max_used_connections','Aborted_connects','Aborted_clients','Uptime','Connection_errors_max_connections','Connections'); SHOW VARIABLES WHERE Variable_name IN ('max_connections','connect_timeout');"
env=os.environ.copy();env['MYSQL_PWD']=env['AMA_TEST_DATABASE_PASSWORD']
r=subprocess.run(['docker','exec','-e','MYSQL_PWD','ama-test-b01-ad9f1af29eff','mysql','-u',env['AMA_TEST_DATABASE_USERNAME'],'--database=ama_b01_fixture','--batch','--raw','-e',q],env=env,capture_output=True,text=True)
assert r.returncode==0,r.stderr
info=json.loads(subprocess.check_output(['docker','inspect','ama-test-b01-ad9f1af29eff']))[0]
print(json.dumps({'utc_epoch':time.time(),'Id':info['Id'],'State':info['State'],'RestartCount':info['RestartCount'],'Limits':{k:info['HostConfig'].get(k) for k in ['Memory','NanoCpus','PidsLimit']},'Mysql_readonly_health':r.stdout,'loadavg':pathlib.Path('/proc/loadavg').read_text(),'meminfo': '\n'.join(pathlib.Path('/proc/meminfo').read_text().splitlines()[:3]),'stats':subprocess.check_output(['docker','stats','--no-stream','--format','{{json .}}','ama-test-b01-ad9f1af29eff'],text=True)},indent=2))
