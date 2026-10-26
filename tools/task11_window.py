"""Task11 exact-source serial test windows. Only the explicitly ACKed fresh schema; no provisioning."""
import base64,datetime,hashlib,json,os,pathlib,re,shlex,shutil,stat,subprocess,sys,time,xml.etree.ElementTree as ET
from task11_report_custody import exclusive_runner_lock, collect_reports
ROOT=pathlib.Path(__file__).resolve().parents[1]
W=ROOT/'.superpowers/sdd/2026-10-23-ama-phase-b-remaining'
OUT=ROOT/'artifacts/phase-b/b-remaining/task-11'
FIXTURES={
 'task11-b2-exact-isolated':('task-11-b2-runtime-fixture-application-ack-20261026.json','b0e4c66e3eaf570e40182a000f43af924ad4699ad7f906f4232043c80b16c204','ama_t11b2_d9e2fdcc611e_behavior'),
 'task11-b2-bootstrap-isolated':('task-11-b2-root-fixture-application-ack-20261026.json','8031f7bcebfb962e1a8b1ca773e57afe49c3dcfaf0fb436dd090e422455de074','ama_t11b2_d9e2fdcc611e_behavior'),
 'task11-fix02-isolated':('task-11-fix02-root-fixture-application-ack-20261025.json','532335be96e47dd428124db01165acff0c7f0790cb0a13f53930a6976c36cd7a','ama_t11fix2_075249f8161d_behavior'),
 'task11-primary':('task-11-root-fixture-application-ack-20261025.json','dcab31da53de0a4e065aa05914f5143a5afd68ddbd785a87617455db1aa1574d','ama_t11_9714255c9e2c_behavior'),
 'task11-fix01-isolated':('task-11-fix01-root-fixture-application-ack-20261025.json','35e262369af28a4d4ac70604538da152df5372ef5c286c566ebc40694650c027','ama_t11fix1_49609b94161e_behavior')
}
def digest(b):return hashlib.sha256(b).hexdigest()
def host_public_bytes(path):
 assert path.is_absolute() and path.is_relative_to(ROOT) and '..' not in path.parts and not path.is_relative_to(ROOT/'.tools')
 current=ROOT
 for part in path.relative_to(ROOT).parts:
  current=current/part;assert not current.is_symlink() and current.exists()
 assert path.is_file() and path.stat().st_size<=1048576
 return path.read_bytes()
def validate_host_matrix_release(ack,ack_sha,label,group,selector,fixture):
 required=ack['requiredBusinessPreflight']
 assert required['label']=='b2-rejected-transport-host-preflight-01'
 assert required['result']=='artifacts/phase-b/b-remaining/task-11/b2-rejected-transport-host-preflight-01/rejected-transport-host-preflight.json'
 assert required['rootVerification']=='task-11-b6-host-business-preflight-root-verification-20261026.json'
 release=json.loads(host_public_bytes(W/'task-11-b6-host-matrix-runtime-release-20261026.json'))
 assert all(release.get(key)==value for key,value in {'status':'HOST_MATRIX_RUNTIME_RELEASED_ONCE','label':label,'group':group,'selector':selector,'fixtureName':fixture,'configurationAckSha256':ack_sha}.items())
 assert type(release['maxInvocations']) is int and release['maxInvocations']==1
 issued=datetime.datetime.fromisoformat(release['issuedUtc'].replace('Z','+00:00'));deadline=datetime.datetime.fromisoformat(release['startBeforeUtc'].replace('Z','+00:00'));now=datetime.datetime.now(datetime.timezone.utc)
 assert issued.tzinfo is not None and deadline.tzinfo is not None and issued<=now<deadline
 def pinned(path,key):
  assert re.fullmatch('[0-9a-f]{64}',release[key]);raw=host_public_bytes(path);assert digest(raw)==release[key];return raw
 checkpoint=json.loads(pinned(W/'task-11-b6-host-runtime-source-checkpoint-20261026.json','sourceCheckpointSha256'))
 for item in checkpoint['files']:
  path=ROOT/item['path'];assert path.is_relative_to(ROOT) and '..' not in path.parts and not path.is_symlink() and path.is_file() and digest(path.read_bytes())==item['sha256']
 pinned(W/required['rootVerification'],'businessPreflightRootVerificationSha256')
 result=json.loads(pinned(ROOT/required['result'],'businessPreflightResultSha256'))
 assert result['status']=='PASS' and result['label']==required['label'] and result['configurationAckSha256']=='9a647cddfce9c7639b1820d20a8c7363e800b573518e489a5898f0e412ce3cca'
 assert result['hostRetainedPreflightSha256']==ack['hostRetainedPreflightSha256'] and len(result['businessPreimage'])==17
 assert result['allThreeConsumedPreimagesMatch'] is True and result['allThreeConsumedFrontsAbsent'] is True
