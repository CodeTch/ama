package ama.storage;
import org.junit.jupiter.api.Test;import java.util.*;import java.net.*;import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;
class Task06WireHttpIT extends Task06Fixture {
 @Override boolean installedTranscript(){return true;}
 @Test void actualHttpSecurityRangeInvalidCompleteSetsAndOversizeArePrivate()throws Exception {
  String raw=raw(),path="/meetings/"+meeting+"/transcript";var ids=j.queryForList("SELECT segment_id FROM transcript_segment WHERE revision_id=? ORDER BY ordinal",String.class,raw);var segments=ids.stream().map(id->Map.of("segmentId",id,"body","Wire😀","marks",List.of())).toList();String payload=json.writeValueAsString(Map.of("expectedRevisionToken","1","sourceRevisionId",raw,"segments",segments));
  for(var invalid:List.of(payload.replace("\"expectedRevisionToken\":\"1\"","\"expectedRevisionToken\":\"01\""),payload.substring(0,payload.length()-1)+",\"unexpected\":true}",payload+"{}"))privateStatus(call("PUT",path,invalid,"invalid"),400);
  privateStatus(call("PUT",path,json.writeValueAsString(Map.of("expectedRevisionToken","1","sourceRevisionId",raw,"segments",List.of(segments.getFirst()))),"incomplete"),422);
  privateStatus(call("GET",path+"/history?limit=51","",null),400);
  var base=URI.create("http://127.0.0.1:"+app.getEnvironment().getProperty("local.server.port")+"/api/v1"+path);
  privateStatus(HttpClient.newHttpClient().send(HttpRequest.newBuilder(base).GET().build(),HttpResponse.BodyHandlers.ofString()),401);
  privateStatus(HttpClient.newHttpClient().send(HttpRequest.newBuilder(base).header("Cookie","__Host-AMA_SESSION="+token).header("Origin",origin).PUT(HttpRequest.BodyPublishers.ofString(payload)).build(),HttpResponse.BodyHandlers.ofString()),403);
  String outsider="task06-admin-"+UUID.randomUUID();identity(outsider);var admin=new ama.contract.IdentityContracts.Actor(outsider,ama.contract.IdentityContracts.Status.ACTIVE,Set.of(ama.contract.IdentityContracts.Role.PLATFORM_ADMIN));assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->ama.api.Task06RuntimeProbe.actual(app).service().readOwner(admin,meeting,raw));
  privateStatus(call("PUT",path,payload,"save"),200);String file=j.queryForObject("SELECT source_file_id FROM transcript_segment WHERE revision_id=? LIMIT 1",String.class,raw);
  for(String range:List.of("bytes=0-3","bytes=999999-","bytes=0-1,3-4")){var request=HttpRequest.newBuilder(URI.create(base+"/sources/"+file)).header("Cookie","__Host-AMA_SESSION="+token).header("Range",range).GET().build();var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());privateStatus(response,range.equals("bytes=0-3")?206:416);if(response.statusCode()==206){assertEquals("Alph",response.body());assertEquals("bytes 0-3/14",response.headers().firstValue("Content-Range").orElseThrow());}}
  String oversized=" ".repeat(8388609);privateStatus(call("PUT",path,oversized,"oversized"),413);
  assertEquals(2,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Integer.class,meeting));
 }
 static void privateStatus(HttpResponse<String> response,int code){assertEquals(code,response.statusCode(),response.body());assertEquals("private, no-store",response.headers().firstValue("Cache-Control").orElseThrow());}
}
