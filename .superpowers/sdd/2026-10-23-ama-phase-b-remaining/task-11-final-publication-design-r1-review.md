# Task 11 R1 bounded design fix review

## Verdict

**SPEC: PASS for the scoped R1 repair. Design quality: APPROVED at design level.** T11DR-01: **ADDRESSED**. T11DR-02: **ADDRESSED**. New fix-delta findings: **0 Critical / 0 Important / 0 Minor**. No unrelated architecture was reopened; no runtime, source, database, provider or Phase B qualification follows.

Scope is the exact `task-11-design-fix-01-brief.md`, both original findings and directly introduced consistency/breakage. R below means `.superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-11-final-publication-design-r1.md`; W is that directory. The full original-to-R1 diff was read once in consecutive ranges 1–65, 66–130, 131–205, 206–270 and 271–336. No diff hunk was truncated or omitted. The complete 93-line closure was read as author claims, not accepted as an independent verdict. Later targeted searches obtained exact R line locations only.

## Artifact identity

Independently measured raw hashes/lengths:

| Artifact | Bytes / lines | Raw SHA256 |
|---|---:|---|
| Original design, unchanged | 69,377 / 316 | `a8ec962bfee7ed1025a8d0dc113750447d9a8cc95b006b758cc544de900981e8` |
| Original review, unchanged | 12,601 / 74 | `b1b6a4af59072f322fd71237636a608d9f0c2a530a98ce339ec57c56574a84f9` |
| R1 design | 102,624 / 405 | `f18ae4734603709284d91345b2909eca9687627c825444fd8cfa06dd49d36ffc` |
| R1 closure | 15,802 / 93 | `b906213c0ff8af494f54495b3dbd337cbf3283994dfabe2e6798255438808c51` |
| Actual review diff | 97,358 / 336, supplied dimensions; entire numbered content read | `f4b03cf34487e9e357a39e50eea6d27608210b1f1ca478f8f7e1eaf39af0c9a0` |

The diff digest was independently measured; its content spans all 336 lines. Original design/review hashes still match the original review pins. No normalized author-generated diff hash was substituted for the actual review artifact.

## T11DR-01 — ADDRESSED

R:198 selects both service bearer authentication and an Ed25519-signed actor/purpose/resource delegation on every controlled route. R:200 and R:202 specify credential custody across process restart, fixed client/store/key/TLS trust, bounded canonical signed claims and no browser-controlled signer. R:204 makes the actor originate from a genuine application session or immutable accepted requester, confines signer issuance and preserves current-owner/original-request/Task7 send admission. R:206 binds finite purpose, method, exact resource and request digest; R:208 binds and freshly checks destination grants/assertions before effect.

This repairs the original missing caller/actor boundary: a claimed actorId or stolen destination assertion alone cannot pass. R:210 separately confines revoked-actor receipt recovery to original client-owned immutable execution facts, including safe absent-execution UNKNOWN, without granting document access or renewed business authority. It therefore does not cure authentication by making required fact-only recovery impossible.

R:212 and R:214 keep official viewing usable through an authenticated same-origin relay with independent application READ and current provider DOCUMENT_READ, fresh post-fetch local recheck, immutable bounded escaped content, no-store/CSP, no arbitrary URL fetching and no credential-bearing redirect. Actual owner/member provider grants are explicit positive fixture prerequisites. R:216 names the relevant negative and positive HTTP/browser evidence. This closes the finding at design level; it does not assert actual credential deployment or HTTP execution.

## T11DR-02 — ADDRESSED

R:267 acknowledges both the real queue terminalization gap and the inability to reconstruct an original accepted header from existing hashes/current fields. R:269 through R:291 proposes one concrete content-free publication_submission model: actual task primary/FK identity, per-publication submission number, immutable final/provider/destination/requester/hash/manifest binding, real nullable local execution link and one-way UNDECIDED→NEVER_MARKED decision. No execution, attempt or provider rejection is invented for an initial no-operation task. The new durable binding directly supports old acknowledgment after subsequent edits/resubmission (R:297, R:367, R:369).

The shape is executable in principle: R:295 places existing submission locks in the declared control rank before tasks, predeclares all actual FK parents, reserves manifest transfer before content reads, and builds proposed submission number/producer/manifests/hashes outside locks before fresh complete F equality. New binding insertion after the real task uses the bounded new-row exception, not a late lock on an existing task. R:297 adds the actual submission number to the Task11 producer identity so repeated no-operation acceptance cannot collide with its terminal predecessor. Legacy absent binding is UNKNOWN/unavailable rather than inferred backfill (R:293).

R:299 confines local recovery to genuine issuer/source/holder/current gate/full inventory/purpose. R:301 distinguishes terminal work from explicitly abandoned eligible work and refuses a live RUNNING owner. R:305 through R:310 gives complete durable proof cases A (no execution), B (actual unmarked PREPARED), and C (genuine locally terminated unmarked execution), full prior-history safety, root/task marker serialization, primary locking resolution of unknown marker ACK, state-aware task/attempt termination, one-time decision/publication application and atomic rollback. A committed marker, possible send, remote UNKNOWN or unresolved prior round cannot be reclassified as local no-effect. A rolled-back marker is eligible only after its actual outcome is definitively resolved.

