# B09 frontend / contract preflight — read-only, 2026-10-23

Status: preparation only. Root supplied clean baseline `34cb226aefe99c67d3681af68d748b0aeea4e073`; this agent did not run Git or independently requalify it. This report neither activates the old prepared task nor completes B09/B06/Phase B.

## Authority and scope

Read DEVELOPMENT_HANDOFF.md, immutable Phase B B06–B09 plan, architecture v0.2, approved product supplement v0.2, 2026-10-22 minutes UX decisions, original requirements/prototype, A prototype index/provenance, current web source/tests/config, current OpenAPI/API controllers/configuration/contracts, selected existing fixture test source, B06.2 consumer contract, and `artifacts/checkpoints/2026-10-22-development-pause/task-B-09-info-components-prepared-brief.md`.

Historical progress references `artifacts/phase-b/b09-ui-preflight/b09-prototype-ui-preflight.md`; that directory is absent from the current root. Original HTML provides final override anchors below. The old prepared brief is evidence, not a newly activated task. No private environment/configuration/credential file was read. A broad filename discovery reached denied ignored worktree directory names; it was stopped, with no content read there. Subsequent reads were scoped to public source/docs.

No source, history, SQL, tests, Git, DB, Docker/service, browser or company endpoint was changed/exercised. No tests ran; all test statements below are source inventory or proposed acceptance. Only this assigned report is written.

## Outcome and dependency boundary

Current web is an honest A-stage shell. OpenAPI supports session/directory, meeting creation/read/info/roster-confirmation, and file begin/part/complete/abort/content. It has **no processing, transcript, minutes, candidate, evidence-review, final-confirmation, publication, task-query/event, meeting-list, category-dictionary or knowledge-location endpoints**. B06.1/B06.2 seams/archives and B07 pure S1 do not deliver those browser APIs.

Full B09 must wait for reviewed B06–B08 runtime and read contracts. An identity/info component slice can proceed earlier with an explicit parent authorization seam; it cannot deliver refreshable lists, owner detection or publication. Missing read projections matter as much as planned mutations.

Preserve B01-IMM-01 permanent PARTIAL, B04-Q401 UNKNOWN, FUP-B05-CANDIDATE-01 OPEN, company SSO/directory/category/model/knowledge prerequisites and HA/fencing/capacity limits. A synthetic platform fixture closes no company qualification.

## Existing implementation and harness

| Area | Existing | Needed |
|---|---|---|
| Routing | `web/src/main.ts`: root→/meetings, App, catch-all return | Meeting-ID processing/transcript/minutes/confirm/publish/read-only routes; authorized refresh/direct navigation |
| Shell | App owned/joined is local ref; import/record/group unavailable notices; no business fetch | Session/list, import launch and correct server-stage routing |
| Components | shared Icon, prototype tokens/buttons/cards/tabs/notice dialog | Import/info/people/workspace/steps/save/conflict/leave/transcript/minutes/candidate/source/final/publish/read-only |
| Transport | `shared/api/client.ts` JSON `request<T>`, cookies, Accept, AbortSignal, 204/205, generic safe errors | Typed methods, real session CSRF, idempotency/body retention, closed errors/actionable409/413/503, separate binary paths |
| Types | generated.ts already includes current identity/meeting/files | Regenerate from reviewed additions; no handwritten future success shapes |
| State | App only scope/notice | Separate saved version/draft/candidate/confirmation/task/publication, cancellation and refresh |
| Packages | Vue3.5.44/router4.6.4; no Pinia installed | Existing Vue/composables sufficient for narrow slices; do not assume architecture's Pinia is available |
| Tests | client.test.ts: 8 source-defined cases; shell.spec.ts: 4 E2E cases; visual shell: 1 case, two viewports | Business component tests plus real DB/storage E2E, distinct evidence |
| Server | Vite config only Vue plugin; Playwright HTTP127.0.0.2:4275 | Deliberate same-origin API/TLS topology for Secure host-only cookie and actual Origin/CSRF; current Vite has no API proxy/backend |

No first-flow fixture, test identity provisioning, TXT asset or first-flow.spec.ts exists in web. README is partly stale: health-only generated-contract description conflicts with current OpenAPI/generated source. Its honest unavailable-shell description remains accurate.

## Current request contracts — usable exactly as specified

