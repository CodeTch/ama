# B06.2 Task 1 — explicit input batch v1 fingerprint

Implementation candidate for root's independent scoped SPEC and quality review. This task does not constitute full B06 or B06.2 acceptance and enables no runtime defaults, file permissions or production services.

## Exact scope and baseline

Owned branch codex/b06-batch-20261023, worktree /home/tanren/ama/.worktrees/b06-batch, BASE ad9960595ab5de6eef7760e2c9094d23666074cb. Review the complete ad9960595ab5de6eef7760e2c9094d23666074cb..HEAD on this isolated branch, including this report and public evidence. The final immutable HEAD is recorded in the root coordinator's task-1-report.md after committing; a tracked report cannot contain its own commit hash.

Only new BatchFingerprint.java, its new test/resources, tools/b06_archives_batch_*.py, this task-specific report and artifacts/phase-b/b06-archives/batch/ are delivered. Explicit file-level allowlist and SHA manifest live under that evidence directory. No peer code was imported and no other agents were spawned.

## Implemented behavior

BatchFingerprint.Member(long ordinal, String fileId, String sha256, long sizeBytes) validates strict canonical UUID/hash and UINT fields using adopted MinutesContracts validators. Instance methods are exactly:

    String compute(String meetingId, String batchId, String kind, long baseRevision,
                   List<Member> members, CanonicalBudget budget, BooleanSupplier cancelled)
    String verify(FullRow batch, List<FullRow> members, List<ContentFile> files,
                  CanonicalBudget budget, BooleanSupplier cancelled)

compute fixes profile ama.input-batch.v1 and envelope kind INPUT_BATCH; data contains exact meetingId, batchId, kind TRANSCRIPT, baseRevision and request-ordered file objects with ordinal/fileId/sha256/sizeBytes. It uses existing CanonicalValue and CanonicalValueCodec.sha256, with no second production encoder. UUID/hash remain lowercase without repair. Members are nonempty, unique by file ID and contiguous 1..N in the supplied order. Every identity, file metadata field and request order participates in the digest. Created timestamp, storage location and task IDs are absent from the envelope.

verify requires INPUT_BATCH plus complete supplied INPUT_BATCH_FILE rows and a bijection with four-field ContentFile facts. It rejects wrong tables/batch IDs, ordinal gaps/reordering, duplicate members or metadata, missing/extra files, contradictory saved SHA and foreign/missing meeting IDs. Metadata list order does not matter. It derives actual file sizes, computes the new v1 digest, compares the actual saved batch.input_fingerprint, and returns that computed digest on success. Changed size/base with otherwise consistent facts produces FINGERPRINT_MISMATCH. Created timestamp changes do not change the digest.

Budget arguments and cancellation are mandatory. Member count is bounded before projected maps/lists are built, using maxContainerEntries and necessary per-member codec lower bounds of 9 nodes/209 encoded bytes; complete codec preflight enforces every envelope bound. Cancellation is checked before work, during loops/codec and immediately before verified success; interruption is retained. Failures use adopted code-only exceptions. Long arguments cannot represent a UINT above Long.MAX_VALUE; negative/wrapped values reject, while Long.MAX_VALUE is valid without allocations or sums proportional to its numeric value.

## Independent fixed vector

New resources backend/core/src/test/resources/ama/transcript/batch-v1.literal.{json,hex,bytes,sha256} freeze the original design literal exactly:

- Typed bytes: 734.
- SHA256: c4a558e4dc8aff8289b4ed60193efdfbc7f64f73ae642e2eec6398c473458053.
- tools/b06_archives_batch_vector.py imports no application code. Its independent typed encoder verifies JSON-to-bytes, literal hex-to-bytes, literal bytes-to-SHA, Python SHA and OpenSSL SHA, and asserts all four resources match preserved old literal resources byte-for-byte.
- Java independently compares typed codec bytes to the frozen bytes and their hash; it also proves raw JSON SHA differs.
- The zero-byte file is valid fingerprint input. This helper makes no TXT parse-success claim.

The independent Python encoder is a test evidence tool, not a runtime codec. --freeze refuses an existing target. Default mode verifies existing resources and requires OpenSSL.

## TDD and verification evidence

Exact test commands run through tools/b06_archives_batch_run.py without sourcing any environment files. Only public Java21 JAVA_HOME/PATH were assigned. The runner snapshots public source before invoking each exact registered selector, then retains raw stdout/stderr/exit and actual JUnit XML before any next run can replace it.

