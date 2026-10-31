package ama.storage;
import ama.task.RecordingArchiveRuntime;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Genuine queue renewal after actual mTLS I/O, before the upload's metadata commit. */
class RecordingUploadRenewalIT extends RecordingArchiveTaskIT {
 @Test void renewedTaskAfterBeginIoIsRecapturedBeforeUploadSessionCommit()throws Exception{renewed("begin");}
 @Test void renewedTaskAfterPartIoIsRecapturedWithoutSendingTheStreamAgain()throws Exception{renewed("part");}
 @Test void renewedTaskAfterCompleteIoIsRecapturedBeforeRegistryPublication()throws Exception{renewed("complete");}
 @Test void deletionAfterBeginIoCannotAcknowledgeANewUploadSession()throws Exception{deleted("begin");}
 @Test void deletionAfterPartIoCannotAcknowledgeANewPart()throws Exception{deleted("part");}
 @Test void deletionAfterCompleteIoCannotRegisterADurableBlob()throws Exception{deleted("complete");}
 private void deleted(String phase)throws Exception{
  try(var f=new Fixture()){
   byte[] bytes={1,2,3,4};String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
   var request=new ama.contract.FileContracts.Begin("RECORDING_ARCHIVE","current.wav",4,hash);
   if(phase.equals("begin")){
    f.afterBegin.set(()->txDelete(f));
    assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->f.uploads.begin(f.actor,f.meeting,UUID.randomUUID().toString(),request,"delete-after-io"));
    assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM upload_session WHERE meeting_id=?",Integer.class,f.meeting));
   }else{
    var upload=f.uploads.begin(f.actor,f.meeting,UUID.randomUUID().toString(),request,"delete-after-io");
    if(phase.equals("part")){
     f.afterPart.set(()->txDelete(f));
     assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->f.uploads.part(f.actor,upload.uploadId(),1,new java.io.ByteArrayInputStream(bytes),4,hash,"delete-after-io"));
     assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM upload_part WHERE upload_id=?",Integer.class,upload.uploadId()));
    }else{
     f.uploads.part(f.actor,upload.uploadId(),1,new java.io.ByteArrayInputStream(bytes),4,hash,"delete-after-io");
     f.afterComplete.set(()->txDelete(f));
     assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->f.uploads.complete(f.actor,upload.uploadId(),new ama.contract.FileContracts.Completion(List.of(new ama.contract.FileContracts.ClientPart(1,hash)),hash),"delete-after-io"));
     assertEquals("FINALIZING",j.queryForObject("SELECT state FROM upload_session WHERE id=?",String.class,upload.uploadId()));
    }
   }
   assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM file_object WHERE meeting_id=?",Integer.class,f.meeting));
   assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM file_reference r JOIN upload_session u ON u.id=r.owner_id WHERE u.meeting_id=?",Integer.class,f.meeting));
  }
 }
 private void renewed(String phase)throws Exception{
  try(var f=new Fixture()){
   var session=f.recorded();var accepted=RecordingArchiveRuntime.accept(f.queue,f.actor,session.sessionId());var lease=f.queue.claim("RECORDING_ARCHIVE","real-upload-renewal-"+phase).orElseThrow();assertEquals(accepted.taskId(),lease.taskId());
   var renewed=new java.util.concurrent.atomic.AtomicBoolean();Runnable renewal=()->{assertTrue(f.queue.renew(lease),"Real claimed worker must renew its same task");renewed.set(true);};
   switch(phase){case "begin"->f.afterBegin.set(renewal);case "part"->f.afterPart.set(renewal);case "complete"->f.afterComplete.set(renewal);default->throw new AssertionError();}
   var result=RecordingArchiveRuntime.prepare(f.queue,lease,LIVE);assertTrue(renewed.get(),"Actual physical I/O must have completed before the renewal");assertTrue(f.queue.completeWithEffects(lease,result));var status=RecordingArchiveRuntime.status(f.queue,f.actor,session.sessionId());assertTrue(status.available(),"Current genuine task renewal cannot invalidate a completed original upload");assertEquals("SUCCEEDED",status.state());
   try(var download=RecordingArchiveRuntime.download(f.queue,f.actor,session.sessionId(),null)){var bytes=download.body().readAllBytes();assertEquals(48,bytes.length);assertArrayEquals(new byte[]{1,2,3,4},Arrays.copyOfRange(bytes,44,48));}
   for(var method:List.of("beginUpload","writePart","complete"))assertEquals(1,f.uploadCalls.get(method).get(),"Original native upload RPC is not sent again: "+method);
   assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM recording_archive WHERE session_id=? AND state='READY'",Integer.class,session.sessionId()));
  }
 }
}