All paths below prefix `/api/v1`; same-origin cookies and server-issued CSRF. Session is not login.

| Action | Exact existing contract | Obligations |
|---|---|---|
| Session | GET /session→actor{enterpriseId,status,roles},csrfToken,expiresAt | Exact enterprise ID/case/trailing spaces; roles USER/PLATFORM_ADMIN never imply meeting ownership/access; keep secrets out of persistence/logs |
| People | GET /directory/people?query=&cursor=&limit=→items[{stableId,displayName,department,active}],nextCursor | Opaque cursor; limit1–100/default20; loading/empty/failure/cancel/stale-response; same names distinct; inactive disabled; no department tree API |
| Draft | POST /meetings `{}` + Idempotency-Key→201{id,revision,stage:ENTRY} | Name/date/category/roster not required; unknown result retries original body/key |
| Saved | GET /meetings/{id}→Saved | Not list/search; no-store; owner gets pending roster draft, member only confirmed roster and null draft |
| Info | PUT /meetings/{id}/info `{expectedRevision,rosterDraftIds,name?,category?,summary?,occurredOn?,externalRosterNames?}` + key→{id,revision} | Complete value; nullable text/date; exact IDs; roster save does not grant/revoke;409 retains draft; re-read saved value after success; no name/summary product max |
| Roster | POST /meetings/{id}/roster-confirmations `{expectedRevision}` + key→{id,revision} | Explicit owner action uses current saved roster; empty legal; old ACL until commit; replay cannot reactivate old grant |
| Begin | POST /meetings/{id}/uploads `{purpose,name,size,sha256?}` + key→201{uploadId,partSize} | B06 must register authentic purpose; no guessed literal. Exact stored name; backend selection hidden from business UI |
| Part | PUT /uploads/{uploadId}/parts/{part}, binary, X-Checksum-SHA256/Content-Length→204 | Retain identical part bytes/checksum; no reslicing accepted receipts; dedicated binary sender; browser-managed header behavior needs browser verification |
| Complete | POST /uploads/{uploadId}/completion `{parts:[{part,sha256}],sha256}`→{fileId} | Retry original manifest; durable file is not processing/business-read success; no additional key currently required |
| Abort | DELETE /uploads/{uploadId}→204 | OPEN only; FINALIZING conflicts; completed business file is preserved |
| Content | GET /files/{fileId}/content optional Range→200/206 binary,416 | Fresh meeting and current business-reference authorization; UPLOAD_SESSION alone grants no business read; no provider keys/physical URLs |

Mutations require X-CSRF-Token, allowed Origin and real session. Key binds actor/resource/operation/semantic body; edited body gets new key. Unknown network/503 keeps original body/key; prevent pending double clicks. Never silently overwrite409 with a fresh revision. Parse only closed BusinessError.error.{code,message,traceId}; arbitrary backend text/cause/body must not escape. Current client lacks business-code/trace/conflict payload support.

Current409 body contains only safe error, not latest revision/diff. Retain original draft and fetch latest into separate conflict view, or freeze a richer backend conflict-read shape. Current files default FilePurposePolicy fails closed; B06.2 explicitly has no runtime importer/HTTP/worker/purpose policy.

## Backend freeze: missing browser contracts

Plan-named routes below are approved planned interfaces, not current routes. Other reads are required capabilities with unresolved wire paths/shapes; no invented generated types.

