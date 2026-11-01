package ama.storage;

import ama.contract.StorageContracts.*;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Actual native immutable replay: durability is mandatory, staging churn is unnecessary. */
class LocalImmutableReplayIT {
 @TempDir Path root;
 @Test void exactReplayRevalidatesBytesAndDurabilityWithoutAnotherStagingPublication() throws Exception {
  var armed=new AtomicBoolean();var calls=new HashMap<String,Integer>();
  byte[] bytes={1,2,3,4};String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  var key=new StorageKey("local","recordings",UUID.randomUUID()+"/"+UUID.randomUUID()+"/1");
  try(var fixture=new LocalFixture(root.resolve("files"));var store=fixture.open(op->{if(armed.get())calls.merge(op,1,Integer::sum);})) {
   store.put(key,new ByteArrayInputStream(bytes),bytes.length,hash);armed.set(true);
   assertEquals(hash,store.put(key,new ByteArrayInputStream(bytes),bytes.length,hash).sha256());
   assertTrue(calls.getOrDefault("FILE_SYNC",0)>0,"Replay must reestablish file durability");
   assertTrue(calls.getOrDefault("DIR_SYNC",0)>0,"Replay must reestablish directory durability");
   assertEquals(0,calls.getOrDefault("LINK",0),"An exact existing replay must not allocate and publish more staging data");
   assertEquals(0,calls.getOrDefault("UNLINK",0),"An exact existing replay must not create staging cleanup debt");
   try(var input=store.read(key,0,bytes.length)){assertArrayEquals(bytes,input.readAllBytes());}
   var rejected=assertThrows(StorageException.class,()->store.put(key,new ByteArrayInputStream(new byte[]{9,9,9,9}),4,hash));
   assertEquals(ErrorCode.CHECKSUM_MISMATCH,rejected.code(),"An existing destination must not bypass validation of the new input body");
   assertEquals(hash,store.stat(key).sha256());
  }
 }
}
