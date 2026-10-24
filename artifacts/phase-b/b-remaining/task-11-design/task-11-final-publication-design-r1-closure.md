# Task11 design fix round1 closure — scoped author report

2026-10-23. Full replacement: `task-11-final-publication-design-r1.md`. This is the original designer's response to T11DR-01 and T11DR-02, not the independent review verdict. Author disposition: **both findings addressed in the proposed R1 contract; independent scoped rereview pending**. No source/runtime/provider/DDL/Phase B qualification follows. Only R1 and this closure were written. Original design and original review remain byte-for-byte unchanged.

## 1. Finding dispositions and exact affected scope

| Finding | Author disposition | R1 locations | Concrete repair |
|---|---|---|---|
| T11DR-01: missing controlled caller authentication/trusted actor delegation | ADDRESSED IN DESIGN PROPOSAL; reviewer decides | §7 prelude and §7.1 (lines196–216), §10 document route/URL projection, §12 gates | Selects 256-bit service bearer plus short-lived Ed25519 actor/purpose/resource delegation on all provider routes; restricted restart-stable credential custody; pinned provider/store/client/signing trust; genuine session/current-owner or original-submission recovery issuer; authentic discovery/assertion/publish/receipt/document reads; exact missing/wrong/mismatched/unauthorized HTTP negatives. Browser official document viewing uses same-origin relay with independent fresh application READ and provider current READ, no secret-bearing redirect. |
| T11DR-02: genuinely unmarked task terminal failure leaves publication READY | ADDRESSED IN DESIGN PROPOSAL; reviewer decides | §8.5 (lines265–322); direct consistency in §§2,3,5,6,8.1/8.2/8.4,9,10,11.6a,12 | Explicit private issuer/holder/fresh-authority local terminal protocol, complete old/current submission/task/attempt/operation/execution union, A=no execution/B=unmarked PREPARED/C=genuine local FAILED proof; committed-marker/UNKNOWN/possible-send rejection; root/task barrier; atomic task/attempt/real local external rows/one-use local decision/publication/audit/outbox; restart and explicit owner recovery; immutable accepted binding before any execution; original acknowledgment after later destination/final change. |

T11DR-01 detail: caller authentication is separate from TLS, loopback and the app session. Provider verifies bearer before protected routing and signed delegation before actor grants/effects. Delegation binds issuer/client/key, audience/store, actor, finite purpose, exact method/resource/request digest and expiry (≤30 seconds, bounded skew2 seconds). No browser-controlled signer, no transport credential/delegation/assertion in semantic digests, manifests, logs or browser. Receipt recovery may read only the exact original service-client binding after actor revocation; it cannot read a document or regain business permission. A missing exact execution can yield only bounded UNKNOWN/NOT_OBSERVED, never zero-effect/resend/supersession permission. A present wrong-client record is denied. Official relay grants owner/member access only when both current ACLs permit, rechecks app ACL/mapping/lifecycle after the bounded outside-lock fetch, and renders escaped immutable content with no-store/CSP. Direct unauthenticated provider portal is unavailable by design; the positive browser route remains usable.

T11DR-02 detail: raw FAILED and local string evidence alone never authorize reopening. The selected original acceptance must be known from an immutable row, current authority must hold, all historical rounds must be safe, and the complete locked inventory must exclude a marker/send/unknown/live owner. Delayed marker loses after local terminalization; marker winner blocks local proof. Ambiguous marker ACK stays frozen until a fresh primary locked read after the transaction finishes definitively resolves it. The local writer is separate from Task7 fact-only observation, which remains without any domain writer. Existing queue-only PREPARED termination is evidence to verify, not a substitute for the new atomic publication lifecycle.

Initial no-operation recovery creates no operation, execution, publication_attempt or provider proof. Same-key PREPARED recovery touches only real existing execution rows after their locks, preserving original proof in already-terminal case C. Recovery seals exactly one local decision, then allows explicit owner correction/edit/resubmission under the normal final and CAS rules. Unchanged content with changed destination does not require artificial final reconfirmation. Changed content requires a real new final. UNKNOWN and permanent published freeze remain binding.

## 2. Explicit schema gap and narrowly proposed forward model

Current V013 has mutable publication confirmation/key/destination and attempts requiring real operation/execution. V008 idempotency_record stores request hash/result ID, not the original accepted header. An initial accepted task may terminate without either execution or publication_attempt. Thus neither a hash nor current publication fields can reconstruct the old accepted destination/final after recovery. R1 does not pretend those facts exist.

§8.5 proposes one content-free `publication_submission` table keyed by real task_id. It specifies every column/type/nullability, publication submission number unique key, FK parents/restrict behavior, bounded immutable manifest/hash/requester/final/provider/destination binding, local decision columns and allowed values, conditional checks, scan index, immutable binding and one-way UNDECIDED→NEVER_MARKED write with affected-count1/exact replay. The real nullable local execution FK cannot fabricate an external identity.

