# Task11 fix01 isolated fixture — PREPARED_UNUSED

This W-only helper is an adaptation of the independently reviewed Task11 provisioning helper. Preparation used only public helper/ACK/freeze/SQL bytes. It did not read or stat a private env, contact MySQL or Docker, create an owner, run a migration, access provider credentials, or change product or central records. The helper itself has not been executed for provisioning.

## Fixed input and isolation

- Preparation HEAD: `ba48f35e7ef97c1e162a2d373453bd1e9689529c`. Original helper SHA256 `e097391ad12f3a6395904ce56ad76ae07b5b09a67a0fafcd7b2762b9015c6659`; its controller review is `task-11-fixture-helper-controller-review-20261025.json` SHA256 `5207f976e123ecbd633b0311e9070df5550b5e53747b0dbe55ca77c3ccccf6a6`.
- Existing Task11 source application ACK `task-11-root-fixture-application-ack-20261025.json` SHA256 `dcab31da53de0a4e065aa05914f5143a5afd68ddbd785a87617455db1aa1574d` and original creation guard SHA256 `93d8afa8809ccdcf7ae811af258f2de12b443c032017f2aee16dbad44abb20a3` are read-only inputs. They identify source owner `task11-9714255c9e2c4861a2795cfaaec8e97f`, schema `ama_t11_9714255c9e2c_behavior`, the original source env path, same CID `11009a6b34fef0416e651100fbde05a07067dbcbd973f1ba731c08b2281140ad`, and current port 46151. The original ACK proves that schema was empty when issued; it does not assert it is empty now.
- Historical Task10 guard/application ACK remain at port 44727. The separate recovery ACK SHA256 `096cbdc38cb350b756c6958a0982d31277e4f0a4bc2f7a10e4f24fd1fc40b767` binds the same CID/image/mounts/limits to 46151. This helper retains all three historical/recovery checks and the Task10 adoption check; none is an output guard for fix01.
- One fresh owner must match `task11fix1-<32 lowercase hex>`; its schema must match `ama_t11fix1_<same first 12 hex>_behavior`. The helper checks both are absent before its first private write. It keeps the original canonical-path, 0700/0600 ACL, selected server, before/after, escaped GRANT, no-reset, one-shot, and partial-outcome guards. It creates only an empty schema plus scoped grant and a private env under the fresh owner.
- The sole future source env is the existing Task11 owner's `.tools/task11-9714255c9e2c4861a2795cfaaec8e97f/env.sh`. At reviewed execution it is parsed as exactly eight literal keys in memory, with no value printing or hashing. The new env retains the same eight `AMA_TEST_*` and `AMA_TASK11_*` keys, changes only database URL to the new schema and owner/run-root values, and carries the existing DB credential and pinned Task5 S3/PKI path references. It neither creates nor copies provider store, keys or credentials. Provider remains attached to its original controlled owner/port 46287 outside this helper.

## Frozen migration relationship

`task-11-formal-migration-freeze-20261025.json` SHA256 `83665f7f6a2443addc1a39b7faa5efc91e0d7f82ad49a2fa6aa6ded69c422a43` fixes the 15 inherited SQL paths, byte sizes and SHA256 values. Its formal sixteenth file `V013_4__publication_submission.sql` is 4,332 bytes, SHA256 `4709fc7a81fa0d3c252b2f2de165a6722d2197d7187f1ec066f1feebb9e42559`; the frozen W SQL and present product file match byte for byte at this preparation snapshot. The product file is currently untracked under HEAD `ba48f35e`; this is a byte check, not a commit/adoption claim. The helper rechecks all 16 public files and the frozen copy before any private write or SQL. It does not apply migrations. After a separate fix01 application ACK, the sole author must run the normal complete 16-migration bootstrap and rerun-zero/history checks on this new schema. The earlier 29-check DDL probe is not rerun.

## Review and one-time application template

Prepared helper: `task-11-fix01-isolated-fixture-prepared-20261025.py`, 29,313 bytes, SHA256 `ee25bbc05b81981ca25f682d058e5979d50ff10ee87f306ecdea757306d780be`. Its unique prepared plan is this note. Future public guard and root application ACK paths are respectively `W/task-11-fix01-fixture-creation-guard-20261025.json` and `W/task-11-fix01-root-fixture-application-ack-20261025.json`; neither was created by preparation. Root must independently review these exact helper bytes, extend protection before first write, select one fresh matching suffix, and authorize one invocation. The helper fails if either future public path already exists.

The coordinator can review this command template; it was not run:

```sh
FIX01_SUFFIX='<root-selected-new-32-lowercase-hex>'
FIX01_OWNER="task11fix1-${FIX01_SUFFIX}"
FIX01_SCHEMA="ama_t11fix1_${FIX01_SUFFIX:0:12}_behavior"
python3 /home/tanren/ama/.worktrees/phase-b-completion/.superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-11-fix01-isolated-fixture-prepared-20261025.py \
  --execute-reviewed-task11-fix01-provisioning \
  --approved-helper-sha256 ee25bbc05b81981ca25f682d058e5979d50ff10ee87f306ecdea757306d780be \
  --task10-adoption-evidence /home/tanren/ama/.worktrees/phase-b-completion/.superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-10-consumer-activation-root-ack-20261025.json \
  --task10-adoption-sha256 763c7da6870188b6ac07177391e197df956c121bdea10a9df2afd81d998f5fb3 \
  --owner "$FIX01_OWNER" --schema "$FIX01_SCHEMA"
```

The helper writes only the new private owner state/env during a reviewed invocation; it prints paths and mode, never values. A distinct root ACK and actual launcher fixture-name allowlist must then bind this exact owner/schema/URL/ACK SHA. Default launcher selection stays original; arbitrary URL or ACK injection is not part of this helper. Task11BootstrapIT may bootstrap only the newly ACKed empty schema through all 16 migrations and rerun zero. The original primary schema, its queue, old ACK/guard, Task10 owners, and provider owner remain untouched.

## Offline verification

- AST parse succeeded; diff against the sealed source helper was inspected. Public-only function invocation checked original ACK/guard relation and all 16 frozen SQL bytes; no process or private path was used.
- Synthetic in-memory env yielded exactly eight Task11-layout keys for a fresh matching owner/schema. AST order confirmed migration freeze before owner write and SQL; escaped grant construction remains present.
- No independent review of this new fix01 helper or runtime qualification is claimed.
