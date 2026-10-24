# Task6fix01 indexed-input implementation and controlled migration brief

Read first: this supplements the existing task-06-fix-01-brief-20261024.md and reviewed index design/plan. Same Task6 soleauthor and current fixround1; no new task, no subagents. Root owns central records; all original443 public artifacts and original W report prefix remain frozen.

## Concrete reviewed and allocated inputs

- Design task-06-input-index-supplement-design-20261024.md SHA12e6ee018b84ce6055e76f13181b830d59d3485af11f8c6a409abbb334ce735f; plan cf0ccc424e6c42ac466cb7a2fb5e566e26ab6220e442a98b0b8986474136a1d0.
- Independent review task-06-input-index-design-review-20261024.md SHA8e8bdfd7be35fd7f527de1acbce086f76cfddb0841f168a9cdca7a56aa2a66dd: SPEC PASS/Approved/0findings/exact75BSQL PASS. Read its material conditions, especially fresh post-gate default-isolation view and actual registered-writer ownership.
- Root formal freeze task-06-input-index-freeze-20261024.json SHAd40844b3e34c648040a290ce6380e76009bdb381ad0edbab1ff30cac62c4ee3d.
- Separate fresh exactselected-target application ACK task-06-input-index-root-application-ack-20261024.json SHA0b7096045651bbf3a2ac9420369f045d9d075c6cd7135ec5a53d3e01784847a2. Both retainedCID selected owner/image/mount/limits/ports exactsame. This now authorizes only the described controlled local application; no further user confirmation required.
- Formal new resource backend/adapters/src/main/resources/db/migration/V013_2__task_input_lookup.sql, literalversion013.2. Full body from W/task-06-input-index-frozen-013_2-20261024.sql: 75bytes SHA11adae7d1d6b46bc7918692593abfbedf84c57a1d79dbe61f46a2c345d68878f, no added header/BOM/whitespace. Copy only these bytes.
- Full actual preallocation13history/immutableSQL/resolvedjars in task-06-input-index-root-preallocation-facts-20261024.json SHAca08e945eb463b7bebfd23452b3f8e24bff171f17ea6186559726fcec78c721d. Carry actual Flyway12.4.0 ordering proof only while jarhashunchanged. Task8 old013.2candidate wasunassigned and is superseded by this formalallocation.

## Application / preservation protocol

You confirmed quiescence/no live tests or DDL. Keep all owned API/Worker/test writers and controlled transactions quiescent throughout full pre-capture, apply, preservation capture, normalrerun0 and finalpreservation capture. Historical182active rows are protected data, never cleanup. Use only existing privateenv/PKI/ownedresources. No new DB/container/credential, no reset/repair/clean/outOfOrder.

Prepare bounded actual-driver/Flyway probe/test in your own source/evidence lane. Before copy/build/apply, retain full13history exactfields and complete physicalschema metadata (columns/index keyparts+directions/checks/FKs/options/SHOWCREATE where useful), plus deterministic complete per-table row counts and SHA256 of all actual oldcolumns/rows. UTC timestamps, NULL distinguished from every byte string, binary/JSON/text unambiguously encoded; order by complete PK or deterministic complete encoded row preserving duplicates. Stream data and store public nonsecret hashes/counts only, keep any necessary raw bodies private. Include allocator/next_id tables. Explain serialization algorithm and prove before/after covers the same tables/columns; no sample-as-complete claim. flyway_schema_history after includes the sole expected addition: preserve exactold13 prefix separately rather than claiming whole tablehashsame.

Retain real missing-index failclosed/schema assertion evidence before applying, clearly labeled actual behavior versus setup; no fabricated TDD credit. Apply exactnewbody with normalFlyway currentresolvedclasspath to exactexisting target. Session lock_wait_timeout=5 seconds bounds metadata-lock wait; observe actualduration/result. Bound test/worker JVM512MiB2CPU or standaloneprobe64MiB2CPU; never plain unbounded javap/java.

Firstapply must execute exactly1; retain actual full14history, old13 allfieldsidentical, newsuccessliteral013.2/script/checksum; exactvisible nonuniqueBTREE index has ascending(input_kind,input_id,id), no prefixes/expressions. Oldphysicalmetadata identical after subtracting onlythese3newindexparts; all oldbusinessdata/allallocator hashes/counts same. NormalFlyway rerun executes0, fullhistory14/schema/businessdata stable. On partialfailure preserve actualstate/alllogs and reportroot; no speculative retry/repair. Once these checks pass report bounded summary plus proofpath before any final installedrun.

## Indexed source / directed qualification

Implement the approved complete typed-input ID-only FORCE INDEX lookup with equality, ORDER BY id, maxTasks+1 sentinel and pre-DTO row/UUID/copy reservations. UnionactualproducerID, deduplicate/sort, exact admitted PK metadata locks, existing exactoneproducer and provenance checks. Reject surplus/foreign IDs before unnecessary foreignmetadata/content. No globalOR/taskPRIMARYtraversal, no subjectnarrowing or unrelatedhistory charge. Preserve fresh post-gate S/F view ordering and same-held-root complete set recheck; no late lower-rank expansion. Missingindex fails closed, no fallback.

Directed cases and actual SQL/parameters/connection traces: foreignsubject sameinput extra rejected;65unrelatedsameSubject notcharged;65relevant sentinel beforebodytransfer; wrong/missingproducer; bothdefault/READCOMMITTED membership/writeblocking; actualtyped-indexEXPLAIN/no filesort and exact sortedtaskPKlocking. Optimizer rows are estimates. ExactnewstructuralprobeIDs + zeroattempt/output/externalreferences + exactrestore1 receipt required; no historical backfill. Existing provenance04RED05GREEN retained as historicalunqualified-globalOR windows.

After migration proof + indexed-directed meaningfulGREEN, request additivequalification with exactcurrentaffectedsourcebytecopies/delta, new14history/schema, actualsourcewindow/SQL proof, and recipecarried by unchangedsource or actualobserved, explicitwhich. Finalcovering/inherited/allunits waits root newpin. Continue already-prepared independentCV03 evidence/docs meanwhile, no rerun merely forformat. Update only new/fix Task6 guards toactual14; all13 priorSQL immutable.

## Delivery / following work

Finalcovering → inheritedforwardcopiesonce (10prepared with exactsource/diff/assertions/exclusions; no originalfullclasspassclaim) → allunitsonce on stablefinalsource/schema. Preserve all failed/setup windows and actualsourcewindows withoutsummingoverlap. Final fourprivate+PEM scan/443original+protectedpreservation/process/selectedresource/ownchaincustody, append W/task-06-report.md exactoldprefix and create newpublicfixreport+manifest, selfreview and owncommit exactownpaths. Do notstage root fourcentraldirtyfiles or any root W protocolrecords. Report DONE onlyafterfullcontract; independent originalreview scopedre-review and rootadoption remain.

Task8immediatelynext owns pending current-model sync for this index plus its Operation column/index; you do notmodify current fourmodel/generator files. Root will amend its narrowownership and allocate Task8 migration fromactualpost6history. No browser/company/futureTask10/11/capacityqualification is implied.
