package ama.transcript;

import java.nio.file.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real production Java TLS/delegation to durable Python; DB/current actor is explicitly substituted. */
class Task11KnowledgeTlsProtocolIT {
 static final Path P=Path.of("/home/tanren/ama/.worktrees/phase-b-completion"),W=P.resolve(".superpowers/sdd/2026-10-23-ama-phase-b-remaining");
 static final String OWNER="task11b6-aba9ef6fe1cb49038cc08ab48f5648eb";
 @Test void realTlsDualCredentialsProviderCasAndRetainedStoreRestart()throws Exception {
  var json=new ObjectMapper();Path out=W.resolve("task-11-b6-runtime-evidence-20261027/tls-cas-01"),root=P.resolve(".tools").resolve(OWNER);assertEquals(out.toString(),System.getenv("AMA_TASK11_EVIDENCE_WINDOW"));assertTrue(Files.isRegularFile(out.resolve("consumed.json")));assertFalse(Files.exists(root));assertEquals(P,Path.of(System.getenv("AMA_TASK11_REPOSITORY_ROOT")));
  var process=new ProcessBuilder("/usr/bin/python3",P.resolve("tools/providers/knowledge_tls_cas_probe.py").toString()).redirectErrorStream(true).redirectOutput(out.resolve("driver.log").toFile()).start();
  try {
   Path ready=out.resolve("java-client-ready.json");long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);while(process.isAlive()&&!Files.exists(ready)&&System.nanoTime()<deadline)Thread.sleep(25);assertTrue(process.isAlive(),"See bounded driver failure evidence");assertTrue(Files.isRegularFile(ready));var start=json.readTree(Files.readAllBytes(ready));Path identities=root.resolve("application.json");assertEquals(identities.toString(),start.path("application").asText());assertEquals("real-viewer",start.path("actor").asText());int port=start.path("port").asInt();assertTrue(port>0&&port<=65535);
   try(var client=new ControlledKnowledgeClient(identities,URI.create("https://127.0.0.1:"+port),"CONTROLLED_TLS")){
    var fixture=new Task11DiscoveryServiceBoundaryTest.Fixture(client);var result=fixture.service.destinations(Task11DiscoveryServiceBoundaryTest.ACTOR,Task11DiscoveryServiceBoundaryTest.M,null,Map.of(),new Task11KnowledgeReadTransportTest.Cancel());var items=(List<?>)result.get("items");assertEquals(1,items.size());assertEquals("controlled-library",((Map<?,?>)items.getFirst()).get("id"));assertEquals(2,fixture.roots.get());assertEquals(Map.of("getWriteAttempts",1L,"postWriteAttempts",0L),client.traffic());assertEquals(json.convertValue(start.path("identities"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,String>>(){}),client.identities());
    Files.writeString(out.resolve("java-client-ack.json"),json.writeValueAsString(Map.of("get",1,"post",0,"actor","real-viewer","actualTlsAndProviderAuth",true,"currentActorSource","explicit database boundary substitute","freshLocalChecks",2)),StandardOpenOption.CREATE_NEW);
   }
   assertTrue(process.waitFor(100,TimeUnit.SECONDS),"Owned protocol driver exceeded bound");assertEquals(0,process.exitValue(),"See exact owned protocol evidence; no retry");var result=json.readTree(Files.readAllBytes(out.resolve("protocol-result.json")));assertTrue(result.path("qualified").asBoolean());assertEquals(1,result.path("documentCount").asInt());assertTrue(result.path("restartPreservedAllSevenTables").asBoolean());assertTrue(result.path("constructedProviderInputs").asBoolean());assertFalse(result.path("applicationBusinessMarker").asBoolean());assertFalse(json.readTree(Files.readAllBytes(out.resolve("provider-2-closed.json"))).path("alive").asBoolean());
  } finally {
   if(process.isAlive()){process.destroy();if(!process.waitFor(5,TimeUnit.SECONDS)){process.destroyForcibly();assertTrue(process.waitFor(5,TimeUnit.SECONDS));}}
   Files.writeString(out.resolve("driver-closed.json"),json.writeValueAsString(Map.of("pid",process.pid(),"alive",process.isAlive(),"exit",process.exitValue())),StandardOpenOption.CREATE_NEW);
  }
 }
}
