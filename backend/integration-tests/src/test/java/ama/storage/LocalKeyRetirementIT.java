package ama.storage;

import ama.contract.StorageContracts.*;
import ama.file.DurabilityHooks;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Exclusively owns new local fixture volumes; never retires live application objects. */
class LocalKeyRetirementIT {
 @TempDir Path root;
 static final byte[] DATA="abc".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
 static final String HASH="ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
 @Test void retirementOfPresentAndAbsentKeysSurvivesReopen()throws Exception{
  for(boolean present:new boolean[]{false,true})try(var fixture=new LocalFixture(root.resolve("reopen-"+present))){
   var key=new StorageKey("local","files","owned/generated-output");
   try(var store=fixture.open(DurabilityHooks.NONE)){
    if(present)store.put(key,new ByteArrayInputStream(DATA),DATA.length,HASH);
    store.retireKey(key);store.retireKey(key);assertThrows(StorageException.class,()->store.stat(key));assertTrue(store.list("local","files","",null,10).items().isEmpty());
   }
   try(var store=fixture.open(DurabilityHooks.NONE)){
    store.retireKey(key);assertEquals(ErrorCode.INVALID_UPLOAD_STATE,assertThrows(StorageException.class,()->store.put(key,new ByteArrayInputStream(DATA),DATA.length,HASH)).code());
    var token=store.beginUpload(new UploadSpec("local","files",key.objectKey(),DATA.length));var part=store.writePart(token,1,new ByteArrayInputStream(DATA),DATA.length,HASH);
    assertThrows(StorageException.class,()->store.complete(token,java.util.List.of(part),HASH));store.discardUpload(token,key);
   }
  }
 }
 @Test void retirementSerializesWithAnAlreadyWritingPut()throws Exception{
  try(var fixture=new LocalFixture(root.resolve("racing"));var store=fixture.open(DurabilityHooks.NONE);var threads=Executors.newFixedThreadPool(2)){
   var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var key=new StorageKey("local","files","owned/in-flight");
   var body=new ByteArrayInputStream(DATA){boolean first=true;@Override public synchronized int read(byte[] bytes,int offset,int length){if(first){first=false;entered.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("release deadline");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}return super.read(bytes,offset,length);}};
   var writer=threads.submit(()->store.put(key,body,DATA.length,HASH));assertTrue(entered.await(5,TimeUnit.SECONDS));var retired=threads.submit(()->store.retireKey(key));
   try{release.countDown();writer.get(10,TimeUnit.SECONDS);retired.get(10,TimeUnit.SECONDS);}finally{release.countDown();}
   assertThrows(StorageException.class,()->store.stat(key));assertThrows(StorageException.class,()->store.put(key,new ByteArrayInputStream(DATA),DATA.length,HASH));
  }
 }
 @Test void mtlsRouteRequiresWriterAndTheOriginalBackend()throws Exception{
  try(var fixture=new LocalFixture(root.resolve("wire"))){
   var writer=fixture.startServer();var key=new StorageKey("local","files","owned/wire");writer.put(key,new ByteArrayInputStream(DATA),DATA.length,HASH);
   assertThrows(StorageException.class,()->fixture.client("reader").retireKey(key));assertEquals(DATA.length,writer.stat(key).size());
   assertThrows(StorageException.class,()->writer.retireKey(new StorageKey("foreign","files",key.objectKey())));assertEquals(DATA.length,writer.stat(key).size());
   writer.retireKey(key);writer.retireKey(key);assertThrows(StorageException.class,()->writer.stat(key));assertThrows(StorageException.class,()->writer.put(key,new ByteArrayInputStream(DATA),DATA.length,HASH));
  }
 }
}
