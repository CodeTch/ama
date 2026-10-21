# 阶段 A 独立审查记录

整体审查未发现 Critical/Important；Minor A-FINAL-M1 已在 `aeb2389` 修复并通过独立范围复审。以下保留原始审查和复审，原始 With fixes 结论应与后附解决记录一起阅读。公司真实联调及生产资格仍未通过。

# Phase A whole-branch independent review

Reviewed branch: codex/phase-a-foundation  
Base: 3b8c15f3994acc1b2a3c6ddd2f6ae6284fe7374c  
Head: d4c9cd4ccef2a01e682f351ac3325d9a47e81d2f  
Date: 2026-10-21

## Scope and method

Reviewed the supplied full 12-commit package in passes: backend/configuration/OpenAPI/build/runner; contract, model and HA tools and their tests; web shell/client/capture/visual gates; delivery documentation and evidence. Large lockfiles, repeated inventory records and captured DOM/style JSON were also parsed structurally rather than omitted due to terminal truncation. Compared the stage plan, master Global Constraints and relevant architecture v0.2 module, configuration, HA, capacity, realtime, security and API sections. Consulted the original model protocol document to resolve the wire-format contract. Original designDocs have no changes from the stage base.

The checkout, index and HEAD remained read-only. Only this report was written; no helpers were dispatched, no company endpoints were contacted and no suites were rerun. The coordinator's recent combined verification is appropriate evidence for this documentation-only final commit: 69 Python, 11 Java, 8 web and 10 browser cases, all without failure/skip, plus OpenAPI lint/bundle, typecheck, build, generated-type comparison and packaged startup. I inspected the summary, corresponding log endings and fresh JUnit counts; I did not characterize those checks as real business, model, MySQL failover or deployment qualification. Wrapper script/JAR and prototype source hashes match the recorded values. The final-review placeholder in the acceptance summary is intentional pending this report.

## Strengths

- Independent database/storage selection is preserved through runtime configuration, topology validation and the handoff. Local configuration does not acquire an accidental S3 dependency. No third project node or mandatory Redis/MQ/Kubernetes appears.
- Health access is constrained by both the actual management port and peer network, with forwarded headers disabled. Liveness is separate from real JDBC readiness; the controlled JDBC outage/recovery test checks behavior rather than toggling a production test flag.
- The integrated runner discovers all delivered Python suites, consumes fresh execution evidence, rejects skips/zero cases, propagates child failures and registers the delivered browser/web suites. The earlier Python accounting fix survives the web integration.
- External contract data is fail-closed and output is deliberately allowlisted. Duplicate/contradictory evidence and arbitrary secret-containing input cannot become a successful integration claim.
- The model probe honors all six known request shapes, validates required/optional response fields, counts errors and unsent work, separates offered rate from observed attempts, and preserves ambiguous work after timeout or incomplete Content-Length framing. Queue censoring is now field-specific.
- HA evaluation distinguishes deployment prerequisites from recovery drills, ties evidence to the inventory hash and freshness, checks all six database/storage combinations, and preserves fencing requirements for external-database/local deployments. The duplicate normalized active-mount fix is included.
- The web shell honestly exposes unavailable integrations; it does not call management health to invent an authenticated business state. Prototype capture consumes the unchanged original, normal visual tests cannot auto-create missing baselines, and original full screenshots/provenance are retained.
- Tests include real controlled HTTP/JDBC boundaries and failure paths, not solely implementation-shaped mocks. Documentation consistently distinguishes local implementation evidence from external qualification.

## Issues

### Critical

None found.

### Important

None found.

### Minor

**A-FINAL-M1 — Module setup instructions are stale after cross-task integration.**

- Files: web/README.md:6; backend/README.md:9 (also backend/README.md:36-37).
- What: The web setup sources the implementer's absolute /home/tanren/ama/.worktrees/phase-a/.tools/env.sh rather than the current repository's environment. The backend's otherwise self-contained fresh-setup sequence installs only root npm dependencies and then calls the now-integrated all runner, which unconditionally discovers web unit tests and needs web/node_modules/.bin/tsx. The backend mode descriptions still say web/e2e/visual are unregistered and omit web tests from all.
- Why: A developer following either module README from a clean checkout at another path can fail before reaching the advertised runnable foundation, or load a different checkout's toolchain. The root README and acceptance summary already contain the correct broader setup, so this is a localized documentation/onboarding defect rather than a broken implementation.
- Fix: Use repository-relative . .tools/env.sh, documenting bootstrap first or linking to the root setup prerequisite. Add npm --prefix web ci --ignore-scripts before the backend all invocation, or point to the canonical root setup. Update backend mode/all descriptions to the integrated runner behavior.
- Verification: Check each documented command sequence against a fresh repository without preexisting web dependencies; no full test rerun is needed for this documentation-only correction.

## Recommendations

Resolve A-FINAL-M1 before declaring the handoff clean, then record the final whole-stage review outcome in the acceptance summary. Keep the current explicit distinction between implementation evidence, company integration evidence and deployment evidence in subsequent phases. The pending external prerequisites should remain pending while independent B work proceeds.

