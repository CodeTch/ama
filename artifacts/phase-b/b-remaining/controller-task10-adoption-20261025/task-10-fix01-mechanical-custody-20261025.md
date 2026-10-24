# Task10 fix01 mechanical custody

Initial window freeze used base 5657d24a4a58916dfca14a29e352ad361dda3319; final fix commit is verified below. Six completed result windows: capacity-red-02, final-affected-01, focused-green-01, focused-red-01, legacy-io-final-01, pure-final-01. Fresh XML 16, case observations 45, distinct class#method keys 39, keys with whole-window qualified PASS 39. Window qualifications: {"assertion-failure": 2, "qualified-pass": 4}. Result totals preserve 3 FAIL and 0 ERROR across runs; repeated cases do not increase distinct coverage.

All command↔source-manifest, 4518 captured source snapshot entries (across source versions), report-custody closed times/hashes, and result↔fresh XML counts/case identities checked. Mechanical issues: 0. Source versions are preserved per window.

Disposition supplied by coordinator: focused-red-01 contains genuine I1 RED while its I2 assertion used tasks4 as a 256-attempt oracle and is not a qualified I2 RED; capacity-red-02 is the corrected I2 genuine RED. Focused/affected/legacy/pure PASS windows are indexed by their actual class#method keys. The two original UNKNOWN facts, Task9 F5 and Task11 M1 remain open. B-FINAL-REPLAY-INTEGRITY-01 mixed PROFILE risk remains for Task13. This index does not make a product or SPEC verdict.

Scope: only Task10-fix01 sealed result windows. Earlier 58 windows, protected tree, private values, DB and raw logs were not rescanned. Commit path/scope verification follows below and is complete.

## Fixed commit limited delta

HEAD 86777940c3f9500b1b89d884deaae2a48740d9a9 (BASE 5657d24a4a58916dfca14a29e352ad361dda3319): 4726 changed paths. Exactly 4724 inventoried payload paths match the final commit tree by path/mode/blob OID/byte size; the two self attestations match their own committed blobs separately. Index empty; 29 own product/test paths match committed byte length and SHA-256 and are clean in the current checkout. Source-window-map matches the previously indexed six results/source manifests/counts/exit. W/A author reports share SHA-256 d01c0bc777a4a3b91330fa6dc78d3621eea24b5197d09d26af40e13f0a41eb48.

Raw membership lists 4694 paths, including 4692 raw-manifest entries; path/committed size closure matches. The overlapping 4692 path/size/SHA fields agree between author raw manifest and scan membership. This audit did not independently recompute each raw byte SHA. Scanner script hash and exact membership binding match the receipt; reported scan scope is 4694 raw files, 4724 staged payload paths, 941 unique payload blobs, 43415865 bytes including overlap, with zero reported private-value/PEM hits. Receipt and inventory are the two bounded self attestations. No private values were read or secret scan rerun.

Protected receipt points to current adopted-input manifest and reports 123051 checked paths, zero mismatch. No protected tree or DB rescan. Original two UNKNOWN, Task9 F5, Task11 M1 and Task13 mixed PROFILE risk remain open. Mechanical issues: none.

The own-stage-pathspec lists the 4724 payload paths; the two attestations are intentionally separate. The raw manifest excludes its own file and own-stage-pathspec as generated inputs; both appear in the 4694-file scan membership. These exclusions matched the final commit.
Nine named manifests, receipts, scanner and W/A report files also matched their committed blob IDs byte for byte in the current checkout.
