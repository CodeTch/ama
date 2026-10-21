from pathlib import Path
import sys,subprocess,json
r=Path(__file__).resolve().parents[1];sys.path.insert(0,str(r))
from tools.checks.database_design import mysql
c="ama-test-b01-5a929c100b88"
pre=(r/"artifacts/phase-b/b05-functional/raw-review-preflight2.sql.txt").read_text()
cases={"capacity":"INSERT INTO model_capacity(model,capacity) VALUES ('TEST_LEGACY',1);","sequence":"UPDATE task_outbox_sequence SET next_id=2 WHERE id=1;","unknown-subject":"INSERT INTO task_subject(subject_kind,subject_id,business_version,cancel_epoch) VALUES ('UNKNOWN','legacy',0,0);","missing-meeting":"INSERT INTO task_subject(subject_kind,subject_id,business_version,cancel_epoch) VALUES ('MEETING','00000000-0000-0000-0000-000000000001',0,0);"}
for name,seed in cases.items():
 try:mysql(c,"START TRANSACTION;"+seed+pre+"ROLLBACK;")
 except subprocess.CalledProcessError as e:print(name,"rejected",e.returncode)
 else:raise AssertionError(name+" unexpectedly passed")
assert mysql(c,"SELECT COUNT(*) FROM model_capacity;").strip()=="0"
assert mysql(c,"SELECT next_id FROM task_outbox_sequence WHERE id=1;").strip()=="1"
# Persistent owned negative seed for formal Flyway failure; preserve thereafter.
mysql(c,"INSERT INTO model_capacity(model,capacity) VALUES ('TEST_LEGACY_PRESERVE',1);")
print("Formal negative seed retained")
