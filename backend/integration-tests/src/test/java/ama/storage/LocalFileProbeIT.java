package ama.storage;

import ama.contract.StorageContracts.*;
import ama.file.DurabilityHooks;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Real mTLS availability never enumerates historical object keys or asserts their integrity. */
class LocalFileProbeIT {
 @TempDir Path root;
 @Test void probeChecksCurrentVolumeWithoutReadingObjectBodiesOrKeys()throws Exception {
  var objectReads=new AtomicInteger();
  var hooks=new DurabilityHooks(){public void before(String operation){}public void afterRead(int fd,int bytes){
   try{if(Files.readSymbolicLink(Path.of("/proc/self/fd/"+fd)).toString().contains("/objects/"))objectReads.incrementAndGet();}
   catch(java.io.IOException e){throw new IllegalStateException(e);}
  }};
  try(var fixture=new LocalFixture(root.resolve("files"))){
   var client=fixture.startServer(hooks);byte[] data={1,2,3,4};String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
   for(int i=0;i<12;i++)client.put(new StorageKey("local","files","historical/"+i),new ByteArrayInputStream(data),data.length,sha);
   objectReads.set(0);client.probe("local");assertEquals(0,objectReads.get(),"availability must not read historical object keys or audio");
   assertEquals(1,client.list("local","files","",null,1).items().size());assertTrue(objectReads.get()>0,"actual list retains its real object checks");
   fixture.client("reader").probe("local");
  }
 }
 @Test void wrongBackendUntrustedCertificateAndChangedRoleCannotPassProbe()throws Exception {
  try(var fixture=new LocalFixture(root.resolve("files"))){
   var client=fixture.startServer();assertThrows(StorageException.class,()->client.probe("foreign"));
   assertThrows(StorageException.class,()->fixture.client("outsider").probe("local"));
   Files.writeString(fixture.role,"retired-instance");assertThrows(StorageException.class,()->client.probe("local"));
  }
 }
}
