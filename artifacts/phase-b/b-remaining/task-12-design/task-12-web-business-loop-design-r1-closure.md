# Task12 design R1 closure — T12DR-01

2026-10-23. Disposition: ADDRESSED in prospective design, pending root packaging and fresh scoped independent review. This is not self-adoption, source correctness, runtime evidence or Phase B qualification. Replacement author resumed the exact fix brief; no subagents. Only `task-12-web-business-loop-design-r1.md` and this closure were written. Original design and review remain byte-identical.

## Finding and exact repair

T12DR-01 is technically confirmed. V008 stores meeting name/summary and roster display name as LONGTEXT; the existing B03 saved reader selects the entire info row before returning a smaller projection. A 50-row page cannot bound that transfer. R1 section2.1 assigns Task12's proposed `ama.meeting.JdbcWebMeetingProjectionReader` and the actual `MeetingConfiguration` / B03 repository-access seam, identifies status-only authentic prepared gate APIs, and forbids composing the unbounded saved helper or nesting standalone producer readers. It preserves actual producer ownership and requires final source/facade/rank reconciliation before activation.

The added protocol covers bounded ID-only discovery; fresh genuine authorization; retained issuer/DataSource/holder/root/parent/row protection and real V008 parent FKs; exact complete keysets including empty rosters; cumulative UTF8/scalar/UTF16/metadata/copy/driver/escaped-response reservation; exact protected text selection and repeated/final acquisitions. It distinguishes DB-only D from producer S→A→F without a second gate, and explicitly handles category-version acquisition/immutable snapshot loading. Missing capability/protection and overflow fail before affected content transfer; successful content remains exact with no invented product name quota.

Section9 item4 adds prospective new-reader actual-MySQL statement/holder/reservation instrumentation for oversized saved names, summary, display/category labels; cumulative escape/copy exhaustion; length-check-versus-fetch and child/pointer/version races, empty sets, revocation, repeated/final reads, and untruncated Unicode positives. Category SQL acquisition is tested through an owned MySQL-backed dictionary adapter using the new metadata/read contract; this does not assert an existing production dictionary table. A selected immutable/archive provider also needs its actual initial-acquisition proof. No test has run.

Direct consistency changes: title/introduction mark R1 and its real activity boundary; section1 and section10 reflect root-supplied Task4 commit59c8b080 under review and Task11 R1 reviewed design awaiting root adoption/source/DDL/runtime integration. Original behavior, UX, authority, routes and all unrelated obligations remain unchanged.

## Reads and focused source verification

Read the exact fix brief first, then all 175 original design lines and all 50 review lines. Initial combined output was truncated; bounded rereads covered design1–64,65–125,126–175 and the complete review without truncation. Read Task6 R1 sections4.2–4.7 (112–175) and Task10 sections2–3 (within20–85), controlled-runtime1–55, and these public source excerpts:

- `V008__meeting_schema.sql`:52–105 and all matching FK definitions148–162: actual LONGTEXT columns and parent/child relationships.
- `JdbcMeetingRepository.java`:1–185: read130–144 and members/confirmedMembers; no pre-transfer content reservation in that existing read.
- `JdbcMeetingAccess.java`:1–210: status-only capture versus name-reading path, real issuer/current transaction/coverage checks, genuine root/grant provenance authorization.
- `MeetingAccess.java`, `CategoryDictionary.java`, `MeetingConfiguration.java`: complete public interface/assembly reads; category validation-only interface/default unavailable confirmed.
- `MeetingController.java`:1–95: existing saved-info GET delegation.
- `JdbcProcessingFactsReader.java`:35–135,207–245: actual retained manifest metadata measurement pattern and standalone/owner boundary; not a reusable held list reader.

Used `receiving-code-review/SKILL.md` for verify-before-fix. Public read commands used PowerShell `wsl.exe -d Ubuntu-24.04 -- bash -lc 'cd /home/tanren/ama/.worktrees/phase-b-completion && ...'` with the stated bounded `sed -n` ranges, `cat`, and `rg`. Two initial `rg --files` attempts returned1 because hidden/ignored W was excluded; rerun with `--hidden --no-ignore` located producer designs. Two guessed source paths were absent (`adapters/.../CategoryDictionary.java`, `ama/processing/JdbcProcessingFactsReader.java`); `rg --files backend` resolved the actual core/transcript paths above. These were read failures only, not failed runtime tests.

## Artifact custody and complete diff checks

Raw bytes (SHA256, not normalized text):

