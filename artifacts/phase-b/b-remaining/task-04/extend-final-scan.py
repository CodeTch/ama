from pathlib import Path
p=Path('artifacts/phase-b/b-remaining/task-04/seal-and-commit.py');s=p.read_text().replace('import subprocess,json,hashlib,os,shlex','import subprocess,json,hashlib,os,shlex,re')
s=s.replace("secret=private['AMA_TEST_DATABASE_PASSWORD'].encode();files=", "native=re.search(r'PASSWORD\\s*=\\s*\"([^\"\\n]+)\"',(root/'backend/integration-tests/src/test/java/ama/storage/LocalFixture.java').read_text());assert native\nsecrets=[private['AMA_TEST_DATABASE_PASSWORD'].encode(),native.group(1).encode()];files=")
s=s.replace("if secret in p.read_bytes()", "if any(secret in p.read_bytes() for secret in secrets)")
s=s.replace("'exact_owned_database_password_hit_count':0", "'exact_owned_database_password_hit_count':0,'exact_native_tls_test_password_hit_count':0")
p.write_text(s,newline='\n')
