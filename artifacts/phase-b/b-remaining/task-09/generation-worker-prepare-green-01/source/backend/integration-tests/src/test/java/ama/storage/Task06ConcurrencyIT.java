package ama.storage;
import ama.contract.TranscriptContracts.*;
import org.junit.jupiter.api.Test;
import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;
class Task06ConcurrencyIT extends Task06Fixture {
 String save(String raw)throws Exception {var ids=j.queryForList("SELECT segment_id FROM transcript_segment WHERE revision_id=? ORDER BY ordinal",String.class,raw);var result=isolated().mutate(actor(),meeting,"saved",new Command(Operation.SAVE,1,raw,ids.stream().map(id->new Edit(id,"Saved😀",List.of())).toList()),UUID.randomUUID().toString());return (String)((Map<?,?>)result.get("result")).get("revisionId");}
 @Test void actualMetadataAndFkLocksProtectGrowthChildrenAndEmptyMarksInBothPhases()throws Exception {
  String working=save(raw());String segment=j.queryForList("SELECT segment_id FROM transcript_segment WHERE revision_id=? ORDER BY ordinal",String.class,working).getFirst();String file=j.queryForObject("SELECT source_file_id FROM transcript_segment WHERE revision_id=? AND segment_id=?",String.class,working,segment);var service=isolated();
  var phases=new AtomicInteger();var attempts=new CopyOnWriteArrayList<Map<String,Object>>();probe.events.clear();
  probe.afterBound=(sql,parameters)->{
   if(!sql.contains("OCTET_LENGTH(body)")||!sql.contains("FOR UPDATE")||!working.equals(parameters.get(1)))return;
   int phase=phases.incrementAndGet();if(phase>2)return;
   try(var writers=Executors.newFixedThreadPool(3)){
    var futures=new ArrayList<Future<?>>();
    for(int kind=0;kind<3;kind++){final int action=kind;futures.add(writers.submit(()->{
     try(var connection=probe.actual.getConnection()){
      connection.setAutoCommit(false);try(var setting=connection.createStatement()){setting.execute("SET SESSION innodb_lock_wait_timeout=2");}
      String statement=switch(action){case 0->"UPDATE transcript_segment SET body=? WHERE revision_id=? AND segment_id=?";case 1->"INSERT INTO transcript_segment(revision_id,segment_id,ordinal,body,source_file_id,source_paragraph) VALUES (?,?,999,'inserted',?,999)";default->"INSERT INTO transcript_segment_mark(revision_id,segment_id,mark) VALUES (?,?,'KEY')";};
      long start=System.nanoTime();boolean blocked=false;
      try(var write=connection.prepareStatement(statement)){
       if(action==0){write.setString(1,"G".repeat(20000));write.setString(2,working);write.setString(3,segment);}else if(action==1){write.setString(1,working);write.setString(2,UUID.randomUUID().toString());write.setString(3,file);}else{write.setString(1,working);write.setString(2,segment);}write.executeUpdate();
      }catch(java.sql.SQLException expected){blocked=expected.getErrorCode()==1205||expected.getErrorCode()==1213;}
      finally{connection.rollback();}
      assertTrue(blocked,"Actual writer must not pass retained row/FK parent locks");attempts.add(Map.of("phase",phase,"writer",action,"blocked",blocked,"elapsedMillis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)));
     }catch(Exception e){throw new RuntimeException(e);}
    }));}
    for(var future:futures)future.get(8,TimeUnit.SECONDS);
   }catch(Exception e){throw new RuntimeException(e);}
  };
  var response=service.readOwner(actor(),meeting,working);probe.afterBound=(sql,parameters)->{};assertEquals(2,phases.get());assertEquals(6,attempts.size());assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM transcript_segment_mark WHERE revision_id=?",Integer.class,working));assertEquals(2,((List<?>)((Map<?,?>)response.get("selected")).get("segments")).size());
  var roots=new HashMap<Object,Long>();for(var event:probe.events)if(((String)event.get("sql")).startsWith("SELECT business_version,cancel_epoch,deleted FROM task_subject"))roots.merge(event.get("connection"),1L,Long::sum);assertEquals(2,roots.size());assertTrue(roots.values().stream().allMatch(n->n==1),"One genuine gate per S/F transaction");
  Files.writeString(receipt.resolve("retained-lock-writers.json"),json.writeValueAsString(attempts));Files.writeString(receipt.resolve("reader-sql-events.json"),json.writeValueAsString(probe.events));
 }
}
