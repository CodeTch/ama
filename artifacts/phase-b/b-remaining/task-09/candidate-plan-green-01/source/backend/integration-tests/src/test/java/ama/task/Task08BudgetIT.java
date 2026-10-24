package ama.task;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
class Task08BudgetIT {
 @Test void openingSuccessCountsBothSessionSetsHighwaterAndRealPolicyClosure(){
  var f=new Task08Fixture("fairheld","T08_BUDGET");String first=f.ids("T08_HELD").getFirst();f.due(first);var scan=new JdbcExternalOperations(f.queue("T08_HELD"),new ReconciliationScanBudget(1,1));f.source.clear();
  assertEquals(first,scan.claimReconciliation("T08_BUDGET").orElseThrow().operationId());
  // Actual policy: owner discovery, identity, repeated subject, two meeting reads, status,
  // input lock and input validation: Q_policy=R_policy=8, all one-row DB operations.
  assertEquals(13+8,f.source.sql.size());assertEquals(11+8,f.source.rows);assertEquals(2,f.source.sql.stream().filter(x->x.text().startsWith("SET ")).count());
  assertEquals(3,Collections.frequency(f.source.controls,"acquire"));assertEquals(1,Collections.frequency(f.source.controls,"commit"));
  print("opening-success-P1-B1-Q8-R8",f);
 }
 @Test void fourUnregisteredRowsCountExactly23Sql17RowsAndDriverControls(){
  var f=new Task08Fixture("fairunregistered","T08_BUDGET");var scan=new JdbcExternalOperations(f.queue(),new ReconciliationScanBudget(2,4));f.source.clear();assertTrue(scan.claimReconciliation("T08_BUDGET").isEmpty());
  assertEquals(23,f.source.sql.size());assertEquals(17,f.source.rows);assertEquals(8,f.source.sql.stream().filter(x->x.text().startsWith("SET ")).count());assertEquals(4,Collections.frequency(f.source.controls,"commit"));assertEquals(7,Collections.frequency(f.source.controls,"acquire"));assertTrue(f.source.sql.size()<=1+2+4*11);assertTrue(f.source.rows<=1+4+4*9);print("all-skipped-P2-B4",f);
 }
 @Test void pageAndRowBudgetIncludesEveryRawCandidateWithNoDueFilter(){
  var f=new Task08Fixture("fairunregistered","T08_BUDGET");var scan=new JdbcExternalOperations(f.queue(),new ReconciliationScanBudget(8,16));f.source.clear();assertTrue(scan.claimReconciliation("T08_BUDGET").isEmpty());
  assertEquals(83,f.source.sql.size());assertEquals(65,f.source.rows);assertEquals(16,Collections.frequency(f.source.controls,"commit"));
  var pages=f.source.sql.stream().filter(x->x.text().startsWith("SELECT reconcile_scan_seq,id")).toList();assertEquals(2,pages.size());for(var page:pages){assertEquals(8,page.parameters().get(3));assertFalse(page.text().contains("OFFSET"));assertFalse(page.text().contains("state"));}
  assertTrue(f.source.sql.size()<=1+2+16*11);assertTrue(f.source.rows<=1+16+16*9);print("all-skipped-P8-B16",f);
 }
 @Test void actualPlansUseOrdinalIndexAndNoFilesort(){
  var f=new Task08Fixture("fairunregistered","T08_PLAN");
  for(String sql:List.of("SELECT reconcile_scan_seq FROM external_operation FORCE INDEX (external_reconcile_scan) ORDER BY reconcile_scan_seq DESC LIMIT 1","SELECT reconcile_scan_seq,id FROM external_operation FORCE INDEX (external_reconcile_scan) WHERE reconcile_scan_seq>1 AND reconcile_scan_seq<=130 ORDER BY reconcile_scan_seq LIMIT 16")){
   var plans=f.j.queryForList("EXPLAIN "+sql);System.out.println("TASK08 actual EXPLAIN "+plans);assertEquals(1,plans.size());assertEquals("external_reconcile_scan",plans.getFirst().get("key"));assertFalse(String.valueOf(plans.getFirst().get("Extra")).contains("filesort"));assertTrue(Set.of("range","index").contains(plans.getFirst().get("type")));
  }
 }
 static void print(String name,Task08Fixture f){System.out.println("TASK08 TRACE "+name+" SQL="+f.source.sql.size()+" rows="+f.source.rows+" statements="+f.source.sql+" driverControls="+f.source.controls);}
}
