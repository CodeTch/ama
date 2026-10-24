from pathlib import Path
import json,hashlib,subprocess
r=Path('/home/tanren/ama/.worktrees/phase-b-completion');e=r/'artifacts/phase-b/b-remaining/task-05-fix-01'
base='857102b917bbef77c38fae3211e3a65207a98c6c'
sha=lambda data:hashlib.sha256(data).hexdigest()
scope=json.loads((e/'explicit-source-paths.json').read_text())
variants={}
p='backend/integration-tests/src/test/java/ama/storage/Task05FixOpenIT.java';s=(r/p).read_text()
s=s.replace('    @Test void genuineLocalHeadersExitAtBatchDeadlineBeforeFixtureRelease() throws Exception { localOpen(false,true); }\n','')
s=s.replace('    void localOpen(boolean renewalLoss) throws Exception {localOpen(renewalLoss,false);}\n    void localOpen(boolean renewalLoss,boolean headers) throws Exception {','    void localOpen(boolean renewalLoss) throws Exception {')
s=s.replace("                if(!headers){exchange.sendResponseHeaders(503, 64); exchange.getResponseBody().write('{'); exchange.getResponseBody().flush();}","                exchange.sendResponseHeaders(503, 64); exchange.getResponseBody().write('{'); exchange.getResponseBody().flush();")
s=s.replace('          finally {server.stop(0);serverPool.shutdownNow();assertTrue(serverPool.awaitTermination(3,TimeUnit.SECONDS));}\n','')
s=s.replace('            release.countDown(); assertTrue(serverExit.await(3,TimeUnit.SECONDS));','            release.countDown(); assertTrue(serverExit.await(3,TimeUnit.SECONDS));\n            server.stop(0); serverPool.shutdownNow(); assertTrue(serverPool.awaitTermination(3,TimeUnit.SECONDS));')
variants[p]=s.encode()
p='backend/integration-tests/src/test/java/ama/storage/Task05FixFixture.java';s=(r/p).read_text().replace(' ama.storage.s3.Task05FixProxy s3Proxy;ama.storage.s3.S3Fixture s3Fixture;',' ama.storage.s3.S3Fixture s3Fixture;').replace('s3Proxy=new ama.storage.s3.Task05FixProxy();s3Fixture=new ama.storage.s3.S3Fixture("task05-fix-infrastructure",s3Proxy.endpoint());','s3Fixture=new ama.storage.s3.S3Fixture("task05-infrastructure");').replace('if(s3Proxy!=null)s3Proxy.close();','');variants[p]=s.encode()
p='backend/integration-tests/src/test/java/ama/storage/Task05FixS3OpenIT.java';s=(r/p).read_text().replace('    @Test void genuineS3HeadersOpenExitsAtBatchDeadlineBeforeProxyRelease() throws Exception {open(false,false,true);}\n','').replace('    void open(boolean lost,boolean error) throws Exception {open(lost,error,false);}\n    void open(boolean lost,boolean error,boolean headers) throws Exception {','    void open(boolean lost,boolean error) throws Exception {').replace('headers?"pause":error?"stall-error":"stall-body"','error?"stall-error":"stall-body"');variants[p]=s.encode()
p='backend/integration-tests/src/test/java/ama/storage/s3/Task05FixProxy.java';variants[p]=(r/p).read_text().replace('    if(!pool.awaitTermination(5, TimeUnit.SECONDS))throw new AssertionError("Owned fix proxy threads did not terminate");','    pool.awaitTermination(5, TimeUnit.SECONDS);').encode()
runs={'red-shutdown':['backend/worker/src/test/java/ama/worker/WorkerShutdownTest.java'], 'red-local-batch-clean-fixture':['backend/integration-tests/src/test/java/ama/storage/Task05FixOpenIT.java','backend/integration-tests/src/test/java/ama/storage/Task05FixFixture.java','backend/integration-tests/src/test/java/ama/storage/Task05FixNativeFixture.java','backend/integration-tests/src/test/java/ama/storage/Task05FixMaintenance.java'],'red-local-renewal-clean-fixture':['backend/integration-tests/src/test/java/ama/storage/Task05FixOpenIT.java','backend/integration-tests/src/test/java/ama/storage/Task05FixFixture.java','backend/integration-tests/src/test/java/ama/storage/Task05FixNativeFixture.java','backend/integration-tests/src/test/java/ama/storage/Task05FixMaintenance.java'],'red-s3-control-open':['backend/integration-tests/src/test/java/ama/storage/Task05FixS3OpenIT.java','backend/integration-tests/src/test/java/ama/storage/Task05FixFixture.java','backend/integration-tests/src/test/java/ama/storage/Task05FixNativeFixture.java','backend/integration-tests/src/test/java/ama/storage/Task05FixMaintenance.java','backend/integration-tests/src/test/java/ama/storage/s3/Task05FixProxy.java']}
manifest=[]
for run,tests in runs.items():
 hashes=json.loads((e/run/'source.json').read_text())
 for name in scope['product']+tests:
  if name not in hashes:continue
  candidates=[]
  if (r/name).exists():candidates.append(('final file',(r/name).read_bytes()))
  if name in variants:candidates.append(('explicit reverse of recorded author edits',variants[name]))
  old=subprocess.run(['git','show',base+':'+name],cwd=r,capture_output=True)
  if old.returncode==0:candidates.append(('exact FIX_BASE git blob',old.stdout))
  matched=next(((method,data) for method,data in candidates if sha(data)==hashes[name]),None)
  assert matched,(run,name,'cannot reconstruct exact run source hash')
  method,data=matched;out=e/run/'derived-source'/name;out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(data)
  manifest.append({'run':run,'path':name,'sha256':sha(data),'derivation':method,'capturedStandaloneAtExecution':False})
(e/'red-source-reconstruction.json').write_text(json.dumps({'qualification':'Additive post-run reconstruction, verified against hashes actually captured before each RED. These are not backdated standalone source snapshots.','entries':manifest},indent=2)+'\n')
print('exact focused RED code reconstructed against actual pre-run seals:',len(manifest))
