package ama.config.json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class Task09ControlledEnvelopeStoreTest {
 @TempDir Path dir;
 AutoCloseable open()throws Exception {Class<?> c=assertDoesNotThrow(()->Class.forName("ama.model.ControlledMinutesProviderStore"));return (AutoCloseable)c.getConstructor(Path.class,String.class).newInstance(dir,"CONTROLLED_INSTALLATION");}
 Object call(Object o,String n,Class<?>[] types,Object...args)throws Exception {try{return o.getClass().getMethod(n,types).invoke(o,args);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception x)throw x;throw e;}}
 Object reserve(Object s,Task09ControlledProtocolTest.Wire w)throws Exception{return call(s,"reserve",new Class[]{byte[].class,String.class},w.body(),w.header());}
 Object lookup(Object s)throws Exception{return call(s,"lookup",new Class[]{String.class},Task09ControlledProtocolTest.E);}
 @Test void exactCanonicalEnvelopeAndPayloadAreValidatedAfterReopen()throws Exception {
  var w=Task09ControlledProtocolTest.wire();Object receipt;
  try(var s=open()){reserve(s,w);receipt=call(s,"commitSuccess",new Class[]{String.class,byte[].class},Task09ControlledProtocolTest.E,new byte[]{1,2,3});}
  try(var s=open()){assertEquals(receipt,lookup(s));assertEquals(receipt,reserve(s,w));}
 }
 @Test void corruptEnvelopeCannotBeRepairedByDuplicatePost()throws Exception {
  var w=Task09ControlledProtocolTest.wire();try(var s=open()){reserve(s,w);}
  Files.writeString(dir.resolve("envelopes").resolve(Task09ControlledProtocolTest.E+".json"),"{}");assertThrows(Exception.class,this::open);
 }
 @Test void changedEnvelopeWithOldHashesNeverChangesExistingMapping()throws Exception {
  var w=Task09ControlledProtocolTest.wire();try(var s=open()){Object original=reserve(s,w);var bad=new Task09ControlledProtocolTest.Wire(w.body(),Task09ControlledProtocolTest.header(Task09ControlledProtocolTest.replace(w.envelope(),"inputFingerprint",ama.canonical.CanonicalValue.string("b".repeat(64)))),w.envelope());assertThrows(RuntimeException.class,()->reserve(s,bad));assertEquals(original,lookup(s));}
 }
 @Test void lookupLeavesReservedExecutionUnknownAcrossReopen()throws Exception {
  var w=Task09ControlledProtocolTest.wire();Object receipt;try(var s=open()){receipt=reserve(s,w);}
  try(var s=open()){assertEquals(receipt,lookup(s));assertEquals("UNKNOWN",call(receipt,"state",new Class[]{}));}
 }
}