1. **Access/lifecycle.** Current Saved fields: id/revision/accessRevision/stage, nullable name/category/summary/occurredOn, confirmedRosterId/confirmedRoster, rosterDraftId/rosterDraft. No owner ID, viewer capability or governed/deleted/read-lock reason. Owner without pending draft and member may both have null rosterDraft. PLATFORM_ADMIN or roster membership cannot identify owner. Freeze authoritative role/capability/lifecycle read projection; server enforcement remains final. An injected canEdit prop is a component seam, not a browser authorization fact.
2. **List/resume.** Need authorized owned/joined list/query/pagination with saved stage/nullable actual date, meeting workbench resume projection, active task IDs, confirmation/candidate/publication discovery. Known-ID route demo alone does not satisfy save-and-return-list or full B09. Follow approved permission/filter semantics; no fake meetings.
3. **Import/capability/refresh.** Freeze TXT encoding/format/paragraph mapping, main-file purpose, ordering/association, available/validation/failure, batch freeze, completed-upload discovery, receipt/status continuation, removal of completed file association, close/cancel semantics. Current API has no upload/receipt-query/list: in-memory receipts support only same-page retry. Do not claim refresh-safe import without server manifest. B06.3 must deliver authentic original upload UUID/purpose/session/AVAILABLE/reference eligibility. First-file-name default in requirements also needs importer saved-result support: current B03 empty create returns unnamed draft.
4. **Transcript.** Planned POST /meetings/{id}/processing `{fileIds,materialSnapshotId}`→taskId, material null legal; PUT /meetings/{id}/transcript `{expectedRevision,segments}`→revision; POST /meetings/{id}/transcript-confirmations `{expectedRevision}`→confirmationId. Need authorized GET working/confirmed transcript, source/segment metadata, validity and task output. stableId/text/sourceFileId; speakerId/startMs/endMs nullable. Freeze meeting-CAS versus transcript revision-ID/number semantics and invalidation. These three actions do not imply corrections/marks/history endpoints exist.
5. **Task.** Need authenticated browser GET/SSE with cursor resume, task→typed output, terminal/failure/UNKNOWN and retry policy; meeting→task discovery on reload; cursor expiry/reconnect, bounded polling fallback, unknown acceptance lookup. Existing TaskContracts is internal queue metadata, not browser DTO. No timer percentage or readyAt success.
6. **Minutes.** Planned POST /meetings/{id}/minutes-generations `{transcriptConfirmationId,expectedRevision}`→taskId, result candidateId/inputFingerprint; POST /minutes-candidates/{id}/adoption `{expectedRevision}`. Need GET saved title/body/seven elements, candidate comparison/eligibility, explicit save and generation disposition (auto-saved/candidate-only/stale), access/revision. Frontend must not auto-adopt SUCCEEDED. First atomic auto-save only with no effective body/no human edit/no version change; existing body regeneration is candidate. How active unsaved browser editing informs the backend guard is not specified by current HTTP contracts; freeze a real protocol or conservative behavior, do not claim frontend-only concurrency safety.
7. **Human source review.** Approved v1: conclusions/actions start 待核对; owner reads original and explicitly reviews exact saved target/evidence/range/version; edits invalidate credit; insufficient support→待确认 with history. Need source preview/review reads+writes or an honestly blocked prerequisite. Pure S1 structural validation cannot paint 已核对. This crosses B07/B08/D03; minimal B09 must not bypass it merely to publish.
8. **Final.** Planned POST /meetings/{id}/final-confirmations `{expectedRevision}`→confirmationId. Need saved preview/readiness blockers, pinned versions/reviews, valid confirmation discovery/invalidation. Unsaved draft/candidate excluded. Final requires name/category; actual date/summary optional, empty roster legal.
9. **Publication/destination.** Planned POST /meetings/{id}/publications `{confirmationId,libraryId,folderId}`→publicationId; GET /publications/{id}→state. Freeze exact enum/fields, success documentID/URL, failed versus unknown/reconciling, safe retry/result lookup, meeting→publication on refresh. External key publicationId+confirmationId. Need permitted library/folder search/selection; prototype path-array is not folderId. Define root-library representation rather than invent sentinel/null IDs. Submit rechecks permissions/snapshot; UNKNOWN locks edit/fresh publish. No reliable company idempotency/query→block true publish, never invent success.
10. **Dictionary/scalars.** No category-options endpoint; nonnull category requires approved port and production fails without it. Need real dictionary projection or test-only controlled dictionary. No invented department hierarchy/enterprise categories. Current OpenAPI revisions are int64 up to9223372036854775806, generated TS number: freeze lossless handling before values above Number.MAX_SAFE_INTEGER; no silent rounding CAS or invented product revision cap.

## Page actions, components and state acceptance

