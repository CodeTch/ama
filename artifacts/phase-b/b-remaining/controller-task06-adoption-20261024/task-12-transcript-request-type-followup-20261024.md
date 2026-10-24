# Task12 transcript request type follow-up — 2026-10-24

Narrow read-only routing of Task6 review T6SR-03 Minor to the future Task12 producer-contract window. Fixed product BASE: `aa490afc2690e9517a691cc8d1ae9487a025806d`. Product evidence is exclusively `git show BASE:path` for the transcript schema, generated request aliases/operation references, web package/lock and check-generator path. No implementation, schema redesign, npm/generator/test execution, or renewed Task12 design/web preflight is performed. Task6 fix01 remains sole author for its separate scope and will not change contracts for this Minor.

Authority: supplied review label `d1a2a7f4`, exact file `W/task-06-independent-review-20261024.md:42–48` (W=`.superpowers/sdd/2026-10-23-ama-phase-b-remaining`). The review document is a separately hashed filesystem input, not represented as a blob at product BASE or as a resolvable commit. It says the generated unknown unions are a downstream producer-contract defect, not weakening of the strict server parser. This follow-up does not independently re-review that parser or any other Task6 finding.

## Fixed BASE boundary map

All generated aliases are under `components["schemas"]` in `web/src/shared/api/generated.ts`. Each retains an object arm but ends with `| unknown | unknown`; union with unknown loses useful assignability constraints. The same aliases feed the generated operation requestBody, so using the operation type alone does not escape the issue.

| Alias | Schema object / alternate revision branches in contracts/openapi/transcripts.yaml | Required payload shape aside from revision | Generated alias / operation body lines |
|---|---|---|---|
|SaveRequest|841–914; anyOf902–913|sourceRevisionId; segments with segmentId, body and marks|1194–1206; saveTranscript3105–3125, body3123|
|MarksRequest|915–984; anyOf972–983|sourceRevisionId; segments with segmentId and marks|1290–1301; replaceTranscriptMarks3568–3588, body3586|
|RestoreRequest|985–1019; anyOf1007–1018|sourceRevisionId|1316–1322; restoreTranscript4152–4172, body4170|
|ConfirmRequest|1020–1054; anyOf1042–1053|savedRevisionId|1308–1314; confirmSavedTranscript3916–3936, body3934|

Originating schemas all have `type:object`, `additionalProperties:false`, required IDs/payload and optional expectedRevision (`./meetings.yaml#/ExpectedRevision`) / expectedRevisionToken properties. Each root anyOf contains two required-only branches, one requiring expectedRevision and the other expectedRevisionToken, without a property/type shape on those branches. The fixed generator output maps those alternatives to unknown arms. Both representations are allowed together by anyOf; descriptions901,971,1006,1041 explicitly require at least one and say equal dual values normalize identically. No new schema composition is selected here.

Token property definitions852–855,926–929,995–998,1030–1033 are strings, canonical decimal pattern `^(0|[1-9][0-9]{0,18})$`, with supported business revisions described as0..9223372036854775806. Save/marks nested marks enumerate KEY, CONCLUSION, DISPUTE and GENERATION_FOCUS; Save body is string. The required source/saved UUID fields are string with canonical UUID patterns. Contract operation bindings reference these aliases at1590,2399,3005,3417; matching operationIds are1394,2203,2809,3221. The shared meetings revision definition is only referenced, not opened in this bounded follow-up.

## Actual generator/check route

`web/package.json:29` and `web/package-lock.json:18,1487–1490` pin `openapi-typescript`7.13.0; TypeScript5.9.3 and vue-tsc3.2.5 appear at package31,33. This is the committed declared/locked tool version, not an assertion about any uninspected installed node_modules or an execution/version probe. Node22.23.4/npm10.9.9 are committed engines6–10.

Future author route: `npm --prefix web run generate:api` reads root `contracts/openapi/api.yaml` and writes `web/src/shared/api/generated.ts` (`web/package.json:17`); `npm --prefix web run check:api` calls `node scripts/check-api.mjs` (18). Check script5–13 creates a temporary directory, invokes Node with `web/node_modules/openapi-typescript/bin/cli.js`, reads the same root schema, requires successful generation, compares full bytes with committed generated.ts and removes the temporary directory. `npm --prefix web run typecheck` is `vue-tsc --noEmit` at14. None was run here.

A byte-consistent regenerated file can still contain unknown aliases; a project typecheck can pass if no relevant request shapes are consumed. Future closure therefore needs actual type-use evidence in addition to mechanical regeneration/check. Do not hand-edit generated.ts, change the generator version merely to avoid the issue, or treat the retained Task6 generation/typecheck receipts as proof of useful request types.

## Concrete future acceptance evidence

