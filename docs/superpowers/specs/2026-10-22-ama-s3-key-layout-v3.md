# S3 key-layout addendum: bounded physical segments, depth and prefix enumeration

Date: 2026-10-22 Asia/Shanghai. **Narrow design proposal for root decision and independent implementation review; not implemented or qualified.**

This addendum leaves the original s3-adapter-design-preflight.md unchanged at SHA-256 **3bb475ce826a739ae6c1f01246084e16fdec76dcf9038a387a79bcb7351904f5**. It changes only physical key layout, scope binding, list-prefix/cursor derivation and their representability/test obligations. Terminal decision, selected logical manifests, native MPU assembly, response-loss/restart recovery, HEAD-based bounded range reads, actual SHA validation and cleanup policy are not reopened.

## Observed qualification conflict; no inferred root-cause closure

Read the implementation worktree's S3KeyBudgetDiagnosticIT.java and artifacts/phase-b/b04-s3/key-budget-diagnostic.log. The real Java SDK diagnostic records:
- single-segment 255-byte key: 200;
- single-segment 256/512/1024-byte keys: 400 InvalidArgument;
- tested slash-separated variants of the same total lengths: 200 at that time;
- the test correctly failed its ordinary 512-character unsegmented-key assertion. Do not convert that failed raw diagnostic into a passing test by weakening its expectation.

Also read provider-exit.json, provider-restart-exit.json and provider-restart-stdout.log. Both exits are 139 with OOMKilled=false; the restart epoch lasts about 10.42 seconds and the preserved log reports rustfs-worker stack overflow. The root reports the deeply segmented 1024-byte probe preceded the failure. These observations do not establish the precise causal code path, nor prove shallow keys repair the provider. Original fixture/data/failures remain retained. No provider request, test, restart, Docker operation or data modification was performed for this addendum.

Original v2 mapping can leave a 512-character ordinary logical component in one provider segment. It therefore does not satisfy the exercised local provider's key envelope. Native S3's total byte limit alone is insufficient qualification. Do not require the business to add slashes, impose a 255-character port/product limit, change the production provider selection, or promote a raw segmented PUT200 into adapter/restart acceptance.

## Proposed layout v3

Use a new layout version and new backend/config/fixture identity for subsequent tests. Never reinterpret v2 objects/tokens/cursors as v3, move retained failed data into the new fixture, or pretend old v2 objects were written by v3. Existing old-layout reads, if needed, stay routed by their original backend/config identity. No migration or cleanup is authorized here.

Keep final and control/staging physical domains separate. Define:

- H = 64 lowercase hex characters of SHA-256 of a domain-separated, versioned, length-prefixed **exact UTF-16 code-unit encoding** of backendId, namespace and the stable physical backend/config identity.
- C: scopes/v3/H/spec = immutable full scope binding, created conditionally and compared in full. It contains schema/layout, exact backendId/namespace, bucket/config identity, and fixed layout parameters below. A hash match is not sufficient: mismatched full scope means fail closed.
- D: data/v3/H/P(F(objectKey)) = final byte object.
- C: keys/v3/K/spec = the v3 full logical-key/layout binding, using a versioned domain-separated K over the exact full StorageKey encoding. This is the sole v3 key-binding path, with full equality checks. K remains a direct-control lookup hash, not a final-object/listing fallback; old keys/v1 bindings are not silently interchangeable.

H hashes scope, **not objectKey**, so listing a namespace or logical prefix can construct its physical prefix directly. A full scope binding is needed before listing; a genuinely absent scope yields an empty page, while permission/unavailability/malformed/mismatching scope is an error. A collision in H cannot mix scopes because full equality is mandatory before any final write/read/list. Never silently alias two scopes or claim SHA-256 removes the need for identity checks.

Backend/namespace may be generic Unicode or longer than the application's 64-character ASCII DB budget. The hash keeps their physical segment fixed; their exact values remain in durable bounded/pageable control metadata rather than being truncated. This does not introduce a 64-character generic port limit. Do not rely on Java UTF-8 replacement of surrogate code units for equality. Here config identity means an explicit immutable **physical namespace/layout identity**, provisioned once and recoverable across nodes. Its H input encoding/domain and field order are versioned. It binds the backend to the same physical namespace/buckets and routing meaning; it is not a process-random value, current default backend, mutable config_revision, credential version, secret, timeout, retry policy or other runtime setting. Credential/key rotation and timeout changes must leave H and historical token/key routing unchanged. Persist no credentials/secrets in the full scope binding. A logical endpoint alias may remain stable as connections rotate, but it cannot silently be repointed to unrelated storage under the same identity. A genuine change of physical namespace/backend meaning requires a new backendId/layout definition, preserving old routing; this addendum introduces no broader configuration-governance mechanism.

