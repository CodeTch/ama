# Task10 future isolated fixture helper — PREPARED, unused

Only the named W helper, this note, its manifest, and the small offline check/raw capture were created. Task9 remains the sole product author. No Task10 product or database work has begun. No private file contents or metadata, Docker, DB, network, credentials, product source/test tree, Git state, or provisioning were accessed. No adoption/application ACK was created. No subagent was spawned.

## Exact adaptation

The source is the complete reviewed Task9 runtimefix02 helper `task-09-fixture-provisioning-runtime-fix02-20261024.py`, 19,588 bytes, SHA-256 `14c6692710382aab0e2a47448864607f8c5e76f08f0aab9f9f51a2c113edff5f`. The independent fix02 review and historical prepared note were read. All original Task9 helpers and evidence remain immutable.

The adaptation consists only of ordered destination Task9→Task10 substitutions, followed by source Task7→Task9 substitutions, the fixed source owner/schema/evidence/doc/base values, the adoption task number, and two checks pinning the already-issued public Task9 fixture record bytes. The offline check reconstructs the full expected helper text from the reviewed source and asserts exact equality. No unchanged helper behavior was refactored.

The input is exclusively `.tools/task09-2d0e28ea775540f8b3525f9344edf5f0/env.sh`; it was not read or statted during preparation. Future literal parsing requires the eight Task9 allowlist keys and emits the corresponding eight Task10 keys. Existing test credentials and Task5 S3/PKI references remain in memory only. Output uses the new Task10 owner/run root/schema URL and canonical full CID. The reviewed full-CID **or exact freshly selected container name without its leading slash** acceptance remains intact; no arbitrary alias/name resolution was added. `check_records`, `selected`, and `server` still precede `env_clone(..., before_selected)`.

Public Task9 application ACK: `task-09-root-fixture-application-ack-20261024.json`, SHA-256 `6f926c62b9710dab9c889eee104543b3250bfd5e33fc23d67fb655677e4c7e5d`. Public creation guard: `task-09-fixture-creation-guard-20261024.json`, SHA-256 `d9b8f75b2dccda19f4e77fa05dc03d512881fa3af9b14ee30bd9c22f5ea6d0f1`. Both seals were verified from public bytes and are enforced by the new helper, along with the original mutual guard/ACK binding. They bind baseline `cff858e163ca7dea774dcf05c707ef7551956156`, source schema `ama_t09_2d0e28ea7755_behavior`, and the recorded parent/server readiness.

Retained parent: owner `task08-848e93aace8b4f0fa34fc548267f3dc5`, CID `11009a6b34fef0416e651100fbde05a07067dbcbd973f1ba731c08b2281140ad`, loopback port 44727, pinned MySQL 8.4.6 image, server UUID `1bdea6eb-cf32-11f1-8fe3-fef4aa4b3a25`, 768 MiB memory and swap, 1 CPU, 256 PIDs, InnoDB/AIO 0. Full mount comparisons, before/after identity/resources/server checks, private owner/intermediate/path/ACL guards, `partial_revokes=0`, safe SQL mode, escaped-underscore GRANT and same-session mode confirmation are unchanged. No new container, credentials, PKI, or S3 resource is needed.

## Future gates and one-shot execution

Root must first observe actual Task9 adoption and repin actual Task10 source/full migration prefix. The fixed future public gate is W/`task-09-consumer-activation-root-ack-20261024.json`; it is not created by this preparation and no Task9 completion is asserted here. The helper requires its exact path and SHA-256, task integer 9, status `COMPLETE_ADOPTED`, baseline `cff858e163ca7dea774dcf05c707ef7551956156`, lowercase 40-hex source/adoption commits, SPEC PASS, quality Approved, zero open Critical/Important findings, and `docs/acceptance/b/task-B-remaining-09-minutes-generation-adoption-20261024.md` with its matching SHA-256. Fixed-path rejection precedes evidence reads; original public symlink/canonical path checks remain. Root verifies actual commits/reviews/doc when issuing that receipt.

After independent review of these exact helper bytes, root may choose **one** fresh owner `task10-<32 lowercase hex>` and corresponding schema `ama_t10_<same first 12 hex>_behavior` and invoke once. This is a future command shape, not an executed command:

```sh
python3 .superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-10-fixture-provisioning-prepared-20261024.py \
  --execute-reviewed-task10-provisioning \
  --approved-helper-sha256 'c624807461392d0844a299ff73ef1cea64cb76aa39cc43846a973c068c86fa18' \
  --task9-adoption-evidence '/home/tanren/ama/.worktrees/phase-b-completion/.superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-09-consumer-activation-root-ack-20261024.json' \
  --task9-adoption-sha256 '<actual issued public adoption ACK SHA-256>' \
  --owner 'task10-<fresh 32 lowercase hex>' \
  --schema 'ama_t10_<same first 12 hex>_behavior'
```

The helper requires absent owner/schema, records intent before SQL, issues only CREATE of the real schema name and the escaped database-level GRANT to the existing test principal, verifies that principal's DB access and zero tables, and writes a 0600 env inside a 0700 owner. It retains honest partial/unknown outcome evidence. Do not retry, switch UUID to hide a failed attempt, drop, clean, or reset. Root must issue a **separate Task10 application ACK** after actual empty-schema readiness and before the future author's normal bootstrap against the actual current full prefix. The author never performs provisioning.

Historical Task9 evidence records invocation 1's pre-SQL exact-name/CID failure and the explicitly approved same-owner invocation 2 after fix02; it remains unchanged. Task10 retains the fix and receives no implicit retry authorization from that history.

## Offline verification

The small standalone check uses a non-main module name and never calls `provision` or `main`. Only the new/old public helper source and actual public Task9 ACK/guard are read. Test private env/adoption document/adoption ACK values are synthetic in-memory bytes. All test file reads are restricted to that memory map; private/public metadata helpers are replaced with explicit fake path allowlists. Process/SQL calls, path metadata, writes, mkdir, and helper provisioning entry points are denied during behavioral tests. Real `check_adoption`, `check_records`, `selected`, and `env_clone` execute against these fakes. The source-level assertion checks exact adaptation and the checked-source call order without provisioning.

Four test methods passed, exit 0: future Task9 adoption ACK fields/hash/fixed-path rejection; exact actual-public parent hashes plus altered-record/full-mount rejection; Task9→Task10 allowlist/CID-or-exact-name acceptance/canonical output/foreign-reference rejection; exact source adaptation/AST call order/new names and CLI. Source env bytes are unchanged after clones. These are targeted offline checks, not live provisioning success or Task9 product test results.

Exact PowerShell literal here-string command used to run and capture the checks:

```powershell
@'
from pathlib import Path
import contextlib
import io
import runpy
w=Path('/home/tanren/ama/.worktrees/phase-b-completion/.superpowers/sdd/2026-10-23-ama-phase-b-remaining')
stream=io.StringIO()
code=0
with contextlib.redirect_stdout(stream),contextlib.redirect_stderr(stream):
 try:runpy.run_path(str(w/'task-10-fixture-helper-offline-20261024.py'),run_name='__main__')
 except SystemExit as ex:code=ex.code or 0
raw=stream.getvalue()+'\nOffline check exit: '+str(code)+'\n'
with (w/'task-10-fixture-helper-offline-20261024.raw.txt').open('x',encoding='utf-8',newline='') as f:f.write(raw)
print(raw)
raise SystemExit(code)
'@ | wsl.exe -d Ubuntu-24.04 -- python3 -
```

The exclusive-create capture above is historical and must not overwrite the retained raw file. To rerun just these offline checks without writing evidence:

```powershell
wsl.exe -d Ubuntu-24.04 -- python3 -B /home/tanren/ama/.worktrees/phase-b-completion/.superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-10-fixture-helper-offline-20261024.py
```

Both new Python sources also parsed successfully with `ast.parse`, without import/provisioning or bytecode artifacts. One attempt to write this note failed at PowerShell parse time because the embedded command's here-string terminator collided with the enclosing authoring here-string; no Python or writes ran in that failed command. The corrected authoring command inserts that terminator as data. This did not affect the successful offline test capture.

The coordinator's independent narrow review is still pending. Leave this preparation unused until all actual future gates are satisfied.

## Deliverable seals

- `task-10-fixture-provisioning-prepared-20261024.py`: 19813 bytes; SHA-256 `c624807461392d0844a299ff73ef1cea64cb76aa39cc43846a973c068c86fa18`.
- `task-10-fixture-helper-offline-20261024.py`: 11251 bytes; SHA-256 `021f03d18304fde6b2428f91c07082c8d058f871f9164cf1e3d958d4c2b35cd7`.
- `task-10-fixture-helper-offline-20261024.raw.txt`: 571 bytes; SHA-256 `16c69363e5164d8e7cb2385ceefe44932bf75599c62ddc1c80870a99a78e0417`.