| Page/component | Necessary actions | States / negatives |
|---|---|---|
| Meetings | Session/list, owned/joined, import, reopen by server stage | Loading/empty/401/403/503; actual date null/--; no admin content override |
| ImportMeetingDialog | Choose qualified TXT main file(s), hash/upload/retry/order, save-return or start with null material snapshot | Chosen/uploading/checking/available/failed/submitting; no mixed media/text batch; preserve other files and inputs; server-saved default name; no extra material-ready checkbox |
| MeetingInfoDrawer / EnterprisePeoplePicker | Edit saved name/date/category/summary/current roster draft/external display names; Add selected only commits picker→drawer; save; separate parent roster-confirm action | Independent picker selection/cancel/Escape, stale search protection, same-name IDs; no date required flag; dirty-close choices; pending/409/network retention; readonly member/published/governed; save never grants ACL |
| ProcessingPage / TaskProgress | Submit ready set, actual task progress/output, defined retry, enter transcript | Reload/leave does not cancel; unknown acceptance retains operation; no timer success while uploads/checks pending |
| TranscriptPage | Read/edit segments, explicit save, TXT paragraph source, confirm saved revision; visible combined CTA may then generate as separate guarded operation | Dirty/saving/saved/failed/conflict/confirmed/invalidated; zero corrections valid; no fake audio/speaker/timestamp; confirm success + generate failure shown independently |
| MinutesPage | Read elements/body, edit/save/autosave, source/review, regenerate, compare/keep/adopt candidate | Separate saved/draft/candidate/task; trust server first-save disposition; member sees old body until adopt; stale409 preserves manual body/candidate; AI never overwrites manual title; copy editor versus confirm/download saved |
| ConfirmationPage | Info drawer, saved preview, readiness blockers, final confirm | Missing name/category, unsaved body, pending candidate, stale transcript/review/input block; modifications invalidate confirmation |
| PublicationPage | Actual permitted destination, submit valid confirmation, progress, reconcile/safe retry, real result | Frozen backend states; unknown never becomes fresh publish; show read-only saved result before success notice |
| ReadOnlyMeetingPage | Reload authorized saved/published transcript/elements/minutes/sources | Member has no edit/confirm/generate/adopt/publish controls; outsider/admin outsider denied; revoked access fails; no draft/candidate leak |

TXT first flow does not complete recording/audio, nonempty materials, full D03/04 editing, templates/topics/feedback/export/mail or C–F. Keep honest unavailable states and Phase B gap reporting.

## Final prototype / visual acceptance

Original unchanged HTML SHA256 `ab21d0b73f4e48bbf701ee8bca5e958940b61f6e6f680d4ced02de0ce4bab867`. Static anchors: import CSS373–375/sorting392+2884–2885; drawer/people2846–2857/CSS387–390; inline transcript2997–3042/CSS400–402; minutes3057–3122 (final pgMinutes3095, save3102–3104, candidate3114–3115); final action dock3137–3139; destination3147–3158; post-success readonly3160–3167; render3179. Do not copy an early overridden function alone.

Approved semantics override simulation: date optional despite HTML2848 aria-required; drawer save2855 must not claim roster updated/grant; sessionStorage/DB.org/mock delay/readyAt are not runtime facts. Preserve visual grouping and all required semantics; do not copy fictional login, empty history handlers or mocked success.

Existing original screenshots: meetings/import/transcript/info drawer at1440×900 and1024×768. Only shell top204px has comparison baselines. New people/minutes/final/publication/read-only references require actual navigation of final unchanged original, provenance and documented semantic differences. Existing reference capture is not exhaustive B09 visual acceptance.

Visual gate: same Ubuntu24.04/pinned browser/fonts, zh-CN/Asia-Shanghai/scale1/light/reduced-motion; 1440×900,1024×768 and narrow375×812. Inspect full images plus measured modal/drawer/padding/scroll/footer/dock. Exact navy#111849, orange#FF6B1A, neutral#F6F6F3, font stack/final layouts. Focus trap/return, Escape/overlay, labels/alerts, keyboard selection, long content and visible footer actions/no page overflow. Preserve shell PNGs; new references must derive from original, never implementation or broad masks.

## Proposed implementation slices after API freeze

No task activated here; no migration version assigned. Each slice gets a fresh exact BASE and independent review.

