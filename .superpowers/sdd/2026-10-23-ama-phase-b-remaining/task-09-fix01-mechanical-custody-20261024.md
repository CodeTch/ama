# Task 09 fix01 mechanical custody

Incremental base 0e22b812000f2efa00c2ef8ed66e06d8254beab5; committed HEAD 591545a4a1a4ce27edea54baa94e9d11666807b7. Only the fix01 delta and commit tree were audited. The initial 165 windows, 20,557 protected inputs, and prior 96,140 raw files were not rescanned. No test, build, SQL, service, migration, private-value read, or secret scan was run.

## Results

9 result windows, 14 fresh XML, 39 case observations, 25 distinct case keys. Mechanical issue windows: 0.

| Window | Exit | T/F/E/S | XML | Outcome |
| --- | ---: | ---: | ---: | --- |
| denied-green-02 | 0 | 1/0/0/0 | 1 | PASS |
| integration-green-01 | 1 | 6/1/0/0 | 6 | ASSERTION_FAIL |
| openapi-green-01 | 0 | 0/0/0/0 | 0 | PASS |
| provenance-red-01 | 1 | 0/0/0/0 | 0 | COMPILE_OR_SETUP |
| provenance-red-02 | 1 | 2/2/0/0 | 2 | ASSERTION_FAIL |
| store-regression-01 | 0 | 8/0/0/0 | 1 | PASS |
| unit-green-01 | 0 | 11/0/0/0 | 2 | PASS |
| unit-red-01 | 1 | 7/3/0/0 | 1 | ASSERTION_FAIL |
| wire-red-01 | 1 | 4/2/0/0 | 1 | ASSERTION_FAIL |

integration-green-01 remains 6 tests: 5 PASS and 1 FAIL. denied-green-02 is a fresh 1 PASS. The OpenAPI window has zero fresh XML; its one stale report was marked qualified=false and excluded. The failed and compilation windows remain in the JSON without relabeling.

## Original fact and source closure

Original-fact custody: 7 listed files, 0 hash issues. Before and settlement receipt retain the same task, execution, operation, and reservation IDs: True. Before was unsettled; later receipt reports settled result, zero unsettled, and no lookup or POST.

All 22 own paths match current SHA-256: 0 issues. Five closure windows share 274 main-source paths; declared window comparisons match: True. unit-green-01 and integration-green-01 differ from final only in Task09DeniedLookupProcess.java test source.

## Exact delta, scan receipt, and commit

Raw manifest 6316 entries; path coverage issues 0, size issues 0. Scanner covers 6317 raw files, including the raw manifest and excluding its own receipt. Pre-receipt stage 6341 paths; commit delta 6342 paths; exact set match True. Parent/base, HEAD, index, and committed receipt blob checks passed. No private .tools paths in delta.

Scanner script hash matches receipt: True. Static review confirms root depth, raw recursion, cached staged paths, staged blob batch reads, PEM pattern, and in-memory receipt check. Receipt reports known 6 plus provider 2 equals 8 unique values, exact hits 0, PEM hits 0; 838 unique pre-receipt staged blobs match the commit tree. Private values were not read or scanned again.

Raw manifest path and size coverage was checked; all 6,316 raw per-file SHA values were not independently rehashed. New windows, original-fact custody files, and own source paths were hashed.
