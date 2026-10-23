"""Read-only audit of the explicitly transferred fixture and frozen public migration/provenance bytes."""
import hashlib,json,pathlib,shlex,subprocess,sys
ROOT=pathlib.Path(__file__).resolve().parents[1]
E=ROOT/'artifacts/phase-b/b06-archives/restart'
NAME='ama-test-b01-a5e7d68612a4'
ID='363e6bef449436390d3e21041408b53002a48311668c86864befe2535404e45b'
def main():
 label=sys.argv[1];d=E/label;d.mkdir(parents=True,exist_ok=False)
 fmt='{{json .Id}} {{json .Image}} {{json .HostConfig.Memory}} {{json .HostConfig.NanoCpus}} {{json .HostConfig.PidsLimit}} {{json .NetworkSettings.Ports}} {{json (index .Config.Labels "ama.b06.owner")}}'
 inspected=subprocess.run(['docker','inspect','--format',fmt,NAME],capture_output=True,text=True,check=True)
 assert inspected.stdout.startswith(json.dumps(ID)+' ') and inspected.stdout.rstrip().endswith('"archives-20261023"')
 (d/'fixture-selected-public.log').write_text(inspected.stdout)
 sql='''SELECT installed_rank,version,description,type,script,checksum,installed_on,execution_time,success FROM flyway_schema_history ORDER BY installed_rank;
SELECT @@version,@@global.innodb_use_native_aio,DATABASE();
SELECT id,meeting_id,SHA2(config_manifest,256) AS manifest_sha,input_fingerprint,base_revision,cancel_epoch FROM transcription_input ORDER BY id;
SELECT id,task_type,pool,subject_kind,subject_id,producer_key,input_kind,input_id,input_fingerprint,business_version,cancel_epoch,priority,max_attempts,state FROM durable_task ORDER BY id;
SELECT sequence_id,event_type,task_id,subject_kind,subject_id,subject_revision,reference_kind,reference_id FROM task_outbox ORDER BY sequence_id;
'''
 command=['docker','exec',NAME,'sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h127.0.0.1 --user="$MYSQL_USER" "$MYSQL_DATABASE" --batch -e '+shlex.quote(sql)]
 result=subprocess.run(command,capture_output=True,text=True,timeout=30)
 (d/'audit.sql').write_text(sql);(d/'stdout.log').write_text(result.stdout);(d/'stderr.log').write_text(result.stderr)
 (d/'command.json').write_text(json.dumps({'argv':command,'exit':result.returncode},indent=2)+'\n');assert result.returncode==0
 expected=json.loads((ROOT/'artifacts/phase-b/b06-archives/facts/final-history-lower-sql.json').read_text())
 for record in expected:assert hashlib.sha256((ROOT/record['path']).read_bytes()).hexdigest()==record['sha256']
 (d/'lower-sql.json').write_text(json.dumps(expected,indent=2)+'\n')
 old=(ROOT/'artifacts/phase-b/b06-archives/facts/final-history.stdout.log').read_text().splitlines()
 current=result.stdout.splitlines();assert current[:13]==old[:13],'frozen twelve-row Flyway history changed'
 (d/'custody.json').write_text(json.dumps({'fixtureId':ID,'owner':'archives-20261023','custody':'Task3 completed; root explicitly transferred sole use to Task4','initialZeroEvidence':'artifacts/phase-b/b06-archives/facts/fixture-initial-zero.log','historyMatchesTask3':True,'formalSqlCount':len(expected)},indent=2)+'\n')
 print('read-only fixture/history/lower-SQL audit retained: '+str(d))
if __name__=='__main__':main()
