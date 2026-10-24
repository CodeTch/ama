package ama.task;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Task08ZOverflowIT {
 static void insert(java.sql.Connection c,String table,Map<String,Object> row)throws Exception{
  var columns=new ArrayList<>(row.keySet());String sql="INSERT INTO "+table+" ("+String.join(",",columns)+") VALUES ("+String.join(",",Collections.nCopies(columns.size(),"?"))+")";
  try(var q=c.prepareStatement(sql)){for(int i=0;i<columns.size();i++)q.setObject(i+1,row.get(columns.get(i)));assertEquals(1,q.executeUpdate());}
 }
 @Test @Order(1) void controlledRestorePreservesExactMappingAndDetectsHiddenNonpositiveMetadata()throws Exception{
  var f=new Task08Fixture("overflow","T08_RESTORE");var retained=f.ids("T08_RESTORE");String operationId,executionId;
  if(retained.isEmpty()){
   assertEquals(0,f.j.queryForObject("SELECT COUNT(*) FROM external_operation",Integer.class));f.source.clear();assertTrue(new JdbcExternalOperations(f.queue(),new ReconciliationScanBudget(1,1)).claimReconciliation("T08_EMPTY_TABLE").isEmpty());assertEquals(1,f.source.sql.size());assertEquals(0,f.source.rows);var marker=f.marker(f.spec("T08_RESTORE"));operationId=marker.operationId();executionId=marker.executionId();
  }else{
   // Resume the one genuine marker retained by the prior null-trace setup error.
   // No reset/reseed: the failed delete/reinsert transaction rolled back on close.
   assertEquals(1,retained.size());assertEquals(1,f.j.queryForObject("SELECT COUNT(*) FROM external_operation",Integer.class));operationId=retained.getFirst();executionId=f.j.queryForObject("SELECT id FROM external_execution WHERE operation_id=? AND execution_no=1",String.class,operationId);assertEquals("MARKED",f.j.queryForObject("SELECT state FROM external_execution WHERE id=?",String.class,executionId));System.out.println("TASK08 restore resumes exact retained genuine marker "+operationId+"; prior empty-table assertion already passed before trace error");
  }
  var operation=f.j.queryForMap("SELECT * FROM external_operation WHERE id=?",operationId);var execution=f.j.queryForMap("SELECT * FROM external_execution WHERE id=?",executionId);long sequence=f.seq(operationId);
  try(var c=f.source.getConnection()){c.setAutoCommit(false);try(var q=c.prepareStatement("DELETE FROM external_execution WHERE id=?")){q.setString(1,executionId);assertEquals(1,q.executeUpdate());}try(var q=c.prepareStatement("DELETE FROM external_operation WHERE id=?")){q.setString(1,operationId);assertEquals(1,q.executeUpdate());}insert(c,"external_operation",operation);insert(c,"external_execution",execution);c.commit();}
  assertEquals(operation,f.j.queryForMap("SELECT * FROM external_operation WHERE id=?",operationId));assertEquals(execution,f.j.queryForMap("SELECT * FROM external_execution WHERE id=?",executionId));
  var next=f.marker(f.spec("T08_RESTORE"));assertTrue(f.seq(next.operationId())>sequence);
  // AUTO_INCREMENT is not a CHECK against controlled explicit invalid imports.
  // Complete validation catches a negative value invisible to a >0 cursor. Roll back it.
  try(var c=f.source.getConnection()){c.setAutoCommit(false);try(var q=c.prepareStatement("UPDATE external_operation SET reconcile_scan_seq=-1 WHERE id=?")){q.setString(1,next.operationId());assertEquals(1,q.executeUpdate());}try(var q=c.createStatement();var r=q.executeQuery("SELECT COUNT(*) FROM external_operation WHERE reconcile_scan_seq<=0 OR reconcile_scan_seq IS NULL")){assertTrue(r.next());assertEquals(1,r.getInt(1),"Complete restore validation rejects hidden invalid metadata");}c.rollback();}
  assertEquals(0,f.j.queryForObject("SELECT COUNT(*) FROM external_operation WHERE reconcile_scan_seq<=0 OR reconcile_scan_seq IS NULL",Integer.class));assertEquals(sequence,f.seq(operationId));
  System.out.println("TASK08 isolated restore exact operation/execution/ID-ordinal preserved; omitted allocation above restored max; invalid import rejected by complete validation, not claimed DB CHECK");
 }
 @Test @Order(2) void signedLongMaximumDoesNotWrapAndAllocationExhaustionReturnsNoToken()throws Exception{
  var f=new Task08Fixture("overflow","T08_MAX");var old=f.j.queryForList("SELECT id,reconcile_scan_seq FROM external_operation ORDER BY id");assertEquals(2,old.size());
  String imported=UUID.randomUUID().toString();var sample=f.j.queryForMap("SELECT * FROM external_operation ORDER BY reconcile_scan_seq LIMIT 1");sample.put("id",imported);sample.put("business_key",Task08Fixture.hash(imported));sample.put("reconcile_scan_seq",Long.MAX_VALUE);sample.put("state","SUCCEEDED");try(var c=f.source.getConnection()){insert(c,"external_operation",sample);}
  assertEquals(Long.MAX_VALUE,f.seq(imported));var scanner=new JdbcExternalOperations(f.queue(),new ReconciliationScanBudget(1,1));for(int i=0;i<3;i++)assertTrue(scanner.claimReconciliation("T08_MAX").isEmpty());assertEquals(Long.MAX_VALUE,Task08CursorIT.position(scanner,"after"));assertFalse(Task08CursorIT.active(scanner));
  var spec=f.spec("T08_MAX");var q=f.queue(spec.type());q.enqueue(spec);var lease=q.claim(spec.pool(),"T08_MAX").orElseThrow();var before=f.j.queryForList("SELECT * FROM external_operation ORDER BY id");var external=new JdbcExternalOperations(q);
  var failure=assertThrows(org.springframework.dao.DataAccessException.class,()->external.beginExternal(lease,f.request(spec)));Throwable cause=failure;while(cause.getCause()!=null)cause=cause.getCause();assertInstanceOf(SQLException.class,cause);var error=(SQLException)cause;System.out.println("TASK08 actual exhausted signedBIGINT SQLState="+error.getSQLState()+" code="+error.getErrorCode()+" token=none sends=0");assertEquals(1467,error.getErrorCode());
  assertEquals(before,f.j.queryForList("SELECT * FROM external_operation ORDER BY id"));assertEquals(0,f.j.queryForObject("SELECT COUNT(*) FROM external_execution WHERE task_id=?",Integer.class,lease.taskId()));assertEquals("RUNNING",f.j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,lease.taskId()));
  assertEquals(old,f.j.queryForList("SELECT id,reconcile_scan_seq FROM external_operation WHERE id<>? ORDER BY id",imported));
 }
}
