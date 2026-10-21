# S3FileStorage adapter design preflight

Date: 2026-10-22, Asia/Shanghai. **Proposal for review, not implemented adapter/common-contract/B-04/production acceptance.**

## Recommendation and exact requirement ruling

Preserve FileStoragePort. Stage arbitrary logical parts immutably in S3; freeze the caller's supplied, server-owned receipt manifest; use a single immutable per-session terminal decision created with If-None-Match:*; assemble the selected byte stream into legal native MPU parts. No MySQL inside the adapter, new tables/nodes, mutable CAS, distributed lease or node-local resume dependency is needed. This is the smallest proposed protocol, conditional on the explicitly reviewed common-semantic clarification below.

Read phase-a task-B-04-brief.md, storage-preflight.md, s3-reference-research.md, both complete capability reports, and phase-b-upload-design HEAD **40cd72717abf079d3373c072eddea19f881a8916**: StorageContracts, FileStoragePort, LocalFileStore, LocalUploadJournal, ImmutableFiles, FileStorageContractIT, the database upload addendum/review and actual upload_session/upload_part schema, adapters POM and Java 21 parent.

Requirement trace:
- FileStoragePort Javadoc requires immutable logical keys, actual size/SHA validation, same-key/content replay, conflicting-content refusal, exact ranges, pinned tokens and idempotent complete/abort. It does **not** require a completion manifest to include every stored logical part.
- Original B-04 brief requires legal provider parts and common completion/replay semantics, without requiring all physically stored candidates to be selected.
- storage-preflight requires a canonical unique ascending contiguous manifest and trusted server receipts, matching the business-frozen manifest and expected size. This does not turn physically stored but DB-uncommitted candidates into business-accepted receipts.
- B00 database-design lines 173–183 at the pinned HEAD explicitly freezes **all DB-committed upload_part rows**, denies receipt insertion after OPEN, and leaves adapter late-I/O handling an outstanding B-04 obligation.
- LocalUploadJournal.complete additionally visits all part-* files and rejects part numbers greater than the selected list count. This is an implementation-specific stricter check; current common tests validate order/duplicates/foreign receipts but do not establish rejection of every unselected storage part as a port requirement.

Concrete inherited race: expectedSize=3, DB receipt part 1="abc"; concurrent storage writePart(2, empty) succeeds, then its DB receipt transaction loses to OPEN -> FINALIZING. The DB-frozen manifest correctly contains only part 1. Current local's extra-part directory scan rejects completion. The same state can follow storage success/DB receipt rollback. Do not “fix” this by changing the frozen DB manifest, accepting late receipts, banning zero logical parts, or having S3 query MySQL.

**Recommended explicit common clarification:** complete selects exactly its supplied trusted immutable receipts. That supplied list must be unique, ascending and contiguous from 1, with actual total equal to expectedSize and the actual complete SHA. Other stored part slots/candidates are unselected technical artifacts, not reasons to alter the supplied manifest or register more bytes. The public service still requires its client list to match ALL DB-committed receipts exactly. A submitted extra receipt remains invalid when it violates ordering/total/frozen request; an unsubmitted physical part is simply outside the selection.

Cost: a narrowly reviewed local change removing only the extra-part directory scan, plus shared selection/late-zero-part tests and explicit contract documentation. Keep local locking, immutable part bytes, actual hashing, decision/result durability and terminal semantics. **No code change or approval is claimed here.** If this clarification is not adopted, preserving that stricter local scan would require additional storage acceptance serialization (e.g. immutable per-session event slots) and still would not repair the DB-freeze gap by itself. The larger append-only log is therefore not recommended merely to mirror incidental behavior.

## Evidence and limits

RustFS **1.0.3**, linux/amd64 manifest **sha256:128220dc9382bac81940a1a9616ee8c274a3f3b8f647eedbeb50df5bb8b49922**, is the exact LOCAL fixture only. Its report records 180 completed synchronized publication races with one winner and 9 deterministic response-loss reconciliations across never-versioned/enabled/suspended modes. Three malformed-Range diagnostic failures and an indeterminate CreateMultipartUpload timeout remain preserved.

Those observations support conditional-create development; they do not qualify this session protocol, Java integration, all control-read visibility, native maxima, capacity, durable ACK, HA or production. SeaweedFS 4.02 remains failed and its original evidence untouched. Current FileStorageContractIT is explicitly local-only; boto3 primitive probes are not a second Java common-contract group.

All following protocol claims are design reasoning under stated prerequisites, not executed results. Production writer/bucket permissions, durable commit/visibility, supported access paths and operational capacity remain gates. **Conditional headers cannot stop an independently authorized unconditional overwrite.** Version provenance cannot repair a mutable key under key-only FileStoragePort.read.

## Existing terminal semantics and public cancellation

LocalUploadJournal:
- same logical part number/content replays original receipt; different content conflicts;
- empty completion list, bad order/duplicates/foreign receipt and wrong total fail;
- actual whole SHA is checked before durable decision, so bad full hash while OPEN does not freeze the upload;
- once complete decision exists, new writePart is INVALID_UPLOAD_STATE and abort is a successful no-op, including before final publication;
- ABORTED rejects write/complete; repeat/missing-session abort is idempotent only for genuine absence, not storage failure.

