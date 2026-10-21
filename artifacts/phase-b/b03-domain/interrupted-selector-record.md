# Interrupted broad selector: no integration database setup ran

This record quotes observations from the tool transcript. The complete original process output is retained as `full-first.log`; it is an interrupted invocation, not a passing run or domain RED result.

Original command:

```text
python3 tools/checks/run.py it "Meeting*IT"
```

Before integration execution, discovery of the broad selector was noticed to include historical schema tests. The exact process was inspected with `ps -eo pid,args` and showed:

```text
710745 /home/tanren/ama/.worktrees/phase-a/.tools/zulu21.52.203-ca-jdk21.0.12.1-linux_x64/bin/java -classpath /home/tanren/ama/.worktrees/phase-b-meeting-domain/.mvn/wrapper/maven-wrapper.jar -Dmaven.multiModuleProjectDirectory=/home/tanren/ama/.worktrees/phase-b-meeting-domain org.apache.maven.wrapper.MavenWrapperMain -B -ntp -pl backend/integration-tests -am -Dit.test=MeetingConcurrencyIT,MeetingRevisionIT,MeetingSchemaIT,MeetingSchemaLegacyAclIT -Dfailsafe.failIfNoSpecifiedTests=false verify
```

The observed process was immediately terminated with `kill -TERM 710745` from the owned WSL worktree; that shell command returned exit 0. At termination the tail was the API module's surefire `IdentitySecurityTest`, following the completed DeploymentConfigTest and IdentityContractsTest. The retained complete log ends with:

```text
[INFO] Running ama.api.IdentitySecurityTest
FAIL: subprocess exited 143
```

The process never reached the integration-tests module, failsafe execution, any MeetingSchema test or a MySQL schema reset/refusal operation. There is no integration XML for this interrupted command and none is counted. The shell's attempted evidence copy had no fresh matching integration XML to copy. All subsequent integration commands use exact class selectors separately.

Cross-check: final Flyway history has the same initial migration installation times (UTC 2026-10-21 20:01:54 through 20:02:05) as the first migration in `red.log` (host timezone UTC+08). No versions were reinstalled at the interrupted command's later time. All eight migration byte hashes match the pre-run snapshot and reviewed BASE. The owned fixture remains intact; no reset/repair/drop/cleanup command was issued.
