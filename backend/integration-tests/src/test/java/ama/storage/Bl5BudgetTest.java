package ama.storage;
import java.nio.file.*;import java.util.*;import java.util.concurrent.*;import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;import static org.junit.jupiter.api.Assertions.*;
class Bl5BudgetTest {
 @TempDir Path root;
 void ready()throws Exception {long now=System.currentTimeMillis();Files.writeString(root.resolve("monitor.json"),new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("owner",root.getFileName().toString(),"status","ADMIT","observedUtcMillis",now,"expiresUtcMillis",now+10000)));}
 @Test void modelGetAndPostBothConsumeActualRequestBudgetBeforeSend()throws Exception {
  ready();int sent=0;for(int i=0;i<10;i++){Bl5Budget.http(root,"model",i%2==0?"GET":"POST");sent++;}assertEquals(10,sent);assertThrows(IllegalStateException.class,()->Bl5Budget.http(root,"model","GET"));assertEquals("20",Files.readString(root.resolve("budget-model")));assertEquals("22",Files.readString(root.resolve("budget-http")));
 }
 @Test void concurrentAdmissionsShareAnExactDurableCeiling()throws Exception {
  ready();var executor=Executors.newFixedThreadPool(8);try{var calls=new ArrayList<Callable<Boolean>>();for(int i=0;i<40;i++)calls.add(()->{try{Bl5Budget.consume(root,"pipelines",1,12);return true;}catch(IllegalStateException exhausted){return false;}});int passed=0;for(var value:executor.invokeAll(calls))if(value.get())passed++;assertEquals(12,passed);assertEquals("12",Files.readString(root.resolve("budget-pipelines")));}finally{executor.shutdownNow();}
 }
 @Test void missingOrRevokedMonitorPreventsCountersAndNetworkAdmission()throws Exception {
  assertThrows(NoSuchFileException.class,()->Bl5Budget.http(root,"native","POST"));assertFalse(Files.exists(root.resolve("budget-http")));ready();Files.writeString(root.resolve("stopping.json"),"{}");assertThrows(IllegalStateException.class,()->Bl5Budget.http(root,"s3","PUT"));assertFalse(Files.exists(root.resolve("budget-http")));
 }
 @Test void pythonAndJavaModesShareTheSameOsLockedCounter()throws Exception {
  ready();String repo=System.getenv("AMA_TASK11_REPOSITORY_ROOT");assertNotNull(repo);
  String script="import sys,pathlib;sys.path.insert(0,sys.argv[2]);from bl5_https_proxy import consume;root=pathlib.Path(sys.argv[1])\nfor _ in range(40):\n try:consume(root,'pipelines',1,12)\n except ValueError:pass\n";
  var child=new ProcessBuilder("/usr/bin/python3","-c",script,root.toString(),repo+"/tools").redirectErrorStream(true).start();for(int i=0;i<40;i++)try{Bl5Budget.consume(root,"pipelines",1,12);}catch(IllegalStateException exhausted){}assertTrue(child.waitFor(5,TimeUnit.SECONDS));assertEquals(0,child.exitValue(),new String(child.getInputStream().readAllBytes()));assertEquals("12",Files.readString(root.resolve("budget-pipelines")));
 }

}
