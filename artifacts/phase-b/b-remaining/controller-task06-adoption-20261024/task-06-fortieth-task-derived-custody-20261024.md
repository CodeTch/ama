# Task6 fortieth task — retrospective read-only mapping

This is a derived custody clarification written after delivery, not a historical HTTP receipt or a test rerun. No public historical receipt, report, product, HEAD or index was changed.

Observed UTC: 2026-10-23T21:07:48.749213+00:00. Delivery HEAD aa490afc2690e9517a691cc8d1ae9487a025806d.

The 39 distinct accepted-*.json task IDs cover 39 original helper calls. The fortieth task is the additional direct HTTP intake in `Task06SavedCommandsIT.newIntakeClearsPointerThenNewConfirmationReattachesOriginalIdentity`, line28. That direct call intentionally bypasses fixture.accept(), so no per-task accepted JSON was written. The preserved source asserts HTTP202, obtains taskId from its actual HTTP response, executes the registered Worker loop, and asserts that exact task SUCCEEDED. Lines29–30 assert old replay does not reattach and fresh confirmation reuses the same immutable ID with meetingrevision5.

Exact retrospective existing-row observation from the retained MySQL, using SELECT only and exact owner IDs collected from the39 public receipts, excluding those39 exact task UUIDs, returned exactly one row:

- task: `7d9892f4-289e-49ea-b71a-e05eee010d87`
- owner: `task06-2a48fc23-8df8-411a-bb3b-83e8e755bcfe`
- meeting: `4cf40013-c9e9-4249-a931-9961fe740dcb`
- input: `0f9eb0a7-0041-460a-bdd8-38f5b53cec34`
- state: `SUCCEEDED`
- RAW output: `1ec41b93-fa08-4eb0-a50a-ea732028d206`
- business version: `4`
- original processing version: `b06.controlled.import-txt.1`

The same exact owner/meeting already has the original helper-accepted task `c9809505-7abb-46ad-93e4-b8c18da4989f`, recorded at `artifacts/phase-b/b-remaining/task-06/pipeline-fe0eee9d-4d9f-43f6-b56e-d779ee2ebf17/accepted-c9809505-7abb-46ad-93e4-b8c18da4989f.json` (SHA256 854576bd89fbb1a09bdabb3506b98588d4f839e01c5a5699f9e879253ac75be9). This identifies the controlled pipeline `pipeline-fe0eee9d-4d9f-43f6-b56e-d779ee2ebf17`; its resource.json retains the same owner. The additional direct request has Idempotency-Key `next-input`, expectedRevisionToken3 and an uploaded `next source` file per the unchanged source, not a newly reconstructed request receipt.

Public source: `backend/integration-tests/src/test/java/ama/storage/Task06SavedCommandsIT.java`, SHA256 10911b1882b1c335430e574ada43743958d7f81fbf1986fa9c357e14be26e6ac, final saved-final-it-01/source.json seal matches. Public XML: `artifacts/phase-b/b-remaining/task-06/saved-final-it-01/integration-tests-TEST-ama.storage.Task06SavedCommandsIT.xml`, SHA256 3e89669cef1e67ccfff6b667d45e412807583d3f6a44ab3669a3f6e7584590a4. XML suite has tests=2, failures=0, errors=0, skipped=0; the named testcase has no failure/error/skip. The test method was newly included in this final16-case run. Therefore custody precision is **39 historical accepted JSON task IDs + 1 retrospective exact-owned DB/source/XML mapping**, not40 historical accepted receipts.
