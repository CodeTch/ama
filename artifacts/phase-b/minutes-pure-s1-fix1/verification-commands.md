# Actual verification commands

All commands ran in `/home/tanren/ama/.worktrees/phase-b-minutes-pure`. The private pinned environment was sourced for Maven runs; its contents and classpath/environment files were not copied. Nonsecret reproduction paths: JDK `/home/tanren/ama/.worktrees/phase-a/.tools/zulu21.52.203-ca-jdk21.0.12.1-linux_x64`; Maven wrapper runtime `/home/tanren/.m2/wrapper/dists/apache-maven-3.10.0-bin/9e1da7b8/apache-maven-3.10.0`. Set `JAVA_HOME` to that JDK and prepend its `bin` to `PATH`; use the workspace `./mvnw`. Python 3 is the system `python3`; Node is unused.

Output redirection for each command is the matching raw log listed below. XML capture immediately followed runs 01, 03, 04, 05 and 06, before the next test runner could overwrite those reports; run 07 was captured separately. No old saved evidence was overwritten.

| Round | Exact command | Actual exit / result | Raw log |
|---|---|---|---|
| baseline | `python3 artifacts/phase-b/minutes-pure-s1-fix1/capture.py baseline` | 0; 273 exact Git/worktree paths; 6 custody originals | baseline-273.json / custody.json |
| 01 | `./mvnw -B -ntp -pl backend/adapters -am -Dtest=RawHashChainTest#fix1* -Dsurefire.failIfNoSpecifiedTests=false test` | 1; 8 tests, 5 assertion failures, 0 errors/skips | raw/01-graph-red.log |
| 02 | `./mvnw -B -ntp -pl backend/contracts -Dtest=MinutesFactBudgetTest test` | 1; compile/API setup failure, no test execution | raw/02-artifact-api-setup.log |
| 03 | `./mvnw -B -ntp -pl backend/adapters -am -Dtest=MinutesFactBudgetTest,RawHashChainTest#fix1* -Dsurefire.failIfNoSpecifiedTests=false test` | 1; contracts 4 passed; core test compilation failed; no adapter tests ran | raw/03-graph-artifact-green.log |
| 04 | `./mvnw -B -ntp -pl backend/adapters -am -Dtest=MinutesFactBudgetTest,StoredSourceGraphTest,RawHashChainTest#fix1* -Dsurefire.failIfNoSpecifiedTests=false test` | 0; 3 suites, 20 tests, 0 failures/errors/skips | raw/04-graph-artifact-green.log |
| 05 | same selector as 04 after 4 additional adapter regression methods | 0; 3 suites, 24 tests, 0 failures/errors/skips | raw/05-expanded-graph-green.log |
| 06 | complete 13-suite command below | 0; 13 suites, 112 unique tests, 0 failures/errors/skips | raw/06-complete-owned-green.log |
| 07 | `python3 tools/checks/run.py unit RawHashChainTest` | 0; 25 tests, 0 failures/errors/skips; all 8 reactor modules compiled | raw/07-canonical-focused-green.log |
| 08 | `java -version` then `./mvnw -v` | both 0; Zulu 21.0.12.1, Maven 3.10.0 | raw/08-toolchain.log |
| 09 | `python3 artifacts/phase-b/minutes-pure-s1-fix1/audit.py` | 1; evidence-script assertion against intentionally changed .gitattributes; not product RED | raw/09-integrity-audit.log |
| 10 | same read-only audit after correcting current-vs-BASE comparison | 0; all 273 BASE forms / 271 payloads verified in Git; 258 / 256 respectively byte-identical now; all old archive/report/probe originals unchanged; 18 vectors; 97 subset of 112; canonical 25 subset | raw/10-integrity-audit.log |
| 11 | `python3 artifacts/phase-b/minutes-pure-s1-fix1/audit.py` after final custody attributes | 0; same scope/counts; 16 final source hashes and 22 XML hashes | raw/11-final-audit.log |
| 12 | `python3 artifacts/phase-b/minutes-pure-s1-fix1/capture.py red-source` | 0; 2 reconstructed exact baseline-RED test source snapshots match the SHA actually captured immediately after run 01 | raw/12-red-source-custody.log |

Final staging/seal checks: `python3 artifacts/phase-b/minutes-pure-s1-fix1/seal.py`, explicit `git add -f --pathspec-from-file=artifacts/phase-b/minutes-pure-s1-fix1/delivery-paths.txt`, `git diff --cached --check` (exit 0, no output), then `python3 artifacts/phase-b/minutes-pure-s1-fix1/seal.py verify` (exit 0, exact explicit payload SHA/path set). The two seal files are excluded from their own payload set. No unlisted generated file is staged.

```sh
./mvnw -B -ntp -pl backend/adapters -am -Dtest=MinutesFactBudgetTest,CanonicalValueCodecTest,SourceAndSupportTest,StoredSourceGraphTest,HashProfilesTest,GenerationFingerprintTest,FinalAndCoverageTest,StrictMinutesJsonReaderTest,OutputBudgetBoundaryTest,ConfigWireReaderTest,ConfigBindingsTest,ArchiveBudgetTest,RawHashChainTest -Dsurefire.failIfNoSpecifiedTests=false test
```

`capture.py junit LABEL SUITE...` copies exact fresh XML for the listed suites into `junit/LABEL` and writes the corresponding JSON counts. Labels and observed totals: 01-graph-red=8/5 failures; 03-setup=4/0; 04-green=20/0; 05-expanded-green=24/0; 06-complete-owned=112/0; 07-canonical-focused=25/0. `xml-hashes.json` hashes all 22 archived XML files. `final-source-hashes.json` hashes every corrected source/attribute path including the new test helper. No setup failure is classified as assertion RED.

The original `independent_vectors.py` is a writer and was not rerun against historical resources. The new read-only `audit.py` independently re-encodes all 18 existing complete vector JSON groups and compares their bytes/SHA. Only owned suites and one focused canonical entry point ran; no DB/IT/S3/native/company/model/network test execution or allocation stress.
