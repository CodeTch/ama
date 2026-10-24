# Final review lower-bound ruling — 2026-10-24

Ruling: the final fresh whole-development review must use canonical initial source BASE `34cb226aefe99c67d3681af68d748b0aeea4e073` through the final adopted development HEAD for product/test/contract/schema changes, because main was independently integrated during this plan. Computing a new merge-base with currentmain can omit Task1–4 already present there. This does not authorize a merge or main mutation. Cost ifwrong: extra reviewer context, bounded by source-only package and authoritative evidence indices.

For same Task5fix01, FIX_BASE remains857102b (head the original source review saw); f989805 is a pure record/archive descendant. Package all actual fix source/test/POM changes over857..finalHEAD and include commit list/stat, author report/evidence manifests plus explicit purecheckpoint disposition. Never useHEAD~1. Artifact/log bytes remain custody evidence referenced separately; do not bury the actual fix in archival/raw diff expansion. Reviewer receives exactly scoped originalT5SR01/02/CV1 plus newbreakage check, not a new fullTask5 review.

This is packaging guidance, not a test/source qualification. Task13 still waits for all required implementations and final affected gates.
