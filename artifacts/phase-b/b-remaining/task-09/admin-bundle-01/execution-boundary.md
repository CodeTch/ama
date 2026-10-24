# Task9 controlled administrative installation — concrete execution boundary

Status: prepared, NOT executed. Coordinator ACK is required for this exact administrative invocation. Focused installer test `controlled-admin-green-01` passed one case (real template/release/capacity/private files/exact replay); it is not this runtime installation.

Command from the phase-b-completion worktree:

```
python3 artifacts/phase-b/b-remaining/task-09/admin-bundle-01/install.py --execute
```

The helper runs `ama.minutes.ControlledMinutesAdmin` from frozen contracts/core/adapters jars and SHA-pinned existing Maven dependencies, with `-Xmx64m -XX:ActiveProcessorCount=2`. It reads the existing private 8-key env without editing it. Existing credentials remain only in the child environment; no credential value/hash is emitted.

Target schema: `ama_t09_2d0e28ea7755_behavior`, existing retained container/44727. Dedicated owner: `.tools/task09-2d0e28ea775540f8b3525f9344edf5f0/controlled-installation-20261024`, already prepared 0700 with `install.properties`0600. No provider secret, archive, capacity action or installation has been performed in this owner.

Assigned template `067313c0-bb33-4b83-8674-47058c6e9a6d`, revision `0f7d6a2b-214e-4bff-ac66-5823755c0718`. The application repository inserts exactly this parent/revision with real database time, owner null, revision1, exact controlled name/themes7. Exact replay verifies immutable fields. No meeting, input, candidate, task or attempt is created or updated. No schema migration or DDL.

The CLI publishes a fresh strict 19-object archive under `archive/` (17 minutes roots plus byte-identical existing parser/processing profiles), binding the persisted revision. It creates `provider-store/`0700, a new local `provider.secret`0600, `provider.properties`0600 for loopback127.0.0.1:45989, `minutes.properties`0600 with explicit finite bounds and pinned archive, and `installation.json`0600 with safe immutable IDs/hashes. It explicitly invokes existing JdbcModelPermitPool.configure for CONTROLLED_MINUTES_V1/capacity1/revision1/enabled/EXPLICIT_SETTLEMENT. Existing equal config is replayed; differing same-revision config or live-capacity conflict aborts. No capacity reservation is created.

Installation can leave a partial new owner on failure; no cleanup, reset, retry or repair is automatic. Template creation is atomic; release publication is immutable/atomic; capacity configuration is independently transactional. A successful installation receipt is written last. The helper retains stdout/stderr/result under execution-01 and checks the original env is unchanged.

This invocation does not start provider/API/worker. The generated provider launch file is ready for a subsequent owned runtime window. Formal new initial-settings and new-generation remain unavailable until actual Task11/Task10 contributions; existing receipt/history and lookup recovery boundaries remain separate.

`manifest.json` seals exact direct DML/configuration sources, frozen jars, dependencies, private config (which contains no credentials) and targets. Direct sources are copied under source/. The first receipt test is already sealed independently. No other heavy/runtime window will overlap the exact installation invocation.