### F: order- and prefix-preserving code-unit escape

Process Java UTF-16 code units, not normalized Unicode code points:

| Input unit | Encoded ASCII |
|---|---|
| U+002E "." | ".0" |
| U+002F "/" | ".1" |
| U+007E through U+FFFF | "~" plus exactly four lowercase hex digits of that unit |
| All other units below U+007E | The original ASCII character, if valid for StorageKey |

Thus literal "~" is "~007e". Ordinary letters, digits, underscore and hyphen remain one byte each. Period and logical slash are data, never physical path separators. Unicode surrogate pairs become two escapes and unpaired units retain their exact Java identity. Invalid full StorageKeys are rejected by the existing contract before encoding; list prefixes use the original literal-prefix rules.

F contains no "/" and is a prefix-free code per logical unit: "." always consumes one following selector 0 or 1; "~" always consumes four lowercase hex digits representing a unit >= U+007E; other permitted ASCII consumes one character. Reject malformed escapes, alternative case/spelling, "~0061", trailing partial escapes and raw forbidden units when decoding a complete key. Decode then re-encode and compare exact bytes to enforce canonical form.

Why F preserves Java order: unchanged units below "." sort below both ".0" and ".1"; those encode "." before "/"; the next unchanged unit "0" sorts after them. Unchanged units through "}" sort below "~"; fixed-width hex after "~" orders all units >= U+007E by value. At the first differing unit the codewords therefore have the same order as String.compareTo, and no unit codeword is a prefix of another. Concatenation preserves proper-prefix ordering and exact String.startsWith. There is no case folding or Unicode normalization.

### P: deterministic shallow physical segmentation

Partition the **complete F character stream** at fixed offsets 199, 398, 597, ... . Each nonfinal payload chunk has exactly 199 ASCII characters; the final chunk has 1..199. Prefix each chunk with literal "x" and join chunks using one physical "/".

P(c1...cL) = x<first 199>/x<next 199>/.../x<remaining>

There is no trailing slash, empty chunk, final suffix, variable-length chunk heuristic, or splitting on logical "/" characters. The marker "x" is removed once from every physical segment during decoding, regardless of whether the original payload itself begins with "x". Literal logical slashes are already ".1", so logical path depth does not control physical depth.

Split **at encoded-character offsets even if this divides ".0", ".1" or "~hhhh"**. Decode only after stripping all chunk markers and concatenating their payloads. Do not decode one physical segment independently and do not shift a boundary to “finish an escape”: shifting would break the deterministic prefix mapping and the budget proof.

Every physical payload segment begins with "x" and has total length 2..200, so even a chunk ending in "." or beginning with the remainder of an escape is neither empty, "." nor "..". Framing segments "data", "v3" and H are also safe, with lengths 4, 2 and 64. No user-derived slash can create an extra level.

Reject noncanonical physical shapes: empty segment, missing marker, nonfinal payload shorter/longer than 199, final empty/overlong payload, unexpected framing/scope, invalid fully reassembled F, decoded invalid StorageKey, or re-encoding mismatch. No alternate chunking spelling may point at the same logical key.

### P ordering/prefix proof

For two F strings, framing insertions occur at exactly the same encoded positions until the first differing character. Both physical strings therefore compare on those differing payload characters, preserving F order. If one F string is a prefix of the other, its P string is also a prefix of the other's P string, including when it ends exactly at a 199-character boundary. The shorter complete key sorts first. There is no suffix after the last chunk that could reverse that comparison.

Consequently within one H scope, physical UTF-8/ASCII key order equals Java String.compareTo of logical objectKey. No namespace-wide sort is needed. As with any S3 representation where one object name can be a prefix of another, the provider must support both objects simultaneously; explicitly test a logical key ending at encoded position 199 together with extensions stored below the following physical segment. Do not assume backing-filesystem behavior from S3 documentation.

## Exact budgets and honest representability

Let L=F(objectKey).length and k=ceil(L/199), for nonempty valid objectKey.

- Fixed prefix data/v3/H/ is 73 bytes.
- P adds L payload bytes, k marker bytes and k-1 separator bytes.
- Full final key length = **72 + L + 2k** bytes.
- Maximum physical segment = **200 bytes**.
- Number of physical segments = **3+k**.

With the configured 1024-byte overall budget, accepted L is at most **942**: L=942, k=5 gives exactly 1024; L=943 gives 1025 and fails before any provider write. Therefore final depth is at most **8 physical segments**, with no empty/dot/dotdot components. These are mathematical properties of the mapping, not provider qualification results.