## Declined to judge

Each line below is an explicit boundary, not an assertion that the later requirement is already satisfied.

1. Real 100-meeting/300-user capacity, long stability runs and fault-time single-node capacity: no authorized target environment or real load evidence exists; A-03/C/F retain those gates.
2. Real model quality, repeated-word preservation, terminology/numbers, overlap, stable-text semantics and online speaker identity: loopback protocol fixtures cannot establish these; authorized samples and platform evidence are still required.
3. Model total wall-clock deadlines under DNS blocking or slow trickle responses: the delivered diagnostic explicitly defines socket I/O timeouts and an admission budget, not a production deadline; production adapters remain C work.
4. Cross-process/global model quota leases, retry orchestration, server cancellation and releasing unknown work across separate invocations: documented limits of the single-process diagnostic; the report warns against blindly rerunning ambiguous work.
5. Corporate proxy, mTLS, private trust and undisclosed authentication requirements: no verified company contract is available; the direct explicit-endpoint diagnostic does not silently inherit proxies/credentials or claim those transports work.
6. Automated audio splitting, overlap generation, six-service orchestration and per-file offset/VAD variants in one batch: the probe intentionally consumes caller-prepared bounded inputs, with separate batches documented; these are not missing business pipeline implementations in A.
7. Real SSO, directory identity, ACLs, CSRF/session protection, live permission revocation, frontend destination restrictions and business endpoint policy: the shell performs no business API operation and the current backend denies public requests; B and later security tasks must implement these before opening authenticated operations.
8. Full /api/v1 business error codes, field/object location and trace correlation: only internal actuator health/security responses exist in this deliverable. Their small explicit OpenAPI contract is reviewed here; the later business API/error contract is not declared complete.
9. MySQL-specific transaction/locking/schema/migration behavior and database failover: the H2 TCP test proves actual JDBC health behavior only. B-01/F require the real MySQL tests, as the handoff explicitly states.
10. Storage adapters, backend pinning during writes, migration/history routing, fsync/atomic publication, mount loss, file ACL/Range and recovery durability: configuration/preflight preserve those requirements, but the actual B/F implementations are not part of this stage.
11. Actual fencing, RHEL kernel/subscription/DRBD compatibility, storage flush semantics, VIP reachability, anti-affinity, physical fault domains, backup restoration and RTO/RPO: offline facts and supplied artifacts cannot prove them; no remote/fencing action was performed.
12. Authenticity of HA evidence, reviewer identities, artifact hashes/content and signatures: the approved supplied-evidence-only boundary is explicit. A syntactically complete future report can pass conditional checks but cannot constitute production certification.
13. HA filesystem symlink/device resolution, aliases and mount topology beyond supplied lexical facts: the tool is expressly offline; actual mounted-volume identity/path verification belongs to PR-04/PR-15. The existing exact duplicate normalized mount contradiction was not excused by this boundary and its fix was reviewed.
14. HA qualification while multiple historical/default backends coexist: the v1 inventory describes one selected backend and requires separate/combined qualification for migration; it does not claim system-wide certification from one default-backend report.
15. A-02 never returning verified/exit 0 for external contracts: intentional offline ledger semantics; complete claims remain pending until independently authenticated platform validation.
16. Full-page meeting data, filters, pagination, import/recording/workbench flows and all future prototype pages: A-05 delivers a shell and captures reference entries, not those later B/C/D/E flows.
17. The agreed 204px shared-strip comparison and demo-identity/control exclusion: explicitly adjudicated scope, with full original and unmasked actual references retained. I reviewed provenance/capture/test implementation and used the coordinator's supplied full-image inspections; I did not independently rerender all images or treat the strip as full-page acceptance.
18. Native Windows/macOS bootstrap, arbitrary Linux/Python environments, enterprise browser support and production SPA hosting: commands target the documented Linux/WSL development environment; real target deployment/browser qualification remains F. The author-specific README path is nevertheless a current defect and is reported above.
19. Existing tool-directory tampering, archive-cache corruption recovery and reauthentication of every pinned dependency publisher: bootstrap verifies downloaded archives and wrapper hashes; this review checks pin/integrity consistency and supplied build evidence, not a new external supply-chain audit.
20. S3 endpoint reachability/credential validity, custom JDBC URL driver timeout policies and production secret distribution: configuration validates the current boundary and documents bounded custom URLs; no real external configuration was supplied or tested.
21. New business features, future IT/load/ops selectors and production observability: their absence is explicit rather than silently skipped or reported passing; adding them belongs to their planned tasks.
22. Arbitrary extra JSON response keys or duplicate-key parser policy beyond the defined required model response fields: the diagnostic validates documented shapes and finite numeric fields; strict whole-response schema validation is not an established platform contract here.

## Assessment

**Ready to merge? With fixes.**

No critical or important implementation defect was found in the reviewed stage-A range. One small, concrete onboarding documentation correction (A-FINAL-M1) remains before a clean handoff; the code and controlled verification support continuing independent phase-B work, not production/business/HA/capacity acceptance.


