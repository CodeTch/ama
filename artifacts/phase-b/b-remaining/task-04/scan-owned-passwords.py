from pathlib import Path
import re,json,shlex
root=Path.cwd();e=root/'artifacts/phase-b/b-remaining/task-04';owned=(e/'owned-paths.txt').read_text().splitlines();private={}
for line in (root/'.tools/phase-b-remaining/env.sh').read_text().splitlines():
 k,v=line.removeprefix('export ').split('=',1);private[k]=shlex.split(v)[0]
text=(root/'backend/integration-tests/src/test/java/ama/storage/LocalFixture.java').read_text();m=re.search(r'PASSWORD\s*=\s*"([^"\n]+)"',text);assert m,'Public fixture password declaration shape changed'
secrets={'current_owned_database_password':private['AMA_TEST_DATABASE_PASSWORD'].encode(),'current_native_tls_test_password':m.group(1).encode()}
files=[p for p in e.rglob('*') if p.is_file()]+[root/p for p in owned];hits={name:[str(p.relative_to(root)) for p in files if value in p.read_bytes()] for name,value in secrets.items()};assert not any(hits.values()),{'credential_hit_paths':hits}
(e/'pre-report-credential-scan.json').write_text(json.dumps({'scanned_file_count':len(files),'exact_owned_password_hit_counts':{name:0 for name in secrets},'secret_values_printed':False},indent=2)+'\n');print('Exact SQL/native test password hits:0; checked',len(files),'files')

