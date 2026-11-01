package ama.storage;
import ama.contract.StorageContracts.*;
import ama.file.DurabilityHooks;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.*;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** New exclusive fixture volumes, including unregistered physical chunks and actual mTLS. */
class LocalRecordingFenceIT {
 @TempDir Path root;
 static final byte[] DATA="abc".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
 static final String HASH="ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
 String session(){return UUID.randomUUID().toString();}
 StorageKey scope(String id){return new StorageKey("local","recordings",id);}
 StorageKey key(String id,int seq){return new StorageKey("local","recordings",id+"/"+"00000000-0000-4000-8000-000000000001/"+seq);}
 @Test void sessionFenceCoversUnknownFutureKeysAndRetainedOrphansWithoutTouchingAnotherSession()throws Exception{
  String id=session(),other=session();try(var fixture=new LocalFixture(root.resolve("session"))){
   try(var store=fixture.open(DurabilityHooks.NONE)){
    for(var selected:List.of(key(id,1),key(id,3),key(other,1)))store.put(selected,new ByteArrayInputStream(DATA),3,HASH);
    store.fenceRecordingSession(scope(id));store.fenceRecordingSession(scope(id));
    assertThrows(StorageException.class,()->store.put(key(id,999),new ByteArrayInputStream(DATA),3,HASH));
    // Fence is distinct from physical completion. Enumerate actual stored orphan keys, too.
    var page=store.list("local","recordings",id+"/",null,10);assertEquals(2,page.items().size());for(var item:page.items())store.retireKey(item.key());
    assertTrue(store.list("local","recordings",id+"/",null,10).items().isEmpty());assertEquals(3,store.stat(key(other,1)).size());
   }
   try(var store=fixture.open(DurabilityHooks.NONE)){assertThrows(StorageException.class,()->store.put(key(id,2),new ByteArrayInputStream(DATA),3,HASH));assertEquals(3,store.stat(key(other,1)).size());}
  }
 }
 @Test void sessionFenceWaitsForAnAlreadyWritingPutThenPreventsEveryNextKey()throws Exception{
  String id=session();try(var fixture=new LocalFixture(root.resolve("race"));var store=fixture.open(DurabilityHooks.NONE);var threads=Executors.newFixedThreadPool(2)){
   var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var body=new ByteArrayInputStream(DATA){boolean first=true;public synchronized int read(byte[] b,int o,int n){if(first){first=false;entered.countDown();try{assertTrue(release.await(5,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}return super.read(b,o,n);}};
   var writer=threads.submit(()->store.put(key(id,1),body,3,HASH));assertTrue(entered.await(5,TimeUnit.SECONDS));var fence=threads.submit(()->store.fenceRecordingSession(scope(id)));release.countDown();writer.get(5,TimeUnit.SECONDS);fence.get(5,TimeUnit.SECONDS);
   store.retireKey(key(id,1));assertThrows(StorageException.class,()->store.put(key(id,2),new ByteArrayInputStream(DATA),3,HASH));
  }
 }
 @Test void privateRouteRejectsReaderWrongBackendAndNonRecordingScopes()throws Exception{
  String id=session();try(var fixture=new LocalFixture(root.resolve("wire"))){var writer=fixture.startServer();assertThrows(StorageException.class,()->fixture.client("reader").fenceRecordingSession(scope(id)));assertThrows(StorageException.class,()->writer.fenceRecordingSession(new StorageKey("foreign","recordings",id)));assertThrows(StorageException.class,()->writer.fenceRecordingSession(new StorageKey("local","files",id)));writer.fenceRecordingSession(scope(id));assertThrows(StorageException.class,()->writer.put(key(id,1),new ByteArrayInputStream(DATA),3,HASH));}
 }
}