---

# Phase A final-fix scoped re-review

Base: d4c9cd4ccef2a01e682f351ac3325d9a47e81d2f  
Head: aeb2389b08a7dca46d176a10eb8ed60efa39f21e  
Date: 2026-10-21

## Verdict

**A-FINAL-M1: ADDRESSED.** No new Critical or Important breakage found within this fix. Ready for the documentation handoff correction.

Only `backend/README.md` and `web/README.md` changed in the reviewed commit range. Reviewed `final-review.md` for A-FINAL-M1 only, `final-fix-report.md`, the supplied `review-final-fix.diff`, actual HEAD README contents and the relevant bootstrap/runner/package definitions. No broader review, helper dispatch or suite rerun was performed. Source, index and HEAD remained read-only; this report is the sole file written.

## Finding verification

- Web setup now runs `python3 tools/bootstrap.py` before repository-relative `. .tools/env.sh`, then installs both root and web dependencies. The author-specific checkout path is removed. Bootstrap derives its root from its own file, so a checkout at another path generates its own environment.
- Backend setup now runs `npm --prefix web ci --ignore-scripts` before `all`, providing the pinned `tsx` required by registered web unit tests on a clean dependency installation.
- Backend descriptions match the runner: `web`, `e2e` and `visual` are registered; `all` discovers Python directories and runs web unit tests, OpenAPI validation/merge and Java unit tests. Browser modes remain explicit. Chromium setup is documented for browser modes. `load`, `ops` and non-OpenAPI contract selectors remain unregistered.

## Validation and exact outputs

Fresh read-only checks from the repository root:

`git rev-parse HEAD` — exit 0:

```text
aeb2389b08a7dca46d176a10eb8ed60efa39f21e
```

`git status --short` — exit 0, no output before this report was created.

`git diff --name-only d4c9cd4ccef2a01e682f351ac3325d9a47e81d2f..HEAD` — exit 0:

```text
backend/README.md
web/README.md
```

`. .tools/env.sh && node --version && npm --version && java -version` — exit 0:

```text
v22.23.4
10.9.9
openjdk version "21.0.12.1" 2026-08-18 LTS
OpenJDK Runtime Environment Zulu21.52+203-CA (build 21.0.12.1+1-LTS)
OpenJDK 64-Bit Server VM Zulu21.52+203-CA (build 21.0.12.1+1-LTS, mixed mode, sharing)
```

`PYTHONDONTWRITEBYTECODE=1 python3 tools/checks/run.py --help` — exit 0:

```text
usage: run.py [-h] {unit,it,web,e2e,visual,contract,load,ops,all} [selector]

Fail-closed local verification. All Java counts come from fresh JUnit XML.

positional arguments:
  {unit,it,web,e2e,visual,contract,load,ops,all}
  selector

options:
  -h, --help            show this help message and exit
```

`git diff --check d4c9cd4ccef2a01e682f351ac3325d9a47e81d2f..HEAD` and `git diff --check` — each exit 0, no output.

Bootstrap evidence is explicitly inherited, not a fresh execution: `final-fix-report.md` records `python3 tools/bootstrap.py` exit 0, reuse of existing pinned JDK/Node directories and the exact final output `Run: . .tools/env.sh`. Inspection confirms bootstrap writes `.tools/env.sh`; it was therefore not rerun under this re-review's sole-write constraint. No fresh clean-checkout dependency installation is claimed. The documented command order was checked against bootstrap and runner requirements.

## Existing combined evidence

Inspected `artifacts/phase-a/verification.json`, Python accounting JSON, Java and browser JUnit summaries, and corresponding all/browser/typecheck/build/generated-type/startup log endings. This is existing evidence, identified by `codeHead` d99fb9fb33c4c778432e991ef735b96cea72b55d, not a fresh run at this documentation-only HEAD.

- Python: 69 discovered/executed, 0 failed, 0 skipped.
- Web: 8 executed, 0 failed, 0 skipped.
- Java: 11 executed (9 configuration plus 2 HTTP helper), 0 failures/errors/skips. Exact all-log ending: `PASS: executed=11, failures=0; evidence=/home/tanren/ama/.worktrees/phase-a/backend/api/target/surefire-reports/TEST-ama.api.DeploymentConfigTest.xml,/home/tanren/ama/.worktrees/phase-a/backend/integration-tests/target/surefire-reports/TEST-ama.support.ContractClientTest.xml`.
- Browser: 10 executed, 0 failures/errors/skips. Exact browser-log ending: `10 passed (2.7s)`.
- Existing OpenAPI lint/bundle, typecheck, web build, generated-type comparison and packaged startup evidence remains applicable to this README-only correction. Generated-type output: `PASS: generated API types match the approved OpenAPI byte for byte`; build ending: `✓ built in 541ms`; startup evidence reports `process_alive: true` and the explicit unavailable-local-MySQL JDBC fixture.

These checks support the scoped documentation fix and do not establish corporate integration, production deployment, real MySQL failover or capacity acceptance.
