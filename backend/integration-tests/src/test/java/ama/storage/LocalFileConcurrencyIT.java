package ama.storage;

import ama.contract.StorageContracts.*;
import java.io.*;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LocalFileConcurrencyIT {
 @TempDir Path root;
 @Test void boundedFileWorkersCanReachSixteenDurableWritesBeforeTheQueueIsFull()throws Exception {
  durableWrites(16);
 }
 @Test void boundedFileWorkersCanReachThirtyTwoDurableWritesBeforeTheQueueIsFull()throws Exception {
  durableWrites(32);
 }
 void durableWrites(int workers)throws Exception {
  var arrived=new CountDownLatch(workers);var release=new CountDownLatch(1);var armed=new AtomicBoolean();
  try(var fixture=new LocalFixture(root.resolve("files"))){
   var client=fixture.startServer(operation->{if(armed.get()&&operation.equals("FILE_SYNC")){arrived.countDown();try{if(!release.await(20,TimeUnit.SECONDS))throw new IllegalStateException("Blocked test durability gate");}catch(InterruptedException stopped){Thread.currentThread().interrupt();throw new IllegalStateException(stopped);}}},workers);
   armed.set(true);byte[] bytes={1,2,3,4};String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));var writes=new ArrayList<Future<StoredBlob>>();
   try(var pool=Executors.newFixedThreadPool(workers)){
    try{
     for(int i=0;i<workers;i++){String key="parallel/"+i;writes.add(pool.submit(()->client.put(new StorageKey("local","objects",key),new ByteArrayInputStream(bytes),bytes.length,sha)));}
     assertTrue(arrived.await(10,TimeUnit.SECONDS),"all "+workers+" durable requests must enter; reached "+(workers-arrived.getCount()));
     assertTrue(writes.stream().noneMatch(Future::isDone),"no write may return before its real durability barrier");
    }finally{armed.set(false);release.countDown();}
    for(var write:writes){var stored=write.get(15,TimeUnit.SECONDS);assertEquals(4,stored.size());assertEquals(sha,stored.sha256());try(var input=client.read(stored.key(),0,4)){assertArrayEquals(bytes,input.readAllBytes());}}
   }finally{release.countDown();}
  }
 }
}
