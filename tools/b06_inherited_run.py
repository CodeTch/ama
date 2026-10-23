"""Approved exact inherited selectors on the SAME B06 owned V013 fixture."""
import hashlib,json,pathlib,re,shutil,subprocess,sys,xml.etree.ElementTree as ET
root=pathlib.Path(__file__).resolve().parents[1]
e=root/"artifacts/phase-b/b06-queue-seams-fix";label=sys.argv[1] if len(sys.argv)>1 else "inherited";d=e/label
groups={
"TaskLeaseIT":["twoWorkersCompeteForOneTask","expiredAttemptCannotCommit","pausedOldCompletionLosesToConcurrentCancellation","versionEpochAndTombstoneEachFenceOldCompletion","sameProducerComparesEntireImmutableSpec","missingSubjectAndUnknownOrCrossSubjectTypedInputFailClosed","heldRowDoesNotBlockOtherCandidate","queueDomainAndOutboxRollbackTogether","completionCallbackFailureRollsBackTaskAndOutbox"],
"ExternalExecutionIT":["markerActuallyCommitsBeforeAuthorityAndAmbientIsRejected","unknownExecutionNeverReentersOrdinaryQueue","oldTaskExpiryDoesNotDiscardAuthoritativeSuccess","reconcilerTakeoverFencesOldOwnerAndPreservesExternalFact","commitAcknowledgementUnknownReturnsNoAuthority","concurrentSameProducerRetryAcceptsOnlyOneExecution","zeroEffectRetryAppendsExecutionWithoutResettingEpoch"],
"ReviewConcurrencyIT":["lateSuccessSeesZeroEffectCommittedWhileWaitingForSubject","lateSuccessRediscoversRetryCommittedWhileWaitingForSubject","observationRejectsAmbientTransactionBeforeAnyEffects","exhaustedRediscoveryRequiresResubmittingFactWithoutAnotherExternalSend"],
"OutboxFenceIT":["domainLocksAndNewEventsAreRejectedAfterFinalFlush"],
"WorkerProcessIT":["independentOperationHeartbeatContinuesWhileHandlerBlocks"],
"MeetingConcurrencyIT":["identityDisabledWhileConfirmationWaitsCannotReceiveGrant","actualServiceLocksSortedIdentitiesBeforeRootAndFlushesLast","readWaitingForRevocationSeesCommittedRosterNotOldSnapshot"],
"ModelPermitIT":["copiedAndReconciliationTokensCannotAdmitAndDuplicateCannotResend"]}
for cls,methods in groups.items():
 files=list((root/"backend/integration-tests/src/test/java").rglob(cls+".java"));assert len(files)==1
 text=files[0].read_text()
 for method in methods:assert re.search(r"void\s+"+method+r"\s*\(",text),(cls,method)
selectors=",".join(cls+"#"+"+".join(methods) for cls,methods in groups.items())
assert "*" not in selectors
# Previous XML is already archived, remove only owned generated reports so counts are fresh.
for m in (root/"backend").iterdir():
 for kind in ["failsafe-reports","surefire-reports"]:
  for f in (m/"target"/kind).glob("TEST-*.xml"):f.unlink()
cmd=[str(root/"mvnw"),"-B","-ntp","-pl","backend/integration-tests","-am",
 "-Dtest=NoUnitTestsForB06FocusedIntegrationSelection","-Dsurefire.failIfNoSpecifiedTests=false",
 "-Dit.test="+selectors,"-Dfailsafe.failIfNoSpecifiedTests=false","verify"]
with (e/(label+".log")).open("w") as out:r=subprocess.run(cmd,cwd=root,stdout=out,stderr=subprocess.STDOUT)
(e/(label+".command.json")).write_text(json.dumps({"argv":cmd,"exit_code":r.returncode,"unit_tests_intentionally_not_executed":True},indent=2)+"\n")
d.mkdir(parents=True,exist_ok=True)
counts=[];actual=set()
for m in (root/"backend").iterdir():
 for kind in ["failsafe-reports","surefire-reports"]:
  for f in (m/"target"/kind).glob("*"):
   if f.suffix in [".xml",".txt"]:
    t=d/m.name/kind/f.name;t.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(f,t)
    if f.name.startswith("TEST-") and f.suffix==".xml":
     s=ET.parse(f).getroot()
     counts.append({"suite":s.get("name"),"kind":kind,**{k:int(s.get(k,0)) for k in ["tests","failures","errors","skipped"]}})
     for c in s.findall("testcase"):
      actual.add((c.get("classname").split(".")[-1],c.get("name").split("(")[0].split("[")[0]))
(e/(label+"-counts.json")).write_text(json.dumps(counts,indent=2)+"\n")
expect={(cls,m) for cls,methods in groups.items() for m in methods}
(e/(label+"-method-coverage.json")).write_text(json.dumps({"expected":sorted(expect),"actual":sorted(actual),"exact_match":actual==expect},indent=2)+"\n")
paths=subprocess.check_output(["git","ls-files","--modified","--others","--exclude-standard"],cwd=root,text=True).splitlines()
sha=[]
for x in paths:
 if x.startswith("backend/") and x.endswith(".java"):
  raw=(root/x).read_bytes();t=d/"source"/x;t.parent.mkdir(parents=True,exist_ok=True);t.write_bytes(raw);sha.append({"path":x,"sha256":hashlib.sha256(raw).hexdigest()})
(d/"source-sha256.json").write_text(json.dumps(sha,indent=2)+"\n")
it=[x for x in counts if x["kind"]=="failsafe-reports"]
totals={k:sum(x[k] for x in it) for k in ["tests","failures","errors","skipped"]}
print(json.dumps({"exit":r.returncode,"IT":totals,"unique_methods":len(actual),"exact_selection":actual==expect}),flush=True)
if r.returncode:sys.exit(r.returncode)
assert actual==expect and totals=={"tests":28,"failures":0,"errors":0,"skipped":0}
