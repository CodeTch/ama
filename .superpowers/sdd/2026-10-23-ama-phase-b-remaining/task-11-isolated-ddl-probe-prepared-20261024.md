# Task11 isolated R1 DDL probe — prepared only, 2026-10-24

**Status: PREPARED / NOT EXECUTED.** The companion Python file has only been parsed and compiled from source. There is no root ACK file, private probe owner, probe schema, Docker inspection, MySQL command, or application claim from this preparation. Root must independently read the helper and this ledger before deciding whether an application ACK is appropriate. Task7 remains the sole product source author.

## Exact source custody

| Input | Immutable scope | SHA-256 |
|---|---|---|
| `task-11-forward-ddl-proposal-r1-20261024.sql` | Complete 4,287 bytes, exact CREATE payload | `195bec3b2b85908b708027774a2551bc25e2deda766d2802e3b3f3234befd3c2` |
| `task-11-forward-ddl-preparation-acceptance-20261024.md` | Root's OPEN probe gate | `0ffd892b914b9d23869e5c4d829ddd730b0c8a644d481e54daa58ac83c3f5f90` |
| `task-08-owned-resource-ready-20261024.json` | `selectedReady`, full public identity/owner/image/limits/ports/mounts seed | `334d4f881e2b658eb9f5c21217bea7fa3d029dbcbf33f7e9a05e546a1235eec3` |
| DE `V002__identity_acl.sql` | lines 4–12, `enterprise_identity.enterprise_id` | `68b0d478504a554344cb12dfa8ef79c62da5c6e3455317791762fca25e70f7d0` |
| DE `V005__tasks_outbox.sql` | lines 10–32, original `durable_task.id` and PK | `55cfdc4aabf169c82f30303ac4d03dce7f24b3c6af5b4cc3ebd0089ae7e7dda7` |
| DE `V007__identity_sessions.sql` | lines 28–41, final `enterprise_identity.enterprise_id` collation | `859970b83fc5623b18340c2de793ea35429cc3c0da5cbd1365b4763a5a64780d` |
| DE `V010__reliable_execution.sql` | lines 69–74 final `durable_task.id`; 187–209 `external_execution.id` and PK | `03e89cc8bd58d5a6aa0d4ab031b8fc05a4f7d2450623df147cd84d05b68977db` |
| DE `V013__publications.sql` | lines 17–35 `final_confirmation.id`; 44–59 `publication.id` | `1de37066869617723a11f05d7423584143a64e0c9ddf8879c7b4fd107de08e3c` |

DE is commit `de1611a67ba483afe21f3b29bb69c2d32028efef`. The helper reads these five immutable Git blobs by exact ref and SHA; it never edits Git. The five parent stubs contain **only** the exact final referenced key column and its primary key. Their complete SQL is public in the helper and, if authorized, in the append-only command ledger. No unrelated parent fields or checks are invented. This qualifies the isolated FK parser and row behavior only; it cannot prove the future full 15-file parent migration prefix.

DE `V002` originally defines `enterprise_identity.enterprise_id` as `VARCHAR(255) COLLATE utf8mb4_bin`; DE `V007:30–31` changes it to `VARCHAR(255) COLLATE utf8mb4_0900_bin`. The latter is the effective adopted 15-file prefix key and matches R1 requester/decider. The helper's identity stub and metadata expectation now use that final key. This correction is static; exact CREATE remains untested on MySQL.

## Root ACK contract and invocation

The sole accepted ACK path is `/home/tanren/ama/.worktrees/phase-b-completion/.superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-11-isolated-ddl-probe-root-ack-20261024.json`, a canonical regular public W file. It **does not exist** at preparation time. Root alone may write it after independent review, with exactly these keys and values:

```json
{
  "status": "ROOT_ACK_EXACT_ISOLATED_TASK11_SQL_PROBE",
  "helperSha256": "<exact current prepared .py SHA-256 from manifest>",
  "r1Sha256": "195bec3b2b85908b708027774a2551bc25e2deda766d2802e3b3f3234befd3c2",
  "owner": "task11-probe-<one newly chosen lowercase UUID hex32>",
  "schema": "ama_t11_<first 12 characters of that UUID hex>_probe",
  "cid": "11009a6b34fef0416e651100fbde05a07067dbcbd973f1ba731c08b2281140ad",
  "constraints": {
    "oneAttempt": true,
    "commandTimeoutSeconds": 15,
    "maximumMysqlCommands": 300,
    "newSchemaOnly": true,
    "noCleanupOrRetry": true,
    "foreignKeyChecks": 1,
    "mysqlImageVersion": "8.4.6",
    "noPrivateEnvironment": true
  }
}
```