def validate_exact_retry_release(ack,ack_sha,label,group,selector,fixture):
 assert all(ack[key] is False for key in ('runtimeAuthorized','grantAuthorized','sendAuthorized','initializationAuthorized','revokeAuthorized'))
 for key in ('transportManifestVerification','transportManifestFacts','hostRetainedPreflight','hostSourceCheckpoint','hostSourceReview','hostRuntimeFacts','hostRuntimeReview','hostRuntimeAdoption','currentSourceCheckpoint','exactRetryPreparation'):
  assert digest(host_public_bytes(W/ack[key]))==ack[key+'Sha256']
 assert ack['hostRuntimeFactsSha256']=='84732b029d9fa3caec2d865bd6adbfa1ace00f487f480827a051e3ccad429587'
 assert ack['hostRuntimeAdoptionSha256']=='ba5c5b4d34c6380c258697aa01882285bf972e1a2f78f2f72d4b2afd83f7f94f'
 assert ack['runtimeReleaseFile']=='task-11-b2-exact-retry-runtime-release-20261026.json' and ack['runtimeSourceCheckpoint']=='task-11-b2-exact-retry-source-r1-checkpoint-20261026.json'
 release=json.loads(host_public_bytes(W/ack['runtimeReleaseFile']))
 expected={'status':'EXACT_RETRY_RUNTIME_RELEASED_ONCE','label':label,'group':group,'selector':selector,'fixtureName':fixture,'configurationAckSha256':ack_sha,'hostRuntimeAdoptionSha256':ack['hostRuntimeAdoptionSha256']}
 assert all(release.get(k)==v for k,v in expected.items()) and release['oneGrantAuthorized'] is True
 assert type(release['maxInvocations']) is int and release['maxInvocations']==1
 issued=datetime.datetime.fromisoformat(release['issuedUtc'].replace('Z','+00:00'));deadline=datetime.datetime.fromisoformat(release['startBeforeUtc'].replace('Z','+00:00'));now=datetime.datetime.now(datetime.timezone.utc)
 assert issued.tzinfo is not None and deadline.tzinfo is not None and issued<=now<deadline
 raw=host_public_bytes(W/ack['runtimeSourceCheckpoint']);assert digest(raw)==release['sourceCheckpointSha256']
 for item in json.loads(raw)['files']:
  assert digest(host_public_bytes(ROOT/item['path']))==item['sha256']
 assert ack['retainedRelayBaseline']=={'get':25,'postAuthorization':3,'postPublication':1,'failures':0,'blockedPublicationAttempts':0,'withheld':False}
 assert ack['futureBudget']=={'servletRequests':43,'receiptGetIncrements':14,'destinationPostIncrements':14,'publicationPostIncrements':0,'relayFinalGet':39,'relayFinalDestinationPost':17,'relayFinalPublicationPost':1,'grantRestorations':1,'nativeReopens':1,'sqliteReadonlyObservations':3,'durableSuccessors':1,'sessionsMax':4,'concurrentHttpMax':2}
 assert ack['grantRestorationIntent']=={'currentlyAuthorized':False,'requiresSeparateRuntimeRelease':True,'actor':'task11b2-owner-43879ba2-aec0-47b2-90c6-9c79337baf88','before':[1,0,2],'after':[1,1,3],'initialization':False,'revoke':False,'count':1}
def validate_exact_readback_release(ack,ack_sha,label,group,selector,fixture):
 assert all(ack[key] is False for key in ('runtimeAuthorized','grantAuthorized','sendAuthorized','initializationAuthorized','revokeAuthorized'))
 for key in ('transportManifestVerification','transportManifestFacts','hostRetainedPreflight','hostSourceCheckpoint','hostSourceReview','hostRuntimeFacts','hostRuntimeReview','hostRuntimeAdoption','currentSourceCheckpoint','exactRetryPreparation','failedWindowCustody','failedWindowFacts','partialReview','numericDiagnosis','correctedSourceReview','correctedSourceAdoption'):
  assert digest(host_public_bytes(W/ack[key]))==ack[key+'Sha256']
 failed=json.loads(host_public_bytes(ROOT/ack['failedWindowResult']));assert digest(host_public_bytes(ROOT/ack['failedWindowResult']))==ack['failedWindowResultSha256']
 assert failed['status']=='PARTIAL_UNQUALIFIED' and failed['label']=='b2-exact-retry-01' and failed['servletRequestsAttempted']==36 and failed['sqliteObservationsAttempted']==2 and failed['grantAttempts']==1
 assert ack['currentSourceCheckpointSha256']=='ad4cdad9a92e60239d3c8e68381ca6ef3a530ea49bc931c95e7234291c894b25'
 assert (ack['existingTaskId'],ack['existingExecutionId'],ack['existingCommandKey'])==('3588aaba-ecfa-4889-8954-46c1454447d4','65fa6b21-22da-4a2e-a886-341b16978106','task11-b2-exact-retry-winner-01')
 assert ack['runtimeReleaseFile']=='task-11-b2-exact-retry-readback-runtime-release-20261026.json' and ack['runtimeSourceCheckpoint']=='task-11-b2-exact-retry-readback-source-promoted-checkpoint-20261026.json'
 release=json.loads(host_public_bytes(W/ack['runtimeReleaseFile']))
 assert all(release.get(k)==v for k,v in {'status':'EXACT_RETRY_READBACK_RUNTIME_RELEASED_ONCE','label':label,'group':group,'selector':selector,'fixtureName':fixture,'configurationAckSha256':ack_sha}.items())
 assert release['oneGrantAuthorized'] is False and type(release['receiptOnlyCallsAuthorized']) is int and release['receiptOnlyCallsAuthorized']==7 and type(release['maxInvocations']) is int and release['maxInvocations']==1
 issued=datetime.datetime.fromisoformat(release['issuedUtc'].replace('Z','+00:00'));deadline=datetime.datetime.fromisoformat(release['startBeforeUtc'].replace('Z','+00:00'));now=datetime.datetime.now(datetime.timezone.utc)
 assert issued.tzinfo is not None and deadline.tzinfo is not None and issued<=now<deadline
 raw=host_public_bytes(W/ack['runtimeSourceCheckpoint']);assert digest(raw)==release['sourceCheckpointSha256']
 for item in json.loads(raw)['files']:assert digest(host_public_bytes(ROOT/item['path']))==item['sha256']
 assert ack['retainedRelayBaseline']=={'get':39,'postAuthorization':17,'postPublication':1,'failures':0,'blockedPublicationAttempts':0,'withheld':False}
 assert ack['futureBudget']=={'servletRequests':7,'receiptGetIncrements':0,'destinationPostIncrements':0,'publicationPostIncrements':0,'relayFinalGet':39,'relayFinalDestinationPost':17,'relayFinalPublicationPost':1,'grantRestorations':0,'nativeReopens':0,'sqliteReadonlyObservations':1,'durableSuccessors':0,'sessionsMax':4,'sessionInsertsMax':4,'sessionDeletesMax':4,'concurrentHttpMax':1,'receiptOnlyContexts':1,'businessWrites':0}
