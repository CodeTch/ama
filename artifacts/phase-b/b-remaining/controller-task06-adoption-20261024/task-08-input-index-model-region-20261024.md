# Task 8 input-index model region map — 2026-10-24

READONLY ownership mapping for the approved `durable_task` nonunique `task_input_lookup(input_kind,input_id,id)` index. Task 6 remains soleauthor; Task 8 immediately next owns synchronizing this index together with its existing `external_operation` delta.

## Exact baseline comparison

The four inspected files match `80c878f52c7b748efdcdb64e707d50cf410843b4` byte for byte. SHA-256 and byte lengths are recorded in `task-08-input-index-model-region-manifest-20261024.json`.

## Precise owned regions

- `tools/checks/database_design.py:87-88`: `durable_task` table declaration and its `indexes=[...]` list. Add `idx('task_input_lookup','input_kind,input_id,id', <reason>)` there. The same source owns SQL/dictionary generation at `448-485`; no separate hand-edited generated region is authorized.
- `tools/checks/database_design.py:487-504`: ER generation. It emits PK/FK marks and FK edges only, with no secondary-index representation. **No ER region change for this index-only delta.** Keep `durable_task` entity/field lines `523-545` and its FK edge at `1284` unchanged.
- `docs/database/ama-schema-draft.sql:320-351`: generated `durable_task` DDL. Existing index declarations are `344-347`; the generated index belongs in this entity block after generation. No other SQL region is implicated by the index-only delta.
- `docs/database/ama-data-dictionary.md:542-581`: generated `durable_task` dictionary entry. Existing index bullets are `572-575`; generator output should add one nonunique index bullet in this block.
- `docs/superpowers/specs/2026-10-21-ama-database-design.md:523-545` and `1284`: ER entity and relation regions contain fields with PK/FK annotations and FK edges, not secondary-index annotations. **No design/ER region change for this index-only delta.**

## Static index count

Read-only source inspection (no generator import/execution) finds 98 table declarations/primary-key definitions and 89 explicit secondary `idx(...)` definitions in the generated SQL/source model, for 187 total current index definitions including each primary key. The approved lookup index raises the secondary count to 90 and total to 188. Task 8's already-approved `external_operation` unique index adds one more, yielding the Task 6 expected combined total of 189. Existing checks and FKs are outside these index regions.

## Ownership boundary

This is an advisory region map only. Task 8 should first preserve the exact four blobs under its already-required preservation procedure, then synchronize only the generator-backed `durable_task` index region and its generated SQL/dictionary output, plus its separately ruled `external_operation` delta. The ER and design ER regions need no index-only edits. No source was edited and no tests, builds, databases, containers, private environments, or Git mutations were used for this mapping.
