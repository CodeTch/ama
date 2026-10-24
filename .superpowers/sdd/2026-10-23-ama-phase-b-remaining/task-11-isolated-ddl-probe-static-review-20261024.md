# Independent static review — Task11 isolated R1 DDL probe preparation, 2026-10-24

**SPEC verdict: ISSUES. Helper quality verdict: NeedsFixes.** The prepared helper must not receive the public root application ACK in its current form. This is a static review only: no helper import/execution, database, Docker inspection, network, private file read, test run, or product source change occurred.

## Findings

### Important — I1. Parent stub does not represent the effective adopted 15-SQL prefix

`task-11-isolated-ddl-probe-prepared-20261024.py:62,66,115-127,466-482` derives and enforces the `enterprise_identity.enterprise_id` stub collation `utf8mb4_bin` from DE V002. But DE `V007__identity_sessions.sql:28-31` drops the old dependent FKs and changes that primary-key column to `VARCHAR(255) COLLATE utf8mb4_0900_bin NOT NULL`. A bounded read-only scan of all 15 DE migration SQL blobs found no later change to that parent key. R1's `requester_id` and `local_decided_by` also use `utf8mb4_0900_bin`. Thus the prepared report's alleged adopted-parent/R1 mismatch (`task-11-isolated-ddl-probe-prepared-20261024.md:19`) is false for the final prefix.

The wrong stub violates the brief's exact-final-parent-key requirement and may make MySQL reject the R1 CREATE for an incompatibility introduced by this probe. Because the helper creates its one-attempt schema and parent stubs before that CREATE, such a false failure would retain partial state and prevent the intended CHECK/FK/DML cases. Use the effective V007 key definition, seal/check V007 as a source input, update parent metadata expectations and the report/manifest, then independently review the renewed helper before any ACK. This finding does not assert an observed MySQL error.

### Important — I2. The private-state parent can redirect the intended 0700 directory

`task-11-isolated-ddl-probe-prepared-20261024.py:443-447` checks whether the new leaf or another matching leaf exists, then calls `mkdir` under `ROOT/.tools`. It does not establish that `ROOT/.tools` is a real canonical directory, is not a symlink, and has acceptable owner/mode before creating `task11-probe-<UUID>`. A symlinked parent would make the 0700 leaf and 0600 evidence files (`:85-89`) land outside the intended private workspace while the current guards pass. Validate the resolved parent, type, owner, and mode before the first state write, and fail closed on a symlink or unexpected parent. The review did not inspect any private path or contents.

### Minor — M1. Reuse the verified R1 bytes for the CREATE payload

`task-11-isolated-ddl-probe-prepared-20261024.py:79-82,427-429,480-482` hashes one R1 read and later opens R1 again with `read_text()` for SQL submission. The sealed R1 currently contains LF-only ASCII bytes, so this is not an observed mismatch. Nevertheless, the exact-byte gate is not tied to the submitted buffer if the public file changes between reads. Keep the once-verified bytes and decode that buffer for the MySQL command; likewise return the same bytes that `public_file` hashes.

## Conforming scope and static evidence

The reviewed package is the 46,894-byte `task-11-isolated-ddl-probe-static-review-package-20261024.diff` with SHA-256 `a9670bb55110aa796a8769dee184f47d577e3442a52682f1a3342c6ab2257727`. The helper, report, and manifest match the three supplied input seals: `8372c5b11f171ce9a9f09a0aa5530057118763ca52a7cf7d0a76b8853bec0b7f`, `f5a82f342b5ca54b4c174b31c0e585d2fed55af51d5bdb97164a7d7af9767478`, and `c935d739463681d8cf1f97d508285e80e68c93691a1320acb71ac09ed7fceef0`. The R1 SQL, root acceptance, and selected-resource guard also match the reported seals. The root ACK file was absent during review. Static `ast.parse` and `compile(tree, path, 'exec')` succeeded without executing or importing the helper.

For the bounded source check, I enumerated and read the 15 SQL blobs under DE `de1611a67ba483afe21f3b29bb69c2d32028efef:backend/adapters/src/main/resources/db/migration/`. The decisive ranges were `V002__identity_acl.sql:4-12` (original key), `V007__identity_sessions.sql:28-41` (effective change; complete blob SHA-256 `859970b83fc5623b18340c2de793ea35429cc3c0da5cbd1365b4763a5a64780d`), `V010__reliable_execution.sql:69-74,187-209`, and `V013__publications.sql:17-35,44-59`. No later enterprise parent-key modification appeared in those 15 blobs. R1 contains the expected 19 columns, six named FKs, four named CHECKs, and six secondary indexes; the helper's static case loops account for 17 direct positives, 55 expected negatives with 55 valid controls, and all 12 referenced-parent DELETE/PK-UPDATE cases. Its command path confines generated SQL to the ACK-named schema, keeps `foreign_key_checks=1`, records one-attempt intent and partial outcomes, and uses the selected CID/root defaults file without a password in argv.

## CannotVerify / remaining gate

No MySQL 8.4.6 parser result, actual CHECK/FK error code or constraint name, rollback outcome, live selected-resource metadata, private path state, command timeout behavior, future full 15-file migration prefix, writer behavior, history order, model sync, or Task11 runtime claim is established by this static review. Correcting these findings requires a newly sealed helper/report/manifest and independent review; only root can decide whether to issue the later exact public ACK. The isolated stub probe, even if it eventually passes, qualifies only R1 SQL mechanics against minimal final parent keys.