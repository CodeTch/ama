package ama.storage;
import org.junit.jupiter.api.Test;import java.util.*;import java.net.*;import java.net.http.*;import java.nio.file.*;import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
class Task06FixHttpsIT extends Task06FixFixture {
 int httpsPort;final Map<String,HttpClient> clients=new HashMap<>();final Map<String,CookieManager> jars=new HashMap<>();
 @Override boolean installedTranscript(){return true;}
 @Override void beforeApplication()throws Exception {try(var socket=new java.net.ServerSocket(0,1,InetAddress.getLoopbackAddress())){httpsPort=socket.getLocalPort();}origin="https://localhost:"+httpsPort;bounds.put("server.ssl.enabled",true);bounds.put("server.ssl.key-store",ROOT.resolve("pki/server.p12").toUri().toString());bounds.put("server.ssl.key-store-password",Files.readString(ROOT.resolve("pki/keystore-password")));bounds.put("server.ssl.key-store-type","PKCS12");}
 @Override int serverPort(){return httpsPort;}
 @Override String apiUrl(){return origin+"/api/v1";}
 @Override HttpRequest.Builder authenticated(HttpRequest.Builder request)throws Exception {client();return request;}
 @Override HttpClient client()throws Exception {if(!clients.containsKey(token)){var jar=new CookieManager(null,CookiePolicy.ACCEPT_ALL);var now=Instant.now();String cookie=ama.api.SessionCookies.issue(new ama.contract.IdentityContracts.IssuedSession(token,csrf,now.plusSeconds(3500)),now);assertTrue(cookie.contains("Secure; HttpOnly; SameSite=Lax"));jar.put(URI.create(origin),Map.of("Set-Cookie",List.of(cookie)));jar.getCookieStore().getCookies().forEach(c->c.setVersion(0));assertTrue(jar.get(URI.create("http://localhost:"+httpsPort),Map.of()).getOrDefault("Cookie",List.of()).stream().noneMatch(x->x.contains("__Host-AMA_SESSION=")));clients.put(token,HttpClient.newBuilder().sslContext(Task05TransportIT.tls("writer")).cookieHandler(jar).build());jars.put(token,jar);}return clients.get(token);}
 @Test void actualHttpsLOCALRolesAndFullRevisionTokens()throws Exception{exercise();}
 @Test void actualHttpsS3RolesAndFullRevisionTokens()throws Exception{exercise();}
 void exercise()throws Exception {
  String raw=raw(),path="/meetings/"+meeting+"/transcript",ownerToken=token;
  String member="task06-fix-member-"+UUID.randomUUID(),memberToken=identity(member),outsider="task06-fix-outsider-"+UUID.randomUUID(),outsiderToken=identity(outsider),admin="task06-fix-admin-"+UUID.randomUUID(),adminToken=identity(admin);
  j.update("INSERT INTO role_assignment VALUES (?,'PLATFORM_ADMIN',UTC_TIMESTAMP(6))",admin);
  String working=saved(raw);confirm(working,2);
  check(call("PUT","/meetings/"+meeting+"/info",json.writeValueAsString(Map.of("expectedRevisionToken","3","rosterDraftIds",List.of(member))),"info"),200);
  check(call("POST","/meetings/"+meeting+"/roster-confirmations","{\"expectedRevisionToken\":\"4\"}","roster"),200);
  for(String role:List.of("owner","member","outsider","admin")){token=switch(role){case "owner"->ownerToken;case "member"->memberToken;case "outsider"->outsiderToken;default->adminToken;};check(call("GET",path,"",null),role.equals("owner")||role.equals("member")?200:403);check(call("GET",path+"/revisions/"+working,"",null),role.equals("owner")?200:403);if(!role.equals("owner"))check(call("POST",path+"/confirmations",json.writeValueAsString(Map.of("expectedRevisionToken","5","savedRevisionId",working)),"deny"),403);}
  token=ownerToken;
  var badOrigin=HttpRequest.newBuilder(URI.create(apiUrl()+path)).header("Origin","https://foreign.invalid").header("X-CSRF-Token",csrf).PUT(HttpRequest.BodyPublishers.ofString("{}")).build();check(client().send(badOrigin,HttpResponse.BodyHandlers.ofString()),403);
  var noCsrf=HttpRequest.newBuilder(URI.create(apiUrl()+path)).header("Origin",origin).PUT(HttpRequest.BodyPublishers.ofString("{}")).build();check(client().send(noCsrf,HttpResponse.BodyHandlers.ofString()),403);
  check(HttpClient.newBuilder().sslContext(Task05TransportIT.tls("writer")).build().send(HttpRequest.newBuilder(URI.create(apiUrl()+path)).GET().build(),HttpResponse.BodyHandlers.ofString()),401);
  var observations=new ArrayList<Map<String,Object>>();
  for(long value:new long[]{9007199254740991L,9007199254740992L,9007199254740993L,9223372036854775806L}){
   rootMutation(()->{j.update("UPDATE meeting SET revision=? WHERE id=?",value,meeting);j.update("UPDATE task_subject SET business_version=? WHERE subject_id=? AND subject_kind='MEETING'",value,meeting);j.update("UPDATE meeting_search_projection SET meeting_revision=? WHERE meeting_id=?",value,meeting);});
   String payload=json.writeValueAsString(Map.of("expectedRevisionToken",Long.toString(value),"savedRevisionId",working)),key="value-"+value;
   var accepted=call("POST",path+"/confirmations",payload,key);check(accepted,200);assertEquals(Long.toString(value),body(accepted).path("observation").path("revisionToken").asText());
   String legacy=payload.replace("\"expectedRevisionToken\":\""+value+"\"","\"expectedRevision\":"+value);
   var replay=call("POST",path+"/confirmations",legacy,key);check(replay,200);assertTrue(body(replay).path("replayed").asBoolean());assertEquals(body(accepted).path("result"),body(replay).path("result"));
   String dual=payload.substring(0,payload.length()-1)+",\"expectedRevision\":"+value+"}";check(call("POST",path+"/confirmations",dual,key),200);
   check(call("POST",path+"/confirmations",payload.substring(0,payload.length()-1)+",\"expectedRevision\":"+(value-1)+"}",key),400);
   if(value==9223372036854775806L){var segments=edits(working,ama.contract.TranscriptContracts.Operation.SAVE).stream().map(e->Map.of("segmentId",e.segmentId(),"body",e.body(),"marks",List.of())).toList();check(call("PUT",path,json.writeValueAsString(Map.of("expectedRevisionToken",Long.toString(value),"sourceRevisionId",working,"segments",segments)),"exhausted"),409);assertEquals(value,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Long.class,meeting));}
   observations.add(Map.of("value",Long.toString(value),"token",200,"legacyReplay",200,"equalDual",200,"mismatch",400));
  }
  var logout=call("POST","/session/logout","{}",null);check(logout,204);String cleared=logout.headers().firstValue("Set-Cookie").orElseThrow();assertTrue(cleared.contains("Secure")&&cleared.contains("HttpOnly")&&cleared.contains("SameSite=Lax")&&cleared.contains("Max-Age=0"));
  Files.writeString(receipt.resolve("https-evidence.json"),json.writeValueAsString(Map.of("transport","actual Spring Boot public HTTPS using existing PKI","origin",origin,"roles",List.of("owner","effective-member","outsider","platform-admin"),"tokens",observations,"secureCookieHttpExcluded",true,"serverLogoutSecureClearing",true,"browserClaim",false,"controlledSessionNotEnterpriseLogin",true)));
 }
 static void check(HttpResponse<String> response,int status){assertEquals(status,response.statusCode(),response.body());assertTrue(response.headers().firstValue("Cache-Control").orElseThrow().contains("no-store"));if(response.uri().getPath().contains("/transcript"))assertEquals("private, no-store",response.headers().firstValue("Cache-Control").orElseThrow());}
}
