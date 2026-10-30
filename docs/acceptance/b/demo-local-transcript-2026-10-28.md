# Local Demo meeting and TXT slice — 2026-10-28

This records current local development evidence for DEMO-01/02 under R2. **M1-Demo is not accepted and Phase B remains INCOMPLETE.** DEMO-01 has functional browser evidence, with prototype differences still listed below. The new local runtime uses the completion worktree's own `ama-local-dev-5c92c4c3a416` MySQL and `.tools/local-app/`; it does not restart the settled B-L5 batch.

## Current behavior and evidence

- Local login smoke passed with real Secure/HttpOnly sessions, exact Origin, account isolation and CSRF protection. Meeting create, information save, real directory search, roster draft save and separate confirmation were exercised in Chromium at 1440×900; saved information and drawer behavior were checked at 1024×768.
- The launcher now starts existing FileServiceMain through loopback mTLS, with a persistent managed backend identity, kernel mount/volume/active-role checks. An explicit offline development setup publishes the existing TXT configuration/profile bytes through ControlledArchiveReleaseWriter; it does not insert a processing result.
- The existing independent TranscriptWorkerMain reports INSTALLED via its authenticated readiness probe. `local_transcript_smoke.mjs` first failed because intake was unavailable; after wiring it passes two actual file uploads in order, real Worker completion, RAW→WORKING restoration, body/KEY mark save, confirmation and browser refresh.
- A valid first file plus invalid UTF-8 second file yields FAILED and an empty transcript history. It does not publish a partial RAW.
- All four owned processes and MySQL were stopped normally; `status` reported all processes false. After `start`, the same browser session read exactly the same saved/confirmation IDs, text, marks, source identities and both original file bytes. Only the independent API boot qualification changed, as expected. This proves normal local stop/reopen for this slice, not in-flight crash or full model/publication recovery.
- The list Import action uses the original durable create flow, verifies route arrival, then opens the upload modal. Its query intent is consumed so refresh does not keep reopening the modal. Existing uncertain create/upload/import commands retain their recovery paths.

Runtime evidence is retained under `.tools/local-app/`: `transcript-red01.log`, `transcript02.log`, `transcript03.log`, `transcript05.log`, `transcript07.log`, `transcript-smoke/report.json`, `transcript-restart02.log`, browser snapshots and service logs. Early selector, response-body inspection, incomplete Vue markup, import-query and overbroad boot-response comparison failures remain in their original logs. They are not counted as successful runs. The response-body inspection issue was handled by observing the validated UI ACK/task ID; the actual task and output were read separately.

## Prototype inspection and limits

Reference is the original `designDocs/智能会议助手1021版.html` after all overrides. The list → import modal and long-transcript workbench → information drawer were entered using their actual visible controls. Original reference files remain unchanged. Current and reference screenshots for 1440×900 and 1024×768 are in `.tools/local-app/transcript-smoke/`; established A-05 captures remain indexed in `docs/acceptance/a/prototype-index.md`.

| State | Current result | Remaining differences / permitted data substitution |
| --- | --- | --- |
| Shared list header/actions/scope | Existing two viewport zero-pixel baseline passes without updating reference PNGs | Actual owned/joined meeting data differs from fictional reference; full list fields/filter treatment still requires final disposition |
| Information drawer | Right side, native modal behavior, actual saved form and separate roster confirmation | Additional draft/recovery controls express existing safety rules; complete field grouping/button placement fidelity still needs final comparison |
| Import | Centered modal with file queue, ordering, retry/recovery, real intake and explicit background-material boundary | TXT only for this slice; no fake audio/video/material success. Extra recovery controls differ from static prototype. Final file-area/CTA layout comparison remains open |
| Transcript | Separate context column and transcript workspace, actual RAW/history/WORKING save/marks/confirmation | No audio input means no fabricated player/timestamps/speakers. Final editing density and navigation/progress alignment remain open; this is not full pixel acceptance |
| Generation/review/publication | Existing components retained behind workspace tab | Local provider assembly and actual browser acceptance are DEMO-03/04 next; current unavailable responses remain honest |

Native information and import dialogs were checked at both sizes for visible controls, responsive bounds, Escape close and modal keyboard focus. Browser chrome can receive Tab; background page controls remain inert while the native dialog is open. Switching workspace panels hides components without discarding their editor instances.

## Current verification

- Web unit suite: 242/242 passing; typecheck and production build pass; OpenAPI generated types match byte for byte.
- Local launcher suite: 7/7 passing, including source change detection and owned-process identity handling.
- Browser shell behavior: 8 cases passing across configured viewports; original shared-region visual baseline: 2 cases passing with zero pixels changed.
- Real TXT success/failure and normal stop/restart checks passed as scoped above. Independent source review is recorded separately under the execution evidence directory; its result is not inferred from this report.

Open B-R01/02/04/11/14 obligations receive only this scoped evidence. Full local chain, member/provider document ACL, model/review/final/publication workflow, representative IME/conflict checks, remaining prototype decisions and deep B-QUAL proofs remain required. Actual company model/knowledge capability is not proven by this development setup.
