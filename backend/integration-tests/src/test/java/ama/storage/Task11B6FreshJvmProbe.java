package ama.storage;

import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.support.GenericApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

/** A genuinely new JVM: reconstruct all adapters from persisted settings, then read only. */
public final class Task11B6FreshJvmProbe {
 public static void main(String[] args)throws Exception {
  assertEquals(1,args.length);Path root=Path.of(System.getenv("AMA_TASK09_RUN_ROOT")).toRealPath();Path input=Path.of(args[0]);assertEquals(input.toAbsolutePath().normalize(),input.toRealPath());assertTrue(input.startsWith(root.resolve("runtime")));assertFalse(Files.isSymbolicLink(input));assertEquals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"),Files.getPosixFilePermissions(input));assertTrue(Files.size(input)<262144);
  Path output=Path.of(System.getenv("AMA_TASK11_EVIDENCE_WINDOW"));assertEquals("tls-genuine-01",output.getFileName().toString());assertTrue(Files.isRegularFile(output.resolve("consumed.json")));var json=new ObjectMapper();var saved=json.readTree(Files.readAllBytes(input));assertNotEquals(saved.path("parentPid").asLong(),ProcessHandle.current().pid());
  var source=ama.publication.Task11B5Source04.open();var sql=new org.springframework.jdbc.core.JdbcTemplate(source);sql.setQueryTimeout(5);var before=Task11B5RetainedDecisionsIT.rows(sql);
  var binding=json.treeToValue(saved.path("binding"),ManagedBackendId.Local.class);var backend=ManagedBackendAdapters.local(binding.id(),binding,Task09NativeFixture.tls("writer"),1,"WRITABLE");var definitions=new FileBackendDefinitions(List.of(backend),binding.id());
  Map<String,Object> settings=json.convertValue(saved.path("settings"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});assertEquals("CONTROLLED_TLS",settings.get("publication.mode"));assertTrue(((String)settings.get("publication.origin")).startsWith("https://127.0.0.1:"));
  try(var app=new SpringApplicationBuilder(ama.api.ApiApplication.class).properties(settings).initializers(context->{var c=(GenericApplicationContext)context;c.registerBean("task09Source",javax.sql.DataSource.class,()->source);c.registerBean("task09Backends",FileBackendDefinitions.class,()->definitions);}).run("--server.address=127.0.0.1","--server.port=0","--management.server.port=0","--logging.level.root=ERROR")){
   assertEquals("CONTROLLED_TLS",app.getEnvironment().getProperty("publication.mode"));var service=ama.api.Task11RuntimeProbe.publication(app);assertEquals(Map.of("getWriteAttempts",0L,"postWriteAttempts",0L),service.traffic());String token=saved.path("token").asText(),meeting=saved.path("meeting").asText(),publication=saved.path("publication").asText();var resolved=new ama.identity.JdbcIdentitySessions(source).resolve(token).orElseThrow();assertEquals(saved.path("actor").asText(),resolved.actor().enterpriseId());
   var receipt=service.readPublishedReceipt(resolved.actor(),meeting,publication);assertEquals(saved.path("receipt"),json.valueToTree(receipt));
   String api="http://127.0.0.1:"+app.getEnvironment().getRequiredProperty("local.server.port")+"/api/v1";
   try(var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build()){
    var finalResponse=get(http,api+"/meetings/"+meeting+"/final-confirmations/"+saved.path("confirmation").asText(),token);assertEquals(saved.path("final"),json.readTree(finalResponse.body()));
    var document=get(http,api+"/meetings/"+meeting+"/publications/"+publication+"/document",token);assertEquals(saved.path("document").asText(),document.body());assertTrue(document.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
   }
   assertEquals(Map.of("getWriteAttempts",2L,"postWriteAttempts",0L),service.traffic());assertTrue(before.equals(Task11B5RetainedDecisionsIT.rows(sql)),"Fresh JVM reads changed selected business tables");
   Files.writeString(output.resolve("fresh-jvm-readback.json"),json.writeValueAsString(Map.ofEntries(Map.entry("pid",ProcessHandle.current().pid()),Map.entry("parentPid",saved.path("parentPid").asLong()),Map.entry("startInstant",ProcessHandle.current().info().startInstant().orElseThrow().toString()),Map.entry("meeting",meeting),Map.entry("publication",publication),Map.entry("freshResolvedSession",true),Map.entry("sameReceiptFinalDocument",true),Map.entry("traffic",service.traffic()),Map.entry("unchangedTables",before.keySet()),Map.entry("qualification","Fresh JVM adapters from saved settings and fresh DB session resolution; read-only original final/receipt/document over genuine TLS. No task claim, recovery or crash-boundary qualification."))),StandardOpenOption.CREATE_NEW);
  }
 }
 static HttpResponse<String> get(HttpClient http,String url,String token)throws Exception {var response=http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("Cookie","__Host-AMA_SESSION="+token).GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,response.statusCode());assertEquals("private, no-store",response.headers().firstValue("Cache-Control").orElse(""));assertTrue(response.body().length()<16777216);return response;}
}
