package ama.storage;
import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import ama.contract.TranscriptContracts.*;import org.junit.jupiter.api.AfterEach;import static org.junit.jupiter.api.Assertions.*;
class Task09GenerationStaleIT extends Task09GenerationRichIT {
 final CountDownLatch observer=new CountDownLatch(1),changed=new CountDownLatch(1);final AtomicBoolean once=new AtomicBoolean();final ExecutorService writer=Executors.newSingleThreadExecutor();Future<Map<String,Object>> update;
 @Override javax.sql.DataSource createSource(){return new Task09TraceSource(ama.minutes.Task09Source.open());}
 @Override String expectedTaskState(){return "STALE";}@Override String expectedCandidateState(){return "STALE";}
 @Override void beforeWorker(String task){
  ((Task09TraceSource)source).before=sql->{if(Thread.currentThread().getName().startsWith("ama-work-MINUTES_GENERATE")&&sql.equals("SELECT * FROM external_operation WHERE id=?")&&once.compareAndSet(false,true)){observer.countDown();try{assertTrue(changed.await(10,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}};
  update=writer.submit(()->{assertTrue(observer.await(15,TimeUnit.SECONDS));try{String working=j.queryForObject("SELECT c.revision_id FROM meeting m JOIN transcript_confirmation c ON c.id=m.transcript_confirmation_id WHERE m.id=?",String.class,meeting);var ids=j.queryForList("SELECT segment_id FROM transcript_segment WHERE revision_id=? ORDER BY ordinal",String.class,working);var result=isolated().mutate(actor(),meeting,"newer-real-save",new Command(Operation.SAVE,4,working,ids.stream().map(id->new Edit(id,"Real later saved source",List.of())).toList()),UUID.randomUUID().toString());var root=j.queryForMap("SELECT * FROM meeting WHERE id=?",meeting);assertEquals(5L,((Number)root.get("revision")).longValue());java.nio.file.Files.writeString(receipt.resolve("actual-business-advance.json"),json.writeValueAsString(Map.of("operation","Task6 SAVE","expectedRevision",4,"result",result,"actualRevision",5,"task",task)));return root;}finally{changed.countDown();}});
 }
 @Override Map<String,Object> expectedRoot(Map<String,Object> before)throws Exception {return update.get(5,TimeUnit.SECONDS);}
 @AfterEach void stopWriter()throws Exception {changed.countDown();writer.shutdownNow();assertTrue(writer.awaitTermination(5,TimeUnit.SECONDS));}
}
