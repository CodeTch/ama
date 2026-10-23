# Whole B06.2 independent SPEC and quality review

Review date: 2026-10-23. Fresh independent whole-delivery reviewer, not an author or any prior task reviewer. Review is complete for the exact candidate below.

**SPEC: PASS. Quality: PASS. Actionable findings: P0=0, P1=0, P2=0, P3=0. New deferred repair findings=0; parked findings=0.** No repair or additional execution is requested for this candidate. These verdicts apply to the approved controlled archive/batch/processing-metadata scope. They do not grant production or whole-B06 acceptance.

## Exact identity and full-path coverage

- Repository: `/home/tanren/ama`.
- BASE: `ad9960595ab5de6eef7760e2c9094d23666074cb`.
- HEAD: `adb2461bb285283663db66434d3ae64c4b4cd980`.
- Complete reviewed range: `ad9960595ab5de6eef7760e2c9094d23666074cb..adb2461bb285283663db66434d3ae64c4b4cd980`.
- Package: `/home/tanren/ama/.superpowers/sdd/task-B-06-archives-prepared-brief/whole-b062-review-package.diff`.
- Package bytes: **17,863,017**. SHA256: **080ed8391fb7715351ae670ebeb71dc4439c402ff82018ac2b6f8290dd79c15b**.

I independently compared the package's complete diff body to `git diff --full-index BASE HEAD`; they are identical. The package uses the normal binary-file markers, not `--binary` payload patches. Binary payloads were nevertheless independently read and verified against their immutable Git blob identities as part of the all-path audit. This was the entire range, not HEAD~1.

All **1,956** changed paths are additions. I read every working payload in the byte audit, reconstructed its Git blob ID, and checked it against the HEAD tree; checked physical executable bits against Git modes; and checked for symlinks. All are regular non-symlink files, mode100644, with exact payload/mode matches. Their total payload size is **16,033,247 bytes**. Git status was clean. The union of the six current delivery seals below, including each seal file itself, is **exactly the complete 1,956-path changed set**, with no uncovered paths or extra paths.

| Current public seal, relative to artifacts/phase-b/b06-archives | Independently verified hash entries |
|---|---:|
| batch/SHA256SUMS | 103 |
| index/fix1/FULL-CURRENT-SHA256SUMS | 458 |
| facts/fix1/SHA256SUMS | 435 |
| restart/SHA256SUMS | 511 |
| coordination/components/SHA256SUMS | 49 |
| coordination/runtime/SHA256SUMS | 394 |

The component full allowlists account for104 batch,459 index,436 facts and512 restart paths including their seals. The facts allowlist excludes its seal, while coordination allowlists exclude their own allowlist/seal files and the coordination seals additionally hash the allowlist itself; those conventions were inspected, not assumed identical. All six seal path sets and all payload hashes reconcile.

The large package is primarily preserved evidence. At tested source HEAD821daaa, the run's350 `.java` inventory entries are19 actual new backend compile-source paths plus331 historical Java evidence copies. The final range contains700 `.java` paths because final-units adds350 copies of that inventory:19 actual backend paths and681 evidence copies overall. Neither350 nor700 is a count of new compiled application classes. The public inventory clarification is accurate.

## Inputs and review method

Fully read the final-review-prepared, common-brief and review-common contracts; the approved immutable prepared brief; proposal sections4–7; original adopted S1 handbook and fix1 addendum; and the relevant B06 queue-seam ACK. Read the current activation, review-ready, integration and Task4 report documents; all five component author/fix reports and the Task4 report; all original and scoped independent reports under components/runtime; and adoption receipts. For ignored author handoffs, their complete tracked-report bodies were byte/text-compared with the already fully read tracked reports, and their remaining exact-HEAD/preservation material was read separately.

Directly reviewed all19 substantive new Java implementation/test/helper paths, all17 new tools, the final-unit capture script, and relevant unchanged codec/config accounting/schema/ConfigBindings, transaction/access/preparation/queue/outbox contracts and source. Inspected the important historical source changes and original failure messages. Repetitive evidence copies, inventories, hash maps and raw XML were audited with independent read-only Python/Git checks rather than treating them as new application source.

This was **static review plus an audit of preserved public execution evidence**. I ran no Java, Maven, unit or integration tests, author runner, database query, Docker command, private-environment read, old fixture, PKI, S3 or network operation. I spawned no subagents and edited no source, test or evidence. This ignored report is the sole output file. No new live execution result is claimed.

