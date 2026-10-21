# V011 preparation checkpoint — no schema acceptance

This checkpoint prepares the B06 schema prerequisite at exact base
`b2f5f5afa3d4de6a44fc7151a06a366c59349901`. It does not activate a migration or
claim database RED/GREEN. V010 is absent at this base and must be supplied as an
independently reviewed dependency by root before formal resumption.

The independent resource is
`backend/integration-tests/src/test/resources/ama/db/transcript-schema-b00-1.2.json`.
It filters the adopted B00-1.2 actual MySQL metadata evidence, SHA-256
`3de8041db8860f1547ff53a94b4ddd79470c4881340111fdafbc3ce5322399ad`, for exactly
the eight reserved V011 tables. It excludes only the FK-column row for
`transcription_input.fk_transcription_input_3`, reserved for V017. Its own SHA-256
is `94e7b136824d1845462df644799db0fe4fbf8dce2ba28e961dd3df29bfe7b182`.

| Metadata group | Rows |
|---|---:|
| Tables | 8 |
| Columns | 63 |
| Index columns | 39 |
| CHECK constraints | 13 |
| FK columns | 22 (21 FK constraints) |

The source-SHA-guarded preparation script makes this resource reproducible.
Run it as `python3 docs/acceptance/b/transcript-schema-preparation.py` from this
worktree. It checks the source evidence and DDL SHA before filtering, and refuses
to overwrite changed review-draft bytes. The initial ignored draft is also
preserved as `initial-V011__transcript_schema.sql.review-only` in the same folder.
`TranscriptSchemaIT` contains actual table-presence and five-group metadata
assertions for later execution. During preparation it is compile-only; its
explicit formal-resumption guard precedes every JDBC connection. That guard's
failure is not feature RED. It still needs the full owned-fixture/upgrade and
physical constraint harness. No offline constant/count test replaces schema
RED/GREEN.

The mutable draft remains ignored at
`.tools/transcript-schema-review/V011__transcript_schema.sql.review-only`, outside
all Flyway locations. Initial draft SHA-256 is
`1922ffb617ab2776bcb32727283f81dcc7a91e08a8219216b15d32c3c31d52dd`.
It contains exact adopted CREATE statements followed by all 21 currently
deployable FK constraints; circular references are added only after all eight
CREATE statements. This hash is preparation provenance, not a freeze SHA ACK.
Do not copy it into active Flyway resources or apply it during the hold.

## Preparation verification only

On 2026-10-22 the pinned runtime from
`/home/tanren/ama/.worktrees/phase-a/.tools/env.sh` compiled the reactor through
integration-test sources with:

```sh
./mvnw -B -ntp -pl backend/integration-tests -am -DskipTests test-compile
```

Exit status was zero; all seven selected reactor modules compiled, including 18
integration-test source files. No unit or integration tests were executed.
The local retained log `.tools/transcript-schema-review/test-compile.log` has
SHA-256 `d2ccca5847857523bcdba4649bdcaad9688430a7c9ef9a2825910daea6591f58`.
Source extraction was run successfully with the counts and source hashes above.
No V010 SQL or Java was imported, no active V011 was created, no register was
changed, and no DB/Docker/startup/migration/DML command was run. Compilation and
data extraction are not actual feature RED/GREEN or schema acceptance.

## Work remaining after explicit formal resumption

1. Receive root's reviewed V010 baseline/SHA and new formal-resumption notice;
   retain this preparation commit in the complete owned range. Verify every
   lower migration's frozen hash/checksum and ordered successful history.
2. Create and confirm a new empty, worktree-owned, loopback functional fixture
   using the reviewed guarded helper. If needed, append process-local
   `--innodb-use-native-aio=OFF` and capture actual args and server variable. No
   shared defaults, sysctl, existing fixture reset, history repair, or cleanup.
3. Write the full private-target historical V011 integration suite. Guard known
   lower/full history and `Flyway.validate()` before DML; preserve failed or
   unknown history. First execute actual missing-eight-tables assertion RED
   against exact lower 001,002,005,006,007,008,009,010. Compilation or fixture
   setup errors are not feature RED.
4. Freeze the assigned `V011__transcript_schema.sql` only through root's final
   register/SHA ACK protocol before first formal application. Retain initial
   draft bytes/provenance; never rewrite frozen migration bytes.
5. Apply V011 once, compare all five actual metadata groups with the independent
   resource, preserve every lower DDL/row/history and SQL hash, and prove rerun
   zero. Preserve before/after full DDL and metadata evidence.
6. Physically test invalid enum values and case variants; EMPTY/PINNED column
   consistency; IMPORT parser required; ASR/ENHANCE model required; ENHANCE source
   required; manifest version positive; nullable prompt/parser/model where
   adopted checks permit; binary IDs including case and trailing-space source
   mismatches; long Unicode; nullable plaintext times and speaker; complete and
   ordered time ranges; source/processing/source-file/speaker/mark FK failures;
   history/order/confirmation/batch-file uniqueness; all four distinct marks on
   one segment and duplicate prevention. Add meaningful scoped low-regression
   checks, then obtain independent specification and quality review.

Schema permits valid PINNED rows while its material FK is deferred. Application
B must enforce EMPTY/NULL until V017; this preparation does not prove that guard.
Same-meeting membership, batch/source membership, immutable revision history,
confirmation eligibility and saved-content hashes are future full B06 business
obligations where these individual FKs cannot establish them. Full B06 APIs,
workers, ACL, parser activation and other business behavior remain outside this
schema prerequisite.
