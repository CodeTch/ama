"""Exact two-profile budget diagnosis; shared qualified producer/custody mechanics after public gate."""
import datetime,hashlib,json,os,pathlib,re,signal,sys
import task11_b3_window as shared
from task11_report_custody import exclusive_runner_lock
ROOT=pathlib.Path(__file__).resolve().parents[1];W=ROOT/'.superpowers/sdd/2026-10-23-ama-phase-b-remaining';OUT=ROOT/'artifacts/phase-b/b-remaining/task-11'
ACK='task-11-b3-budget-diagnostic-r2-configuration-ack-20261026.json';ACK_SHA='8439e275bc31861f25743f9527b0d15f167d9831ccbf478cbbcf358bc1bd45d5';TUPLE=('b3-budget-diagnostic-02','it','Task11B3BudgetDiagnosticIT','task11-b2-exact-isolated')
BUDGET={'serviceInvocations': 2, 'parentContexts': 2, 'servletRequests': 0, 'sessionInserts': 0, 'sessionDeletes': 0, 'ordinaryClaims': 0, 'ordinaryRenewals': 0, 'reconciliationClaims': 0, 'workerInstances': 0, 'markerTransactions': 0, 'providerGetIncrements': 2, 'destinationPostIncrementsMax': 2, 'publicationPostIncrements': 0, 'selectedFinalTransactionsMax': 2, 'controlFinalSubmissionStops': 1, 'committedSuccessors': 0, 'sqliteObservations': 0, 'grants': 0, 'nativeStarts': 0, 'freshJvmChildren': 0, 'producerTimeoutSeconds': 240}
PINS={'priorDiagnosticSource','priorDiagnosticReview','failedDiagnosticFacts','failedDiagnosticCustody','initialDiagnosticSource','initialDiagnosticReview','relayAfterFailure', 'allocation', 'pipelineFacts', 'credentials', 'sourceReview', 'partialReview', 'retainedOrigin', 'sourceReviewAddendum', 'partialFacts', 'partialAdoption', 'overlay', 'priorSource', 'partialCustody'}
digest=shared.digest;parse=shared.parse

def public_bytes(path):
 assert path.is_absolute() and path.is_relative_to(ROOT) and '..' not in path.parts and not path.is_relative_to(ROOT/'.tools')
 current=ROOT
 for part in path.relative_to(ROOT).parts:current=current/part;assert current.exists() and not current.is_symlink()
 assert path.is_file() and path.resolve()==path and path.stat().st_size<=1048576
 return path.read_bytes()

def pinned(path,sha):
 assert re.fullmatch('[0-9a-f]{64}',sha);raw=public_bytes(path);assert digest(raw)==sha;return parse(raw)

def budget(value):assert value==BUDGET and all(type(value[key]) is int for key in BUDGET)