## Combined specification and quality assessment

### Batch v1

The new BatchFingerprint fixes the precise `ama.input-batch.v1` / `INPUT_BATCH` envelope and delegates production encoding/hash to the existing typed codec. Meeting ID, batch ID, TRANSCRIPT kind, base revision, contiguous request-ordered ordinals, file IDs, saved SHA and actual supplied size all participate. Lowercase UUID/hash and nonnegative long grammar are retained; nonempty membership and duplicate rejection precede success. Count bounds precede projected maps/lists and use division rather than overflowing count multiplication; the codec enforces the full envelope bounds.

verify checks complete supplied batch/member/file consistency, exact row types and batch IDs, four-field file meeting attribution, duplicate/missing/extra facts and the saved fingerprint. Metadata map removal plus cardinality/emptiness checks prevents overwrite or disappearance. It returns the computed digest and cannot establish authorization or upload provenance. Long.MAX_VALUE values are handled as values, not allocation sizes. Zero-byte hashability is correctly distinguished from TXT parse success.

I independently reconstructed typed bytes from the literal JSON using a separate in-memory encoder. They equal the frozen bytes and hex, are exactly734 bytes, and hash to `c4a558e4dc8aff8289b4ed60193efdfbc7f64f73ae642e2eec6398c473458053`. All four new literal formats equal the retained B06.1 originals. The existing saved OpenSSL/Python cross-check is consistent. No old batch algorithm is silently reinterpreted.

### Immutable archives, budgets and publication

The exact pinned index SHA under an explicit normalized absolute protected root is the trust anchor. The reader has a closed canonical index grammar, strict UTF8/duplicate/unknown-field/numeric checks, sorted unique identities and fixed controlled maxima. It verifies a continuous predecessor chain to generation1 and requires every older entry to remain identical in the successor. It validates every selected-release object, including unselected historical configurations, with actual size/SHA/header/canonical/schema/dependency checks. A retained metadata snapshot does not cache trusted raw arrays or mint an S1 result.

Runtime traversal starts from the filesystem root and uses SecureDirectoryStream-relative NOFOLLOW operations. Unsupported providers and invalid links/nonregular files fail closed. Size and applicable cumulative capacities precede transport allocation; exact EOF, size and file-key checks bracket reads. Each load admits the whole requested closure plus manifest reservation before its first object array, rehashes current transport bytes and returns fresh arrays. Original manifest refs determine historical loading; no latest/default fallback exists.

Startup uses a shared archive validation state for the complete selected release. The chain-inclusive release admission and separate acquisition admission both precede object reads/captures. Publication authenticates the predecessor, obtains its actual full index-chain byte count, admits candidate object lengths and exact candidate index size before candidate reload/capture, and validates the whole candidate with one state. Fixed-width hash placeholders are only a metadata size calculation; actual captured SHA values replace them before final index output. This preserves the existing codec/canonical writer authority.

The publisher rejects existing identities, has no drop/remap operation, and copies original objects into a unique staging release without shared hardlinks. Files/directories are forced before held-SDS atomic sibling publication. Existing final releases are never overwritten/deleted. An interrupted stage remains evidence; a post-rename failure can leave a complete release with no success ACK, as documented. There is no automatic repair/GC.

Offline path-only mkdir/fsync is assessed under the approved deployment-owned protected administrative-root assumption, with directory identity checks, sole publication lock and held-SDS final move. Existing nonregular `.publish.lock` is checked before potentially blocking WRITE open; the absent case uses CREATE_NEW and the raced case rechecks type. This closes the avoidable FIFO hang under the stated assumption. It does not claim safety against a malicious privileged directory replacer, power loss or HA.

Source open/load and publisher install a cooperative30-second monotonic predicate, including nested predecessor work. Standalone StrictConfigIndexReader and ProcessingManifestRefs are finite-budget/caller-predicate helpers without their own timer. The current integration guide precisely states that distinction and the absence of OS-level preemption. Manifest discovery strictly validates original bytes and yields refs only; ConfigBindings repeats original-byte/schema/closure/fact verification and retains private success constructors.

### Authorized complete processing facts

JdbcProcessingFactsReader privately builds its transaction/access/fence dependencies from one DataSource and rejects ambient transactions. It locks and bounds the actual identity before shared access materializes its name, then uses current ACTIVE identity and owner EDIT authority. Actor status is not authority. It returns detached facts, never a write capability.

