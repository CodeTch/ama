# Task11 isolated probe helper fix round 1 — prepared only

This is the same-author W-only correction to the independent static review `task-11-isolated-ddl-probe-static-review-20261024.md` (SHA-256 `f7d81d5aa7a964b3277080d41169dc810bf50426511ab1eefa7cd2bce34fdd15`). It addresses I1, I2, and M1 without changing the R1 SQL (`195bec3b2b85908b708027774a2551bc25e2deda766d2802e3b3f3234befd3c2`) or executing the helper. No ACK, schema, Docker/MySQL call, private-existing-content read, or product source edit occurred.

| Artifact | Old archived SHA-256 | Corrected SHA-256 |
|---|---|---|
| `task-11-isolated-ddl-probe-prepared-20261024.py` | `8372c5b11f171ce9a9f09a0aa5530057118763ca52a7cf7d0a76b8853bec0b7f` | `ba481d2e00272b8ce7acbf86da57dbed5fda5a4e3cd1d54d73d28fdd8f8d79a7` |
| `task-11-isolated-ddl-probe-prepared-20261024.md` | `f5a82f342b5ca54b4c174b31c0e585d2fed55af51d5bdb97164a7d7af9767478` | `fc6a8e80be818d5d85655944b706c312ba59d79f5844f53ec2819288e95f07c8` |
| `task-11-isolated-ddl-probe-prepared-20261024-manifest.json` | `c935d739463681d8cf1f97d508285e80e68c93691a1320acb71ac09ed7fceef0` | `9dfab5613a0864ce06ffa0e82644e277e04032f7c789743a21fc9af6feba5ea9` |

The complete exact old-three-to-new-three unified diff is `task-11-isolated-probe-fix01-old-to-new-20261024.diff`, 19,350 bytes, SHA-256 `e7add69f5c4d59c64ff7121f599c3bf483a8dd15b8031d86d1e61a4bb52204df`. The old files come from root's archive `task-11-isolated-probe-before-fix01-20261024/` and were seal-checked before diff generation.

I1: DE `V007__identity_sessions.sql:30–31` changes `enterprise_identity.enterprise_id` to `VARCHAR(255) COLLATE utf8mb4_0900_bin NOT NULL`. The complete V007 blob has SHA-256 `859970b83fc5623b18340c2de793ea35429cc3c0da5cbd1365b4763a5a64780d`. The helper now seals V007, verifies its excerpt, uses that final key in the minimal parent stub, and checks the corresponding `information_schema` metadata. The former V002-only mismatch inference is corrected in the prepared report and retained in root's archive for traceability. This is a static correction, not a MySQL CREATE observation.

I2: Before `mkdir` or the first private result write, the helper checks that the workspace root is canonical without an aliased ancestor and that `.tools` is the expected canonical, real directory owned by the current effective UID with no group/other write. An existing owner-owned 0755, 0750, or 0700 `.tools` is allowed. The new leaf remains mode 0700 and evidence files mode 0600. Any unexpected type, symlink, owner, mode, or path fails closed.

M1: `public_file` now reads each public input once, hashes that same byte buffer, and returns it. `main` retains the one verified R1 buffer; the exact CREATE command derives its SQL text from that buffer with an ASCII assertion and no second R1 read. The authored R1 SQL bytes are unchanged.

Covering command and exact saved output:

```text
python3 /home/tanren/ama/.worktrees/phase-b-completion/.superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-11-isolated-probe-fix01-static-check-20261024.py
EXIT_CODE: 0
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

The check source is `task-11-isolated-probe-fix01-static-check-20261024.py` SHA-256 `da02d8c4f6a93f8781d95ba7404b23ef92f25ca468154d8f07976ca73384775f`; the command/output capture is `task-11-isolated-probe-fix01-static-check-20261024.txt` SHA-256 `f9728de94aac164c1e945b09696e42919813b2981d1dc61dad23a00be09bfc04`. It parsed and compiled helper source without import or main execution, checked the sealed V007 Git blob read-only, asserted finite AST relationships, and exercised only the extracted pure path guard on a temporary new W fixture. The fixture accepted owner-owned 0755 and rejected a group-writable directory, simulated wrong UID, and a symlinked parent. It did not inspect any existing private contents.

The helper/report/manifest are now frozen for independent scoped review. Task11-SQL-PROBE remains OPEN until root's separate public ACK and an actual isolated engine result. This report makes no runtime qualification claim.
