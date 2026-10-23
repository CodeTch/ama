"""Public allowlisted history/provenance for the sole new archives fixture."""
import hashlib,json,pathlib,subprocess,shlex,sys
ROOT=pathlib.Path(__file__).resolve().parents[1]
E=ROOT/"artifacts/phase-b/b06-archives/facts"
owner=json.loads((ROOT/".tools/b06-archives/owned.json").read_text())
name=owner["name"]
label=subprocess.run(["docker","inspect","--format",'{{index .Config.Labels "ama.b06.owner"}}',name],capture_output=True,text=True,check=True).stdout.strip()
assert label=="archives-20261023"
sql="""SELECT installed_rank,version,description,type,script,checksum,installed_on,execution_time,success FROM flyway_schema_history ORDER BY installed_rank;
SELECT @@version,@@global.innodb_use_native_aio,@@global.time_zone,UTC_TIMESTAMP(6),DATABASE();
SELECT id,meeting_id,kind,base_revision,input_fingerprint,created_at FROM input_batch ORDER BY id;
SELECT batch_id,ordinal,file_id,sha256 FROM input_batch_file ORDER BY batch_id,ordinal;
SELECT id,meeting_id,batch_id,kind,processing_config_version,parser_version,manifest_schema_version,OCTET_LENGTH(config_manifest) AS manifest_bytes,CHAR_LENGTH(config_manifest) AS manifest_chars,SHA2(config_manifest,256) AS manifest_sha,input_fingerprint,base_revision,cancel_epoch FROM transcription_input ORDER BY id;
SELECT id,task_type,pool,subject_kind,subject_id,producer_key,input_kind,input_id,input_fingerprint,business_version,cancel_epoch,priority,max_attempts,state FROM durable_task ORDER BY id;
SELECT id,meeting_id,revision,batch_id,input_fingerprint,processing_input_id,producer_task_id FROM transcript_revision ORDER BY id;
SELECT id,meeting_id,sha256,size_bytes FROM file_object ORDER BY id;
"""
command=["docker","exec",name,"sh","-c",'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h127.0.0.1 --user="$MYSQL_USER" "$MYSQL_DATABASE" --batch -e '+shlex.quote(sql)]
result=subprocess.run(command,capture_output=True,text=True,timeout=60)
stem=sys.argv[1]
(E/(stem+".sql")).write_text(sql)
(E/(stem+".stdout.log")).write_text(result.stdout)
(E/(stem+".stderr.log")).write_text(result.stderr)
(E/(stem+".command.json")).write_text(json.dumps({"argv":command,"exit_code":result.returncode},indent=2)+"\n")
assert result.returncode==0
scripts=sorted((ROOT/"backend/adapters/src/main/resources/db/migration").glob("V*.sql"))
records=[{"path":str(p.relative_to(ROOT)),"sha256":hashlib.sha256(p.read_bytes()).hexdigest()} for p in scripts]
assert records==json.loads((E/"lower-sql-before.json").read_text())
(E/(stem+"-lower-sql.json")).write_text(json.dumps(records,indent=2)+"\n")
print("captured public history/provenance "+stem)
