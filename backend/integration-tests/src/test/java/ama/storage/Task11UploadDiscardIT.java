package ama.storage;
import ama.contract.StorageContracts.*;import ama.file.*;import java.io.*;import java.nio.file.Path;import java.util.*;import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;import static org.junit.jupiter.api.Assertions.*;
/** Fresh test-owned native volume only; no retained fixture, DB, server or external service. */
class Task11UploadDiscardIT {
 @TempDir Path temporary;
 @Test void completedOrOpenSessionIsPermanentlyDiscardedAcrossStoreRestart()throws Exception{
  for(boolean complete:List.of(false,true)){var key=new StorageKey("local","files","orphan");UploadToken token;PartReceipt receipt;String hash="ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
   try(var fixture=new LocalFixture(temporary.resolve(complete?"completed":"open"))){try(var store=fixture.open(DurabilityHooks.NONE)){token=store.beginUpload(new UploadSpec("local","files","orphan",3));receipt=store.writePart(token,1,new ByteArrayInputStream("abc".getBytes()),3,hash);if(complete)store.complete(token,List.of(receipt),hash);assertThrows(StorageException.class,()->store.discardUpload(token,new StorageKey("local","files","wrong")));store.discardUpload(token,key);store.discardUpload(token,key);assertThrows(StorageException.class,()->store.stat(key));assertThrows(StorageException.class,()->store.complete(token,List.of(receipt),hash));assertThrows(StorageException.class,()->store.put(key,new ByteArrayInputStream("abc".getBytes()),3,hash));assertTrue(store.list("local","files","",null,10).items().isEmpty());}
    try(var store=fixture.open(DurabilityHooks.NONE)){store.discardUpload(token,key);assertThrows(StorageException.class,()->store.complete(token,List.of(receipt),hash));assertThrows(StorageException.class,()->store.writePart(token,1,new ByteArrayInputStream("abc".getBytes()),3,hash));}
   }
  }
 }
}
