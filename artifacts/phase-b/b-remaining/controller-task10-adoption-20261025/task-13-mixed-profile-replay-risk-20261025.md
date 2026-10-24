# Task13 mandatory review input: inherited mixed PROFILE replay risk

Tracking ID: **B-FINAL-REPLAY-INTEGRITY-01 (OPEN, mandatory Task13 triage)**. Distinct from Task9 F5 extra preparation F. Identified during Task10 fix1 after new raw-byte ARCHIVE_IO_UNAVAILABLE was separated from PROFILE_UNAVAILABLE integrity failures. Root requires independent final source triage; current Task10 fix1 does not silently expand into rewriting these inherited protocols.

Four existing callers in `backend/adapters/src/main/java/ama/transcript/`:
- `JdbcSavedTranscripts.java:53,59,92-93`, `ordinaryPreparationFailure`: S acquisition or A failure then F concurrent accepted winner.
- `JdbcMinutesInitialSettings.java:64`, `ordinary`: initial prepare or later A failure then authenticated concurrent accepted lookup.
- `JdbcMinutesGenerationCommands.java:22`, `ordinary`: preparation.prepare failure then accepted lookup.
- `JdbcProcessingIntake.java:76`, MinutesContracts.Failure catch around preparer/pending work then accepted lookup.

All receive the newly named ordinary raw-byte IO code to preserve actual ordinary unavailable/replay compatibility. However the inherited classifiers still treat PROFILE as ordinary. ControlledConfigArchiveSource SHA/structure/dependency and ConfigBindings integrity failures can share that old PROFILE code. If a genuine initial accepted lookup misses, such a nonordinary integrity failure occurs, and a same-key winner commits concurrently, the old recovery branch may turn the observed integrity failure into successful replay. This is a specifically located potential source risk, not a claim that each full old path has already been behaviorally reproduced. Initial already-accepted receipt replay is different and remains valid; auth/cancellation/mismatch precedence must still be examined.

Task13/final whole-branch reviewer must trace each actual reachable failure origin and classifier at the final source, determine severity and require correction or a documented technically grounded disposition. Earlier Task4/6/9 adoption or source inheritance does not waive the issue. Do not mark the risk closed merely because new IO compatibility tests pass, or because Task10 new commands use a precise classifier. Do not conflate it with F5. This item must appear in Task13 brief and final closure; no generic new matrix or unrelated error-refactor is required now.

Locations were read directly from the current fix1 working source on2026-10-25 Asia/Shanghai; final reviewer must remap to final commit if lines move. SavedTranscripts185/187 are concrete PROFILE integrity-origin examples (original index and closure cardinality). The identified four classifier lines explicitly still include PROFILE; GenerationCommands22 andProcessingIntake76 also include PROVENANCE, which final source triage must interpret against actual callers rather than assuming every broad unavailable code is ordinary. This is not a new current-wave fix request.
