package ama.api;

import ama.contract.IdentityContracts.*;
import ama.contract.StorageContracts.StorageKey;
import ama.storage.FileRegistry;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthorizedDownloadsTest {
 private final Actor actor=new Actor("viewer",Status.ACTIVE,Set.of(Role.USER));
 private final FileRegistry.FileRecord file=new FileRegistry.FileRecord(UUID.randomUUID().toString(),UUID.randomUUID().toString(),new StorageKey("test-local","files","export/test"),5,"a".repeat(64),"AVAILABLE",0,1,"纪要.md");
 @Test void rangeBytesRemainBoundToTheSameCurrentFileAndFreshActor()throws Exception{
  var checked=new AtomicBoolean();var probeClosed=new AtomicBoolean();
  var original=new FileRegistry.Download(file,new ByteArrayInputStream(new byte[]{2,3}),1,2);
  try(var guarded=AuthorizedDownloads.guard(original,()->actor,(current,range)->{assertEquals(actor,current);assertEquals("bytes=0-0",range);checked.set(true);return new FileRegistry.Download(file,new ByteArrayInputStream(new byte[]{1}){public void close(){probeClosed.set(true);}},0,1);})){
   assertEquals(1,guarded.offset());assertEquals(2,guarded.length());assertArrayEquals(new byte[]{2,3},guarded.body().readAllBytes());
  }
  assertTrue(checked.get());assertTrue(probeClosed.get());
 }
 @Test void initialSessionRevocationClosesTheAlreadyOpenedSource()throws Exception{
  var closed=new AtomicBoolean();var original=new FileRegistry.Download(file,new ByteArrayInputStream(new byte[]{1}){public void close(){closed.set(true);}},0,1);
  assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->AuthorizedDownloads.guard(original,()->{throw new ama.access.MeetingAccess.AccessDeniedException();},(current,range)->{throw new AssertionError("Revoked identity cannot query a file");}));assertTrue(closed.get());
 }
 @Test void changedLocationOrReferenceCannotRenewAnOldDownload()throws Exception{
  var changed=new FileRegistry.FileRecord(file.fileId(),file.meetingId(),file.key(),file.size(),file.sha256(),"AVAILABLE",file.locationRevision()+1,file.referenceEpoch(),file.displayName());
  assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->AuthorizedDownloads.guard(new FileRegistry.Download(file,new ByteArrayInputStream(new byte[]{1}),0,1),()->actor,(current,range)->new FileRegistry.Download(changed,new ByteArrayInputStream(new byte[]{1}),0,1)));
 }
}
