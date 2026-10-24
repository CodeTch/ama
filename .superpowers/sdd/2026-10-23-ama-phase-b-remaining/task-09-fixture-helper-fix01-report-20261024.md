# Task 9 fixture helper static fix 01 — prepared, not executed

Scope: same-author repair of the four P2 guard findings and one P3 diagnostic in `task-09-fixture-helper-static-review-20261024.md` (`abad9b1698afdd74c5c84067397383c379bfc4d6b22fe993118ddfa8167ce69d`). The exact three-file fix base is retained in `task-09-fixture-helper-before-fix01-20261024/`, sealed by `snapshot-seal.json` (`c10222c06d0a19c3d9a444367a9a7a946ea79c74eb6b852e0832e28f7a7efbcf`). The complete old-to-fix unified diff is `task-09-fixture-helper-fix01-complete-old-to-fix-20261024.diff` (`6bcac4a2c89fac9f8844c980b4b3050e092bcc071d6be22d2f46bc89de6067f9`). No original 3fe helper bytecopy is claimed; that was an unretained intermediate revision.

Fixes:

1. Private input guards now validate owner directories, every intermediate component, resolved containment, exact private modes, Task 8 mounted cnf paths, Task 7 env, and exact Task 5 S3/PKI references and relevant path metadata before SQL. No Task 5 private values were read during preparation.
2. The selected Docker mount dictionaries must match the frozen Task 7 guard in full, including type, mode, and propagation.
3. The grant invocation sets the captured safe SQL mode in the same MySQL session as the escaped-underscore GRANT and checks the effective mode; the global `partial_revokes=0` and `NO_BACKSLASH_ESCAPES` absence guards remain. It changes no global setting.
4. Adoption evidence is restricted to the exact future public `task-07-consumer-activation-root-ack-20261024.json`. The helper rejects other paths before reading/hashing and validates completion fields plus the SHA-256 of the exact public adoption document. Root must create that ACK only after actual Task 7 adoption; it does not exist as part of this fix.
5. If the first owner/state write fails, the helper reports that no SQL was issued and that the exact owner path may exist without `state.json`. It does not delete or retry the path.

Final file seals: helper `a868bc63152e65a4ff0b3165811c1f0f5c80e682e922c06fdf66ca8e3f90deac` (19,505 bytes); note `1b68e652eb82fca7e8c563a93dc4e62aac834ec2cb8d0dfe8717b528c002eaaf` (6,204 bytes); manifest `944873f3812703f125cf9c200ec70e9d6acefb508f15772a77899b253e36cac6` (2,486 bytes). Static checker source `6ec536c7c70931cb63c209e587ed33faefb67546bf619efcb1d789cef8614362` (3,541 bytes).

Static covering command from `/home/tanren/ama/.worktrees/phase-b-completion`:

```text
python3 .superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-09-fixture-helper-fix01-static-20261024.py
```

Exit code: `0`. Raw output:

```text
STATIC_OK snapshot:task-09-fixture-provisioning-prepared-20261024.py
STATIC_OK snapshot:task-09-fixture-provisioning-prepared-20261024.md
STATIC_OK snapshot:task-09-fixture-provisioning-prepared-20261024-manifest.json
STATIC_OK ast.parse
STATIC_OK tree-level
STATIC_OK fixed-public-ack
STATIC_OK ack-field:COMPLETE_ADOPTED
STATIC_OK ack-field:sourceCommit
STATIC_OK ack-field:adoptionCommit
STATIC_OK ack-field:reviewSpec
STATIC_OK ack-field:reviewQuality
STATIC_OK ack-field:openCriticalImportant
STATIC_OK ack-field:adoptionDocumentSha256
STATIC_OK public-doc-binding
STATIC_OK private-owner-path
STATIC_OK private-inputs
STATIC_OK pinned-s3-pki
STATIC_OK complete-mounts
STATIC_OK same-grant-session
STATIC_OK pre-sql-orphan-diagnostic
STATIC_OK no-runtime-provisioning
STATIC_OK pure-grant-escape:ama_t09_0123456789ab_behavior
STATIC_OK pure-grant-escape:ama_t09_ffffffffffff_behavior
STATIC_ONLY_DONE; helper was parsed, never imported or executed
```

No helper import/execution, Docker, MySQL, private value read, JVM, tests, product source/model, Git/index action, or Task 9 activation occurred. Actual Task 7 adoption and root machine ACK, Task 9 source/prefix repin, live selected resource and env compatibility, scoped grant result, empty-schema observation, and separate root application ACK remain future gates.