The required diagnostic envelope "a" repeated 512 maps to L=512, k=3, **590 bytes / 6 segments**, with payload lengths 199,199,114 and physical segment lengths 200,200,115. The application's backendId/namespace up to 64 ASCII characters each do not add variable final-key length because the complete scope is bound through H. Ordinary generated ASCII alphabet letters/digits/underscore/hyphen has this same 512-character envelope, with no caller-inserted slashes.

A valid slash-rich 511-character logical key consisting of 256 "a" components separated by 255 "/" characters maps to L=766, k=4, 846 bytes and 7 physical segments, rather than 256 path levels. The old raw "a/" repeated 512 probe ends in "/" and is not a valid generic StorageKey; do not replay it through the adapter or reproduce its unbounded physical depth in a new fixture.

**Do not overstate the envelope:** periods/slashes expand 2x and tilde/non-ASCII units 5x. As in the prior explicitly budgeted generic design, some legal long/escape-heavy keys cannot fit one provider key. For example, a valid single logical component of 512 periods has L=1024 and would be 1108 bytes; it is not representable by this v3 algorithm. Return technical INVALID_ARGUMENT before any write, with no truncation, caller segmentation demand or hash fallback. Product display_name remains LONGTEXT and unrelated; this is a physical representation boundary, not a product filename/file-size rule.

The demonstrated/adopted 512 **ordinary generated** ASCII envelope must not be quietly relabelled “every possible 512-character ASCII string.” If root intends the latter stronger requirement, this proposal does not establish it and must not be adopted as satisfying it. A more compact block/bit encoding would need additional prefix-boundary/query proof and review; it is not silently introduced here. Ordinary server key generation must be documented from actual application behavior, not inferred merely because the DB permits VARCHAR(512). The test obligation remains the real ordinary 512 key and declared generated grammar; do not change business keys to pass provider limits.

All calculations use overflow-safe counters. Full-key oversize fails visibly as transport representability; a configuration unable to meet the required application envelope is unqualified. Scope hash does not justify silently accepting a malformed/mismatching full binding.

Control keys also need physical validation before provider I/O: generated fixed names/UUIDs/hashes/decimal chunk indices only, no raw backend/namespace/objectKey path interpolation; each segment <=200, no empty/dot/dotdot, total <=1024, and reviewed fixed templates depth <=8. Inventory actual existing control templates during implementation review. Manifest pages keep arbitrary generic scope values in payload, not provider paths. If an existing template exceeds the bound, explicitly version/fix that physical template; no protocol state or table change is required by this addendum.

## Mapping examples and boundary cases

Here B(H) means the exact data/v3/<64-hex-H>/ prefix; "a{N}" denotes N repeated "a" characters, not literal braces.

| Logical key | F | Final physical suffix after B(H) |
|---|---|---|
| abc | abc | xabc |
| a.b/c~ | a.0b.1c~007e | xa.0b.1c~007e |
| a/b | a.1b | xa.1b |
| x | x | xx |
| a{199} | a{199} | xa{199} |
| a{200} | a{200} | xa{199}/xa |
| a{512} | a{512} | xa{199}/xa{199}/xa{114} |
| a{198}/b | a{198}.1b | xa{198}./x1b |
| a{198}~ | a{198}~007e | xa{198}~/x007e |
| one U+D800 unit | ~d800 | x~d800 |

The split-escape examples intentionally demonstrate that a physical segment may end with the escape introducer; the "x" marker prevents special dot-segment shape, and concatenation restores the full code. A substring of a physical key is not independently decoded as a logical key.

For logical prefix a{199}, send B(H)+xa{199} **without adding a trailing slash**. It must match the object exactly at that logical prefix as well as longer logical keys whose next physical bytes start "/x". Adding "/" would wrongly exclude the exact key. For empty prefix, send B(H), with no synthetic chunk. For prefix a{198}/, F is a{198}.1 and the provider prefix is B(H)+xa{198}./x1. The same fixed-boundary transform handles prefixes ending at any logical code-unit boundary.

A returned complete physical key always undergoes full canonical decoding and full scope/key-binding equality checks. No digest-only identity assertion and no accepting an arbitrary provider prefix result as valid data.

## Prefix listing and cursor

Within validated H scope, map a literal logical prefix p to:
- B(H) for p="";
- B(H)+P(F(p)) otherwise.

The equivalence is exact: valid logical q startsWith(p) iff its canonical final physical key startsWith the mapped prefix. This follows from F's prefix-free per-unit code plus P's fixed insertions. Logical slash remains literal data, not S3 Delimiter hierarchy. Do not pass Delimiter. A prefix impossible for any valid StorageKey or beyond the representable encoded length yields empty after normal validation; do not truncate it into a broader query.