Only after root writes that exact ACK and reviews the self SHA should root invoke the absolute helper path with the single `--execute-reviewed-isolated-task11-probe` flag. No import, `python -m`, relative launch, or alternate flag is accepted. A new `.tools/task11-probe-UUID/` is created with mode 0700 after ACK validation and a fail-closed canonical private-parent check, and each append-only result file is mode 0600. The parent `.tools` must be a real canonical directory owned by the executing UID with no group or other write; owner-owned 0755, 0750, and 0700 pass. The workspace root itself must resolve to its exact path without an aliased ancestor. Any preexisting `task11-probe-*` private path or `ama_t11_` server schema hard-stops. A partial DDL result stays in place; there is no retry, drop, cleanup, reset, old-schema use, migration installation, or application to shared schemas.

## Command and expected-case ledger

Every external command uses `subprocess.run` with a 15-second timeout and exact argv, never a shell or interpolated connection string. Git `show` reads four sealed DE blobs; Docker `inspect --format` selects only ID, name, image reference/id, one owner label, running/OOM, memory/swap/CPU/PID limits, ports, and mounts. It never requests `Config.Env` or full inspect. The selected fields must equal the sealed public `selectedReady` record, including both bind mounts and exact `127.0.0.1:44727` port. MySQL runs only through `docker exec -i <exact CID> mysql --defaults-extra-file=/run/ama-task08/root.cnf --batch --raw --skip-column-names --connect-timeout=5`, adding `--force` solely for an expected-negative transaction. Neither root password nor private env file is read, hashed, placed in argv, or logged.

Each MySQL process sets UTF-8, UTC, strict SQL mode, 5-second lock waits and SELECT time limit, and `foreign_key_checks=1`. First it checks exact server `VERSION()=8.4.6` and absence of the target and any other Task11 probe schema. It creates only the ACK-named schema, then five parent key stubs, verifies each key type/collation/PK through `information_schema`, and submits the **exact R1 SQL bytes** after `USE` on that schema. On successful CREATE it records `information_schema.COLUMNS`, CHECK enforcement, FK targets and `RESTRICT`/`NO ACTION` rules, all six secondary indexes plus PK, and `SHOW CREATE TABLE`. It seeds one fixed row per referenced parent key, with three identity keys (requester, decider, 255-character boundary).

Every positive insert has its own explicit `START TRANSACTION; INSERT; SELECT COUNT=1; ROLLBACK; SELECT COUNT=0`. Each negative first runs an independent valid control with that same accepted/rollback proof. The negative then uses a fresh explicit transaction, expects exactly one named MySQL error, executes `ROLLBACK`, requires an `END_TXN` footer, and verifies zero persisted submission rows through a new connection. Parent DELETE/PK UPDATE negatives insert their referencing child within the same transaction before the parent mutation; afterward they also verify the fixed parent row remains. Errors other than the expected errno **and** named constraint/column fail the probe. A parser/setup error never counts as a CHECK rejection.

| Case family | Concrete cases | Expected |
|---|---|---|
| Accepted states | `UNDECIDED` with all five tuple fields null; `NEVER_MARKED` with each of `PREPARATION_EXHAUSTED`, `TERMINAL_UNMARKED_FAILURE`, `EXPIRED_UNMARKED_ABANDONED`; optional execution absent and present | 5 accepted rows, rollback to zero |
| Accepted numeric/LOB bounds | submission `1`, `9223372036854775806`; manifest ASCII 65,536 bytes; `你` × 21,845 = 65,535 bytes | accepted; four server `OCTET_LENGTH` fixture checks include the failing boundary sizes |
| Accepted string bounds | provider 64, business/library/folder 255 each, requester/decider 255, both hashes 64 | accepted |
| Undecided tuple violations | each of decided-at, reason, decider, attempt, execution set individually | errno 3819, `ck_publication_submission_3` |
| Never-marked required nulls | each of decided-at, reason, decider, attempt individually null | errno 3819, `ck_publication_submission_3` |
| State spelling and bytes | `UNDECIDED `, `NEVER_MARKED `, `undecided`, `NEVER_marked`, empty, `OTHER` | errno 3819, `ck_publication_submission_1` |
| Reason spelling and bytes | each of three valid reasons with trailing ASCII space, lowercase preparation reason, empty, `OTHER` | errno 3819, `ck_publication_submission_3` |
| Rejected numeric/LOB bounds | submission `0`, `9223372036854775807`; ASCII 65,537 bytes; `你` × 21,846 = 65,538 bytes | errno 3819, named CHECK 0 or 2 |
| Overlong fields | provider 65; business/library/folder/requester/decider 256; both hashes 65; task/publication/confirmation/execution ID 37 | errno 1406, named column |
| Missing FK parents | task, publication, confirmation, requester identity, decider identity, optional execution | errno 1452 and respective one of six FK names |
| Referenced parent mutation | DELETE and primary-key UPDATE independently for all six FK roles; requester and decider use separate identity rows | errno 1451 and respective FK name; fixed parent remains |

