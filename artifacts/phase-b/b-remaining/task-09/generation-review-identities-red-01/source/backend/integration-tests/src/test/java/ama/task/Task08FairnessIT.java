package ama.task;
import ama.contract.ExternalContracts.OperationToken;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class Task08FairnessIT {
 @Test void moreThan64HeldIdentitiesProgressAcrossCalls()throws Exception{run(true);}
 @Test void moreThan64UnregisteredPoliciesDoNotStarveRegisteredTail()throws Exception{run(false);}
 static JdbcExternalOperations scanner(JdbcTaskQueue q){return new JdbcExternalOperations(q,new ReconciliationScanBudget(16,32));}
 void run(boolean held)throws Exception{
  String target=held?"fairheld":"fairunregistered",block=held?"T08_HELD":"T08_ABSENT",tailType="T08_TAIL";
  var f=new Task08Fixture(target,block);
  List<String> blockers=f.ids(block),tails=f.ids(tailType);
  if(blockers.isEmpty()){
   assertTrue(tails.isEmpty());
   for(int i=0;i<129;i++)f.marker(f.spec(block));f.marker(f.spec(tailType));
   blockers=f.ids(block);tails=f.ids(tailType);
  }
  assertEquals(129,blockers.size());assertEquals(1,tails.size());String tail=tails.getFirst();
  assertEquals(130,f.j.queryForObject("SELECT COUNT(*) FROM external_operation",Integer.class));
  for(String id:blockers)f.due(id);f.due(tail);
  List<String> affected=List.of("A-t08-fair-union","\uE000-t08-fair-union");
  if(held){for(String id:affected)if(f.j.queryForObject("SELECT COUNT(*) FROM enterprise_identity WHERE enterprise_id=?",Integer.class,id)==0)f.j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",id,id);f.affected.addAll(affected);}
  String before=f.skippedSnapshot(tail);
  var scan=scanner(held?f.queue(block,tailType):f.queue(tailType));
  try(Connection lock=f.source.getConnection()){
   lock.setAutoCommit(false);
   if(held){try(var stmt=lock.prepareStatement("SELECT status FROM enterprise_identity WHERE enterprise_id=? FOR UPDATE")){for(String id:blockers){stmt.setString(1,f.owner(id));try(var rows=stmt.executeQuery()){assertTrue(rows.next());}}}}
   f.source.clear();Optional<OperationToken> claimed=Optional.empty();int calls=0;
   while(calls<5&&claimed.isEmpty()){calls++;claimed=scan.claimReconciliation("T08_FAIR");}
   var trace=List.copyOf(f.source.sql);
   System.out.println("TASK08 fairness target="+target+" blockers=129 calls="+calls+" found="+claimed.isPresent()+" SQL="+trace.size()+" rows="+f.source.rows);
   assertTrue(claimed.isPresent(),"129 "+(held?"held identities":"unregistered policies")+" must not starve later eligible operation by call5");
   assertEquals(tail,claimed.orElseThrow().operationId());assertEquals(5,calls,"Explicit P16/B32 consumes at most32 rows/call");assertFalse(scan.hasCallAuthority(claimed.orElseThrow()));
   assertEquals(before,f.skippedSnapshot(tail),"Complete skipped business/identity/task/execution/outbox bytes unchanged");
   List<String> locks=trace.stream().map(Task08ProbeSource.Sql::text).filter(s->s.contains("FOR UPDATE")).toList();
   if(!held)assertEquals(3,locks.stream().filter(s->s.contains("FROM durable_task")).count()+locks.stream().filter(s->s.contains("FROM external_operation")).count()+locks.stream().filter(s->s.contains("FROM external_execution")).count(),"Only registered tail acquires task/operation/execution");
   if(held){
    var identity=trace.stream().filter(s->s.text().contains("FROM enterprise_identity")&&s.text().contains("FOR UPDATE")).toList();assertEquals(129*2+3,identity.size());
    assertEquals("A-t08-fair-union",identity.get(identity.size()-3).parameters().get(1));assertEquals("\uE000-t08-fair-union",identity.getLast().parameters().get(1));
    int firstSubject=-1,lastIdentity=-1;for(int i=0;i<trace.size();i++){var s=trace.get(i).text();if(s.contains("FROM enterprise_identity")&&s.contains("FOR UPDATE"))lastIdentity=i;if(firstSubject<0&&s.contains("FROM task_subject"))firstSubject=i;}
    assertTrue(lastIdentity<firstSubject,"Complete identity union before subject, blocked prefix got no roots");
    try(var free=f.source.getConnection();var st=free.prepareStatement("SELECT enterprise_id FROM enterprise_identity WHERE enterprise_id=? FOR UPDATE SKIP LOCKED")){free.setAutoCommit(false);st.setString(1,affected.getFirst());try(var rows=st.executeQuery()){assertTrue(rows.next(),"Partial union released after every skipped candidate");}free.rollback();}
   }
   lock.rollback();
  }
  if(held)assertEquals(blockers.getFirst(),scan.claimReconciliation("T08_RELEASED").orElseThrow().operationId(),"Next pass revisits released prefix");
 }
}