| Slice / prerequisites | Owned files | Tests / visual acceptance |
|---|---|---|
| S0 transport/session/state; auth/error/revision freeze | shared/api/client.ts; generated.ts through generator; new shared/api/identity.ts,meetings.ts,useSession.ts; shared save/conflict/leave | Extend client.test.ts/focused tests: real CSRF, exact key/body retry, edited body new key, safe closed errors/no secrets, cancellation, revision handling; typecheck/build/check:api + existing shell |
| S1 information components; B02/B03 + explicit authorization/dictionary seam | features/meeting/MeetingInfoDrawer.vue, EnterprisePeoplePicker.vue, scoped styles and clients; test-only host outside prod entry | tests/e2e/info-components.spec.ts: clicks/focus/Escape/Add-cancel, out-of-order search, same-name IDs, inactive, null date, save vs confirm, retained409, same key/body retry, changed edit new key, readonly. Interception proves UI only. Original drawer/people images at all widths |
| S2 importer/task/transcript; actual B06/upload-reference/task/read freeze | features/capture/ImportMeetingDialog.vue, features/processing/ProcessingPage.vue, features/transcript/TranscriptPage.vue; binary/file/task/transcript clients, workspace/router | Upload bytes/hash/part replay, actual progress, TXT null fields, explicit save-confirm, failed generation handoff, dirty409/refresh. Original import/processing/transcript. Real flow only with owned DB/storage fixture |
| S3 minutes/candidate/source; B07 save/first-save/review freeze | features/minutes/MinutesPage.vue, candidate/source/review components, typed clients | First-save disposition, existing-body candidate, stale adopt, manual title, review pending/invalidation, copy-editor vs confirm-saved; API proves member sees saved adopted only. Original minutes/candidate/source |
| S4 final/publication/readonly; B08 readiness/location/state freeze | features/publication/ConfirmationPage.vue, PublicationPage.vue, ReadOnlyMeetingPage.vue; knowledge/publication clients/router | Invalid/stale final, unknown locks, stable operation retry/query, readonly before success notice, refresh and outsider/admin denial. Original confirm/destination/success/readonly |
| S5 dual-backend E2E + report | web/tests/e2e/first-flow.spec.ts, fixtures.ts, synthetic TXT; dedicated test-only backend assembler/launcher + integration Playwright config as needed; docs/acceptance/b/summary.md later | Same page actions/assertions in independently owned local and s3; four sessions, real API/DB/storage/worker, controlled external providers explicitly labeled; no business route.fulfill success; fresh evidence/counts/screenshots |

S0/S1 independently useful, never full B09. Adopt reviewed contracts/generated types before parallel feature writers. Single integrator owns App/router/global style/shared client to avoid incompatible state models.

## Controlled E2E fixture plan and matrix

Current source assets are building blocks, not turnkey browser fixtures. MeetingRevisionIT shows real HTTP+MySQL session setup; FileRegistryHttpIT shows secure-session hash/CSRF/Origin; IdentityAccessIT proves roles/revocation but clears shared tables, so do not reuse its reset pattern. Storage/worker/restart fixtures exist; no Playwright first-flow provisioning exists. Never run old resetting fixtures against shared state.

Root owns/approves a bounded fixture at activation: unique run ID, dedicated DB/storage namespace/ports; four independent ACTIVE owner/member/outsider/platform-admin sessions; controlled directory/category/model/knowledge test assemblers; synthetic ordered UTF-8 TXT with paragraph sources/null timestamps/speakers. Provision hashed sessions in test backend/DB harness, not production login/account-switcher. Keep tokens/CSRF/credentials out of logs/screenshots/browser persisted storage. TLS/same-origin must exercise actual Secure HttpOnly host-only SameSite=Lax cookie and Origin/CSRF, not arbitrary request-cookie bypass. Production remains company mode/fail closed.

Actual full-flow uses real controllers/transactions/MySQL/registered file references/worker TXT parsing/storage/reviewed transitions. Only explicitly identified external test providers may be controlled. route.fulfill business-success fixtures belong to component/error tests, not first-flow. S3 is a bounded approved actual test service/version, not memory fake; local works with no S3 service/credentials. Fault injection belongs to test transport/providers, never production UI debug switches.