The script's finite bound is 300 MySQL processes. It never invokes Flyway, a daemon, a child JVM, another datasource, or any old schema. It preserves command argv, complete public SQL stdin, exit code, bounded stdout, and redacted bounded stderr in append-only private JSON snapshots after every command. The final status is `QUALIFIED_ISOLATED_SQL_PROBE_ONLY` only if every metadata and DML assertion passes. Any failure records `PARTIAL_HARD_STOP` and retains all prior snapshots and partial schema without cleanup.

## Scope and static preparation check

This probe can establish only exact isolated MySQL 8.4.6 R1 CREATE/CHECK/FK mechanics with minimal parent keys. It cannot establish the full future prefix migration, temporal binding immutability, one-way writer behavior, replay, application privileges, model sync, history order, or Task11 completion. Expected impact, if root later authorizes it, is one new isolated schema and public fixture data on the already-owned parent; no old schema, Flyway history, product source, or business data changes.

Static preparation used `ast.parse` and `compile(tree, path, 'exec')` only; the helper was never imported or called. The separate manifest records the resulting exact helper/report seals and check facts. Root should independently inspect the renewed helper before issuing any ACK.

## Fix round 1 after independent static review

The original prepared helper/report/manifest were archived by root under `task-11-isolated-probe-before-fix01-20261024/` with these exact SHA-256 seals: helper `8372c5b11f171ce9a9f09a0aa5530057118763ca52a7cf7d0a76b8853bec0b7f`, report `f5a82f342b5ca54b4c174b31c0e585d2fed55af51d5bdb97164a7d7af9767478`, manifest `c935d739463681d8cf1f97d508285e80e68c93691a1320acb71ac09ed7fceef0`. The first report's V002/R1 collation warning was a mistaken final-prefix inference. The archive preserves it; the effective key and current report are corrected here. No engine failure was observed.

Independent review `task-11-isolated-ddl-probe-static-review-20261024.md` found I1 (effective V007 identity key), I2 (canonical private parent), and M1 (verified R1 byte buffer). The helper now seals DE V007, uses its `utf8mb4_0900_bin` identity key in the stub and metadata assertion, validates the canonical `.tools` owner/type/mode before leaf creation or first private write, and passes one verified R1 byte buffer through ASCII decode into the CREATE command. The R1 SQL file remains unchanged.

Covering command (source check only):

```text
python3 /home/tanren/ama/.worktrees/phase-b-completion/.superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-11-isolated-probe-fix01-static-check-20261024.py
```

The exact saved output is `task-11-isolated-probe-fix01-static-check-20261024.txt` (SHA-256 `f9728de94aac164c1e945b09696e42919813b2981d1dc61dad23a00be09bfc04`); check source SHA-256 is `da02d8c4f6a93f8781d95ba7404b23ef92f25ca468154d8f07976ca73384775f`. Its exit code was 0:

```text
AST_PARSE_COMPILE_OK
V007_SEAL_AND_FINAL_KEY_RANGE_OK
V007_STUB_EXPECTATION_SEAL_CHAIN_OK
SINGLE_VERIFIED_R1_BUFFER_OK
GUARD_BEFORE_FIRST_PRIVATE_WRITE_OK
GUARD_ACCEPTS_OWNER_0755_OK
GUARD_REJECTS_GROUP_WRITABLE_OK
GUARD_REJECTS_WRONG_OWNER_OK
GUARD_REJECTS_SYMLINK_PARENT_OK
NO_HELPER_IMPORT_OR_EXECUTION_OK
```

The fixture exercised only the extracted pure path-guard function in a new temporary W directory; it did not import or execute the helper module, inspect existing private contents, or contact Docker/MySQL. The final three artifact seals are in the updated manifest. This fix remains prepared only; no ACK or probe schema exists.
