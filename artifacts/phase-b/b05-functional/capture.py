"""Read-only evidence capture for THIS worktree's helper-owned B05 fixture."""
import datetime
import hashlib
import json
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
from tools.checks.database_design import mysql

container, label = sys.argv[1:]
assert re.fullmatch(r"ama-test-b01-[0-9a-f]{12}", container)
assert re.fullmatch(r"[a-z0-9-]+", label)
assert (ROOT / ".tools/mysql-fixture" / container / "env.sh").is_file()
meta = json.loads(subprocess.check_output(["docker", "inspect", container], text=True))[0]
assert meta["Config"]["Labels"].get("ama.test-fixture") == "b01"
bindings = meta["NetworkSettings"]["Ports"]["3306/tcp"]
assert len(bindings) == 1 and bindings[0]["HostIp"] == "127.0.0.1"
queries = {
 "tables": "SELECT JSON_OBJECT('table',TABLE_NAME,'engine',ENGINE,'collation',TABLE_COLLATION) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME;",
 "columns": "SELECT JSON_OBJECT('table',TABLE_NAME,'column',COLUMN_NAME,'ordinal',ORDINAL_POSITION,'type',COLUMN_TYPE,'nullable',IS_NULLABLE,'default',COLUMN_DEFAULT,'collation',COLLATION_NAME,'extra',EXTRA) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME,ORDINAL_POSITION;",
 "indexes": "SELECT JSON_OBJECT('table',TABLE_NAME,'name',INDEX_NAME,'unique',1-NON_UNIQUE,'seq',SEQ_IN_INDEX,'column',COLUMN_NAME,'direction',COLLATION,'prefix',SUB_PART,'type',INDEX_TYPE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX;",
 "checks": "SELECT JSON_OBJECT('table',t.TABLE_NAME,'name',t.CONSTRAINT_NAME,'clause',c.CHECK_CLAUSE,'enforced',t.ENFORCED) FROM information_schema.TABLE_CONSTRAINTS t JOIN information_schema.CHECK_CONSTRAINTS c ON c.CONSTRAINT_SCHEMA=t.CONSTRAINT_SCHEMA AND c.CONSTRAINT_NAME=t.CONSTRAINT_NAME WHERE t.CONSTRAINT_SCHEMA=DATABASE() AND t.CONSTRAINT_TYPE='CHECK' ORDER BY t.TABLE_NAME,t.CONSTRAINT_NAME;",
 "foreign_keys": "SELECT JSON_OBJECT('table',k.TABLE_NAME,'name',k.CONSTRAINT_NAME,'column',k.COLUMN_NAME,'ordinal',k.ORDINAL_POSITION,'target',k.REFERENCED_TABLE_NAME,'remote',k.REFERENCED_COLUMN_NAME,'delete',r.DELETE_RULE,'update',r.UPDATE_RULE) FROM information_schema.KEY_COLUMN_USAGE k JOIN information_schema.REFERENTIAL_CONSTRAINTS r ON r.CONSTRAINT_SCHEMA=k.CONSTRAINT_SCHEMA AND r.CONSTRAINT_NAME=k.CONSTRAINT_NAME WHERE k.CONSTRAINT_SCHEMA=DATABASE() AND k.REFERENCED_TABLE_NAME IS NOT NULL ORDER BY k.TABLE_NAME,k.CONSTRAINT_NAME,k.ORDINAL_POSITION;"
}
actual = {key: [json.loads(x) for x in mysql(container, query).splitlines()] for key, query in queries.items()}
rows = {}
ddl = {}
for item in actual["tables"]:
    table = item["table"]
    assert re.fullmatch(r"[a-z0-9_]+", table)
    columns = [x["column"] for x in actual["columns"] if x["table"] == table]
    assert all(re.fullmatch(r"[a-z0-9_]+", col) for col in columns)
    arguments = ",".join("'" + col + "',`" + col + "`" for col in columns)
    rows[table] = [json.loads(x) for x in mysql(container, "SELECT JSON_OBJECT(" + arguments + ") FROM `" + table + "`;").splitlines()]
    ddl[table] = mysql(container, "SHOW CREATE TABLE `" + table + "`;")
payload = {
 "captured_at_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
 "capture_label": label,
 "scope": "Timestamped historical snapshot, not a claim about later fixture state",
 "container": container,
 "container_id":meta["Id"], "args":meta["Args"], "labels":meta["Config"]["Labels"], "port_bindings":meta["HostConfig"]["PortBindings"], "state":{k:meta["State"][k] for k in ("Status","Running","ExitCode","OOMKilled")},
 "loopback_port": bindings[0]["HostPort"],
 "mysql_version": mysql(container, "SELECT VERSION();").strip(),
 "image": meta["Config"]["Image"],
 "native_aio": mysql(container, "SELECT @@innodb_use_native_aio;").strip(),
 "auto_remove":meta["HostConfig"]["AutoRemove"], "mounts":meta["Mounts"],
 "metadata": actual,
 "rows": rows,
 "show_create": ddl,
 "migration_sha256": {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((ROOT / "backend/adapters/src/main/resources/db/migration").glob("*.sql"))}
}
out = pathlib.Path(__file__).parent / (label + "-snapshot.json")
assert not out.exists(), "never overwrite a historical snapshot"
out.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n")
print(json.dumps({"capture": str(out), "tables": len(actual["tables"]), "history": rows.get("flyway_schema_history", [])}, ensure_ascii=False, indent=2))