| Scenario — both local/s3 unless inapplicable | Evidence |
|---|---|
| Owner full chain | Actual page clicks: TXT→durable file→processing→saved transcript→confirm→generate. Guarded first auto-save if eligible; regenerate→candidate→explicit adopt→human review prerequisites→final confirm→controlled publish. publication-state exactly 已发布 only from server success |
| Refresh/restart | Reload after task accept/publish; discover IDs/state from authorized server, no reimport/recreate/republish. Bounded process restart verifies persistence, not browser cache |
| Unknown commit / retry | Drop post-commit or external-success response; same body/key or same-publication reconciliation; one effect/document; changed body+old key409; no fresh key blind retry |
| Failure | Import/save/generate/publish preserves inputs/other files/current body, actionable reason/safe retry; no timers changing failure to ready |
| Stale revision | Two owner windows, changed revision: info/transcript/body/adopt/final conflict retains typed draft and current saved body |
| Member | Explicit roster confirmation grants read; candidate not adopted→old saved body; no edit/confirm/generate/adopt/publish buttons; direct writes403 including reload/published |
| Outsider | Known meeting/file/task/candidate/publication IDs grant nothing; direct route/API403 without content |
| Admin outsider | PLATFORM_ADMIN never content override; identical content negative assertions; no prod role switcher |
| Revocation | Next read/reload/write rejects expired/revoked/disabled identity; no continued draft/candidate leak or fake success |
| Null/text-only | Date blank/--, empty summary/roster legal, no mandatory date/fake speaker/time/player; category/name only required at final |
| Final / publication negative | Dirty/unconfirmed/stale/pending-candidate/missing-name/category/unreviewed blocks; UNKNOWN locks new effect/edit; safe retry retains stable publication identity |

Record each backend separately: code/API/fixture version, actual storage/provider identities, company-real vs controlled adapter labels, commands/fresh counts/screenshots, nonsecret task/confirmation/publication IDs and external-create count. Do not add historical Java counts together. Controlled success proves demonstrable integration; real company qualification remains separate.

## Tool paths / replay feasibility — public source and docs only

Configured Linux root `/home/tanren/ama`. Node `/home/tanren/ama/.tools/node-v22.23.4-linux-x64/bin/node`, npm sibling, pins22.23.4/10.9.9 from tools/toolchain-lock.json/bootstrap.py/package.json. Bootstrap supports Linux/WSL x86_64, verifies archive SHA, writes .tools/env.sh to set JDK/Node bins. This preflight did not read generated/private env or execute/probe tools.

Runner `/home/tanren/ama/web/node_modules/.bin/playwright`; @playwright/test1.58.2; Chromium145.0.7632.6/revision1208 from committed provenance/pins. Actual installed executable/browser cache not established. Do not substitute unrelated Windows/bundled browser/Node and claim original visual environment. Root may resolve configured Linux executable paths under approved execution later.

Documented replay commands, NOT run here:

```sh
. .tools/env.sh
npm --prefix web ci --ignore-scripts
npm --prefix web exec playwright install chromium
python3 tools/checks/run.py web client.test.ts
python3 tools/checks/run.py e2e shell.spec.ts
python3 tools/checks/run.py visual shell.spec.ts
npm --prefix web run typecheck
npm --prefix web run build
npm --prefix web run check:api
# only after actual spec and owned fixture exist
python3 tools/checks/run.py e2e first-flow.spec.ts
```

run.py checks selector matches and fresh JUnit, rejects zero/fail/skips; launches .bin/playwright from web using Vite config/output artifacts/web. first-flow.spec.ts absent would fail discovery. Current runner/config does not orchestrate local/s3 DB/API/worker services. Provide distinct integration topology rather than equating Vite with complete E2E. `all` excludes browser/visual; `contract` only registers OpenApi, not arbitrary planned company names.

API generation `npm --prefix web run generate:api`; check:api byte-compares temp generation; root check:openapi lints/bundles. New HTTP types need real backend behavior and these checks before UI adoption. Playwright updateSnapshots:none, zero diff/threshold/retries, two viewports, forbidOnly and fresh JUnit support strict evidence. Do not weaken them. Original capture writes four reference states, explicit --refresh-reference required; preserve outputs, add future references under review, never implementation-derived baseline.

## Root readiness decision

Implement only with fresh exact BASE and reviewed backend API/read model for the chosen slice. Isolated S1 is feasible with injected test-only clients; full B09 depends on B06 real import/output, B07 persisted first-save/candidate/review, B08 snapshot/publication, and refresh/destination/access projections. Unresolved work is wire/read/runtime design and company qualification, not reconfirmation of already approved optional dates, save-vs-roster authorization, candidate/first-save/source-review rules. This report proposes no new company protocol or product limits.