Continue using one bounded ListObjectsV2 page with MaxKeys<=requested limit and that independently constructed physical Prefix, not a global auto-paginator. No flat object hash, full namespace scan, side index or global limit+1 heap is introduced. Full-content stats may hash only returned-page objects as the existing design says; listing is not a new checksum shortcut. Range reads still use HEAD and a bounded GET, not stat.

New cursor envelope must bind layout=v3, backend/config identity, exact backendId/namespace, H, exact logical prefix, mapping parameters (or immutable layout ID defining them), provider continuation token and last canonical physical key for monotonic-progress validation. Old v2 cursor is rejected for v3 rather than reinterpreted. Token alone never selects bucket/scope. Validate decoded scope and response-prefix/order/progress independently. Preserve live enumeration/no-snapshot and short/empty advancing-page behavior under concurrent deletion.

AWS documents an overall 1024-byte UTF-8 key limit and sorted general-purpose ListObjectsV2 behavior. These are interpretation references, not proof of RustFS's per-segment/depth/list implementation. Actual fixed-layout RustFS tests remain required. [AWS key naming](https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-keys.html), [ListObjectsV2](https://docs.aws.amazon.com/AmazonS3/latest/API/API_ListObjectsV2.html).

## Independent implementation/review and real evidence gate

No test is run or provider request authorized by this addendum itself. Root decides the layout; the author then implements and an independent reviewer evaluates it. Required evidence:

1. Pure algorithm/property tests separately establish decode(encode(s))==s, code-unit order and literal-prefix equivalence over valid keys and prefixes, including period/slash/tilde, raw ASCII punctuation, Unicode/surrogate pairs/unpaired units, and identical prefixes at encoded offsets 198/199/200/397/398/399. These are necessary mathematical/implementation tests, not storage qualification.
2. Explicit canonical-shape rejection tests: missing "x", extra/empty/trailing segments, short intermediate chunk, ".0"/".1"/"~hhhh" split across every possible chunk offset, noncanonical hex, bad full scope/key binding and a forced test-hash collision. Verify errors cannot trigger fallback/repair overwrites.
3. Budget tests: segment max exactly200, total exactly1024 at L942 and overbudget L943 rejected before provider I/O, fixed depth<=8, 512 ordinary logical characters with 64+64 application scope, and valid many-logical-slash keys mapping to shallow depth. Do not use the old a/-repeated raw deep probe as a new success-path test.
4. Fresh identity/isolated shallow fixture, pinned RustFS digest and exact Java SDK: real put/stat/HEAD/range/list/delete and multipart publication at mapped 512 ordinary key and selected shallow boundary keys. Independent full-byte/SHA checks and actual signed physical key capture, request budgets, response classifications and VersionIds. Exact1024 shallow keys remain unqualified until real success/replay/restart; do not assume old deep-key200 qualifies them.
5. List across mapping boundaries with exact logical order: key ending at encoded199 and longer keys sharing it must coexist; include prefix equality/exact-boundary queries, split escape prefixes, and cursor pages spanning these names. Assert provider Prefix is mapped/narrowed, MaxKeys bounded, no unrelated namespace/meeting scan, no v2 cursor acceptance and no empty/dot/dotdot physical segments.
6. Restart the new owned shallow fixture only under the separately authorized implementation workflow; verify acknowledged v3 objects and control bindings recover and list correctly. Preserve all failures. A passing restart does not establish why the old deep fixture crashed or prove arbitrary maximum-depth safety.
7. Review all generated data AND control key templates for the <=200/<=1024/<=8 properties. Verify checks run before first affected provider mutation and that raw logical slash depth never leaks through. Review full scope comparison at put/get/list and stable backend/config/layout pinning across nodes.
8. Re-run the existing protocol/common-contract/recovery tests unchanged in substance against v3. This is a layout change, not permission to modify terminal races, receipt selection, tiny logical parts, read amplification assertions, native transport limits or cleanup retention. Report actual test counts and evidence; do not count this document as execution.

Required remaining gates include supported-writer policy/durable ACK, original old-layout/provider failures, tested logical/application envelope, total/segment/depth/provider pagination qualification and production suitability. The proposal bounds the adapter's physical layout mathematically; it does not remove the provider's unresolved failure evidence.

## Scope of this work

Read-only source/log/state-artifact inspection and this one addendum. No source/SQL/DB/container edits, provider/network requests, test execution, services, git operations, old report rewrite or child agents. Existing original design SHA was checked before writing this addendum. A broad read-only file discovery encountered inaccessible retained provider data/log directories and left them untouched; evidence was then read from the existing sanitized artifact paths.
