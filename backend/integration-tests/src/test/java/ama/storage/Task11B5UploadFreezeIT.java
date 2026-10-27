package ama.storage;

import ama.contract.StorageContracts.*;
import java.nio.file.*;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP S/native I/O/F interleaving on an owned genuine final/publication pipeline. */
class Task11B5UploadFreezeIT extends Task11B5CombinedIT {
 Map<String,List<Map<String,Object>>> previous;
 final CountDownLatch physicalDone=new CountDownLatch(1),releaseCompletion=new CountDownLatch(1);
 final AtomicBoolean armed=new AtomicBoolean();
 final AtomicInteger capturedCompletes=new AtomicInteger(),discardChecks=new AtomicInteger();
 volatile UploadToken nativeToken;volatile List<PartReceipt> nativeParts;volatile String nativeHash;volatile StoredBlob nativeBlob;
 ExecutorService executor;Future<HttpResponse<String>> pending;
 StorageKey winnerKey;
 String loser,winner,winnerFile;String winnerValue="Already attached before final",loserValue="Physical complete crossing final";
 @Override javax.sql.DataSource createSource(){var ds=ama.publication.Task11B5Source04.open();var sql=new org.springframework.jdbc.core.JdbcTemplate(ds);sql.setQueryTimeout(5);previous=Task11B5RetainedDecisionsIT.rows(sql);return ds;}
 @Override @Test void genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate()throws Exception {
  Path out=Path.of(System.getenv("AMA_TASK11_EVIDENCE_WINDOW"));assertEquals("upload-freeze-01",out.getFileName().toString());assertTrue(Files.isRegularFile(out.resolve("consumed.json")));
  try{super.genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate();}
  finally{releaseCompletion.countDown();if(executor!=null){executor.shutdown();if(!executor.awaitTermination(35,TimeUnit.SECONDS)){executor.shutdownNow();assertTrue(executor.awaitTermination(10,TimeUnit.SECONDS),"Upload HTTP thread did not terminate");}}var after=Task11B5RetainedDecisionsIT.rows(j);previous.forEach((table,rows)->assertTrue(after.get(table).containsAll(rows),"Prior row changed: "+table));Files.writeString(out.resolve("prior-business-preserved.json"),json.writeValueAsString(Map.of("tables",previous.keySet(),"oldRowsPreserved",true)),StandardOpenOption.CREATE_NEW);}
 }
 @Override void beforeApplication(){
  super.beforeApplication();var backend=definitions.backends().getFirst();var delegate=backend.storage();
  FileStoragePort observed=(FileStoragePort)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),delegate instanceof AutoCloseable?new Class[]{FileStoragePort.class,AutoCloseable.class}:new Class[]{FileStoragePort.class},(proxy,method,args)->{
   boolean barrier=method.getName().equals("complete")&&armed.compareAndSet(true,false);
   if(barrier)assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive(),"Native complete cannot hold root transaction");
   if(method.getName().equals("discardUpload")&&nativeToken!=null&&nativeToken.equals(args[0])){assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());assertEquals("ABORTED",j.queryForObject("SELECT state FROM upload_session WHERE id=?",String.class,loser));assertNull(j.queryForObject("SELECT completed_file_id FROM upload_session WHERE id=?",String.class,loser));assertNoAttachment();discardChecks.incrementAndGet();}
   Object result;try{result=method.invoke(delegate,args);}catch(java.lang.reflect.InvocationTargetException error){throw error.getCause();}
   if(barrier){nativeToken=(UploadToken)args[0];@SuppressWarnings("unchecked") var receipts=(List<PartReceipt>)args[1];nativeParts=List.copyOf(receipts);nativeHash=(String)args[2];nativeBlob=(StoredBlob)result;capturedCompletes.incrementAndGet();physicalDone.countDown();assertTrue(releaseCompletion.await(45,TimeUnit.SECONDS),"Final/publication barrier timeout");}
   return result;
  });
  definitions=new FileBackendDefinitions(List.of(new FileBackendCatalog.Backend(backend.id(),backend.type(),backend.revision(),backend.status(),observed,backend.definition())),backend.id());
 }
 @Override Task11KnowledgeFixture knowledgeFixture()throws Exception {
  if(knowledge!=null)return knowledge;Path baseline=Path.of(System.getenv("AMA_TASK11_REPOSITORY_ROOT"),".superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-11-b5-frozen-history-runtime-20261027/frozen-history-01/pipeline-0241f0bd-347a-4d76-9737-b678a7720ef8");var prior=json.readTree(Files.readAllBytes(baseline.resolve("knowledge-before-start.json")));var closed=json.readTree(Files.readAllBytes(baseline.resolve("knowledge-closed.json")));assertFalse(closed.path("alive").asBoolean());assertEquals(1958909L,closed.path("pid").asLong());knowledge=Task11KnowledgeFixture.reopenOnly(receipt,owner,ROOT.resolve("knowledge"),prior.path("port").asInt(),json.convertValue(prior.path("identities"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){}));knowledge.grant(owner);return knowledge;
 }
 @Override void beforeFinalSubmission()throws Exception {
  long revisionBefore=revision();winner=open(winnerValue);var completed=complete(winner,winnerValue);assertEquals(200,completed.statusCode());winnerFile=body(completed).path("fileId").asText();assertFalse(winnerFile.isEmpty());var winnerRow=j.queryForMap("SELECT backend_id,namespace,object_key FROM file_object WHERE id=?",winnerFile);winnerKey=new StorageKey((String)winnerRow.get("backend_id"),(String)winnerRow.get("namespace"),(String)winnerRow.get("object_key"));
  loser=open(loserValue);assertEquals(revisionBefore,revision());armed.set(true);executor=Executors.newSingleThreadExecutor();pending=executor.submit(()->complete(loser,loserValue));assertTrue(physicalDone.await(20,TimeUnit.SECONDS),"Real native complete never reached barrier");assertFalse(pending.isDone());assertEquals(1,capturedCompletes.get());
  var session=j.queryForMap("SELECT state,completed_file_id FROM upload_session WHERE id=?",loser);assertEquals("FINALIZING",session.get("state"));assertNull(session.get("completed_file_id"));assertEquals(hash(loserValue),actualStoragePort.stat(nativeBlob.key()).sha256());assertNoAttachment();
  Files.writeString(receipt.resolve("upload-before-final-barrier.json"),json.writeValueAsString(Map.of("meeting",meeting,"winner",winner,"winnerFile",winnerFile,"loser",loser,"state","FINALIZING","realNativeCompleteReturned",true,"outsideRootTransaction",true,"revisionUnchanged",true)),StandardOpenOption.CREATE_NEW);
 }
 void assertNoAttachment(){assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM file_object WHERE backend_id=? AND namespace=? AND object_key=?",Integer.class,nativeBlob.key().backendId(),nativeBlob.key().namespace(),nativeBlob.key().objectKey()));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM file_reference WHERE owner_kind='UPLOAD_SESSION' AND owner_id=?",Integer.class,loser));}
 @Override void runPublication(Map<String,Object> accepted)throws Exception {
  assertEquals("READY",j.queryForObject("SELECT state FROM publication WHERE id=?",String.class,accepted.get("publicationId")));var before=Task11B5RetainedDecisionsIT.rows(j);releaseCompletion.countDown();var denied=pending.get(35,TimeUnit.SECONDS);assertEquals(503,denied.statusCode(),denied.body());assertEquals("FILE_STORAGE_UNAVAILABLE",body(denied).at("/error/code").asText());assertTrue(before.equals(Task11B5RetainedDecisionsIT.rows(j)),"Denied F changed selected tables");assertEquals("FINALIZING",j.queryForObject("SELECT state FROM upload_session WHERE id=?",String.class,loser));assertNoAttachment();assertEquals(nativeHash,actualStoragePort.stat(nativeBlob.key()).sha256());
  var abort=call("DELETE","/uploads/"+loser,"",null);assertEquals(204,abort.statusCode());assertEquals("ABORTED",j.queryForObject("SELECT state FROM upload_session WHERE id=?",String.class,loser));assertNull(j.queryForObject("SELECT completed_file_id FROM upload_session WHERE id=?",String.class,loser));assertNoAttachment();
  assertEquals(1,discardChecks.get());verifyRetiredAndAcknowledged("READY");
  Files.writeString(receipt.resolve("upload-frozen-F-and-abort.json"),json.writeValueAsString(Map.of("meeting",meeting,"publication",accepted.get("publicationId"),"loser",loser,"deniedFStatus",denied.statusCode(),"deniedFUnchangedTables",before.keySet(),"abortedBeforeNativeDiscard",true,"nativeCompleteCallsHeld",capturedCompletes.get())),StandardOpenOption.CREATE_NEW);
  super.runPublication(accepted);verifyRetiredAndAcknowledged("PUBLISHED");assertEquals(3,discardChecks.get());
 }
 void verifyRetiredAndAcknowledged(String phase)throws Exception {
  var before=Task11B5RetainedDecisionsIT.rows(j);var missing=assertThrows(StorageException.class,()->actualStoragePort.stat(nativeBlob.key()));assertEquals(ErrorCode.NOT_FOUND,missing.code());var late=assertThrows(StorageException.class,()->actualStoragePort.complete(nativeToken,nativeParts,nativeHash));assertEquals(ErrorCode.INVALID_UPLOAD_STATE,late.code());assertEquals(ErrorCode.NOT_FOUND,assertThrows(StorageException.class,()->actualStoragePort.stat(nativeBlob.key())).code());
  assertEquals(204,call("DELETE","/uploads/"+loser,"",null).statusCode());assertEquals(409,complete(loser,loserValue).statusCode());var replay=complete(winner,winnerValue);assertEquals(200,replay.statusCode());assertEquals(winnerFile,body(replay).path("fileId").asText());assertEquals(204,call("DELETE","/uploads/"+winner,"",null).statusCode());assertEquals("COMPLETED",j.queryForObject("SELECT state FROM upload_session WHERE id=?",String.class,winner));assertNoAttachment();var physicalWinner=actualStoragePort.stat(winnerKey);assertEquals(hash(winnerValue),physicalWinner.sha256());assertEquals(winnerValue.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,physicalWinner.size());assertTrue(before.equals(Task11B5RetainedDecisionsIT.rows(j)),"Frozen abort/replay changed selected tables");
  Files.writeString(receipt.resolve("upload-retired-"+phase+".json"),json.writeValueAsString(Map.of("phase",phase,"lateNativeComplete",late.code().name(),"nativeStat",missing.code().name(),"winnerFile",winnerFile,"winnerOriginalAckPreserved",true,"winnerActualNativeSizeHashPreserved",true,"repeatedAbortNoSecondBusinessMutation",true,"unchangedTables",before.keySet())),StandardOpenOption.CREATE_NEW);
 }
}