Complete rank places existing submission rows after final headers and before mapping/publication_attempt headers. New-row insertion occurs after its actual task and declared FK parents, using the bounded new-row exception; no late acquisition of existing tasks. Every manifest transfer requires protected metadata length reservation and cumulative effective read budget. S discovers proposed next submission number, A constructs producer/manifest/hashes outside locks, and F checks unchanged full membership/max/root CAS before installing that exact number. Inventory changes abort/reprepare outside locks. The new number separates genuine no-operation resubmission producer keys without changing the logical business key or generic retry semantics.

Legacy tasks without an authentic submission row are UNKNOWN/unavailable for this new recovery/replacement; no inferred backfill from current fields, supplied request, hash or fake old header. Already-qualified marked fact recording is preserved independently. The provider sees only real remote-accepted predecessor receipts, not fabricated rejection records for locally never-marked executions; whole actual provider slot history still governs proof-CAS/no-document binding.

**This table remains a proposal.** No migration file/version was allocated, frozen, ACKed or applied. Root must arrange independent exact DDL review/version/freeze/ACK and synchronize current generator, dictionary, DDL and ER against the actual post-Task8 schema before implementation. R1 also exposes the publication-only queue terminal hook as a prospective genuine private composition extension requiring actual facade/source review. No public Runnable/DTO/FAILED-state authorization substitutes for issuer/holder/full-rank proof.

## 3. Focused actual source evidence checked

The following ordinary files were read in bounded ranges and raw-hashed. These are observed source facts, not a claim that Task4 work in progress or future shared facades are adopted.

| File and focused lines | Actual fact | Raw SHA256 |
|---|---|---|
| `backend/adapters/src/main/java/ama/task/JdbcTaskQueue.java:100–153` | :108 max-attempt terminalization; :136–143 exhausted RETRYABLE becomes FAILED; :145–147 returns with no execution; :148–152 genuine PREPARED local failure touches external rows, not publication. | `83c2ba70b1a80449e83e49b0e5bf6efbd4574c7597bc687d2c41721768794b14` |
| `backend/worker/src/main/java/ama/worker/WorkerLoop.java:88–170` | Ordinary completion at126 and failure/quarantine128–150 do not supply a publication-domain failure writer. Unknown completion avoids a second completion. | `4479f78fc37566a2bf6ebdb3118512e377bd0987808d4ba1e246f8aba05f4027` |
| `backend/adapters/src/main/resources/db/migration/V013__publications.sql:1–113` | Mutable publication current fields/state, unique meeting/key; real operation/execution-required attempts; no premarker acceptance history. | `1de37066869617723a11f05d7423584143a64e0c9ddf8879c7b4fd107de08e3c` |
| `backend/adapters/src/main/resources/db/migration/V010__reliable_execution.sql:119,130–133,187–209` | Operation marked_at NOT NULL; execution nullable marker/task_attempt, unique task, exact states/proof constraint. Cannot create a fake first premarker operation. | `03e89cc8bd58d5a6aa0d4ab031b8fc05a4f7d2450623df147cd84d05b68977db` |
| `backend/adapters/src/main/resources/db/migration/V008__meeting_schema.sql:113–126` | Idempotency stores hashes/result_id/status, no historical acceptance response/body/header. | `fa44424aa1ff32e049929d45865fe7133c2548fc9ea451f3350cd8984c118ce3` |
| `backend/adapters/src/main/java/ama/task/JdbcExternalOperations.java:155–175` | Exact retry retains full fixed spec/logical key/hash equality; replay currently compares operation current hash. Original contract remains unchanged; historical publication replacement must use its separate reviewed path. | `63e84274395c74c8d5e5375fcfc898c5cc39f19447318a2093eebaff1496ad19` |
| `W/task-09-minutes-generation-design.md:192–204` | Actual adopted original Task9 selected per-run bearer/authenticated routes and durable request/result store. This does not silently authenticate the separate KB provider. | `cf174e2a54827da3c2e2152dbddf7cfc292680882e676f0291fd1c014835c56b` |

Read-only `rg -n --glob '*.sql' 'CREATE TABLE.*idempotency|result_id|marked_at.*NOT NULL|publication_submission' .../backend/adapters/src/main/resources/db/migration` returned the exact existing V008/V010 locations and no publication_submission DDL. No broad runtime/schema interrogation was performed.

## 4. Actual document diff and verification

The full original-to-R1 textual diff was inspected in consecutive bounded character slices (0:14000,14000:29000,29000:44000,44000:59000,59000:end) without omitted content. Subsequently five small literal consistency changes were individually count-checked and read: safe client/store IDs in original execution manifest; clientId in acceptance manifest; S/A/F allocation of submission number without hashing under locks; mixed remote/local proof wording in the supersession evidence gate; authenticated absent-execution lookup remains UNKNOWN. No further substantive edits followed. Pure final §4, provider proof-CAS §8.3 and the exact existing retryAfterZeroEffect constraint paragraph compare unchanged.

