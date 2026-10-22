# New evidence commands
Working directory: /home/tanren/ama/.worktrees/phase-b-transcript-runtime
Only new private environment: .tools/b06-queue-seams/env.sh
Java: /home/tanren/ama/.worktrees/phase-a/.tools/zulu21.52.203-ca-jdk21.0.12.1-linux_x64

1. python3 tools/b06_owned_fixture.py (exit0, setup; initial-zero; requested ephemeral HostConfig port empty)
2. python3 tools/b06_capture_port.py (exit0, selected actual allocated loopback port before JDBC)
3. source .tools/b06-queue-seams/env.sh; export JAVA_HOME=<public Java above>; export PATH="$JAVA_HOME/bin:$PATH"; python3 tools/checks/run.py it B06QueueSeamsIT (exit1; 3 IT,2 assertion failures,0 errors,0 skips;179 unit pass)
4. python3 tools/b06_capture_evidence.py (exit0; preserve run-1 raw reports/source before next runner deletes old failsafe XML)
5. python3 tools/b06_batch_vector.py (exit0; raw batch design bytes)
6. openssl dgst -sha256 artifacts/phase-b/b06-queue-seams/batch-v1.literal.bytes (exit0; matches734-byte literal SHA)
7. python3 tools/b06_add_output_control.py (exit0; adds current-order behavior control; changes unreachable test effect to valid TRANSCRIPT_PENDING stage)
8. same own env and public Java: python3 tools/checks/run.py it B06QueueSeamsIT#currentThreeArgumentCompletionValidatesOutputBeforeEffects (exit0;1 IT pass;179 units repeated pass)
9. python3 tools/b06_finalize_evidence.py (exit0; run-2 raw reports/source, unchanged production source hashes)

All Docker selected-query/create invocations and real statuses are preserved in adjacent *.command.json/*.log files. Readiness startup poll commands are setup, with every attempted status/stdout/stderr preserved in fixture-ready-attempts.json. Polling there is bounded MySQL process readiness, not a test timing technique.

Read-only inspection issues, reconstructed from tool output (not raw process logs, no RED claim): find ../ traversed sibling .tools directories and got permission denied; initial guessed task-B-05-integration.md, ama/db/MysqlTestDatabase.java, ama/config/RawHashChainTest.java and V011__transcript_import_confirmation.sql paths were absent. Actual adopted handbooks/classes/migration filenames were then located with rg and read. rg .superpowers in this owned tree was absent (coordinator path is absolute); no missing path was created or old environment opened. Some long tool read outputs were truncated and relevant source/handbook segments were subsequently read separately.
