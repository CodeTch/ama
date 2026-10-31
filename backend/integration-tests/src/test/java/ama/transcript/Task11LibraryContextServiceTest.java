package ama.transcript;
import ama.access.MeetingAccess;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class Task11LibraryContextServiceTest {
 static class Fixture extends Task11DocumentServiceBoundaryTest.Fixture {
  final Map<String,Object> location;
  Fixture()throws Exception {
   var call=mock(ControlledKnowledgeClient.Call.class);doAnswer(i->{assertFalse(TransactionSynchronizationManager.isActualTransactionActive());var auth=(JdbcPublicationService.Authorization)i.getArgument(0);auth.require(client,"DOCUMENT_READ");assertEquals(viewer.enterpriseId(),auth.actor());assertEquals("GET",i.getArgument(2));assertEquals("/v1/knowledge/documents/"+Task11DocumentServiceBoundaryTest.DOC+"?view=location",i.getArgument(3));return call;}).when(client).authorized(any(),eq("DOCUMENT_READ"),eq("GET"),endsWith("?view=location"),isNull());
   location=new LinkedHashMap<>();for(String key:List.of("providerId","storeInstanceId","publicationId","payloadSha256","libraryId","folderId"))location.put(key,manifest.get(key));location.putAll(Map.of("viewerId",viewer.enterpriseId(),"documentId",Task11DocumentServiceBoundaryTest.DOC,"resultId",Task11DocumentServiceBoundaryTest.RESULT,"libraryName","Actual library","libraryDescription","","folderPath",List.of("Actual root"),"readGrantVersionToken","1"));rehash();
   doAnswer(i->{assertFalse(TransactionSynchronizationManager.isActualTransactionActive());assertEquals(2,contentReads.get());network.set(true);if(cancelAfterNetwork)cancelled.set(true);return location;}).when(client).exchange(eq(call),eq(65536),same(cancellation));
  }
  void rehash(){location.remove("locationVersion");location.put("locationVersion",ControlledKnowledgeClient.digest(ControlledKnowledgeClient.canonical(location)));}
  Map<String,Object> context()throws IOException{return service.libraryContext(viewer,Task11DocumentServiceBoundaryTest.M,Task11DocumentServiceBoundaryTest.P,cancellation);}
 }
 @Test void currentMemberGetsOnlyVerifiedMetadataAndSameOriginDocumentWithoutOwnerReceipt()throws Exception {var f=new Fixture();var result=f.context();assertEquals("Actual library",result.get("libraryName"));assertEquals("/api/v1/meetings/"+Task11DocumentServiceBoundaryTest.M+"/publications/"+Task11DocumentServiceBoundaryTest.P+"/document",result.get("documentUrl"));assertEquals(3,f.reads.get());assertEquals(2,f.roots.get());assertEquals(4,f.contentReads.get());assertFalse(result.containsKey("payload"));verify(f.client,never()).exchange(any(),eq(8454144),any());}
 @Test void currentInitialOrFinalReadDenialAndRootDriftNeverReturnMetadata()throws Exception {var initial=new Fixture();initial.denyInitial=true;assertThrows(MeetingAccess.AccessDeniedException.class,initial::context);assertFalse(initial.network.get());var revoked=new Fixture();revoked.denyFinal=true;assertThrows(MeetingAccess.AccessDeniedException.class,revoked::context);assertTrue(revoked.network.get());var drift=new Fixture();drift.drift=true;assertThrows(RuntimeException.class,drift::context);}
 @Test void providerWrongDestinationInvalidVersionAndCancellationStopBeforeFinalCapture()throws Exception {var wrong=new Fixture();wrong.location.put("libraryId","foreign");wrong.rehash();assertThrows(RuntimeException.class,wrong::context);assertEquals(1,wrong.roots.get());var changed=new Fixture();changed.location.put("libraryName","unauthenticated edit");assertThrows(RuntimeException.class,changed::context);var cancelled=new Fixture();cancelled.cancelAfterNetwork=true;assertThrows(RuntimeException.class,cancelled::context);}
 @Test void changedStoredExecutionOrOversizedMetadataNeverCallsProvider()throws Exception {var f=new Fixture();f.tables.get("external_execution").put("request_manifest",f.original.replace("original-owner","tampered-owner"));assertThrows(RuntimeException.class,f::context);assertFalse(f.network.get());var large=new Fixture();large.oversized=true;assertThrows(RuntimeException.class,large::context);assertEquals(0,large.contentReads.get());assertFalse(large.network.get());}
}