Domain current reads precede sorted files and the complete sorted task union. FullRow projection follows Table.columns, exact numeric conversion and UTC microseconds. readStored includes the actual input, batch and every member, every owning revision, all matching input tasks and all referenced producer task IDs in one union. Contradictory extras and foreign edges remain visible for S1 to reject. Missing files fail; empty outputs/tasks remain actual complete empty sets. Historical stored fences are never replaced by current root20/5. LIMIT remaining+1, saturation at Long.MAX_VALUE, per-member limits, aggregate row/edge reservation and remaining-capacity arithmetic reject overflow/excess instead of truncating success.

Both identity and manifest preflight measure UTF8 bytes, scalars and actual UTF16 units independently under retained row locks. The UTF16 expression is `OCTET_LENGTH(CONVERT(value USING utf16)) DIV 2`; CHAR_LENGTH is used only for scalar accounting. Manifest body selection follows those gates, and bounded streaming rechecks actual bytes/scalars/units. Initial builder capacity uses the admitted actual size capped at1024. No unbounded getString(config_manifest) precedes the gate. Cancellation checks include rows/stream/copy and the post-commit return boundary; interrupt semantics are retained. The five-second owned-query timeout is correctly distinct from an arbitrary-driver or whole-transaction guarantee.

### Genuine enqueue and process recovery

Task4 uses its own newly committed bounded metadata with matching current20/5 meeting/subject fences and a real batch v1 hash. A reads detached authorized facts and computes manifest/S1/TEST producer key outside the write transaction. The short write reacquires authentic access, rechecks owner/current fence and all batch/member/immutable file facts used by the draft, inserts the exact precomputed input, adopts that authentic gate through JdbcTaskPreparations and calls genuine JdbcTaskQueue.enqueue with the full TaskSpec.

The unchanged queue validates same-DataSource/current-holder/full-spec/purpose preparation, domain policy and current subject fence. Its ambient call joins the outer transaction and does not flush internally. The fixture flushes once at the outer end; TransactionRunner returns after commit. A then performs separate stored/S1/fullspec/READY/outbox checks before emitting COMMIT_ACK. The TEST producer domain is explicit and no production registration is added. Task3 direct-insert fixtures are not misrepresented as enqueue evidence.

Fresh B opens appended pinned index2, obtains actual old stored input/task facts, compares original manifest bytes/SHA, discovers its exact old refs and recovers the original root.1/digest. A separate new draft selects root.2; the old manifest is untouched. Three additional bounded fresh processes use separate missing-history, same-size-mutated-history and wrong-pin copies. They return typed unavailable, lack recovered/draft success fields, and cannot fall back. Recovery source has no database write path. The retained successful test checks the same selected metadata snapshot after each negative and reopens the original positive release afterward.

This composes a committed batch, detached read and authentic write recheck. It does not invent an ambient reader or qualify uncommitted batch/held-facts composition; that remains B06.3 work.

## Original findings and introduced effects

| Original finding | Independent current disposition |
|---|---|
| Task2 F1, P2: publish/open release-budget mismatch | CLOSED. Entire predecessor-index chain is included before staging. Retained exact boundary is4646 bytes;4000 refuses. |
| Task2 F2, P2: release limit after allocation/capture | CLOSED. Whole selected/candidate admission precedes object reads and candidate owned copies; tighter budget controls admission. |
| Task2 F3, P2: FIFO lock opened before regularity guard | CLOSED. Pre-open NOFOLLOW type guard plus CREATE_NEW/race recheck; real bounded FIFO RED/GREEN retained. |
| Task2 F4, P3: universal deadline overstatement | CLOSED. Versioned correction and current consumer guide distinguish timed file entries from pure caller-predicate helpers. Historical report remains immutable. |
| Task3 F1, P2: scalar count used as UTF16 preflight | CLOSED. Exact locked UTF16 dimension precedes both projections;40-emoji rejection and32-emoji boundary evidence remain source-associated. |

No new combined defect or actionable regression was identified. No original finding is waived or parked. Assumptions and known future gates are stated below, rather than promoted into implemented guarantees.

## Independent evidence audit

All386 saved JUnit XML files parsed; every declared tests/failures/errors/skipped count matches actual testcase elements.413 JSON payloads parsed successfully; deliberate malformed fixtures were not reclassified as corrupt delivery. Current/historical seals cover every raw payload, including logs and binary fixtures.

