"""Exact standalone lifecycle dispatcher; shared qualified producer/group/report mechanics."""
import datetime,hashlib,json,os,pathlib,re,signal,sys
import task11_b3_window as shared
from task11_report_custody import exclusive_runner_lock
ROOT=pathlib.Path(__file__).resolve().parents[1];W=ROOT/'.superpowers/sdd/2026-10-23-ama-phase-b-remaining';OUT=ROOT/'artifacts/phase-b/b-remaining/task-11'
ACK='task-11-b3-lifecycle-r1-configuration-ack-20261026.json';ACK_SHA='2a51cae7486b44dde7b2ce4a375f3ce7ed60f39180c6cb90312b91bea0fd775d';TUPLE=('b3-lifecycle-01','it','Task11B3LifecycleIT','task11-b2-exact-isolated')
BUDGET={'servletRequests': 13, 'parentContexts': 3, 'childContexts': 1, 'sessionInsertsMax': 1, 'sessionDeletesMax': 1, 'attemptEndingFaults': 1, 'localDecisions': 5, 'successors': 6, 'ordinaryLeases': 10, 'reconciliationClaims': 1, 'ordinaryRenewalsMax': 10, 'claimCallsMax': 40, 'workerInstances': 3, 'reconciliationLoops': 1, 'markerTransactions': 2, 'markerRollbacks': 1, 'markerCommitAckLosses': 1, 'freshJvmChildren': 1, 'providerGetIncrements': 7, 'destinationPostIncrements': 8, 'publicationPostIncrements': 0, 'sqliteObservations': 0, 'grants': 0, 'nativeStarts': 0, 'producerTimeoutSeconds': 420}
PINS={'preparedAdoption','preparedReview','preparedFacts','preparedCustody','sourceBase','pipelineFacts','allocation','overlay','credentials','retainedOrigin','initialLifecycleAck','initialLifecycleSource','initialLifecycleReview'}
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
 assert cfg['status']=='B3_LIFECYCLE_CONFIGURATION_INERT' and tuple(cfg[key] for key in ('label','group','selector','fixtureName'))==TUPLE
 assert all(cfg[key] is False for key in ('runtimeAuthorized','grantAuthorized','nativeStartAuthorized','sendAuthorized')) and cfg['noNewPipeline'] is True and cfg['noBusinessSqlSeed'] is True
 assert cfg['owner']=='task11b2-d9e2fdcc611e4e21bb360e539ca46258' and cfg['schema']=='ama_t11b2_d9e2fdcc611e_behavior' and cfg['username']=='t11b2_d9e2fdcc611e' and cfg['cid']=='11009a6b34fef0416e651100fbde05a07067dbcbd973f1ba731c08b2281140ad' and type(cfg['port']) is int and cfg['port']==46151
 assert (cfg['meetingId'],cfg['publicationId'],cfg['taskId'],cfg['executionId'],cfg['operationId'])==('36db643e-5fbd-4f9a-9a85-a7e6b7d76e38','142a236c-bcd6-4608-8e93-2dd5446c5fa9','3588aaba-ecfa-4889-8954-46c1454447d4','65fa6b21-22da-4a2e-a886-341b16978106','896456c1-cc73-4a8c-803d-8b5bb058d4c7')
 assert type(cfg['meetingRevision']) is int and cfg['meetingRevision']==13 and type(cfg['publicationRevision']) is int and cfg['publicationRevision']==7
 assert (cfg['baselineTaskState'],cfg['baselineExecutionState'],cfg['baselineExecutionNoEffect'],cfg['baselineLocalState'])==('FAILED','FAILED','NOT_SENT','NEVER_MARKED') and cfg['unavailableMinutesArchive']==cfg['owner']+'/b3-lifecycle-unavailable-archive-01' and cfg['finalDisposition']=='REAL_REGISTERED_RECONCILIATION_QUERY_ONLY_UNKNOWN_NO_RESEND'
 budget(cfg['budget']);assert set(cfg['inputPins'])==PINS;values={}
 for key,pin in cfg['inputPins'].items():
  assert pathlib.Path(pin['path']).name==pin['path'];raw=public_bytes(W/pin['path']);assert digest(raw)==pin['sha256']
  if key not in ('preparedReview','initialLifecycleReview'):values[key]=parse(raw)
 adopted=values['preparedAdoption'];assert adopted['status']=='B3_PREPARED_READY0_LOCAL_CLOSURE_ADOPTED_FOR_REMAINING_B3' and adopted['factsSha256']==cfg['inputPins']['preparedFacts']['sha256'] and adopted['evidenceReviewSha256']==cfg['inputPins']['preparedReview']['sha256'] and adopted['runtimeCustodySha256']==cfg['inputPins']['preparedCustody']['sha256'] and adopted['sourceCheckpointSha256']==cfg['inputPins']['sourceBase']['sha256'] and adopted['readbackSqliteBudgetRemaining']==0
 facts=values['preparedFacts'];assert all(cfg[key]==facts[key] for key in ('taskId','executionId','operationId','publicationId','meetingId')) and (facts['taskState'],facts['executionState'],facts['noEffectBasis'],facts['localDecision'],facts['taskAttempt'],facts['publicationRevision'])==('FAILED','FAILED','NOT_SENT','NEVER_MARKED',0,7) and facts['full19RowDeltaRootVerified'] is True
 assert cfg['baselineRows']=='artifacts/phase-b/b-remaining/task-11/b3-prepared-local-01/business-final.json' and len(pinned(ROOT/cfg['baselineRows'],cfg['baselineRowsSha256']))==19
 assert cfg['runtimeSourceCheckpoint']=='task-11-b3-lifecycle-r1-source-checkpoint-20261026.json' and cfg['runtimeReleaseFile']=='task-11-b3-lifecycle-runtime-release-20261026.json'
 release=parse(public_bytes(W/cfg['runtimeReleaseFile']));assert release['status']=='B3_LIFECYCLE_RUNTIME_RELEASED_ONCE' and tuple(release[key] for key in ('label','group','selector','fixtureName'))==TUPLE and release['configurationAckSha256']==ACK_SHA and type(release['maxInvocations']) is int and release['maxInvocations']==1
 assert all(release[key] is False for key in ('oneGrantAuthorized','nativeStartAuthorized','sendAuthorized'));budget(release['budget']);issued=datetime.datetime.fromisoformat(release['issuedUtc'].replace('Z','+00:00'));deadline=datetime.datetime.fromisoformat(release['startBeforeUtc'].replace('Z','+00:00'));now=datetime.datetime.now(datetime.timezone.utc);assert issued.tzinfo is not None and deadline.tzinfo is not None and issued<=now<deadline
 cp=pinned(W/cfg['runtimeSourceCheckpoint'],release['sourceCheckpointSha256']);assert isinstance(cp['files'],list) and 0<len(cp['files'])<=2048;seen=set()
 for row in cp['files']:assert row['path'] not in seen and digest(public_bytes(ROOT/row['path']))==row['sha256'];seen.add(row['path'])
 return values,cp

def run_window(lock=None):
 assert tuple(sys.argv[1:])==TUPLE,'Only exact fresh B3 lifecycle window is supported'
 cfg=pinned(W/ACK,ACK_SHA);values,cp=validate_configuration(cfg)
 return shared.execute_window(cfg,values,cp,lock,run_tuple=TUPLE,ack_sha=ACK_SHA,producer_timeout=BUDGET['producerTimeoutSeconds'],root=ROOT,workspace=W,out=OUT)

def main():
 previous=signal.signal(signal.SIGTERM,shared.stop_signal)
 try:
  with exclusive_runner_lock(OUT/'.task11-runner.lock',{'arguments':sys.argv[1:],'cwd':str(ROOT),'purpose':'exact-b3-lifecycle'}) as lock:code=run_window(lock)
 finally:signal.signal(signal.SIGTERM,previous)
 sys.exit(code)
if __name__=='__main__':main()
