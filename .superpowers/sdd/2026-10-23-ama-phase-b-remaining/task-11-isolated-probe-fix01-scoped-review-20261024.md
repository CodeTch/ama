# Task11 isolated probe helper fix01 — independent scoped review, 2026-10-24

**SPEC verdict: PASS for I1/I2/M1 and the fix01 changed scope. Helper quality verdict: Approved for this scoped fix.** One nonblocking Minor report inconsistency is recorded below. This review makes no application or engine claim and does not replace the separate root ACK gate.

## Prior findings

- **I1 ADDRESSED.** `task-11-isolated-ddl-probe-prepared-20261024.py:30-35,63-69,133-146,485-495` now seals DE V007, checks its final `enterprise_identity.enterprise_id` alteration, creates the minimal parent stub with `utf8mb4_0900_bin`, and expects that same metadata. `task-11-isolated-ddl-probe-prepared-20261024.md:14,18-19` and the manifest correct the V002-only inference. This matches the effective 15-SQL parent key established by the prior review; no MySQL result is inferred.
- **I2 ADDRESSED.** `task-11-isolated-ddl-probe-prepared-20261024.py:90-100,462-468` checks the canonical root and `.tools` parent, rejects symlinks/unexpected type/owner/group-or-other write, then creates the 0700 leaf before the first 0600 private result. The supplied source-only check reports owner-owned 0755 accepted and group-writable, wrong-owner, and symlink parents rejected. The check source and output were inspected, not rerun.
- **M1 ADDRESSED.** `task-11-isolated-ddl-probe-prepared-20261024.py:81-87,446-447,500-504` reads and hashes each public input once, retains the sealed R1 buffer, asserts ASCII, decodes that buffer, and submits it through the existing UTF-8 MySQL stdin path. The R1 SQL remains 4,287 bytes, SHA-256 `195bec3b2b85908b708027774a2551bc25e2deda766d2802e3b3f3234befd3c2`.

## New breakage and unchanged scope

**Minor, nonblocking — stale command-ledger count.** `task-11-isolated-ddl-probe-prepared-20261024.md:51` still says Git `show` reads four sealed DE blobs, although the fix adds V007 and the same report's source-custody section correctly says five (`:14-17`). This line was unchanged in the patch but became inaccurate because of I1's correction. Change “four” to “five” when next editing the report; it does not change the helper's command path or its bound.

No other new breakage was found in the fix01 changed code, report, or manifest. The rest of the original helper was outside this scoped re-review; observations about unchanged behavior from the first review remain static-only and nonblocking here.

## Evidence and limits

The old review seal is `f7d81d5aa7a964b3277080d41169dc810bf50426511ab1eefa7cd2bce34fdd15`. I read the complete 19,350-byte old-to-new diff (SHA-256 `e7add69f5c4d59c64ff7121f599c3bf483a8dd15b8031d86d1e61a4bb52204df`) and verified the current helper/report/manifest seals `ba481d2e00272b8ce7acbf86da57dbed5fda5a4e3cd1d54d73d28fdd8f8d79a7`, `fc6a8e80be818d5d85655944b706c312ba59d79f5844f53ec2819288e95f07c8`, and `9dfab5613a0864ce06ffa0e82644e277e04032f7c789743a21fc9af6feba5ea9`. The supplied static-check source/output seals match `da02d8c4f6a93f8781d95ba7404b23ef92f25ca468154d8f07976ca73384775f` and `f9728de94aac164c1e945b09696e42919813b2981d1dc61dad23a00be09bfc04`; the saved output records exit 0. I did not rerun it. The public root ACK was absent at review time.

**CannotVerify:** actual MySQL 8.4.6 CREATE/CHECK/FK behavior, live selected-resource identity, current private parent metadata, rollback/error cases, future full prefix, writer/runtime protocol, and Task11 completion remain open. No helper import/execution, DB, Docker, network, private-content access, Git command, test rerun, or source/central-record mutation occurred in this review.