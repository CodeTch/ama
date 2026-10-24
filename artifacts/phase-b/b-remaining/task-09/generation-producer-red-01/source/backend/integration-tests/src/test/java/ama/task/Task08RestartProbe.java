package ama.task;
import ama.contract.ExternalContracts.*;
/** Separate bounded fresh JVM, same persisted fixtures and no schema reset. */
public final class Task08RestartProbe {
 public static void main(String[] args){
  var f=new Task08Fixture("behavior","T08_RESTART");var scan=new JdbcExternalOperations(f.queue(args[0]),new ReconciliationScanBudget(8,16));
  for(int i=1;i<=100;i++){var token=scan.claimReconciliation("T08_FRESH_JVM");if(token.isPresent()){if(!token.orElseThrow().operationId().equals(args[1])||scan.hasCallAuthority(token.orElseThrow()))throw new AssertionError("Wrong or sending authority");if(f.j.queryForObject("SELECT COUNT(*) FROM external_execution WHERE operation_id=?",Integer.class,args[1])!=1)throw new AssertionError("UNKNOWN resent");System.out.println("TASK08 fresh JVM claimed expected UNKNOWN tail calls="+i+" pid="+ProcessHandle.current().pid()+" sends=0 executions=1");return;}}
  throw new AssertionError("Fresh JVM failed to reach tail");
 }
}