| Evidence run | Exact selector after python3 tools/checks/run.py unit | Executed | Failures | Errors | Skips | Exit |
|---|---|---:|---:|---:|---:|---:|
| red-1 | BatchFingerprintTest | 6 | 6 | 0 | 0 | 1 |
| green-1 | BatchFingerprintTest | 6 | 0 | 0 | 0 | 0 |
| codec-green | CanonicalValueCodecTest | 5 | 0 | 0 | 0 | 0 |
| fact-budget-green | MinutesFactBudgetTest | 4 | 0 | 0 | 0 | 0 |

The first Java run used a minimal compilable null-return compute/verify stub. All six methods failed actual assertions (expected fixed hash versus null, or expected a Failure but none was thrown); none failed compilation/setup. The three named plan methods exist and executed: literalVectorMatchesIndependentBytes, orderAndEveryIdentityFieldAffectDigest, invalidOrIncompleteMembersFailClosed. The remaining three exercise saved fingerprint return/comparison, exact metadata consistency, and budgets/cancellation.

Final unique method coverage is 15 passing methods across three exact selectors, including 6 new task methods; repeated RED/GREEN executions are not added to unique counts. There are no parameterized invocations. Reactor compilation of other modules does not count as executed tests or DB coverage. Full current units are intentionally deferred to root's single combined integration run per activated common brief.

One earlier shell launch failed before either Java or the evidence runner because unquoted inherited PATH contained spaces and parentheses. It is setup only, never RED. It was retried using Python subprocess with explicit JAVA_HOME/PATH. setup-note.txt records this, and the original tool transcript remains in chat; expanded inherited PATH was not copied into the public evidence. Successful initial reactor compilation emitted existing deprecation notices from unchanged integration-test sources; no new compiler warning was introduced. One report-writing tool invocation had a JavaScript string syntax error before execution; the corrected invocation wrote the report without affecting tests or source.

vector.stdout.log/stderr/exit retain independent Python/OpenSSL verification. preservation.* proves the root attributes, old tool and B06.1 artifacts and existing migration path have no diff against BASE. No DB, Docker, fixtures, credentials, SQL, old tests/goldens or handbooks were changed or used for execution.

## Source seals and evidence boundaries

Final production source SHA256: 0559a2809dc03759241a8bc881c7ee4b7dd56af91ba49d2603e08675617db315.
Final task test source SHA256: 0a20664c0187e8841d60fb76003e5d7e99a76446a1ebfdc387e1136bf7861531.

Each run retains its own source-sha256.txt and copied public source/resources. The RED snapshot contains the actual stub and original test source; GREEN snapshots contain the implemented source. The runner's selector choice list was corrected after green-1 from an unused nonexistent MinutesContractsTest to the real MinutesFactBudgetTest; this tooling-only change is visible in later snapshots. No unsupported selector was executed.

PUBLIC_ALLOWLIST.txt enumerates the exact public delivery files. SHA256SUMS covers that allowlist and every listed file except itself. No root .gitattributes, private environment/tool files, or unrelated repository files are in this new delivery manifest. New nested attributes mark resource/evidence bytes -text. Initial staged diff-check reported the untouched raw Maven log trailing spaces; a new evidence-local *.log -whitespace rule preserves those raw bytes and permits a meaningful source whitespace check. The runner now retains this rule on subsequent runs. No raw log was edited.

## Rulings and limitations

Ruling: Keep the proposed instance method APIs and use existing strict FullRow/ContentFile/MinutesContracts grammar — these are the adopted authority; no contract module change is necessary — cost if wrong: a consumer would require an explicit new protocol version rather than relaxed historical grammar.

Ruling: The Java API's long is already bounded by Long.MAX_VALUE — reject negative values and verify the upper boundary without inventing a string/JSON parser — cost if wrong: callers supplying external numeric text must still strictly parse before this API.

Ruling: Root owns independent reviews and the one full current-unit run — exact affected selectors here avoid repeating old cohorts — cost if wrong: root must withhold adoption until combined validation passes.

This helper verifies supplied consistency only. It cannot establish authoritative query completeness, database existence, authorization, original upload purpose, AVAILABLE state, storage durability, or a business grant. It does not auto-apply v1 to old hashes, add profile kinds, or solve future persisted algorithm discrimination. Authoritative bounded JDBC acquisition, controlled archive persistence/recovery and restart integration belong to sibling tasks. No production policy/provider/WorkerLoop/API/config defaults, task/FUP fairness query or shared source behavior changed. No push, main merge or external/company send occurred.
