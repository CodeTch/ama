# Task5 final queue delta custody clarification

This note is requested source-window custody clarification only. No product source, public artifact, HEAD or index was changed. HEAD remains `857102b917bbef77c38fae3211e3a65207a98c6c`; status/index were clean at this read-only check.

## Origin of exact edit

The exact replacement below is transcribed from the still-retained executed `functions.exec` / `tools.exec_command` invocation in this author conversation. That invocation ran a PowerShell `$code = @' ... '@` Python here-string through `$code | wsl -d Ubuntu-24.04 -- python3 -`. Immediately before the replacement it printed the actual `confined-runnable-red2` failure (expected callback count0, observed1); after the source/test edits it launched `task-05-run.py confined-runnable-green it "Task05AfterTaskIT"`. The returned tool output for that call had chunk `6d54eb` and live test session `27180`; the subsequent wait reported 2 passing tests. This excerpt is the executed queue edit, not an inferred SUCCEEDED variant:

```python
r=Path('/home/tanren/ama/.worktrees/phase-b-completion');e=r/'artifacts/phase-b/b-remaining/task-05/confined-runnable-red2'
# XML failure printing preceded the following exact executed queue edit:
p=r/'backend/adapters/src/main/java/ama/task/JdbcTaskQueue.java';s=p.read_text().replace('var r=valid.orElseThrow();policy(r.spec()).validateOutput(r.spec(),result);','var r=valid.orElseThrow();if(policy(r.spec()) instanceof ConfinedTaskCompletionPolicy)throw new IllegalStateException("Confined carrier required");policy(r.spec()).validateOutput(r.spec(),result);');p.write_text(s)
```

The executable queue-edit line is verbatim. The explanatory comment is added in this note. The same tool invocation also added two production-boundary assertions to Task05PipelineIT; that test-only change does not add another production-file delta.

Before text:

```java
var r=valid.orElseThrow();policy(r.spec()).validateOutput(r.spec(),result);
```

After text:

```java
var r=valid.orElseThrow();if(policy(r.spec()) instanceof ConfinedTaskCompletionPolicy)throw new IllegalStateException("Confined carrier required");policy(r.spec()).validateOutput(r.spec(),result);
```

This is inside `completeWithEffects(TaskLease, TaskResult, Runnable)`, after successful `validLease` and before validateOutput/effects. It denies all outcomes for confined registrations. There was no SUCCEEDED conditional at this location in the prior queue bytes; the production ImportPolicy's separate SUCCEEDED output check already existed and is unchanged by this edit.

## Read-only reconstruction verification

A fresh read found exactly one occurrence of the after text in delivered JdbcTaskQueue. Replacing that single occurrence in memory with the exact before text from the executed invocation produced SHA256:

`bb51773d2d5af8b37604b5da818c023b9c735927709e062e8029a46e54fba57e`

It equals the retained JdbcTaskQueue SHA256 in all four original source seals:

- `artifacts/phase-b/b-remaining/task-05/final-owned-integration/source.json`
- `artifacts/phase-b/b-remaining/task-05/final-inherited-upload/source.json`
- `artifacts/phase-b/b-remaining/task-05/final-pipeline-custody/source.json`
- `artifacts/phase-b/b-remaining/task-05/confined-runnable-red2/source.json`

The delivered file SHA256 is:

`05d9965f0d448d37295680288aeb46c79d3908fc943edb6955c10d0fec94bc64`

It equals `confined-runnable-green/source.json` and `final-all-units/source.json` for that path. Thus the exact recorded replacement reconstructs the prior sealed bytes without a guessed guard variant.

No separate full prior JdbcTaskQueue source file was archived in the evidence tree; the prior source receipts retained SHA256 manifests, while the exact edit is retained in the executed tool conversation. The in-memory reverse candidate used for this clarification is derived evidence, not a claim that an original full-source snapshot had been retained. No derived old source file was written over any original or public artifact.
