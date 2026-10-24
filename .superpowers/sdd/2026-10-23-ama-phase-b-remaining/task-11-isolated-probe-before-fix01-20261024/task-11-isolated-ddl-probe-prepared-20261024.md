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
| DE `V010__reliable_execution.sql` | lines 69–74 final `durable_task.id`; 187–209 `external_execution.id` and PK | `03e89cc8bd58d5a6aa0d4ab031b8fc05a4f7d2450623df147cd84d05b68977db` |
| DE `V013__publications.sql` | lines 17–35 `final_confirmation.id`; 44–59 `publication.id` | `1de37066869617723a11f05d7423584143a64e0c9ddf8879c7b4fd107de08e3c` |

DE is commit `de1611a67ba483afe21f3b29bb69c2d32028efef`. The helper reads these four immutable Git blobs by exact ref and SHA; it never edits Git. The five parent stubs contain **only** the exact final referenced key column and its primary key. Their complete SQL is public in the helper and, if authorized, in the append-only command ledger. No unrelated parent fields or checks are invented. This qualifies the isolated FK parser and row behavior only; it cannot prove the future full 15-file parent migration prefix.

**Review concern before application:** DE `V002` defines `enterprise_identity.enterprise_id` as `VARCHAR(255) COLLATE utf8mb4_bin`, while R1 defines requester and decider as `VARCHAR(255) COLLATE utf8mb4_0900_bin`. MySQL may reject exact R1 CREATE as an incompatible FK. The helper deliberately preserves that mismatch and hard-stops on the exact CREATE error, leaving the new schema and parent stubs intact for root inspection. It does not alter R1 or claim this concern is an observed engine result.

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

Only after root writes that exact ACK and reviews the self SHA should root invoke the absolute helper path with the single `--execute-reviewed-isolated-task11-probe` flag. No import, `python -m`, relative launch, or alternate flag is accepted. A new `.tools/task11-probe-UUID/` is created with mode 0700 immediately after ACK validation, and each append-only result file is mode 0600. Any preexisting `task11-probe-*` private path or `ama_t11_` server schema hard-stops. A partial DDL result stays in place; there is no retry, drop, cleanup, reset, old-schema use, migration installation, or application to shared schemas.

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

Static preparation used `ast.parse` and `compile(tree, path, 'exec')` only; the helper was never imported or called. The separate manifest records the resulting exact helper/report seals and check facts. Root should independently inspect potential MySQL behavior, especially the DE/R1 identity collation mismatch, before issuing any ACK.