R:312 supplies the previously missing constructor-bound queue terminal hook for this registered family, with complete declaration before task acquisition and no new fact-only domain writer or generic callback authority. R:314 and R:316 add bounded maintenance plus explicit owner recovery, immutable decision replay and fresh-process discovery, keeping the projection frozen until the local decision is durably proved. R:318 permits explicit correction/edit/resubmission with valid final and the same publication root; R:320 separates local never-sent history from actual provider-accepted predecessor receipts so provider proof-CAS does not require fabricated remote rejection. R:322 and R:385 cover the original exhaustion/recovery/old-ACK races in future tests.

## Fix-delta consistency and quality

- R:140 through R:146 makes the reopening predicate a complete history of admitted remote zero-effect or sealed local-never-marked decisions, while keeping genuine in-progress/UNKNOWN/success frozen. The new exception is not based on READY/FAILED strings alone.
- R:162, R:164, R:224, R:240 and R:242 consistently add immutable submission acceptance to initial, retry and replacement transactions. R:318 explicitly handles same stable key with no existing operation, so the marked-operation sequence is not incorrectly applied to initial unmarked recovery.
- R:257 preserves old execution-manifest/token truth and uses submission binding for unmarked history; the legacy generic fixed-input retry paragraph and provider-wide sole-document proof-CAS are not weakened. R:320 supplies the necessary mixed local/provider history rule.
- R:336 preserves Task7 receipt-only behavior while assigning local failure to its separate fresh-authority protocol. New history/recovery/document routes at R:358 through R:360 correspond to durable facts or authenticated reads, not fabricated associations.
- R:293, R:397 and R:399 explicitly separate logical model approval from an allocated/frozen/ACKed/applied migration, current-model synchronization and actual implementation. Proposing this side table and private queue extension is within the original finding's necessary repair scope.

## Source and activation gates — not new findings

The original review's source observations about `JdbcTaskQueue.java:108`, :136–153, `WorkerLoop.java:128`/:148, V008/V010/V013 and legacy `JdbcExternalOperations.java:155`–175 remain the basis for the identified gap. R1 accurately targets those missing lifecycle/receipt behaviors. This bounded rereview did not rerun source/runtime checks merely to regenerate the author's closure evidence, and did not review Task4's separate implementation.

1. **Exact DDL/model gate:** independently review final column/collation/FK/index/CHECK definitions, number exhaustion and immutable-binding/one-way-decision enforcement; root allocates/freezes/ACKs the forward migration after actual Task8 schema and synchronizes the current model. No migration allocation/application is approved here (R:291, R:293, R:397).
2. **Private queue composition gate:** prove the complete extra domain/task/attempt/operation union is acquired once, registered terminal hooks consume real private phases exactly once, and ordinary completion/exhaustion plus local publication application roll back atomically. Current ordinary completion may have a valid live lease; its legitimate terminalization must be distinguished from forbidden abandonment of another live owner. No public callback, second root gate or post-task discovery may bridge this (R:299–312).
3. **Recovery authority/progress gate:** maintenance may repair eligible terminal work but must not turn its fresh owner gate into an implicit user abandonment of READY/live work. Explicit command and background paths must preserve R:301's distinction, actual taskId/request semantic digest, replay-first behavior and bounded cursor progress without consuming Task8's reserved sweep (R:301, R:314, R:316).
4. **Marker/history/receipt gate:** prove both lock-barrier orders, actual primary-DB resolution of commit ambiguity, no delayed marker/send after local closure, genuine PREPARED termination, immutable historical acknowledgment after destination/final changes and missing-legacy denial. Source review must reconcile acceptance versus execution manifests without modifying original accepted bindings or old execution facts (R:295–320, R:385).
5. **Controlled security/view gate:** execute actual per-route missing/wrong client and delegation cases, store/key pinning, actor/resource mismatch, revoked receipt-only recovery and dual-ACL official document reads. Verify credential custody, no exposure, bounded cancellable transport and fresh local recheck; the prose itself proves none of those executions (R:198–216).

The unchanged final hashing/reviews, full graph bounds/rank, all-mutator freeze, Task5/7/8 ownership/settlement/fairness, native+S3/four-session browser and retained-store recovery evidence gates from the original review remain mandatory. No deferred unrelated finding was introduced in this scoped round.

## Assessment

**Both original findings are ADDRESSED; scoped SPEC PASS / design APPROVED, with zero new fix-delta findings.** R1 replaces the missing trust boundary with explicit client/delegated-actor authorization and supplies a concrete durable acceptance plus local terminal recovery contract without treating marker uncertainty as no-send proof. Logical design approval is distinct from the outstanding exact DDL, genuine source composition and executable evidence gates.

Only this new review file was written. Original report/design are unchanged. No source, Git/index/HEAD, DB, Docker, private environment, network, runtime tests, resources or subagents were used or mutated. No runtime pass is claimed.