Preserve these semantics except the explicitly proposed unselected-part clarification. The terminal key chooses completion or abortion; it does not by itself mean final bytes or business registration are complete.

B00 public behavior remains OPEN -> FINALIZING -> COMPLETED; unknown completion stays FINALIZING. Ordinary public abort wins only from OPEN; FINALIZING abort conflicts; COMPLETED abort preserves the file as a no-op. Because the port's void abort may acknowledge a prior complete decision, the service must never translate it into “canceled” success without the DB decision. Deleted/unauthorized/aborted business state cannot register a late object: recheck identity/access, purpose, lifecycle/cancel_epoch and frozen request in the short registration transaction. A late object may be an unreadable orphan. No raw-key download route or early AVAILABLE row is introduced.

## Minimal official Java client and configuration

Propose official AWS SDK Java v2 BOM **2.56.0**, direct dependencies software.amazon.awssdk:s3 and software.amazon.awssdk:url-connection-client. Explicit synchronous S3Client avoids CRT/transfer-manager/async machinery. Exclude unused default Apache/Netty transports if otherwise resolved. No DynamoDB/new persistence dependency. This is a minimal dependency surface, not a claim of earliest compatible or security-qualified version.

Official release 2.56.0 is dated 2026-10-14; its tagged S3 service model includes IfNoneMatch header members for PutObject and CompleteMultipartUpload. Confirm exact Maven artifacts/transitive graph/hashes and Java 21 compilation in implementation; nothing was installed here. [Official release](https://github.com/aws/aws-sdk-java-v2/releases/tag/2.56.0), [tagged service model](https://raw.githubusercontent.com/aws/aws-sdk-java-v2/2.56.0/services/s3/src/main/resources/codegen-resources/service-2.json).

URLConnection is an official lightweight synchronous transport with fewer pooling controls. Bound active requests with an adapter semaphore; configure connection/socket/call deadlines and verify close/cancel releases the stream/connection/admission. If its measured behavior is inadequate, review the official Apache client separately. [Transport documentation](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/http-configuration-url.html).

Pin immutable backend definition: backendId, endpoint/region, physical buckets, layout/config identity, namespace mapping, credentials, addressing mode and capability profile. Never reassign an existing backendId to another physical namespace. UploadToken/StorageKey select their own backend, never current default. Unknown/retired backend is STORAGE_UNAVAILABLE. Local mode requires no S3 configuration.

Use explicit SigV4 configuration and credentials, production TLS, no unintended proxy or credential-chain fallback. Initially disable SDK mutation auto-retries (one total attempt) and perform bounded explicit reconciliation with replayable bodies. Record outgoing conditions in Java evidence. Never automatically replay an arbitrary caller InputStream. Redact credentials, native upload IDs, signed URLs, physical paths and provider exceptions.

## Physical layout, bounded manifests and trust

Use physically separate configured final and control/staging domains, preferably private buckets D and C on the same S3 backend. This adds no service/node. A same-bucket variant requires two owned physical prefixes and mandatory logical-key mapping; never reserve an apparent staging prefix within arbitrary logical objectKey.

**Narrow key-layout refinement (2026-10-22):** replace flat SHA-mapped final keys with a reversible encoding that preserves both logical prefixes and local Java ordering. Define E per Java UTF-16 code unit u: code units below U+007E pass through unchanged where allowed by StorageKey; U+007E through U+FFFF encode as literal "~" followed by exactly four lowercase hexadecimal digits. Thus literal "~" becomes "~007e"; non-ASCII/surrogate units are never silently normalized or replaced. This is a proposed internal encoding, not a provider feature.

This code is uniquely decodable: "~" always begins one five-character escape, raw characters are below it, and escapes are canonical only for code units >= U+007E. Reject noncanonical encodings such as "~0061", partial escapes and malformed hexadecimal. E is concatenative and preserves Java String.startsWith and String.compareTo: raw lower units sort below "~"; escaped higher units sort by their fixed-width hex values. The encoding includes no added terminator or final suffix that could change prefix ordering. ASCII path separators inside objectKey remain literal; backendId and namespace cannot contain "/" under StorageContracts, so their boundaries are unambiguous.

- D: data/v2/E(backendId)/E(namespace)/E(objectKey) — final byte object.
- C: keys/v1/K/spec — immutable full logical key/layout binding, where K is SHA-256 of the versioned, length-prefixed exact StorageKey code-unit encoding; this hash is for direct control lookup only, never final-object enumeration or an overflow fallback.

Persist and compare the exact full key/spec/config identity on every access. Listing can reverse the physical final name to a StorageKey, recompute K and verify that binding. A malformed physical name, hash collision, mismatching spec or mismatching backend/config fails closed. Case and exact Unicode code-unit identity remain distinct. The fixed data domain plus encoded backend/namespace scope cannot be escaped by a logical key resembling staging/session paths; C's staging objects remain physically isolated. Use SDK path/query/XML serialization, never hand-build URLs: legal raw characters such as spaces, "?", "#" or XML metacharacters need correct transport encoding but are not logical delimiters. No URL decode/percent decode may be applied to E a second time.

AWS documents a 1,024-byte UTF-8 limit for the entire physical key, including prefixes; pin and qualify the actual provider budget instead of assuming parity. E emits ASCII, so its physical character count is the actual S3 key-byte count (HTTP percent-encoding expansion is not that count). Before any write/begin, calculate the full mapped data-key length with overflow checks against the configured qualified budget. Exceeding it is a visible technical INVALID_ARGUMENT for unrepresentable storage key; a backend unable to represent required server-generated keys remains unqualified. Do not silently hash overflow into an unindexed alternate location. [AWS key-size/encoding rules](https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-keys.html).

Actual application object keys are server-generated ASCII; B00's existing object_key VARCHAR(512) ASCII and namespace VARCHAR(64) ASCII are internal locator budgets, not product filenames. With ordinary generated ASCII below "~", a 64-character backend, 64-character namespace and full 512-character key map to 650 bytes including this exact framing. Literal tildes/non-ASCII expand and must pass the same calculated technical bound; do not claim every possible generic StorageKey or every possible 512-character tilde string fits. User display_name remains LONGTEXT and is completely unrelated to physical key length. E deliberately avoids the 4x expansion of all-code-unit hex for ordinary generated keys while retaining exact generic representability where it fits. No new filename/file-size/product limit or schema is proposed.

For list(prefix), E(prefix) is computed by the same code-unit mapping, including prefixes ending in a surrogate or inside an ordinary logical path segment. Prefixes are literal String.startsWith filters, not directory paths. If prefix contains a character impossible in any valid StorageKey (e.g. NUL or backslash), local would return no match; return an empty page after ordinary argument/scope validation, rather than inventing a public range or name error. A mapped prefix longer than the provider key budget likewise cannot match any representable stored key and is empty. Never narrow by truncating its encoded prefix.

Layout version is pinned in backend definition and session spec. No objects were created under the superseded flat-hash proposal in this task. If an implementation had already used it, migration/version routing would require explicit review; do not silently reinterpret an existing backend's persisted locations.
- C: sessions/v1/S/spec — immutable generated UUID, UploadSpec, backend/config identity, schema and transport planning inputs.
- C: sessions/v1/S/parts/P — immutable accepted logical-part descriptor: part/actual size/SHA, candidate manifest root and stable receipt identity.
- C: staging/v1/S-or-put-attempt/candidate/chunk/I — immutable bounded byte chunks.
- C: staging/v1/.../manifest-page/I and root — immutable ordered chunk references, actual lengths/hashes.
- C: sessions/v1/S/completions/M/... — immutable canonical completion request pages/root.
- C: sessions/v1/S/terminal — **single authoritative** ABORTED or COMPLETE(manifest root, actual total/full hash, spec digest).
- C: sessions/v1/S/attempts/A/intent and known-upload — native attempt ownership; known-upload includes private UploadId when available.
- C: sessions/v1/S/result — verified StoredBlob bound to the immutable terminal digest.

Every control/staging create uses If-None-Match:*. No mutable journal, If-Match CAS or checkpoint is authoritative. 412/lost response means GET and compare exact immutable object bytes. Stale workers cannot overwrite spec, selected part descriptor, terminal or result. Separate complete/abort keys would allow both winners and are forbidden.

Canonical encoding includes explicit schema/domain, exact numeric types, normalized hashes and deterministic receipt order; reject malformed/unknown schemas, duplicate fields, trailing data, cross-session references and overflow. Bound single metadata pages and stream/page larger manifests, rather than introducing logical part-count/name/file limits. A content root references exact page hashes/lengths; validate each fetched page. Large legal Java lists are already caller-owned input, but do not add another whole-manifest/full-file heap copy.

providerToken is an opaque internal receipt identity (e.g. version/session/part/descriptor digest), never an actionable path/URL or native MPU ID. Resolve it through the fixed part slot and verify the full receipt equality. Checksums of control-object bytes plus semantic linkage detect stale/malformed data; they do not authenticate malicious authorized writers. Supported-writer permissions remain necessary.

Metadata SHA, ETag and “verified=true” fields are never byte authority. Independently hash/count staged and final actual streams. ETag is used only as an opaque native completion receipt or optional conditional read validator. Do not trust a journal/result claim when its referenced bytes are missing/corrupt.

## State transitions and linearization

State is derived from spec plus terminal, not mutable in-memory status:
- valid spec, terminal absent: OPEN;
- terminal=ABORTED: ABORTED forever;
- terminal=COMPLETE(M): COMPLETE_DECIDED forever, possibly still publishing;
- matching verified result: completed storage operation; terminal remains COMPLETE(M).

Linearization points:
1. beginUpload: durable creation/reconciliation of spec before returning token.
2. New logical part while OPEN: immutable part-slot creation, subject to terminal observation below; same-content replay observes its original slot.
3. complete-vs-abort: successful conditional creation of the same terminal key. At most one terminal wins.
4. final immutable object: successful conditional final PUT or MPU completion, independent of per-session terminal.
5. completed replay: actual final reconciliation and matching durable result.
6. business availability: caller's later DB transaction.

writePart checks terminal before I/O and again after creating/reconciling its slot, before returning a receipt. If terminal is observed, return INVALID_UPLOAD_STATE and retain unselected objects; **do not delete a slot another operation may already reference**. If its postcheck sees OPEN and complete/abort wins afterward, the overlapping part call may linearize before terminal. A slot created by an in-flight call after terminal is not newly admitted: postcheck rejects it, and complete uses only its frozen selection. No public receipt is committed after FINALIZING.

Why this is sufficient without an event log: completion need not atomically enumerate all part slots. It selects pre-existing immutable descriptors in the supplied receipt list, verifies them, then freezes exactly that list. Unselected late slots cannot affect chosen bytes or digest. Abort/complete have one competing key; part creation has no path that publishes final bytes. Required control GET consistency and conditional-create cross-client behavior must still be qualified.

No process mutex or timing window supplies correctness. Unknown writes are reconciled by fixed keys; no temporary absence or timeout means “definitely no effect.” If a previously observed immutable terminal/slot disappears or changes, fail STORAGE_UNAVAILABLE instead of treating it as OPEN or recreating history.

## beginUpload and arbitrary logical writePart

beginUpload creates a fresh S/spec after validating backend and expectedSize. Another node needs only token and stable config to resume. Lost begin response may leave an unreferenced technical session: this port has no request idempotency key, so public B00 idempotency owns which token is accepted. Do not sweep unknown sessions without policy.

writePart validates positive int part number, nonnegative size <= expectedSize and normalized SHA, matching local. Read actual body to EOF with overflow-safe counter, detecting both short and long bodies; size+1 detection prevents declared length hiding excess data. Chunk into bounded objects (for example 8 MiB, a tunable transport budget). Each chunk is hashed, conditionally uploaded to a unique candidate key and actual-GET verified before candidate acceptance. Compute the entire logical SHA while streaming. No final part slot is created until whole size/hash match.

A large logical part spans many chunks; zero bytes gives an empty-chunk manifest and empty SHA. Never impose S3's native 5 MiB minimum or 10,000 part ceiling on logical parts.

After verified candidate/root persistence, recheck OPEN and conditionally create parts/P. If a slot exists, consume/verify the caller's actual content and compare it to the immutable original; same content returns original receipt, different content CONFLICT. A losing same-content candidate must use the winner's descriptor/token, not a fresh receipt. Perform the terminal postcheck before success. Interrupted/mismatched bodies leave only private unaccepted candidates; they cannot contaminate a later accepted slot. Same-number races have one immutable content winner.

A lost part-slot response is resolved by full descriptor/content verification at the fixed slot. If the session remains OPEN, replay original receipt. If now terminal, return INVALID_UPLOAD_STATE rather than newly acknowledging an obsolete part; a previously acknowledged receipt can still be used by the frozen complete request. The public service's DB recheck remains mandatory.

## Complete, frozen manifests and native assembly

1. Validate nonempty supplied list, ascending unique contiguous numbering from 1, exact receipts belonging to this session, and safe total equal to expectedSize. Resolve each selected fixed slot; do not enumerate/require every unselected slot. Reject malformed/forged receipt as INVALID_ARGUMENT; actual bound part size/hash mismatch as CONFLICT/integrity failure. Verify actual chunk bytes.
2. Stream selected ordered bytes and compute actual whole SHA. Empty logical parts contribute no bytes but remain part of the canonical request. Wrong full hash while OPEN returns CHECKSUM_MISMATCH without terminal, preserving local retry behavior.
3. Persist canonical full request manifest pages/root, including exact selected receipts and full hash. Compete terminal=COMPLETE(M) against terminal=ABORTED with conditional creation. If existing COMPLETE has exactly the same canonical request, help/resume it; a different valid completion request is CONFLICT, even if two different selected manifests happen to yield identical bytes. ABORTED yields INVALID_UPLOAD_STATE. If terminal is already COMPLETE, compare request before new work; never swap a bad/different request into that decision.
4. No final upload is initiated before winning/observing matching COMPLETE. Check pinned final key/binding. If present, actual full size/SHA and logical identity must match. Same-content replay succeeds even if another legitimate session or put created the object; session provenance must not wrongly turn it into conflict. Different actual content is CONFLICT, never overwritten/deleted.
5. If absent, generate a native attempt A, persist intent, then create its own native MPU at the final physical key. Concurrent helpers use **separate upload IDs**, never shared mutable native part numbers. Reconstruct deterministic legal transport intervals from the selected immutable chunks. Split/coalesce across logical boundaries.
6. For zero data use conditional empty PUT. For bounded small data use verified buffered conditional PUT. Larger data uses native UploadPart with exact known length and a replayable ContentStreamProvider that reopens immutable chunk/range streams. Persist known native ID before uploading parts. Native ETags populate the ordered unique transport completion list, not SHA fields. Rehash/count assembly and refuse final completion if anything differs.
7. CompleteMultipartUpload always includes IfNoneMatch("*"). Interpret entire SDK result, including error in HTTP 200 body. On success, 412, 409, NoSuchUpload or missing response, reconcile actual final key before returning a logical result. Matching verified bytes may resolve success, different bytes conflict, absence plus uncertainty stays unresolved/retryable. A new attempt can rebuild from frozen bytes; no fresh logical key/content is chosen.
8. Full GET of the final object verifies actual length/full SHA before success. Persist immutable result bound to terminal digest and actual final VersionId (nullable), then return. If result write/verification fails, return STORAGE_UNAVAILABLE with state retained; another node reconstructs/reconciles. Do not trust an old result while referenced final bytes or config are unavailable.

Native NoSuchUpload covers aborted/completed/nonexistent IDs, not a definite logical state. SDK completion parses embedded 200 errors; 409 conditional-complete recovery may require a new MPU. [Complete API](https://docs.aws.amazon.com/AmazonS3/latest/API/API_CompleteMultipartUpload.html), [conditional-write behavior](https://docs.aws.amazon.com/AmazonS3/latest/userguide/conditional-writes.html).

Ordinary put uses the same durable bounded candidate staging/verification and conditional final publisher under a generated private attempt, without a public resumable token. Caller retry uses the same logical key/content. Never forward unchecked caller bytes into an irreversible final key. Wrong size/SHA must leave final absent.

Avoid synchronous SDK unknown-length bodies that buffer the entire stream; known lengths must come from verified staging, not unchecked caller claims. Never use InputStream.available as length or silently retry an exhausted stream. [Official streaming guidance](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/best-practices-s3-uploads.html).

## Native bounds are transport constraints, not logical/product limits

Planner uses total S and a qualified provider profile:
- T >= native minimum nonfinal size and T >= ceil(S/nativeMaxParts), with safe rounding;
- T <= native maximum part size; ceil(S/T) <= nativeMaxParts;
- intervals cover selected bytes exactly, nonfinal intervals legal, final may be short;
- S=0 uses empty conditional PUT; generic complete still requires a nonempty selected receipt list, as local does.

Current AWS primary limits list 10,000 native parts, 5 MiB–5 GiB per part except no last-part minimum, and maximum object 48.8 TiB. Do not reuse stale 5-TB lore or infer RustFS supports AWS's present maximum. [Current multipart limits](https://docs.aws.amazon.com/AmazonS3/latest/userguide/qfacts.html).

RustFS only has exercised 5 MiB boundary/short-final evidence, not maxima. Require a documented/qualified provider profile; separate planner boundary tests from actual exercised huge-object evidence. A provider-unrepresentable requested total is a documented technical INVALID_ARGUMENT, not an invented product cap. A deployment unable to meet its required envelope remains unqualified. Do not claim every long value can fit one finite S3 object. Supporting above-native-maximum logical objects requires a separate reviewed segmented-final-object design, not covert truncation or part-size restrictions.

>10,000 tiny/zero logical parts can coalesce to a few native parts. A single logical part can span native intervals. No new application partSize persistence column or negotiation constraint is required; B00's actual receipts are recovery facts.

## Abort, response loss, crashes and native cleanup

Abort OPEN creates terminal=ABORTED with If-None-Match:*. If it wins, no compliant completion publisher can start; a late writer may finish private staging/slot creation but sees terminal and cannot produce final data. Same ABORTED replay succeeds. Existing COMPLETE is a successful no-op preserving publication/result; never abort its required native attempts or delete its object. Missing valid session is idempotent only on confirmed NoSuchKey, not 403/network failure.

Lost terminal response: read exact terminal key and compare canonical bytes/state. Absent/unavailable observation is not proof the mutation failed; retry the same conditional operation or return unavailability. A late first request and retry still compete at one key. There is no second abort record to disagree with complete. Crash before terminal leaves OPEN/candidates; crash after terminal resumes that one decision.

Native attempt intent is durable before CreateMultipartUpload. Known ID is recorded immutably before UploadPart. A crash/lost create response may leave an **unknown native upload ID**; Create has no qualified idempotency token. A before/after list diff, guessed timestamp or node-local journal cannot attribute a shared-key native upload safely.

Recovery needs no unknown ID: inspect terminal/final, then reconstruct a fresh attempt from durable chunks if necessary. Delayed old attempts can only publish the same frozen bytes conditionally. Preserve unknown intents for diagnosis; a currently empty list is not proof a delayed create cannot appear. Do not blindly abort every upload on the final key: another legitimate session/helper may own it.

Known failed attempts may be aborted by exact bucket/key/uploadId only after their owner will no longer submit work. In-flight native parts can finish during abort, so reclamation may need repeated abort/ListParts after they settle. Logical abort success does not certify all provider storage is reclaimed. [Abort API](https://docs.aws.amazon.com/AmazonS3/latest/API/API_AbortMultipartUpload.html).

Unknown IDs are retained by default. A future explicitly authorized cleanup may abort listed MPUs for an already durably published immutable key only with proven owned namespace/non-reuse and the guarantee that all legitimate helpers reconcile the retained final object. Without those conditions, especially absent final/shared writers/concurrent deletion, leave them unresolved. Do not introduce lifecycle AbortIncompleteMultipartUpload or a guessed TTL.

No automatic history/journal/result/staging retention policy is invented. Even aborted unselected chunks may still be receiving writes; no active technical cleanup race is required for correctness. Later cleanup needs approved protection windows, references/session checks and precise ownership. A failed DB query is not absence or deletion authority. Unknown-upload accumulation is a real capacity/operations prerequisite before production.

Recovery table:
| Crash/loss boundary | Durable truth and restart behavior |
|---|---|
| spec ACK/token response lost | unreferenced session may remain; public idempotency decides accepted token |
| chunks/root before part slot | unaccepted candidate; retry with new candidate or reconcile known slot |
| part slot response lost | immutable slot; return original receipt only while OPEN |
| complete manifest before terminal | OPEN until terminal; invalid hash never terminalizes |
| terminal response lost | one fixed decision key; read/retry it, no contradictory state |
| native create response/record lost | unknown provider attempt retained; rebuild from S3 |
| native part response lost | owned attempt may retry exact bytes or be abandoned; no logical part mutation |
| final complete response lost/NoSuchUpload | verify final actual bytes; absence stays uncertain/rebuildable |
| final before result/result response lost | matching terminal + actual final reconstruct same result |
| abort response lost | ABORTED replay or COMPLETE no-op; never infer public cancellation from void return |

## Stat/read/list/delete and lifetime limits

Explicit stat hashes/counts actual final bytes with bounded memory, checks exact logical binding and returns nullable provenance. Metadata SHA/ETag is not a digest shortcut. **read(offset,length) MUST NOT call stat or any full-object hashing/verification path to obtain size.** The separate read path validates the key/binding and uses HeadObject without Range to obtain the provider-maintained actual Content-Length and current VersionId/ETag, analogous to local fstat on its opened object. This trusts qualified provider structural length, not user-supplied size/SHA metadata. HeadObject returns no object body. [HeadObject API](https://docs.aws.amazon.com/AmazonS3/latest/API/API_HeadObject.html).

Reject negative offset/length and arithmetic overflow before any data GET (and before HEAD when the invalidity is already decidable). After HEAD, require offset<=size and length<=size-offset; calculate inclusive end only for nonzero valid length. Invalid/out-of-bounds intervals perform no final-object GET. A valid zero-length interval, including EOF or empty object, confirms existence/size through HEAD and returns an empty stream locally, again with no final-object GET. Missing/access-denied HEAD retains normal NOT_FOUND/unavailability classification, never a fabricated zero-sized object.

For a nonzero interval, issue exactly one bounded Range GET for [offset,offset+length). Pin HEAD's concrete non-null/non-"null" VersionId when available, and verify response provenance. In never-versioned or suspended/null-version cases, use a provider-qualified same-object read validator such as GET If-Match with the observed ETag in addition to the supported immutable-key/non-reuse lifetime assumption. ETag here is an opaque read precondition, never SHA-256, and cannot prove arbitrary-overwrite safety. If that conditional read is not qualified, explicitly require and qualify the immutable-object lifetime between HEAD and GET rather than silently claiming a validator guarantee. No new full-object read is an acceptable fallback. AWS documents versionId and If-Match for GET; exact RustFS behavior remains a Java qualification test. [GetObject API](https://docs.aws.amazon.com/AmazonS3/latest/API/API_GetObject.html).

Require status 206, exact Content-Range start/end and total matching the HEAD observation, and Content-Length==requested length. Enforce exact returned byte count while streaming; reject provider clamp, mismatched version/validator, premature EOF, oversized response or ignored Range. Abort an unexpected whole-object 200 response on headers without consuming/draining the file. Close/cancel/error must abort unread response data when necessary so connection reuse never triggers an O(object-size) drain. Validate the chosen SDK/URLConnection cancellation behavior; any transport read-ahead must be bounded independently of object size. Do not hash unrelated bytes, retry by whole-object GET, readAllBytes, or eagerly consume the remaining range on caller cancellation. Fully consumed valid reads transfer only requested final-object payload, with bounded buffers and separately bounded control metadata I/O. A sanitized stream error never becomes a successful shorter file.

These pins tie the HEAD/GET observations; they do not add a version-aware public contract or support for arbitrary mutable history. Under concurrent explicit deletion, a pinned version may finish reading an object observed before deletion, analogous to local's already-open descriptor, or the provider may return NOT_FOUND/unavailability; do not silently switch to a newer object. Key-only immutability, no key reuse and business authorization/cancellation limits remain unchanged. Neither HEAD nor read supplies a new full-SHA proof or durable-ACK qualification; publication verification and explicit stat retain those separate responsibilities.

list uses prefix-aware bounded provider enumeration rather than a namespace-wide scan. FileStoragePort's Javadoc mandates scope isolation but does not explicitly mandate lexicographic order. Nevertheless, LocalFileStore.list uses Java String.compareTo/TreeMap and existing FileStorageContractIT.localListingContract asserts pages ["prefix/a","prefix/b"] then ["prefix/c"]. Preserve that observed common behavior without relaxing tests: E maps Java code-unit ordering directly to physical ASCII ordering. General-purpose AWS S3 ListObjectsV2 documents sorted results and Prefix filtering; directory buckets do not share that ordering and are not part of this profile. The exact RustFS Prefix/order/continuation behavior needs new real Java qualification; the prior primitive probe did not prove it. [ListObjectsV2 ordering and pagination](https://docs.aws.amazon.com/AmazonS3/latest/API/API_ListObjectsV2.html).

Each call sends Prefix=data/v2/E(backendId)/E(namespace)/E(prefix), no Delimiter, and MaxKeys<=limit (existing limit 1..1000). The first page has no continuation; later pages carry only the validated cursor's provider continuation token for this exact scope. Process the single returned provider page, decode and validate every final key/spec, then compute actual stats for returned entries. No global limit+1 sort, whole-namespace discovery or mutable side index is needed. Never use an SDK auto-paginator that drains all pages. Provider list key count and binding lookups are O(limit); full-byte stat hashing still costs the bytes of returned objects as required by the unchanged stat contract, so this is bounded enumeration I/O, not a claim of fixed-size total data I/O.

Cursor is an opaque versioned envelope containing backend/config/layout identity, namespace, exact logical prefix, provider continuation token, and last physical key for order/progress validation. Validate scope before the request and retain the exact independently constructed Prefix; opaque token alone never selects bucket/prefix. Validate every response key starts with that prefix and decodes to the expected exact backend/namespace/logical prefix; reject malformed/out-of-order/repeated keys and nonprogressing continuation as STORAGE_UNAVAILABLE. Invalid or cross-scope caller cursor is INVALID_ARGUMENT. Do not treat provider cursor as a user-visible storage locator.

Use IsTruncated/NextContinuationToken to return the next envelope, not a global lookahead scan. A concurrent deletion discovered during stat may produce a short/empty page with a nonnull advancing cursor; bounded pages need not fill limit and callers continue until nextCursor=null. A concurrently deleted object's known absence is skipped, while permission/network/integrity errors are not silently dropped. In a stable dataset the existing two-page test remains exact. This is live enumeration, not a consistent snapshot across pages: additions before a cursor may not appear during that pass. Orphan cleanup must use its separate authorized recovery/reference checks and may rescan; listing itself never grants deletion authority. The new mapping cheaply narrows recording/meeting prefixes without adding schema or relaxing lifecycle rules.

delete is caller-authorized deletion of exact pinned final key, with known absence idempotent and unknown failure retained. Preserve bindings and session decisions/results unless separately authorized lifecycle cleanup. Versioned delete-marker behavior and response-loss need real tests.

Important lifetime boundary: conditional create alone does not fence a delayed publisher after a permitted delete; delete markers also allow new creation. Current local similarly deletes data but retains session decision, and complete replay re-enters put. This design preserves the approved business non-reuse/cancellation boundary: deleted business state cannot register late bytes, which may be unreadable orphans and require later cleanup. It does **not** promise generic permanent delete fencing against every old/new writer. That stronger promise requires a reviewed common per-key lifetime protocol/local changes, not a HEAD/tombstone check. Never delete live final data merely to recover a completion or claim immutability survives arbitrary deletion/reuse.

## Errors and durable success

| Condition | Mapping / state |
|---|---|
| malformed argument/key/token/hash/receipt/cursor; selected bad order/duplicate/foreign receipt | INVALID_ARGUMENT; no new terminal |
| actual size or aggregate differs | SIZE_MISMATCH; no accepted candidate/complete decision |
| actual body/full SHA mismatch while OPEN | CHECKSUM_MISMATCH; OPEN retryable |
| existing key/part with different content; different valid completed request | CONFLICT; winner retained |
| confirmed missing required object/session | NOT_FOUND, never treating AccessDenied as absence |
| invalid interval before request | INVALID_RANGE |
| write sees terminal; complete sees ABORTED | INVALID_UPLOAD_STATE |
| network/unknown outcome/auth config/provider 5xx/corrupt journal/unqualified backend | STORAGE_UNAVAILABLE; preserve state |
| raw 412/409/NoSuchUpload | reconciliation branches, not automatic logical error mapping |
| EntityTooSmall/InvalidPartOrder after valid planner | adapter/provider qualification defect -> STORAGE_UNAVAILABLE |
| native checksum error despite independently verified replayable bytes | integrity/unavailability; no fabricated caller mismatch |

Use existing sanitized 400/404/409/416/503 envelopes; no provider locators/messages/headers leak. Business auth fails before this port. A stream error after headers aborts output instead of forging a successful length.

S3 successful visibility alone is not physical fsync proof. Result replay after lost ACK depends on a qualified durable commit/visibility guarantee; raw RustFS GET evidence does not establish it. Production readiness must fail closed until durable-ACK assumptions and writer enforcement are qualified; local test success only supports the exercised environment.

## Required real tests and acceptance gates

Run shared FileStorageContractIT twice: local through real LocalFileClient -> ama-file -> disk, S3 through this exact Java SDK -> fresh owned pinned RustFS HTTP. Report actual counts separately, never weaken tiny logical inputs or skip a failing backend.

1. Existing same/different-content put, actual stat/SHA, empty/range boundary/overflow, short/long/broken streams, listing scope, backend pinning/default change and idempotent delete. Wrong size/SHA must not publish final. For the refined key layout, test reversible identity/order/prefix properties at ASCII/tilde/non-ASCII/surrogate boundaries, literal path prefixes, prefix-of-another-key, SDK escaping and internal-looking names without staging collision. Assert exact physical key budget/budget+1 outcomes without truncation/hash fallback; include full 512-character ordinary generated key and unchanged LONGTEXT display names. Real HTTP listing must send the scoped mapped Prefix and MaxKeys, never enumerate unrelated namespace/recording-prefix objects; preserve local two-page ordering, reject cross-scope cursors, and test short/empty advancing pages under concurrent deletion. Encoding/property tests alone do not qualify provider pagination.
2. Explicitly adopted selection semantics: DB-selected [part1="abc"] completes despite unselected stored empty part2; replay exact selected manifest succeeds, changed selected request conflicts, extra client/DB manifest mismatch still rejects. Demonstrate local narrow fix and S3 behave identically. No late receipt insertion into FINALIZING.
3. Logical [3-byte,3-byte] with out-of-order arrival; zero parts among tiny parts/all-empty nonempty manifest; >10,000 tiny logical parts; large logical part crossing transport intervals. Capture actual legal native sizes/order and full bytes. Arithmetic max/overflow tests are not real multi-TiB execution evidence.
4. Two independent clients/processes race different bodies: PUT/PUT, complete/complete, PUT/complete. One immutable content wins; same-content loser can reconcile, different-content loser conflicts. Repeat version modes and restart; inspect key-only GET.
5. Barriers at slot create/postcheck, terminal create, native create and final publication: writePart-vs-abort/complete, late unselected zero part, same/different part-number races, same/different manifests, complete-vs-abort. ABORTED winner must never publish through that session; COMPLETE winner survives abort no-op. No two terminal winners.
6. Fault proxy drops full upstream responses for spec/chunk/root/part slot/terminal/native create/part/complete/result/abort. Inject clearly labelled 200-with-error, 409/412/NoSuchUpload and truncated/clamped range branches while retaining real provider operations. No timeout=no-effect inference.
7. Kill Java process after each durable boundary, discard its local temporary/cached state, resume on a fresh second JVM with token/config only. Include native-create response lost, completion before result, stale first process continuing, aborted late chunk writes, and complete response lost followed by conflicting writer.
8. Corrupt only test-owned control/content objects via explicit privileged injection; malformed page, false metadata, cross-session receipt, changed spec/result or missing selected chunk fail closed. Ordinary supported paths never overwrite. Test actual independent byte hashes, not only expected journal JSON.
9. Measure bounded heap/connections and cancellation with large streams/repeated failures. Reopen verified sources per retry; no whole-file buffering or exhausted-body retry. Add real provider-response body byte counters (separately identify final-object payload versus control metadata/headers) around the Java transport/proxy: on a large stored object, read(offset,1) fully consumed must request/receive only that one data byte, with HEAD returning no payload and no full digest GET; a valid zero-length read at start/EOF and negative/overflow/beyond-EOF reads must issue zero final-object GETs and read zero final-object body bytes. Include an empty object and a prefix/middle/suffix small interval. Verify actual recorded requests and bytes, not merely wrapper output length or a mocked stat invocation. Inject delete/change between HEAD and GET, wrong Content-Range/length/version, truncated body and ignored-Range 200: require fail-closed behavior and prompt abort without whole-object drain; measure any fixed transport read-ahead separately and show it does not scale with object size. Early caller cancellation likewise cannot read the remaining object for connection reuse. Explicit stat may read/hash the whole requested object; list may do that only for returned-page objects, never as hidden range validation or unrelated-prefix scanning. Small correctness races do not establish load capacity.
10. Known/unknown native upload cleanup cannot abort another active helper; unknown create remains retained without policy; native part racing abort does not falsely prove reclaimed bytes. No history TTL. Versioned delete and delayed final publication remain explicitly bounded by business orphan semantics.
11. Separate real API+MySQL B-04 tests: blob success/DB rollback => business GET404; retry registers one fileId/name/purpose; FINALIZING public abort conflicts; completed abort preserves file; permission/purpose/delete-epoch recheck rejects late registration. Reproduce extra-zero storage/DB freeze gap and prove the reviewed common selection fix, without changing frozen DB rows.

Also qualify control GET visibility/conditional-create across distinct Java processes and provider orderly restart. Preserve all failures and exact provider/client identity. These tests still do not prove power loss, RHEL/XFS equivalence, DRBD/fencing/HA, production permissions/durable ACK, actual capacity or company integrations.

## Actions and delivery scope

Only read-only source/evidence/primary web research and this authorized report were performed. The focused follow-ups refined physical key mapping, prefix-aware list pagination, and explicit HEAD-based range validation with measured bounded payload I/O; session/terminal/upload-transport/recovery decisions remain unchanged. No product/dependency/DDL edits, DB or external company calls, Docker actions, services, git mutations, child agents, production selection or main merges. No implementation/provider tests ran.

One read-only shell quoting attempt failed without effects. An initial report-write command exceeded the Windows process argument limit and did not start; the report was then written in bounded chunks to this sole authorized path. using-superpowers explicitly excludes dispatched subagents; verification-before-completion is applied to report-file existence/content checks, not an assertion of implemented correctness.