def validate_configuration(cfg):
 assert cfg['status']=='B3_BUDGET_DIAGNOSTIC_CONFIGURATION_INERT' and tuple(cfg[key] for key in ('label','group','selector','fixtureName'))==TUPLE
 assert all(cfg[key] is False for key in ('runtimeAuthorized','grantAuthorized','nativeStartAuthorized','sendAuthorized')) and all(cfg[key] is True for key in ('noNewPipeline','noBusinessSqlSeed','baselineMustMatchCurrentBeforeAnyEffect'))
 assert (cfg['owner'],cfg['schema'],cfg['username'],cfg['cid'],cfg['port'])==('task11b2-d9e2fdcc611e4e21bb360e539ca46258','ama_t11b2_d9e2fdcc611e_behavior','t11b2_d9e2fdcc611e','11009a6b34fef0416e651100fbde05a07067dbcbd973f1ba731c08b2281140ad',46151) and type(cfg['port']) is int
 assert (cfg['meetingId'],cfg['publicationId'],cfg['operationId'],cfg['taskId'],cfg['executionId'])==('36db643e-5fbd-4f9a-9a85-a7e6b7d76e38','142a236c-bcd6-4608-8e93-2dd5446c5fa9','896456c1-cc73-4a8c-803d-8b5bb058d4c7','3ef6677d-63c3-43ab-860b-c99ed94cf394','c980509b-5f73-4b97-948c-54aa29abdf4f')
 for key,value in [('meetingRevision',13),('publicationRevision',15),('operationEpoch',11),('currentExecutionNo',6),('baselineTaskAttempt',3)]:assert type(cfg[key]) is int and cfg[key]==value
 budget(cfg['budget']);assert cfg['profiles']==[dict(name='ORIGINAL_1M',archiveTotalBytes=1048576,expected='BUDGET_EXCEEDED'),dict(name='CONTROL_2M',archiveTotalBytes=2097152,expected='FINAL_SUBMISSION_SEAM_ROLLED_BACK')] and all(type(profile['archiveTotalBytes']) is int for profile in cfg['profiles'])
 assert set(cfg['inputPins'])==PINS;values={}
 for key,pin in cfg['inputPins'].items():
  assert pathlib.Path(pin['path']).name==pin['path'];raw=public_bytes(W/pin['path']);assert digest(raw)==pin['sha256']
  if key not in ('sourceReview','sourceReviewAddendum','partialReview','initialDiagnosticReview','priorDiagnosticReview'):values[key]=parse(raw)
 previous=values['failedDiagnosticFacts'];assert previous['status']=='B3_BUDGET_DIAGNOSTIC_PARTIAL_UNQUALIFIED_NEVER_RERUN' and previous['window']=='b3-budget-diagnostic-01';pd=previous['diagnostic'];assert type(pd['serviceCalls']) is int and pd['serviceCalls']==0 and type(pd['contexts']) is int and pd['contexts']==1 and pd['profiles']==[] and pd['initialStateMatchesFrozenBaseline'] is True and pd['finalRowsEqualInitial'] is True and pd['status']=='PARTIAL_UNQUALIFIED' and pd['safeFailureType']=='org.springframework.boot.diagnostics.FailureAnalyzedException';assert previous['junit']==dict(tests=1,failures=0,errors=1,skipped=0);assert previous['sourceCheckpointSha256']==cfg['inputPins']['priorDiagnosticSource']['sha256'] and previous['runtimeCustodySha256']==cfg['inputPins']['failedDiagnosticCustody']['sha256'] and pd['configurationAckSha256']=='5c66ea0baa061e7de9d30fdf06facb33781c1f56a91fea6b9110e06abfb8572e';fc=values['failedDiagnosticCustody'];assert fc['status']=='DIAGNOSTIC_WINDOW_ALL_PUBLIC_FILES_SEALED' and fc['window']=='artifacts/phase-b/b-remaining/task-11/b3-budget-diagnostic-01' and len(fc['files'])==1157
 adopted=values['partialAdoption'];assert adopted['status']=='LIMITED_O1_O3_AND_O4_LEASE_HISTORY_CLEANUP_ADOPTED_ORIGINAL_WINDOW_FAILED'
 for name,field in [('partialFacts','factsSha256'),('partialReview','reviewSha256'),('partialCustody','custodySha256'),('priorSource','sourceCheckpointSha256')]:assert cfg['inputPins'][name]['sha256']==adopted[field]
 assert adopted['postFailureCurrentRowsVerified'] is False and adopted['noRerun'] is True
 facts=values['partialFacts'];assert facts['status']=='B3_LIFECYCLE_PARTIAL_O1_O4_REACHED_O5_HTTP413_EVIDENCE_REVIEW_PENDING' and cfg['taskId']==facts['lastObservedTaskId'] and cfg['executionId']==facts['lastObservedExecutionId'] and facts['lastObservedPublicationRevision']==15 and facts['genuineSuccessorsObserved']==4 and facts['postFailureFullBusinessReadbackAvailable'] is False
 assert values['partialCustody']['status']=='PARTIAL_FAILED_WINDOW_ALL_FILES_SEALED' and len(values['partialCustody']['files'])==1151
 relay=values['relayAfterFailure'];assert relay['status']=='READONLY_RELAY_FILE_OBSERVED_AFTER_FAILED_ONCE_WINDOW' and (relay['relay']['get'],relay['relay']['postAuthorization'],relay['relay']['postPublication'])==(44,23,1)
 assert all(type(relay[key]) is int and relay[key]==0 for key in ('sqlObservations','sqliteObservations','networkRequests','mutations'))
 assert cfg['baselineRows']=='artifacts/phase-b/b-remaining/task-11/b3-lifecycle-01/business-o4-closed.json' and cfg['baselineRowsSha256']=='94c52cfb6986e6bba4120ea18898a57908bc57e662a4663e51274bc923aeff20' and len(pinned(ROOT/cfg['baselineRows'],cfg['baselineRowsSha256']))==19
 assert cfg['runtimeSourceCheckpoint']=='task-11-b3-budget-diagnostic-r2-source-checkpoint-20261026.json' and cfg['runtimeReleaseFile']=='task-11-b3-budget-diagnostic-02-runtime-release-20261026.json'
 release=parse(public_bytes(W/cfg['runtimeReleaseFile']));assert release['status']=='B3_BUDGET_DIAGNOSTIC_RUNTIME_RELEASED_ONCE' and tuple(release[key] for key in ('label','group','selector','fixtureName'))==TUPLE and release['configurationAckSha256']==ACK_SHA and type(release['maxInvocations']) is int and release['maxInvocations']==1
 assert all(release[key] is False for key in ('oneGrantAuthorized','nativeStartAuthorized','sendAuthorized'));budget(release['budget']);issued=datetime.datetime.fromisoformat(release['issuedUtc'].replace('Z','+00:00'));deadline=datetime.datetime.fromisoformat(release['startBeforeUtc'].replace('Z','+00:00'));now=datetime.datetime.now(datetime.timezone.utc);assert issued.tzinfo is not None and deadline.tzinfo is not None and issued<deadline and issued<=now<deadline
 cp=pinned(W/cfg['runtimeSourceCheckpoint'],release['sourceCheckpointSha256']);assert isinstance(cp['files'],list) and 0<len(cp['files'])<=2048;seen=set()
 for row in cp['files']:assert row['path'] not in seen and digest(public_bytes(ROOT/row['path']))==row['sha256'];seen.add(row['path'])
 return values,cp

def run_window(lock=None):
 assert tuple(sys.argv[1:])==TUPLE,'Only exact fresh B3 budget diagnosis window is supported'
 cfg=pinned(W/ACK,ACK_SHA);values,cp=validate_configuration(cfg)
 return shared.execute_window(cfg,values,cp,lock,run_tuple=TUPLE,ack_sha=ACK_SHA,producer_timeout=240,root=ROOT,workspace=W,out=OUT)

def main():
 previous=signal.signal(signal.SIGTERM,shared.stop_signal)
 try:
  with exclusive_runner_lock(OUT/'.task11-runner.lock',{'arguments':sys.argv[1:],'cwd':str(ROOT),'purpose':'exact-b3-budget-diagnostic'}) as lock:code=run_window(lock)
 finally:signal.signal(signal.SIGTERM,previous)
 sys.exit(code)
if __name__=='__main__':main()
