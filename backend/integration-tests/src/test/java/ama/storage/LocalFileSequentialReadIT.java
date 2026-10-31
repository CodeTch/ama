package ama.storage;

import ama.contract.StorageContracts.*;
import java.io.*;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Actual mTLS ordered streaming. The sequence carries I/O bounds, never registry/business authority. */
class LocalFileSequentialReadIT {
 @TempDir Path root;
 static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
 static ReadChunk chunk(StorageKey key,byte[] bytes)throws Exception{return new ReadChunk(key,bytes.length,sha(bytes));}
 @Test void orderedReadUsesOneStreamingResponseAndKeepsEveryOriginalChunk()throws Exception {
  try(var fixture=new LocalFixture(root.resolve("files"))){
   var writer=fixture.startServer();byte[] first={1,2,3},second={4,5,6,7};StorageKey a=new StorageKey("local","recordings","first"),b=new StorageKey("local","recordings","second");writer.put(a,new ByteArrayInputStream(first),first.length,sha(first));writer.put(b,new ByteArrayInputStream(second),second.length,sha(second));
   var reader=fixture.client("reader");var sequence=new ReadSequence(List.of(chunk(b,second),chunk(a,first)));assertEquals(7,sequence.size());
   try(var input=reader.readSequence(sequence)){assertArrayEquals(new byte[]{4,5,6,7,1,2,3},input.readAllBytes());}
   try(var input=reader.readSequence(new ReadSequence(List.of(chunk(a,first),chunk(b,second))))){assertEquals(1,input.read());input.close();assertThrows(IOException.class,input::read);}
  }
 }
 @Test void wrongHashOrMissingOriginalCannotReturnASuccessfulCompleteSequence()throws Exception {
  try(var fixture=new LocalFixture(root.resolve("files"))){
   var client=fixture.startServer();byte[] bytes={1,2,3};StorageKey a=new StorageKey("local","recordings","first"),b=new StorageKey("local","recordings","missing");client.put(a,new ByteArrayInputStream(bytes),bytes.length,sha(bytes));
   assertThrows(Exception.class,()->{try(var input=client.readSequence(new ReadSequence(List.of(new ReadChunk(a,3,"0".repeat(64)))))){input.readAllBytes();}});
   assertThrows(Exception.class,()->{try(var input=client.readSequence(new ReadSequence(List.of(chunk(a,bytes),chunk(b,bytes))))){input.readAllBytes();}});
   try(var input=client.read(a,0,bytes.length)){assertArrayEquals(bytes,input.readAllBytes());}
  }
 }
 @Test void boundedSequenceCannotMixBackendsNamespacesOrDuplicateOriginalKeys()throws Exception {
  var a=new StorageKey("local","recordings","first");var valid=new ReadChunk(a,2,"a".repeat(64));
  assertThrows(IllegalArgumentException.class,()->new ReadSequence(List.of()));assertThrows(IllegalArgumentException.class,()->new ReadSequence(List.of(valid,valid)));
  for(var key:List.of(new StorageKey("other","recordings","second"),new StorageKey("local","files","second")))assertThrows(IllegalArgumentException.class,()->new ReadSequence(List.of(valid,new ReadChunk(key,2,"a".repeat(64)))));
  assertThrows(IllegalArgumentException.class,()->new ReadChunk(a,64001,"a".repeat(64)));assertThrows(IllegalArgumentException.class,()->new ReadChunk(a,0,"a".repeat(64)));
  var many=new ArrayList<ReadChunk>();for(int i=0;i<4097;i++)many.add(new ReadChunk(new StorageKey("local","recordings","part/"+i),2,"a".repeat(64)));assertThrows(IllegalArgumentException.class,()->new ReadSequence(many));
 }
}