Canonical document-diff recipe actually executed via `wsl -d Ubuntu-24.04 -- python3 -` (PowerShell single-quoted here-string on stdin; no shell interpolation and no diff file written):

```python
from pathlib import Path
import hashlib, difflib, re
w = Path('/home/tanren/ama/.worktrees/phase-b-completion/.superpowers/sdd/2026-10-23-ama-phase-b-remaining')
a = (w/'task-11-final-publication-design.md').read_text()
b = (w/'task-11-final-publication-design-r1.md').read_text()
def section(s, start, end):
    return s[s.index(start):s.index(end)]
assert section(a, '## 4.', '## 5.') == section(b, '## 4.', '## 5.')
assert section(a, '### 8.3', '### 8.4') == section(b, '### 8.3', '### 8.4')
p = next(p for p in a.split('\n\n') if p.startswith('Existing retryAfterZeroEffect checks '))
assert p in b
assert 'All accepted failed rounds have original marked execution manifests.' not in b
for term in ['### 7.1', '### 8.5', '| GET /publications/{publicationId}/document',
             '| POST /publications/{publicationId}/local-recoveries',
             '| GET /publications/{publicationId}/submissions?limit=&cursor=',
             '11.6a R1 local-never-marked lifecycle']:
    assert b.count(term) == 1
assert b.count('```') % 2 == 0
h = re.findall(r'^#{1,6} .+$', b, re.M)
assert len(h) == len(set(h))
d = ''.join(difflib.unified_diff(a.splitlines(True), b.splitlines(True),
          fromfile='original-design', tofile='R1-design', n=2))
print(len(d.encode()), len(d.splitlines()), hashlib.sha256(d.encode()).hexdigest())
```

Final result: exit0; document assertions passed; normalized UTF8/LF diff **80,472 bytes /258 lines /18 hunks**, **125 additions /36 deletions**. Diff SHA256 `ec47ae2c2732114131aa7aebad15e50f19c18690923f720afd0a69a4e3f57bce`. This digest includes the named original-design/R1-design headers and context2; it is not a raw artifact hash or Git diff. Raw originals were also independently compared with the expected SHA values below. R1 headings were printed and inspected (23 unique headings); scoped routes have one route-table row each. These checks validate documents, not executable behavior.

Verification limitations/tool corrections are retained honestly: an initial full diff output exceeded display context, so it was reread in the bounded consecutive slices above. Earlier WSL/native-Python startup failures under old permissions produced no validation evidence; ordinary WSL Python succeeded after permissions changed. One assertion initially compared all of §8.1, but its explicitly required accepted-binding introduction changed; the bounded diff isolated that intended sentence and the check was corrected to the exact unchanged old retry paragraph. A second assertion counted the local-recovery route in both prose and its table; it was corrected to count table rows. The final command then returned exit0. None of these earlier failed checks is represented as passing. No runtime tests, HTTP calls, Docker operations, DB writes, source changes or Git operations were used to validate prose. User interruptions were followed by raw file checks; no original design/review was recreated.

## 5. Raw artifact pins and handoff boundary

| Artifact | Bytes /lines | Raw SHA256 |
|---|---|---|
| Original `task-11-final-publication-design.md` (unchanged) | 69,377 /316 | `a8ec962bfee7ed1025a8d0dc113750447d9a8cc95b006b758cc544de900981e8` |
| Original `task-11-final-publication-design-review.md` (unchanged) | 12,601 /74 | `b1b6a4af59072f322fd71237636a608d9f0c2a530a98ce339ec57c56574a84f9` |
| Replacement `task-11-final-publication-design-r1.md` | 102,624 /405 | `f18ae4734603709284d91345b2909eca9687627c825444fd8cfa06dd49d36ffc` |
| Fix brief `task-11-design-fix-01-brief.md` (read only) | 7,653 /34 | `7afd80d136ee77a065bc21b11a206e57c654eacda4c9a877af24935789dab85b` |

This closure's raw SHA is measured after the final write and returned in the designer handoff/tool evidence; it is not embedded recursively into its own bytes. Reviewer should independently hash both output files and compare the two original pins before scoped review. R1 is the full proposed replacement, not merely an addendum to apply mentally.

Remaining concerns are explicit prospective activation gates: the new table's exact DDL/current-model synchronization and private queue terminal union are not implemented/adopted; actual issuer/holder, complete old/new ranks, field immutability, marker barriers, old acknowledgment and real HTTP/browser/restart evidence remain to be proved in source/tests after independent design review. Root still owns actual source pinning and future implementation activation. Original retry, Task7 no-domain-writer fact-only branch, UNKNOWN no resend, permanent ever-published freeze and same publication root survive unchanged. No completion qualification is inferred from this author report.