| File | Bytes | Lines | SHA256 |
|---|---:|---:|---|
| Original design | 57259 | 175 | `2ce7e7457f07314b7ce967f433c716764d64f97f890576755ee61f7208e9253a` |
| R1 design | 69418 | 196 | `12602bdfaeda0672478c6bb08bf92fa844746990eda08fcd67b20b0ceb14ed9c` |
| Original review | 9799 | 50 | `b9bbedb4fa71cac0ccf3c65c62d00703b947c9e10c6031586702308260f43566` |
| Fix brief | 4876 | 24 | `f813c0201d33d3a740a4939ec12a085db68d453f380812508e2bfd32678ea4a3` |

Before writing, original/review hashes matched these values and both allowed output files were absent. R1 was created using exclusive `open('xb')`; original bytes were loaded and only six controlled intervals changed. Full actual `difflib.unified_diff` (context2, labels `original` and `R1`, keepends=True) was printed and read in full without output truncation:24560 bytes, SHA256 `eaa5f96bdaaeb55e4cfda816f1f41093639a95379a867d08fcf1e021e43207d1`. It was not written as a third artifact. The complete six changed intervals, represented as tag/original-inclusive-start/end/R1-inclusive-start/end, are:

```
[('replace', 1, 1, 1, 1), ('replace', 3, 3, 3, 3),
 ('replace', 9, 9, 9, 9), ('insert', 42, 41, 42, 62),
 ('replace', 157, 157, 178, 178), ('replace', 173, 173, 194, 194)]
```

The insert's reversed original interval means zero original lines consumed. Sections3–8 compare byte-equal, and SequenceMatcher covers every unchanged interval, not selected snippets. UTF8 decoding succeeds and no replacement-character bytes occur. First interval assertion expected the inserted blank line at43 rather than42; it failed with exit1. Corrected only that verification expectation (no document change), reran:exit0 and both explicit PASS messages. Full diff output itself was inspected before this correction.

Exact successful check invocation: PowerShell literal here-string containing the following Python piped to `wsl.exe -d Ubuntu-24.04 -- python3 -`. This is a file/diff check, not a test of the application:

```python
from pathlib import Path
import difflib,hashlib
w=Path('/home/tanren/ama/.worktrees/phase-b-completion/.superpowers/sdd/2026-10-23-ama-phase-b-remaining')
a=(w/'task-12-web-business-loop-design.md').read_bytes();b=(w/'task-12-web-business-loop-design-r1.md').read_bytes()
al=a.decode().splitlines(keepends=True);bl=b.decode().splitlines(keepends=True)
ops=[(tag,i+1,j,k+1,l) for tag,i,j,k,l in difflib.SequenceMatcher(None,al,bl,autojunk=False).get_opcodes() if tag!='equal']
print('CHANGED_INTERVALS',ops)
expected=[('replace',1,1,1,1),('replace',3,3,3,3),('replace',9,9,9,9),('insert',42,41,42,62),('replace',157,157,178,178),('replace',173,173,194,194)]
assert ops==expected,(ops,expected)
for n in range(3,9):
 start='## '+str(n)+'.';end='## '+str(n+1)+'.'
 x=a.decode().split(start,1)[1].split(end,1)[0];y=b.decode().split(start,1)[1].split(end,1)[0]
 assert x==y
print('PASS: six exact edit intervals only; sections 3 through 8 byte-equal; all other unchanged intervals equal')
assert b.count(b'\xef\xbf\xbd')==0
print('PASS: UTF-8 valid; no replacement-character bytes')
```

Exact full-diff/hash check used the same w/a/b reads plus review bytes, asserted original/review expected hashes, printed each length/line count/hash, then:

```python
d=''.join(difflib.unified_diff(a.decode().splitlines(keepends=True),b.decode().splitlines(keepends=True),fromfile='original',tofile='R1',n=2))
print('DIFF_BYTES',len(d.encode()),'DIFF_SHA256',hashlib.sha256(d.encode()).hexdigest())
print(d)
```

Result:exit0, all lengths/hashes above and complete five-hunk diff (six edit intervals). Final raw closure size/hash is reported after writing by the terminal receipt/author return, not embedded recursively in its own content.

## Remaining concerns and boundary

No blocker to scoped design review identified. Runtime proof remains entirely prospective. Actual B03 projection/category implementation, all held producer facade signatures/cumulative-budget integration, precise common rank and complete writer/FK coverage, actual index support, qualified deployment budgets and new-path MySQL race/no-transfer instrumentation must close before activation. Existing producer tests cannot qualify newly introduced Task12 paths. Task4 source review and Task11 root adoption/source/DDL remain root-owned gates. No source, Git/index/HEAD, DB, Docker, environment, browser, network, dependency, resource or runtime-test operation occurred; no evidence files beyond these two additive Markdown documents were written. Parent may now package the actual delta for independent ADDRESSED/NOTADDRESSED and newly introduced breakage review only.
