package ama.task;
import ama.contract.ExternalContracts.OperationToken;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class Task08MixedBlockersIT {
 @Test void seventyMixedIdentitySubjectRootInputTaskOperationExecutionLocksStillReachTail()throws Exception{
  var f=new Task08Fixture("behavior","T08_MIXED");String type="T08_"+UUID.randomUUID().toString().replace("-","");
  List<String> lockSql=new ArrayList<>();List<String> lockIds=new ArrayList<>();List<OperationToken> blocked=new ArrayList<>();
  for(int i=0;i<70;i++){
   var spec=f.spec(type);var token=f.marker(spec);f.due(token.operationId());blocked.add(token);String sql,id;
   switch(i%7){
    case 0->{sql="SELECT enterprise_id FROM enterprise_identity WHERE enterprise_id=? FOR UPDATE";id=f.owner(token.operationId());}
    case 1->{sql="SELECT subject_id FROM task_subject WHERE subject_kind='MEETING' AND subject_id=? FOR UPDATE";id=spec.subject().id();}
    case 2->{sql="SELECT id FROM meeting WHERE id=? FOR UPDATE";id=spec.subject().id();}
    case 3->{sql="SELECT id FROM meeting_info_revision WHERE id=? FOR UPDATE";id=spec.input().id();}
    case 4->{sql="SELECT id FROM durable_task WHERE id=? FOR UPDATE";id=f.j.queryForObject("SELECT task_id FROM external_execution WHERE id=?",String.class,token.executionId());}
    case 5->{sql="SELECT id FROM external_operation WHERE id=? FOR UPDATE";id=token.operationId();}
    default->{sql="SELECT id FROM external_execution WHERE id=? FOR UPDATE";id=token.executionId();}
   }
   lockSql.add(sql);lockIds.add(id);
  }
  var tail=f.marker(f.spec(type));f.due(tail.operationId());String before=f.skippedSnapshot(tail.operationId());
  int population=f.j.queryForObject("SELECT COUNT(*) FROM external_operation",Integer.class);var scanner=new JdbcExternalOperations(f.queue(type),new ReconciliationScanBudget(16,32));int calls=0;OperationToken answer=null;
  try(var held=f.source.getConnection()){
   held.setAutoCommit(false);for(int i=0;i<lockSql.size();i++)try(var q=held.prepareStatement(lockSql.get(i))){q.setString(1,lockIds.get(i));try(var rows=q.executeQuery()){assertTrue(rows.next());}}
   for(int bound=(population+31)/32+1;calls<bound;){calls++;var result=scanner.claimReconciliation("T08_MIXED");if(result.isPresent()){answer=result.orElseThrow();break;}}
   assertNotNull(answer);assertEquals(tail.operationId(),answer.operationId());assertFalse(scanner.hasCallAuthority(answer));assertEquals(before,f.skippedSnapshot(tail.operationId()));held.rollback();
  }
  OperationToken revisited=null;for(int i=0;i<(population+31)/32+1;i++){var result=scanner.claimReconciliation("T08_RELEASED");if(result.isPresent()){revisited=result.orElseThrow();break;}}
  assertNotNull(revisited);assertEquals(blocked.getFirst().operationId(),revisited.operationId());
  System.out.println("TASK08 mixed blockers=70 phases=7 each=10 existingPopulation="+population+" page=16 budget=32 tailCalls="+calls+" skippedFullGraph=unchanged releasedPrefix=revisited initialSendAuthority=false");
 }
}