I checked690 entries across25 JSON/text source maps, including all350 final-unit inventory entries, against their saved source bytes. Separately checked320 historical/fix index run snapshot entries, the29-entry current index source seal, current final Java associations, and35 restart dependencies against exact BASE/current bytes. The final root source is unchanged from tested821daaa through reviewedadb2461 (`backend` and `tools` diff is empty). A later restart capture-runner delta only excludes stale failsafe reports for unit-only selectors; it does not change the IT command or successful Java sources. The batch runner's selector/tooling evolution is similarly preserved, not falsely represented as an unchanged run script.

Original index329-entry seal and24-entry delivery seal were verified against historical `f58bff56c0e823b1d542d623e8db9b21e011635f` blobs. Original facts306-entry seal was verified against `298695761706419ee20c1b6b98f3ad73eff65cdd`. They are not current-source assertions. Their original raw evidence/reports/seals remain retained; the fix1 seals govern current changed adapters.

| Qualified saved result | Actual count and distinction |
|---|---|
| Batch real RED / GREEN |6 assertion failures, then6 passing new methods; codec5/fact-budget4 regressions overlap final units. |
| Index initial history | Initial16 tests/9 entrypoint errors are not assertion RED; later pinned-history, initial-republish and descriptor-leak assertion REDs retained. |
| Index fix1 RED / final |4 real assertion failures, then28 distinct passing methods (24 original+4 fix). |
| Facts initial history | Compile/schema/FK setup remains setup; expanded8 run has7 assertion failures+1 error, not8 clean REDs. Commit-cancel and Long.MAX REDs retained. |
| Facts fix1 RED / final |2 actual assertion failures, then12 IT methods,0 failure/error/skip. Repeated179 dependency units are not added. |
| Restart setup / RED / GREEN | Two compile attempts execute0 IT; real RED1 failure/0 errors; GREEN1 method/0 failure/error/skip. Five child JVMs are not five tests. |
| Root final current units |29 suites,213 tests,0 failures/errors/skips;8 reactor modules SUCCESS; raw exit0 and BUILD SUCCESS. |

Root final units ran `python3 tools/checks/run.py unit` at `821daaa05a4c4faffb924ae4c00b72cabdd62347`. Saved capture code assigns only the public Java21 path, strips AMA_TEST_ environment keys and loads no private fixture environment. The raw log retains the unchanged DeclaredErrorResponse deprecated-API notice.179 baseline+6 new batch+28 new archive methods=213; zero-test modules and repeated cohorts are not independent coverage.

I directly inspected the critical raw assertion messages, FIFO output, final success logs, process argv/classpath, receipts/exits and SQL provenance. GREEN parent649138 differs from A649176, B649230, missing649265, mutated649289 and wrong-pin649375. Parent Process.pid files match each child's receipt; positive exits are0, negatives23. Real RED retains A647004 COMMIT_ACK and B647060 PROFILE_UNAVAILABLE23, followed by the parent's expected0/actual23 assertion failure. The RED-to-GREEN Java delta changes only historical root selection in B; test/fixture remain identical.

Independently rehashed both positive release indexes and every indexed object in retained RED/GREEN roots, and reconstructed the typed old processing digests from the persisted raw manifests. GREEN digest is `b322434caf46dbb33cf2026396e8b3c069847a94803e83fce06e5592d0a6f640`; original manifest SHA is `26759402b72105075210b3e588c35387f0a5b6cd622a35407ee5a8fce824cedc`. Index1 is `c6e3b29cedbdc5234f368fa6d1f7152442c95f56b0caaf52bc8019bafea13e9d`; index2 is `d2436e244f966e5841941e282b0f6ad0dc6c76c6369cb90203dab42ed876472d`. The fresh root.2 draft digest is separately recorded as `f31844f7210e517a72f1456730e681c2c08fd62e25157d5402900811e6442c02`.

Byte comparison proves missing-history differs only by the release2 root.1 object removal; mutated-history only by that object's same-sized payload; wrong-pin is an identical independent copy with the wrong pin argument. Release1 remains intact. Before/after selected DB snapshot hashes agree at `2a0312b070f6a1e231071b1bb6f6a2b5755f1b8b8a60b07394c5adcc8befa6a1`. The snapshot covers full selected input/task/outbox/current meeting/subject rows and relevant table counts, not every unrelated field in the fixture.

