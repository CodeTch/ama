# Task 9 helper runtime fix 02 — offline complete, execution pending

H09R2-01 is addressed by a new helper file only. This is helper round 2/5; Task 9 product implementation remains unstarted. The original prepared helper (SHA-256 `a868bc63152e65a4ff0b3165811c1f0f5c80e682e922c06fdf66ca8e3f90deac`), its note and manifest, and every prior intent, failed receipt, diagnostic, and archived copy remain unchanged.

The controller diagnostic records that the existing Task 7 env contains the exact guarded parent container name without the leading slash. The original clone guard accepted only its full CID. The supplied booleans show every other reference/credential check passing, the owner absent, actual read-only schema COUNT 0, and failure before owner creation or any mutating SQL. This author used those public booleans, not private env contents or value hashes.

## Exact repair and trust boundary

Only three helper lines change:

1. `env_clone` receives `parent_selected` as a required fourth argument.
2. Its container-reference guard accepts exactly `CID` or `parent_selected["name"].removeprefix("/")`.
3. `provision` passes its existing `before_selected` result, after unchanged `check_records()`, `selected()`, and `server()` checks.

`selected()` still compares the fresh selected name and CID against the parent guard and pinned identity, and `check_records()` still binds that guard to the parent ACK. The accepted name comes only from that verified result. There is no arbitrary alias, hostname, regex fallback, or resolution through the old env. A slash-prefixed name is not newly accepted. The emitted Task 9 env still uses canonical `CID` and the new Task 9 schema URL.

The exact inverse-substitution check proved all other helper text unchanged: private paths/ACLs, source hash, consumer adoption ACK, complete mounts, versions/server UUID, SQL-mode checks, escaped GRANT, one owner and one CREATE, separate root application ACK, partial-state handling, and no alternate UUID behavior are preserved. No existing Task 7 env bytes or mode were read or changed by this work.

## Offline verification

The test script AST-parses and imports the helper under a non-main name with subprocess execution blocked. Tests call only the real `check_records`, `selected`, and `env_clone` functions with fake sanitized literal data. File reads are replaced with an exact in-memory map under `/fake-W-runtime-fix02`; private metadata checks and selected transport are isolated at the external boundary. MySQL, subprocess execution, writes, and directory creation are blocked. Neither `provision` nor `main` is called, even with mocks. Their unchanged sequencing and passing of the fresh selected result are checked by AST.

The exact-name regression was written and observed failing against the original helper before creating the repair. After the repair, the final raw capture repeated that RED check and ran GREEN checks. The negative-reference test accepts any RuntimeError because malformed multiline literals may fail the parser before the reference guard; it does not require one incidental error message.

Commands were invoked from PowerShell through `wsl.exe -d Ubuntu-24.04` using Python literal here-strings where a script wrapper was needed. The focused commands inside WSL and observed results are:

```text
python3 /home/tanren/ama/.worktrees/phase-b-completion/.superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-09-helper-runtime-fix02-offline-20261024.py --helper old --case exact-name
```

Exit `1`: 1 test, expected regression failure at Task 7 env reference/credential guard.

```text
python3 /home/tanren/ama/.worktrees/phase-b-completion/.superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-09-helper-runtime-fix02-offline-20261024.py --helper new --case exact-name
```

Exit `0`: 1 test passed.

```text
python3 /home/tanren/ama/.worktrees/phase-b-completion/.superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-09-helper-runtime-fix02-offline-20261024.py --helper new --case all
```

Exit `0`: 8 tests passed, including parameterized rejection cases.

The eight cases cover: exact guarded name; full CID; other name, wrong CID, foreign name, short CID, slash-prefixed name, hostname, suffix alias, shell-like injection, multiline injected assignment, and whitespace rejection; live name/CID/owner/image mismatch; changed full mount record; parent ACK guard hash/CID/owner mismatch; canonical output CID and Task 9-only URL/allowlist; and AST preservation of the parent-check/fresh-selection/server-check/clone binding order. The source fixture bytes remain unchanged across successful clones.

Additional focused verification command (executed via the same PowerShell literal here-string to WSL Python), exit 0:

```python
from pathlib import Path
import ast
import hashlib
import json
w=Path('/home/tanren/ama/.worktrees/phase-b-completion/.superpowers/sdd/2026-10-23-ama-phase-b-remaining')
tree=w.parents[2]
old=(w/'task-09-fixture-provisioning-prepared-20261024.py').read_text()
new=(w/'task-09-fixture-provisioning-runtime-fix02-20261024.py').read_text()
changes=[('def env_clone(schema, owner, folder):','def env_clone(schema, owner, folder, parent_selected):'),('or values["AMA_TEST_CONTAINER"] != CID','or values["AMA_TEST_CONTAINER"] not in (CID, parent_selected["name"].removeprefix("/"))'),('cloned_env = env_clone(schema, owner, folder)','cloned_env = env_clone(schema, owner, folder, before_selected)')]
for a,b in changes:
 assert old.count(a)==1 and new.count(b)==1
 new=new.replace(b,a)
assert new==old
lines=['VERIFY_OK exact three-line repair; all other helper text unchanged']
for n in ['task-09-fixture-provisioning-runtime-fix02-20261024.py','task-09-helper-runtime-fix02-offline-20261024.py']:
 ast.parse((w/n).read_text())
 lines.append('VERIFY_OK ast.parse '+n)
for entry in json.loads((w/'task-09-helper-before-runtime-fix02-20261024/manifest.json').read_text())['entries']:
 for key in ['path','archive']:
  p=tree/entry[key]
  raw=p.read_bytes()
  assert len(raw)==entry['bytes'] and hashlib.sha256(raw).hexdigest()==entry['sha256'],str(p)
 lines.append('VERIFY_OK original+archive unchanged '+Path(entry['path']).name)
lines.append('VERIFY_DONE exit=0; six preserved original/archive pairs; no private reads or runtime actions')
out='\n'.join(lines)+'\n'
print(out)
with (w/'task-09-helper-runtime-fix02-offline-raw-20261024.txt').open('a',encoding='utf-8') as f:
 f.write('\n$ focused in-memory exact-diff/AST/archive-seal verification (full Python command recorded in report)\nexit=0\n'+out)
```

Raw commands, exit codes, stdout/stderr, and preservation results are retained in `task-09-helper-runtime-fix02-offline-raw-20261024.txt`. The original-helper regression exit 1 is expected and retained; no failure is hidden. Product tests were explicitly prohibited by this task and were not run. No product suite or runtime integration success is claimed.

## Continuation accounting and limits

There has been one actual provisioning invocation, which failed before SQL and owner creation. This author performed zero actual provisioning invocations. If root continues after independent review, the corrected call is invocation 2, with invocation 1 retained as a pre-SQL failure; it must never be reported as one invocation. It must use the SAME owner `task09-2d0e28ea775540f8b3525f9344edf5f0` and schema `ama_t09_2d0e28ea7755_behavior`, under the recorded controller ruling, without an alternate UUID or hidden retry. This report does not authorize or perform that invocation.

Coordinator still generates the old-to-new diff and obtains independent scoped SPEC and quality review. Root then verifies the current preconditions, executes the reviewed helper if authorized, inspects actual outcome, and supplies the separate application ACK before Task 9 author activation. Actual current private env compatibility, Docker/server state, schema creation/grant outcome, and empty-schema readiness are not established by these offline tests.

No private input, DB, Docker, network, provider, helper provisioning, product test, product source, Git mutation, or subagent action was performed. Writes were confined to the five new runtime-fix02 deliverables listed below. Original helper/docstrings and historical note retain their historical wording intentionally; this report supplies the current continuation context.

## Deliverable seals

- `task-09-fixture-provisioning-runtime-fix02-20261024.py`: 19588 bytes, SHA-256 `14c6692710382aab0e2a47448864607f8c5e76f08f0aab9f9f51a2c113edff5f`.
- `task-09-helper-runtime-fix02-offline-20261024.py`: 10494 bytes, SHA-256 `5cb0d84fe0983052b65dd1ab31280e3daa34a2fa82cefb3718f19a97814ad13c`.
- `task-09-helper-runtime-fix02-offline-raw-20261024.txt`: 4871 bytes, SHA-256 `142f5f5bf51977f3a2f24fa17fe20f10110c294c82137788d526d3885af89adb`.

This report and the other deliverables are sealed by `task-09-helper-runtime-fix02-manifest-20261024.json`; the manifest self-hash is returned separately.
