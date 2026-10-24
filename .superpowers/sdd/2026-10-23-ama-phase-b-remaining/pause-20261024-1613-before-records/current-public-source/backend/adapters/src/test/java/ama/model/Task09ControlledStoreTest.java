package ama.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.lang.reflect.*;
import static org.junit.jupiter.api.Assertions.*;

class Task09ControlledStoreTest {
 @TempDir Path dir;
 static final String E="00000000-0000-0000-0000-000000000001", I="00000000-0000-0000-0000-000000000002";
 static final byte[] P="{\"input\":\"synthetic\"}".getBytes(StandardCharsets.UTF_8), O="{\"result\":\"synthetic\"}".getBytes(StandardCharsets.UTF_8);
 static String sha(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
 AutoCloseable open()throws Exception {
  Class<?> c=assertDoesNotThrow(()->Class.forName("ama.model.ControlledMinutesStore"),"durable controlled store must be installed");
  return (AutoCloseable)c.getConstructor(Path.class,String.class).newInstance(dir,"CONTROLLED_INSTALLATION");
 }
 static Object call(Object o,String n,Class<?>[] types,Object...args)throws Exception{
  try{return o.getClass().getMethod(n,types).invoke(o,args);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception x)throw x;throw e;}
 }
 Object reserve(Object s,String execution,String key,byte[] payload)throws Exception{return call(s,"reserve",new Class[]{String.class,String.class,String.class,String.class,String.class,byte[].class},execution,key,"a".repeat(64),"a".repeat(64),sha(payload),payload);}
 Object success(Object s,byte[] output)throws Exception{return call(s,"commitSuccess",new Class[]{String.class,byte[].class},E,output);}
 Object lookup(Object s)throws Exception{return call(s,"lookup",new Class[]{String.class},E);}
 String id(Object receipt)throws Exception{return (String)call(receipt,"resultId",new Class[]{});}
 byte[] result(Object s,String id)throws Exception{return (byte[])call(s,"result",new Class[]{String.class},id);}
 @Test void reserveAndSuccessSurviveRestartAndDuplicateReturnsExactIdentity()throws Exception {
  Object receipt;try(var s=open()){reserve(s,E,"minutes:"+I,P);receipt=success(s,O);assertArrayEquals(O,result(s,id(receipt)));}
  try(var s=open()){assertEquals(receipt,lookup(s));assertEquals(receipt,reserve(s,E,"minutes:"+I,P));assertEquals(receipt,success(s,O));assertArrayEquals(O,result(s,id(receipt)));}
 }
 @Test void reservedComputationSurvivesRestartAndLookupDoesNotCompute()throws Exception {
  Object reserved;try(var s=open()){reserved=reserve(s,E,"minutes:"+I,P);assertNull(id(reserved));}
  try(var s=open()){assertEquals(reserved,lookup(s));assertNull(id(lookup(s)));assertNotNull(id(success(s,O)));}
 }
 @Test void immutableLogicalAndExecutionTuplesRejectChangedBytes()throws Exception {
  try(var s=open()){reserve(s,E,"minutes:"+I,P);
   assertThrows(IllegalStateException.class,()->reserve(s,E,"minutes:"+I,new byte[]{1}));
   assertThrows(IllegalStateException.class,()->reserve(s,UUID.randomUUID().toString(),"minutes:"+I,P));
   assertThrows(IllegalStateException.class,()->reserve(s,E,"minutes:"+UUID.randomUUID(),P));
   success(s,O);assertThrows(IllegalStateException.class,()->success(s,new byte[]{2}));
  }
 }
 @Test void definitiveRejectionIsDurableAndPermanentlyProhibitsComputation()throws Exception {
  Object rejection;try(var s=open()){reserve(s,E,"minutes:"+I,P);rejection=call(s,"reject",new Class[]{String.class,String.class},E,"CONTROLLED_REJECTION");assertThrows(IllegalStateException.class,()->success(s,O));}
  try(var s=open()){assertEquals(rejection,lookup(s));assertThrows(IllegalStateException.class,()->success(s,O));}
 }
 @Test void exclusiveStoreLockRejectsSecondProcessOwner()throws Exception {
  try(var s=open()){assertThrows(Exception.class,this::open);reserve(s,E,"minutes:"+I,P);}
  try(var s=open()){assertNotNull(lookup(s));}
 }
 @Test void corruptAcknowledgedResultFailsClosedOnReopen()throws Exception {
  String resultId;try(var s=open()){reserve(s,E,"minutes:"+I,P);resultId=id(success(s,O));}
  Path result=dir.resolve("results").resolve(resultId+".json");assertTrue(Files.exists(result));Files.write(result,new byte[]{1});
  assertThrows(Exception.class,this::open);
 }
 @Test void incompleteUnacknowledgedJournalTailPreservesCompletePrefix()throws Exception {
  Object receipt;try(var s=open()){reserve(s,E,"minutes:"+I,P);receipt=success(s,O);}
  Files.write(dir.resolve("journal.bin"),new byte[]{0,0,1},StandardOpenOption.APPEND);
  try(var s=open()){assertEquals(receipt,lookup(s));assertEquals(receipt,success(s,O));}
  try(var files=Files.list(dir)){assertTrue(files.anyMatch(x->x.getFileName().toString().startsWith("torn-")));}
 }
 @Test void rejectedModeAndOversizeNeverReserve()throws Exception {
  Class<?> c=assertDoesNotThrow(()->Class.forName("ama.model.ControlledMinutesStore"));
  assertThrows(InvocationTargetException.class,()->c.getConstructor(Path.class,String.class).newInstance(dir,"PRODUCTION"));
  try(var s=open()){assertThrows(IllegalArgumentException.class,()->reserve(s,E,"minutes:"+I,new byte[16*1024*1024+1]));assertNull(lookup(s));}
 }
}