## Adoption, custody and baseline preservation

Independently compared complete author endpoint Git trees to their coordinator adoption endpoints, including payload and mode for every task path:

| Author endpoint | Coordinator endpoint | Exact task paths |
|---|---|---:|
|0b65a68f100beed8172c3c3f270a19c5e1cf8523|f6304fa12a3aabc33243a6169c2736d41aceb015|104|
|38a321a5bd11d56b8071dc03965fe413c71bd78e|ba8af64a09aa4ae7ec5f48db033ce6b3d7b891c8|459|
|76a50c72006835a567ff200d231bf78420d67f3a|5563f4cebd6f3f8d5c44d67c4e9b54f0a1d83d00|436|
|41a41e1d7d345fd3b6d46e0a7bc01757298abb3c|821daaa05a4c4faffb924ae4c00b72cabdd62347|512|

Each component has its preserved independent dual-PASS disposition before its exact adoption; Task2/3 include original requested changes followed by the same reviewer's scoped fix PASS. This whole-delivery verdict is independently based on current combined code and evidence, not merely those prior verdicts.

All2840 protected-baseline public files match their recorded SHA values. The complete range modifies/deletes no existing tracked path. All12 formal migration files match BASE and their public lower-SQL hashes; no new formal migration or shared production behavior is introduced. Read-only audit SQL in the new evidence is not a new migration. Root `.gitattributes` SHA remains `20ca4a186a886496c32011dae1cc530dd4cc57b78eb71d7afa1bd139272efadc`; main remains `0676a21311f2b2193f73a5e096f4a5605ee9b41f`; stash reflog retains `bc12e825246c60c12752129c4d512ef9c545758c`.

Public initial-zero output records0 tables on MySQL8.4.6. Selected fixture metadata and custody records identify the sole bounded loopback fixture `ama-test-b01-a5e7d68612a4`, container `363e6bef449436390d3e21041408b53002a48311668c86864befe2535404e45b`, owner archives-20261023, pinned image,768MiB/1CPU/256pids and process-local native AIO OFF. The initial empty HostConfig HostPort issue is preserved; later selected metadata gives actual loopback58628. This review did not contact that process.

Full13-line header/history blocks are byte/text-identical across post-migration, original final, fix1 and Task4 custody audits:12 successful formal versions001,002,005,006,007,008,009,010,011,011.1,012,013, preserving003/004 gaps. All41 original exported manifests independently match SQL-recorded size/SHA and remain preserved through fix1. Task4 custody preserves all62 transferred manifest SHAs; final64 includes the retained RED and GREEN additions. Actual final raw SQL contains both queued TEST tasks, full selected immutable fields, READY state and one TASK_ENQUEUED outbox event for each. No old fixture reset/repair or production upload claim is inferred from these records.

## Limits and pending closeout

No fresh runtime or database verification was performed by this reviewer. Saved execution evidence can establish internal source/run/result consistency; it does not independently establish current live database/container state or prove the absence of every unrecorded external action. Secret-value scanning was not repeated because private environment access was prohibited. Public paths/logs/commands were inspected, and no private environment file is part of the delivery. No push/production activation is present in the reviewed changes; remote-state absence was not independently queried.

The protected-root/local-filesystem assumptions, cooperative non-preemptive I/O, post-rename ACK uncertainty and no power-loss/HA/capacity qualification remain material limits. File rows are controlled metadata: no qualified original upload/session/purpose/reference/download route, provider read, TXT execution, worker output or HTTP business transaction is qualified here. Batch algorithm discrimination, current/retained file capability design, uncommitted/held-facts reader seam, production policy/provider/Supplier/Worker/API work, and full B07 graph acquisition remain later work.

FUP-B05-CANDIDATE-01 remains OPEN; B04-Q401 UNKNOWN; B01-IMM-01 permanently PARTIAL; D01 design-only. Company, full-B06, phase-B and production gates remain unchanged. These are existing scope exclusions and future obligations, not new deferred repair findings.

The original prepared file must remain immutable historical PREPARED. Current activation/review-ready documents explicitly distinguish execution from historical preparation. Old handoff/entry/resume text is a known pending coordinator closeout: **this review does not claim that the final adoption record, entry/resume update or B06.3 handoff has already been written.** Root may now fully read this report, publish it with exact adoption and make those documentation-only updates. Any subsequent source change would require a new source association and appropriately scoped review. No source repair is requested by this verdict.