def run_window(lock_identity):
 label,group,selector=sys.argv[1:4]
 fixture=sys.argv[4] if len(sys.argv)==5 else "task11-primary"
 assert len(sys.argv) in (4,5) and fixture in FIXTURES
 ackname,ACK,schema=FIXTURES[fixture]
 if not re.fullmatch(r'[A-Za-z][A-Za-z0-9_]*(?:#[A-Za-z][A-Za-z0-9_]*)?',selector) or not re.fullmatch(r'[a-z0-9-]+',label) or group not in ('it','unit'):raise SystemExit('Invalid bounded window')
 bootstrap=fixture=='task11-b2-bootstrap-isolated'
 runtime_b2=fixture=='task11-b2-exact-isolated'
 runtime_preflight=runtime_b2 and (label,group,selector)==('b2-runtime-preflight-01','it','Task11B2RuntimePreflightIT')
 runtime_pipeline=runtime_b2 and (label,group,selector)==('b2-pipeline-01','it','Task11B2PipelineIT')
 runtime_local=runtime_b2 and (label,group,selector)==('b2-local-acceptance-01','it','Task11B2LocalAcceptanceIT')
 runtime_local_continuation=runtime_b2 and (label,group,selector)==('b2-local-acceptance-continuation-01','it','Task11B2LocalAcceptanceContinuationIT')
 runtime_host_preflight=runtime_b2 and (label,group,selector)==('b2-rejected-transport-host-preflight-01','it','Task11B2RejectedTransportHostPreflightIT')
 runtime_host_matrix=runtime_b2 and (label,group,selector)==('b2-rejected-transport-host-01','it','Task11B2RejectedTransportHostContinuationIT')
 runtime_exact_retry=runtime_b2 and (label,group,selector)==('b2-exact-retry-01','it','Task11B2ExactRetryIT')
 runtime_readback=runtime_b2 and (label,group,selector)==('b2-exact-retry-readback-01','it','Task11B2ExactRetryReadbackIT')
 runtime_host=runtime_host_preflight or runtime_host_matrix
 runtime_transport_manifest=runtime_readback or runtime_exact_retry or runtime_host or runtime_b2 and (label,group,selector)==('b2-rejected-transport-manifest-01','it','Task11B2RejectedTransportManifestContinuationIT')
 runtime_transport_continuation=runtime_transport_manifest or runtime_b2 and (label,group,selector)==('b2-rejected-transport-continuation-01','it','Task11B2RejectedTransportContinuationIT')
 runtime_transport=runtime_transport_continuation or runtime_b2 and (label,group,selector)==('b2-rejected-transport-01','it','Task11B2RejectedTransportIT')
 runtime_rejection=runtime_b2 and (label,group,selector)==('b2-rejection-01','it','Task11B2RejectedContinuationIT')
 if runtime_b2:
  assert runtime_preflight or runtime_pipeline or runtime_local or runtime_local_continuation or runtime_rejection or runtime_transport,'B2 runtime requires exact configured stage'
  if runtime_pipeline:ackname,ACK='task-11-b2-pipeline-fixture-configuration-ack-20261026.json','acdd3252b97f82637faf54d5fcf75299e83ec9a541a2aca3f09d11e9a4a2d69a'
  if runtime_local:ackname,ACK='task-11-b2-local-acceptance-configuration-ack-20261026.json','5d2a74f2be5ac910c3cc84f59113b5678e4a39c71caf4fcdadb3cf9c5d38e279'
  if runtime_local_continuation:ackname,ACK='task-11-b2-local-continuation-configuration-ack-20261026.json','7473a20f46ecc86d56403acd4e414f56a79a9cd552643e32950927896ab66e26'
  if runtime_transport:ackname,ACK='task-11-b2-rejected-transport-configuration-ack-20261026.json','40f34957837c5ddccf98abf87e50ef3ff5fb40a00be208929c2c00efb1745837'
  if runtime_transport_continuation:ackname,ACK='task-11-b2-rejected-transport-continuation-configuration-ack-20261026.json','60499f4b2a259902ee0af5bfa6a72886f4d909446e0067acb00fe6714c187343'
  if runtime_transport_manifest:ackname,ACK='task-11-b2-rejected-transport-manifest-configuration-ack-20261026.json','80ccbb1d8d15730745b2c0924aa68776c2d02cdab83fb0b13c6bf8e09307ab0e'
  if runtime_host_preflight:ackname,ACK='task-11-b6-host-preflight-configuration-ack-20261026.json','9a647cddfce9c7639b1820d20a8c7363e800b573518e489a5898f0e412ce3cca'
  if runtime_host_matrix:ackname,ACK='task-11-b6-host-matrix-configuration-ack-20261026.json','d4c2820fac12717ade12074633e3a4f8fe81438f6ed89399920e8ebaf4a382d5'
  if runtime_rejection:ackname,ACK='task-11-b2-rejection-configuration-ack-20261026.json','45c793ed0d72b3c2cdf79d9c6b1e4fe6595629a99cdc6da6ded5f946b66e1ffb'
  if runtime_exact_retry:ackname,ACK='task-11-b2-exact-retry-configuration-ack-r1-20261026.json','f2db0e2e19d3a21fe06a87073cc9c0336e809cc00399b1b22cbe72faeadbc591'
  if runtime_readback:ackname,ACK='task-11-b2-exact-retry-readback-promoted-configuration-ack-20261026.json','5c2c7cdb5674795bd113f000659d91049f2aed09a0f8e1ab946110d58031e8c5'
 if bootstrap:assert (label,group,selector)==('b2-bootstrap-01','it','Task11BootstrapIT'),'B2 database-only bootstrap requires exact released window'
 ackbytes=(W/ackname).read_bytes();assert digest(ackbytes)==ACK
 ack=json.loads(ackbytes);private=ROOT/ack['privateEnv'];assert stat.S_IMODE(private.stat().st_mode)==0o600 and stat.S_IMODE(private.parent.stat().st_mode)==0o700
 if bootstrap:
  expected={'status':'READY_ACK_ISSUED_B2_DATABASE_ONLY_BOOTSTRAP_AUTHORIZED','fixtureName':fixture,'allowedGroup':group,'allowedSelector':selector,'allowedLabel':label,'databaseOnly':True,'originalProviderAccess':False,'runtimeProviderAccess':False,'owner':'task11b2-d9e2fdcc611e4e21bb360e539ca46258','cid':'11009a6b34fef0416e651100fbde05a07067dbcbd973f1ba731c08b2281140ad','port':46151,'privateEnv':'.tools/task11b2-d9e2fdcc611e4e21bb360e539ca46258/database.env.sh'}
  assert all(ack.get(k)==v for k,v in expected.items()) and ack['ready']['principal']=='t11b2_d9e2fdcc611e@%'
 b2_allocation=None;b2_overlay=None;b2_credentials=None
 if runtime_b2:
  expected={'status':('EXACT_RETRY_READBACK_CONFIGURATION_ACK_NO_STAGE_EXECUTION' if runtime_readback else 'EXACT_RETRY_CONFIGURATION_ACK_NO_STAGE_EXECUTION' if runtime_exact_retry else 'HOST_PREFLIGHT_CONFIGURATION_ACK_NO_STAGE_EXECUTION' if runtime_host_preflight else 'HOST_MATRIX_CONFIGURATION_ACK_NO_STAGE_EXECUTION' if runtime_host_matrix else 'MANIFEST_CONTINUATION_INERT_CONFIGURATION_SOURCE_ONLY' if runtime_transport_manifest else 'REJECTED_TRANSPORT_CONTINUATION_CONFIGURATION_ACK_NO_STAGE_EXECUTION' if runtime_transport_continuation else 'REJECTED_TRANSPORT_CONFIGURATION_ACK_NO_STAGE_EXECUTION' if runtime_transport else 'REJECTION_CONFIGURATION_ACK_NO_STAGE_EXECUTION' if runtime_rejection else 'LOCAL_CONTINUATION_CONFIGURATION_ACK_NO_STAGE_EXECUTION' if runtime_local_continuation else 'LOCAL_ACCEPTANCE_CONFIGURATION_ACK_NO_STAGE_EXECUTION' if runtime_local else 'PIPELINE_CONFIGURATION_ACK_NO_STAGE_EXECUTION' if runtime_pipeline else 'OPERATIONAL_CONFIGURATION_ACK_NO_STAGE_EXECUTION'),'fixtureName':fixture,'owner':'task11b2-d9e2fdcc611e4e21bb360e539ca46258','schema':schema,'username':'t11b2_d9e2fdcc611e','cid':'11009a6b34fef0416e651100fbde05a07067dbcbd973f1ba731c08b2281140ad','port':46151,'privateEnv':'.tools/task11b2-d9e2fdcc611e4e21bb360e539ca46258/runtime.env.sh','runtimeAuthorized':False,'sendAuthorized':False}
  assert all(ack.get(k)==v for k,v in expected.items())
  assert (ack['initialAllowedStage']['label'],ack['initialAllowedStage']['group'],ack['initialAllowedStage']['selector'])==(label,group,selector)
  pins=ack['publicInputPins'];assert set(pins)=={'task-11-b2-root-fixture-application-ack-20261026.json','task-11-b2-bootstrap-root-verification-20261026.json','task-11-b2-runtime-allocation-intent-20261026.json','task-11-b2-runtime-overlay-prepared-ack-20261026.json','task-11-b2-credentials-pki-created-ack-20261026.json','task-11-b2-runtime-harness-checkpoint-plan-20261026.md'}
  for name,expected_sha in pins.items():
   public=W/name;assert pathlib.Path(name).name==name and not public.is_symlink() and public.is_file() and digest(public.read_bytes())==expected_sha
  if runtime_pipeline or runtime_local or runtime_local_continuation or runtime_rejection or runtime_transport:
   assert ack['preflightVerification']=='task-11-b2-runtime-preflight-root-verification-20261026.json' and ack['preflightVerificationSha256']=='b76b5469131ca9df5dc6521f8d4432ba3799c0d49aa72b942ce630ab3cd710f9'
   assert digest((W/ack['preflightVerification']).read_bytes())==ack['preflightVerificationSha256']
  if runtime_local or runtime_local_continuation or runtime_rejection or runtime_transport:
   assert ack['pipelineVerification']=='task-11-b2-pipeline-root-verification-20261026.json' and ack['pipelineVerificationSha256']=='7dcbde521d7094f4b01f5846e1dff3c08e254f48d43e6e0f0675b1bcccaf9458'
   assert ack['pipelineAttainedFacts']=='task-11-b2-pipeline-attained-facts-20261026.json' and ack['pipelineAttainedFactsSha256']=='46dce27194f97d410ccf87768d9a382a47a2f38f0ad427626db15a231c6229a0'
   assert digest((W/ack['pipelineVerification']).read_bytes())==ack['pipelineVerificationSha256'] and digest((W/ack['pipelineAttainedFacts']).read_bytes())==ack['pipelineAttainedFactsSha256']
  if runtime_local_continuation or runtime_rejection or runtime_transport:
   assert ack['initializationAuthorized'] is False and ack['grantAuthorized'] is False
   assert ack['partialVerification']=='task-11-b2-local-acceptance-root-verification-20261026.json' and ack['partialVerificationSha256']=='9a24397f74bc52d1aeb8d7581a58ba3e484af6a07c18b622f4c246da14bdf388'
   assert ack['partialAttainedFacts']=='task-11-b2-local-acceptance-attained-facts-20261026.json' and ack['partialAttainedFactsSha256']=='7d2c74011cd49aee871ec94de9c48920a8d689deb299956706d4efb0568c996d'
   assert digest((W/ack['partialVerification']).read_bytes())==ack['partialVerificationSha256'] and digest((W/ack['partialAttainedFacts']).read_bytes())==ack['partialAttainedFactsSha256']
  if runtime_rejection or runtime_transport:
   assert ack['localClosureVerification']=='task-11-b2-local-continuation-root-verification-20261026.json' and ack['localClosureVerificationSha256']=='81e73a4711d16cb51c60a923ac037d408c9dfecd83d612033da5ead312fcd435'
   assert ack['localClosureAttainedFacts']=='task-11-b2-local-continuation-attained-facts-20261026.json' and ack['localClosureAttainedFactsSha256']=='02c4749472290ea1a4a8f9a560a2e15fba1602c3ca87b94530c20f45f4d8708c'
   assert digest((W/ack['localClosureVerification']).read_bytes())==ack['localClosureVerificationSha256'] and digest((W/ack['localClosureAttainedFacts']).read_bytes())==ack['localClosureAttainedFactsSha256']
  if runtime_transport:
   assert ack['revokeAuthorized'] is False
   assert ack['rejectionVerificationSha256']=='e99d41f0a56b07338f7805ca0e7f03ef10dca674b8d6416a771bacff7e1c3106'
   assert ack['rejectionAttainedFactsSha256']=='fd2ac24399e9443af4dd9fe7078c6c2b4ee0a8f7201428249bbf16551b0ef556'
   for name,key in [('rejectionVerification','rejectionVerificationSha256'),('rejectionAttainedFacts','rejectionAttainedFactsSha256')]:
    assert digest((W/ack[name]).read_bytes())==ack[key]
  if runtime_transport_continuation:
   assert ack['transportPartialVerification']=='task-11-b2-rejected-transport-root-verification-20261026.json' and ack['transportPartialVerificationSha256']=='ad5b64f49e877ca3f80e4878fe31a799818a6fd2297a20cb646fd704719541bf'
   assert ack['transportPartialFacts']=='task-11-b2-rejected-transport-attained-facts-20261026.json' and ack['transportPartialFactsSha256']=='43e580194ac602fbabcb2f5b457ecd3b9c96b6fa0078a17806000834035f9cd5'
   for name,key in [('transportPartialVerification','transportPartialVerificationSha256'),('transportPartialFacts','transportPartialFactsSha256')]:
    assert digest((W/ack[name]).read_bytes())==ack[key]
  if runtime_transport_manifest:
   assert ack['transportContinuationVerification']=='task-11-b2-rejected-transport-continuation-root-verification-20261026.json' and ack['transportContinuationVerificationSha256']=='5f67bff3da16023cc411d762165083a6162929b2ca15fdfe83072290a5252f18'
   assert ack['transportContinuationFacts']=='task-11-b2-rejected-transport-continuation-attained-facts-20261026.json' and ack['transportContinuationFactsSha256']=='a70911410827f27aa748fec8ab63ae7bf501ea1298edc6666d86793dc828271f'
   for name,key in [('transportContinuationVerification','transportContinuationVerificationSha256'),('transportContinuationFacts','transportContinuationFactsSha256')]:
    assert digest((W/ack[name]).read_bytes())==ack[key]
  if runtime_host:
   host_pins={
    'transportManifestVerification':('task-11-b2-rejected-transport-manifest-root-verification-20261026.json','3e61016a1f94840289bb325cd0a67d4747c74dffd65f555b5587e56ca1d226ad'),
    'transportManifestFacts':('task-11-b2-rejected-transport-manifest-attained-facts-20261026.json','9588a52656f0a6a4df6a28a8c38fbcf2c67ba149e2715e3af267bf3f0afb416c'),
    'hostRetainedPreflight':('task-11-b6-host-retained-preflight-20261026.json','be3bff5b61690ac21792d28b63efca909c2aeac0f6661e50ab1e682aa1c86faf'),
    'hostSourceCheckpoint':('task-11-b6-host-source-checkpoint-20261026.json','cd6cc0bc2ec3dc982caeb827430a19933ddc1a6b3e802670f78817d21b28f506'),
    'hostSourceReview':('task-11-b6-host-source-independent-review-20261026.md','162a8f5ae0adbdd7094a206b5a78122d4f00c5c1e156826609dec53216b5de01')}
   for key,(name,sha) in host_pins.items():
    assert ack[key]==name and ack[key+'Sha256']==sha
    assert digest(host_public_bytes(W/name))==sha
   baseline={'get':1,'postAuthorization':3,'postPublication':1,'failures':0,'blockedPublicationAttempts':0,'withheld':False}
   assert ack['retainedRelayBaseline']==baseline
   assert type(ack['incrementalGetBudget']) is int and ack['incrementalGetBudget']==(0 if runtime_host_preflight else 24)
   assert type(ack['expectedCumulativeGetAfter']) is int and ack['expectedCumulativeGetAfter']==(1 if runtime_host_preflight else 25)
   observed=json.loads(host_public_bytes(W/ack['hostRetainedPreflight']))
   assert all(observed['relayCounters'][key]==value for key,value in baseline.items())
   if runtime_host_matrix:validate_host_matrix_release(ack,ACK,label,group,selector,fixture)
  if runtime_exact_retry:validate_exact_retry_release(ack,ACK,label,group,selector,fixture)
  if runtime_readback:validate_exact_readback_release(ack,ACK,label,group,selector,fixture)
  b2_allocation=json.loads((W/'task-11-b2-runtime-allocation-intent-20261026.json').read_text());b2_overlay=json.loads((W/'task-11-b2-runtime-overlay-prepared-ack-20261026.json').read_text());b2_credentials=json.loads((W/'task-11-b2-credentials-pki-created-ack-20261026.json').read_text())
  assert b2_overlay['privateEnv']==ack['privateEnv'] and b2_overlay['credentialPkiAckSha256']==pins['task-11-b2-credentials-pki-created-ack-20261026.json'] and b2_overlay['allocationSha256']==pins['task-11-b2-runtime-allocation-intent-20261026.json']
 env=os.environ.copy()
 if bootstrap or runtime_b2:env={k:v for k,v in env.items() if not k.startswith('AMA_')}  # No inherited old fixture/provider/runtime aliases.
 env['JAVA_HOME']='/home/tanren/ama/.worktrees/phase-a/.tools/zulu21.52.203-ca-jdk21.0.12.1-linux_x64'
 assert pathlib.Path(env['JAVA_HOME'],'bin/java').is_file()
 env['PATH']=env['JAVA_HOME']+'/bin:'+env['PATH']
 seen_b2_keys=set()
 for line in private.read_text().splitlines():
  if not line.strip() or line.lstrip().startswith('#'):continue
  match=re.fullmatch(r'(?:export )?([A-Z][A-Z0-9_]*)=(.*)',line);assert match
  if bootstrap:assert match.group(1) in ('AMA_TEST_DATABASE_URL','AMA_TEST_DATABASE_USERNAME','AMA_TEST_DATABASE_PASSWORD','AMA_TEST_CONTAINER','AMA_TASK11_OWNER','AMA_TASK11_RUN_ROOT')
  if runtime_b2:
   assert match.group(1) in b2_overlay['envKeys'] and match.group(1) not in seen_b2_keys;seen_b2_keys.add(match.group(1))
  values=shlex.split(match.group(2));assert len(values)==1;env[match.group(1)]=values[0]
 assert ack['schema']==schema and env['AMA_TEST_DATABASE_URL']=='jdbc:mysql://127.0.0.1:46151/'+schema
 if bootstrap:
  assert env['AMA_TEST_DATABASE_USERNAME']=='t11b2_d9e2fdcc611e' and re.fullmatch(r'[A-Za-z0-9_-]{43}',env['AMA_TEST_DATABASE_PASSWORD'])
  assert env['AMA_TEST_CONTAINER']==ack['cid'] and env['AMA_TASK11_OWNER']==ack['owner'] and env['AMA_TASK11_RUN_ROOT']==str(ROOT/'.tools'/ack['owner'])
 if runtime_b2:
  assert seen_b2_keys==set(b2_overlay['envKeys']) and len(seen_b2_keys)==13
  assert env['AMA_TEST_DATABASE_USERNAME']==ack['username'] and re.fullmatch(r'[A-Za-z0-9_-]{43}',env['AMA_TEST_DATABASE_PASSWORD']) and env['AMA_TEST_CONTAINER']==ack['cid']
  expected_env={'AMA_TASK11_OWNER':ack['owner'],'AMA_TASK11_RUN_ROOT':b2_allocation['privateRoot'],'AMA_TASK11_PKI_ROOT':b2_allocation['native']['pkiRoot'],'AMA_TASK11_PROVIDER_ROOT':b2_allocation['knowledge']['root'],'AMA_TASK11_MODEL_AUTHORIZATION_FILE':b2_overlay['modelAuthorizationFile'],'AMA_TASK11_MODEL_ROOT':b2_allocation['model']['root'],'AMA_TASK11_NATIVE_ROOT':b2_allocation['native']['root'],'AMA_TASK11_ARCHIVE_ROOT':b2_allocation['archive']['root'],'AMA_TASK11_ALLOCATION':str(W/'task-11-b2-runtime-allocation-intent-20261026.json')}
  assert all(env[k]==v for k,v in expected_env.items())
 env['AMA_TASK11_FIXTURE']=fixture
 extra_credential_ack=None;extra_credential_sha=None
 if not bootstrap and not runtime_b2:
  env['AMA_TASK11_PROVIDER_ROOT']=str(ROOT/'.tools/task11-9714255c9e2c4861a2795cfaaec8e97f/knowledge-fixture-20261025')
  owned_provider_selectors={'b1-fix03-member-publication-01':'Task11Fix03MemberPublicationIT','b1-fix03-member-publication-continuation-01':'Task11Fix03MemberPublicationContinuationIT'}
  if label in owned_provider_selectors:
   assert fixture=='task11-fix02-isolated' and selector==owned_provider_selectors[label]
   raw=(W/'task-11-b1-fix03-kb-credential-ack-20261026.json').read_bytes();extra_credential_sha=digest(raw);assert extra_credential_sha=='6b9151855b1b2bfd2640bddf7c1dc8144403a4ef6a6eacda414cc2987883dcea'
   extra_credential_ack=json.loads(raw);env['AMA_TASK11_TEST_OWNED_PROVIDER_ROOT']=extra_credential_ack['providerRoot']
  else:env.pop('AMA_TASK11_TEST_OWNED_PROVIDER_ROOT',None)
 target=OUT/label;target.mkdir(exist_ok=False)
 env.update(JAVA_TOOL_OPTIONS='-Xmx512m -XX:ActiveProcessorCount=2',MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2',MAVEN_CONFIG='-Dtest=Task11NoImplicitUnitSuite -Dsurefire.failIfNoSpecifiedTests=false',AMA_TASK11_EVIDENCE_WINDOW=str(target),AMA_TASK11_REPOSITORY_ROOT=str(ROOT))
 if group=='unit':env.pop('MAVEN_CONFIG',None)
 if not bootstrap and not runtime_b2:
  for suffix in ('OWNER','RUN_ROOT','S3_ENV','PKI_ROOT'):env['AMA_TASK09_'+suffix]=env['AMA_TASK11_'+suffix]
  env['AMA_TASK09_EVIDENCE_WINDOW']=str(target)
 elif runtime_b2:
  for suffix in ('OWNER','RUN_ROOT','PKI_ROOT'):env['AMA_TASK09_'+suffix]=env['AMA_TASK11_'+suffix]
  env['AMA_TASK09_EVIDENCE_WINDOW']=str(target)
 modules=[ROOT/'backend/integration-tests'] if group=='it' else sorted(p.parent for p in ROOT.glob('backend/*/pom.xml'))
 matched=[p for m in modules for p in (m/'src/test/java').rglob(selector.split('#',1)[0]+'.java')]
 assert len(matched)==1,'Selector must identify exactly one discovered class'
 canonical=re.search(r'package\s+([^;]+);',matched[0].read_text()).group(1).strip()+'.'+matched[0].stem
 if bootstrap:
  secret_values=[env['AMA_TEST_DATABASE_PASSWORD'].encode()]
 elif runtime_b2:
  secret_values=[];owner=ROOT/'.tools'/ack['owner'];assert not owner.is_symlink() and stat.S_IMODE(owner.stat().st_mode)==0o700
  def private_marker(name):
   path=pathlib.Path(name);assert '..' not in path.parts and path.is_relative_to(owner)
   current=owner
   for part in path.relative_to(owner).parts:
    current=current/part;assert not current.is_symlink() and current.exists()
    assert stat.S_IMODE(current.stat().st_mode)==(0o600 if current==path else 0o700)
   assert path.is_file() and path.resolve().is_relative_to(owner.resolve()) and path.stat().st_size<=65536
   return path.read_bytes()
  private_inputs=b2_credentials['privateScanInputs']+b2_overlay['privateScanInputsAdditional'];assert len(private_inputs)==8 and len(set(private_inputs))==8
  for name,value in [(None,env['AMA_TEST_DATABASE_PASSWORD'].encode())]+[(name,private_marker(name)) for name in private_inputs]:
   encoded=base64.b64encode(value);url=base64.urlsafe_b64encode(value);secret_values.extend([value,value.hex().encode(),encoded,encoded.rstrip(b'='),url,url.rstrip(b'=')])
   if name and name.endswith(('.pk8','.spki')):
    kind=b'PRIVATE KEY' if name.endswith('.pk8') else b'PUBLIC KEY';secret_values.append(b'-----BEGIN '+kind+b'-----\n'+b'\n'.join(encoded[i:i+64] for i in range(0,len(encoded),64))+b'\n-----END '+kind+b'-----')
 else:
  secret_values=[v.encode() for k,v in env.items() if k.startswith('AMA_') and any(x in k for x in ('PASSWORD','SECRET','TOKEN'))]
  credential_ack=json.loads((W/'task-11-knowledge-credential-root-ack-20261025.json').read_text())
  for name in credential_ack['privateScanInputs']+(extra_credential_ack['privateScanInputs'] if extra_credential_ack else []):
   value=pathlib.Path(name).read_bytes();encoded=base64.b64encode(value);url=base64.urlsafe_b64encode(value)
   secret_values.extend([value,value.hex().encode(),encoded,encoded.rstrip(b'='),url,url.rstrip(b'=')])
   if name.endswith(('.pk8','.spki')):
    kind=b'PRIVATE KEY' if name.endswith('.pk8') else b'PUBLIC KEY'
    secret_values.append(b'-----BEGIN '+kind+b'-----\n'+b'\n'.join(encoded[i:i+64] for i in range(0,len(encoded),64))+b'\n-----END '+kind+b'-----')

 report_glob='backend/*/target/'+('failsafe-reports' if group=='it' else 'surefire-reports')+'/TEST-*.xml'
 previous={str(f):(f.stat().st_mtime_ns,digest(f.read_bytes())) for f in ROOT.glob(report_glob)}
 (target/'reports-before.json').write_text(json.dumps(previous,indent=2)+'\n')
 for f in ROOT.glob(report_glob):
  assert f.resolve().is_relative_to((ROOT/'backend').resolve()) and 'target' in f.parts
  f.unlink()
 assert not list(ROOT.glob(report_glob))
 (target/'reports-cleared.json').write_text(json.dumps({'paths':list(previous),'verifiedAbsent':True,'atNs':time.time_ns(),'atMonotonicNs':time.monotonic_ns(),'lock':lock_identity},indent=2)+'\n')
 candidates=subprocess.check_output(['git','ls-files','--cached','--others','--exclude-standard','--','backend','contracts/openapi','tools/checks/run.py','tools/checks/database_design.py','tools/task11_window.py','tools/task11_report_custody.py','tools/tests/test_task11_report_custody.py','tools/providers']+(['tools/task11_rejected_transport.py'] if runtime_transport else []),cwd=ROOT,text=True).splitlines()
 manifest=[];head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
 changed=set(subprocess.check_output(['git','ls-files','--modified','--others','--exclude-standard','--','backend','contracts/openapi','tools'],cwd=ROOT,text=True).splitlines())
 for name in sorted(set(candidates)):
  path=ROOT/name
  if not path.is_file() or '/target/' in name:continue
  b=path.read_bytes();manifest.append({'path':name,'bytes':len(b),'sha256':digest(b)})
  if name in changed:
   saved=target/'source'/name;saved.parent.mkdir(parents=True,exist_ok=True);saved.write_bytes(b)
 (target/'source-manifest.json').write_text(json.dumps({'base':head,'files':manifest},indent=2)+'\n')
 def sample(where):
  cmd=['docker','exec',ack['cid'],'sh','-c','for f in memory.current memory.peak memory.max memory.events; do echo "$f"; cat "/sys/fs/cgroup/$f"; done']
  r=subprocess.run(cmd,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True);(target/('resource-'+where+'.txt')).write_text(r.stdout);assert r.returncode==0
 sample('before');started=time.time_ns();started_mono=time.monotonic_ns();command=[sys.executable,'tools/checks/run.py',group,selector]
 (target/'command.json').write_text(json.dumps({'command':command,'canonicalSelector':canonical,'reportsBeforeSha256':digest((target/'reports-before.json').read_bytes()),'reportsClearedSha256':digest((target/'reports-cleared.json').read_bytes()),'cwd':str(ROOT),'startedNs':started,'startedMonotonicNs':started_mono,'runnerLock':lock_identity,'startedUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'heapMiB':512,'processors':2,'schema':ack['schema'],'fixtureName':fixture,'providerRoot':env.get('AMA_TASK11_PROVIDER_ROOT'),'testOwnedProviderRoot':env.get('AMA_TASK11_TEST_OWNED_PROVIDER_ROOT'),'testOwnedCredentialAckSha256':extra_credential_sha,'ackSha256':ACK,'sourceManifestSha256':digest((target/'source-manifest.json').read_bytes()),**({'databaseOnly':True,'privateEnv':ack['privateEnv']} if bootstrap else {}),**({'runtimePreflightOnly':runtime_preflight,'runtimePipelineOnly':runtime_pipeline,'runtimeLocalAcceptanceOnly':runtime_local,'runtimeLocalContinuationOnly':runtime_local_continuation,'runtimeRejectionOnly':runtime_rejection,'runtimeRejectedTransportOnly':runtime_transport,'runtimeRejectedTransportContinuationOnly':runtime_transport_continuation,'runtimeRejectedTransportManifestOnly':runtime_transport_manifest,'runtimeHostPreflightOnly':runtime_host_preflight,'runtimeHostMatrixOnly':runtime_host_matrix,'runtimeExactRetryOnly':runtime_exact_retry,'runtimeExactRetryReadbackOnly':runtime_readback,'privateEnv':ack['privateEnv'],'runtimeOverlayAckSha256':ack['publicInputPins']['task-11-b2-runtime-overlay-prepared-ack-20261026.json'],'credentialPkiAckSha256':ack['publicInputPins']['task-11-b2-credentials-pki-created-ack-20261026.json'],'databaseAckSha256':ack['publicInputPins']['task-11-b2-root-fixture-application-ack-20261026.json'],'allocationSha256':ack['publicInputPins']['task-11-b2-runtime-allocation-intent-20261026.json'],'privateScanInputPaths':private_inputs,'pkiRoot':env['AMA_TASK11_PKI_ROOT'],'nativeRoot':env['AMA_TASK11_NATIVE_ROOT'],'archiveRoot':env['AMA_TASK11_ARCHIVE_ROOT'],'modelRoot':env['AMA_TASK11_MODEL_ROOT']} if runtime_b2 else {})},indent=2)+'\n')
 with (target/'run.log').open('w') as log:
  result=subprocess.Popen(command,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
  (target/'producer.json').write_text(json.dumps({'pid':result.pid,'runnerPid':os.getpid(),'startedMonotonicNs':time.monotonic_ns(),'command':command},indent=2)+'\n')
  result.wait()
 child_exit=time.time_ns();child_exit_mono=time.monotonic_ns()
 (target/'child-exit.json').write_text(json.dumps({'pid':result.pid,'returnCode':result.returncode,'childExitNs':child_exit,'childExitMonotonicNs':child_exit_mono},indent=2)+'\n')
 sample('after');counts=[];cases=[];reports=[]
 collected=collect_reports(lambda:list(ROOT.glob(report_glob)),target/'run.log',canonical,started,child_exit_mono)
 # Check the manifest again before qualifying artifacts; a changed source invalidates this window.
 source_matches=all((ROOT/r['path']).is_file() and digest((ROOT/r['path']).read_bytes())==r['sha256'] for r in manifest)
 finished=collected['closedNs']
 for row in collected.pop('reports'):
  raw=row.pop('raw');report=pathlib.Path(row['path']);tree=ET.fromstring(raw);suite=tree.get('name','')
  sha=row['sha256'];prior=previous.get(str(report))
  qualified=collected['qualified'] and source_matches and prior!=(row['mtimeNs'],sha)
  reports.append({**row,'path':str(report.relative_to(ROOT)),'previous':prior,'qualified':qualified,'suite':suite,'canonicalLogAssociation':('Running '+suite) in (target/'run.log').read_text(errors='replace')})
  if not qualified:continue
  dest=target/'junit'/report.relative_to(ROOT);dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(raw)
  assert digest(dest.read_bytes())==sha
  counts.append({'path':str(dest.relative_to(target)),'suite':suite,**{k:int(tree.get(k,'0')) for k in ('tests','failures','errors','skipped')}})
  for c in tree.findall('testcase'):cases.append({'suite':suite,'reportSha256':sha,'reportMtimeNs':row['mtimeNs'],'startedNs':started,'endedNs':finished,'class':c.get('classname'),'name':c.get('name'),'failure':c.find('failure') is not None,'error':c.find('error') is not None,'skipped':c.find('skipped') is not None})
 (target/'report-custody.json').write_text(json.dumps({'startedNs':started,'startedMonotonicNs':started_mono,'childExitNs':child_exit,'childExitMonotonicNs':child_exit_mono,'endedNs':finished,'observedCollectionCloseNs':finished,'canonicalSelector':canonical,'runnerLock':lock_identity,'sourceManifestMatchesAtCollection':source_matches,'collection':collected,'reports':reports,'logSha256':digest((target/'run.log').read_bytes())},indent=2)+'\n')
 for f in target.rglob('*'):
  if f.is_file():assert not any(len(v)>3 and v in f.read_bytes() for v in secret_values),'private value found; do not print'
 sealed_ns=time.time_ns();sealed_mono=time.monotonic_ns()
 assert lock_identity['acquiredMonotonicNs']<=started_mono<=child_exit_mono<=collected['openedMonotonicNs']<=collected['closedMonotonicNs']<=sealed_mono
 (target/'seal.json').write_text(json.dumps({'sealedNs':sealed_ns,'sealedMonotonicNs':sealed_mono,'runnerLock':lock_identity,'collectionCustodySha256':digest((target/'report-custody.json').read_bytes()),'rawCopiesVerified':True},indent=2)+'\n')
 totals={k:sum(c[k] for c in counts) for k in ('tests','failures','errors','skipped')}
 exitcode=result.returncode or (1 if totals['tests']==0 or any(totals[k] for k in ('failures','errors','skipped')) or any(not r['qualified'] for r in reports) else 0)
 summary={'exitCode':exitcode,'processExitCode':result.returncode,'startedNs':started,'childExitNs':child_exit,'finishedNs':finished,'observedCollectionCloseNs':finished,'collectorClosedMonotonicNs':collected['closedMonotonicNs'],'suites':counts,'totals':totals,'cases':cases}
 (target/'result.json').write_text(json.dumps(summary,indent=2)+'\n');print(json.dumps(summary,indent=2),flush=True)
 sys.exit(exitcode)
def main():
 with exclusive_runner_lock(OUT/'.task11-runner.lock',{'arguments':sys.argv[1:],'cwd':str(ROOT)}) as lock_identity:
  run_window(lock_identity)
if __name__=='__main__':main()