Task12 author owns the minimal generator-compatible schema correction and mechanically generated diff, then focused source review. Exact composition is deliberately left to that author/review. The following is the bounded acceptance matrix, not implemented tests or claimed results:

| Evidence layer | Positive compatibility cases | Rejection / useful-shape cases |
|---|---|---|
|Compile-time consumers of each of the four component aliases and corresponding operation JSON body alias|Valid required ID/payload with legacy-only expectedRevision7; token-only expectedRevisionToken`"7"`; equal dual7/`"7"`|Reject scalar/null/object with missing ID; missing both revision representations; numeric expectedRevisionToken; string expectedRevision. Demonstrate aliases are neither unknown nor any.|
|SaveRequest nested shape|Complete typed segment with segmentId, string body and marks array|Reject missing segments; segment missing segmentId/body/marks; numeric body; marks scalar or unknown mark enum.|
|MarksRequest nested shape|Complete segmentId/marks array, including empty marks for a segment|Reject missing segments; missing segmentId or marks; marks scalar or unknown enum.|
|ConfirmRequest / RestoreRequest identity|Confirm uses savedRevisionId; Restore uses sourceRevisionId|Reject a payload supplying only the other operation's ID; reject missing required ID.|
|Exact revision transport compatibility|Token-only strings`"9007199254740991"`, `"9007199254740992"`, `"9007199254740993"`, `"9223372036854775806"` preserve all digits; equal safe dual7/`"7"` remains permitted along with legacy-only7|Runtime/schema evidence retains malformed-token and unequal-dual rejection; do not claim static TypeScript checks can compare arbitrary number/string equality or validate every decimal range/UUID/array-content rule.|

For negative type-use evidence, compile deliberate malformed fresh payload literals against the actual generated aliases/operation body types using expected-error assertions (or equivalent compiler diagnostics), without `as any`, casts through unknown, suppression, or a hand-written replacement DTO. An unused expected-error assertion exposes accidental acceptance through unknown. Check both component and operation body types and consume valid fields without narrowing from unknown. Positive examples must compile, including dual representation; a change that makes both alternatives mutually exclusive would break that compatibility. These are acceptance requirements, not a prescribed test-file location or schema design.

Separate runtime compatibility evidence must exercise actual request acceptance/equal-dual normalization/replay and retain token-only digits without JS-number conversion, in Task12's reviewed real HTTP/browser source window and fresh evidence root. No backend source/tests were opened to infer a currently qualified runtime matrix. The review's statement that the server remains strict is retained as attribution, not upgraded to independent execution proof. A compile-time string property cannot establish numeric range, canonical token pattern, UUID validity, full segment-set/order semantics or dynamic dual equality; exact schema/runtime checks remain separate.

## Custody and limits

Only this new follow-up and its uniquely named manifest are written. No product/tests/contracts/generated file, HEAD, index, DB, resources or private environment was changed or accessed for runtime use; no tests, npm, generators, network/browser or subagents. This does not close T6SR-03 or grant Task12 activation. Actual upstream Tasks6/8/7/9/10/11 API/source repin remains mandatory at Task12 activation; this BASE-only delta is not that repin.

Earlier `W/task-12-deferred-boundary-map-20261024.md` remains unchanged with SHA256 `154de68991632899889ecb655dd63830e22f346b105019349eb4dd1f584c2ed8`. Its T4 mapping is neither repeated nor superseded. The companion `task-12-transcript-request-type-followup-20261024-manifest.json` records exact BASE blob identities, byte sizes, full SHA256 and 1-based inclusive excerpt ranges, plus separately hashed review authority and old-map preservation.

| BASE path | Git blob SHA1 | Bytes | Evidence lines |
|---|---|---:|---|
| `contracts/openapi/transcripts.yaml` | `0cb5ecafe793904d58970aa8427f3e6de9c22b26` | 116651 | 841–914, 915–984, 985–1019, 1020–1054, 1394, 1585–1595, 2203, 2394–2404, 2809, 3000–3010, 3221, 3412–3422 |
| `web/src/shared/api/generated.ts` | `07c30bd7be854809768c4b872ae5c6b6f81cca0d` | 193702 | 1193–1206, 1289–1301, 1307–1322, 3105–3125, 3568–3588, 3916–3936, 4152–4172 |
| `web/package.json` | `2d746c212806e2c7dd1d26d1679e3a1245d8cb43` | 975 | 6–18, 25–34 |
| `web/package-lock.json` | `0afcf32161b2bdba8632d70f4a05886eab30796c` | 66457 | 15–22, 1487–1492 |
| `web/scripts/check-api.mjs` | `8bf9a57af5a7635bee3dc320d595846206930b96` | 873 | 1–13 |
