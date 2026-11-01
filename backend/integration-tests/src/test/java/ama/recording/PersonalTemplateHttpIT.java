package ama.recording;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Reuses the owned-schema HTTP/session fixture; inherited recording ACL checks also run. */
class PersonalTemplateHttpIT extends RecordingAccessIT {
 Map<String,String> command(String key){return Map.of("Content-Type","application/json","Idempotency-Key",key);}
 byte[] body(Object value)throws Exception{return json.writeValueAsBytes(value);}
 @Test void actualHttpCommandsKeepOriginalReceiptsAcrossEditAndRemoval()throws Exception{
  String owner=identity("template-http-"+UUID.randomUUID()),key=UUID.randomUUID().toString();
  var original=body(Map.of("name","周会模板","themes",List.of("结论","后续行动")));
  var created=accepted(call("POST","/templates",owner,original,command(key)),201);String id=created.path("id").asText();
  assertEquals("1",created.path("revisionToken").asText());assertEquals(created,accepted(call("GET","/templates/"+id,owner,new byte[0],Map.of()),200));
  var edit=body(Map.of("expectedRevisionToken","1","name","已编辑","themes",List.of("行动")));
  var edited=accepted(call("PUT","/templates/"+id,owner,edit,command(UUID.randomUUID().toString())),200);assertEquals("2",edited.path("revisionToken").asText());
  assertEquals(created,accepted(call("POST","/templates",owner,original,command(key)),201));
  accepted(call("PUT","/templates/"+id,owner,edit,command(UUID.randomUUID().toString())),409);
  String removal=UUID.randomUUID().toString();byte[] deletion=body(Map.of("expectedRevisionToken","2"));
  var removed=accepted(call("DELETE","/templates/"+id,owner,deletion,command(removal)),200);assertTrue(removed.path("deleted").asBoolean());
  assertEquals(removed,accepted(call("DELETE","/templates/"+id,owner,deletion,command(removal)),200));
  accepted(call("GET","/templates/"+id,owner,new byte[0],Map.of()),403);
  assertTrue(accepted(call("GET","/templates?limit=2",owner,new byte[0],Map.of()),200).path("items").isEmpty());
  assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM template_revision WHERE template_id=?",Integer.class,id));
 }
 @Test void anotherSessionCannotInspectOrChangePrivateTemplates()throws Exception{
  String owner=identity("template-private-"+UUID.randomUUID()),other=identity("template-foreign-"+UUID.randomUUID());
  String id=accepted(call("POST","/templates",owner,body(Map.of("name","私有模板正文","themes",List.of("私有主题"))),command(UUID.randomUUID().toString())),201).path("id").asText();
  for(String method:List.of("GET","PUT","DELETE")){
   byte[] value=method.equals("GET")?new byte[0]:body(method.equals("PUT")?Map.of("expectedRevisionToken","1","name","越权","themes",List.of("越权")):Map.of("expectedRevisionToken","1"));
   var response=call(method,"/templates/"+id,other,value,command(UUID.randomUUID().toString()));accepted(response,403);assertFalse(response.body().contains("私有"));
  }
  assertTrue(accepted(call("GET","/templates",other,new byte[0],Map.of()),200).path("items").isEmpty());
  var anonymous=call("GET","/templates","",new byte[0],Map.of());accepted(anonymous,401);
 }
 @Test void strictWireRejectsInvalidBodiesAndQueryBeforeCreatingAnything()throws Exception{
  String owner=identity("template-wire-"+UUID.randomUUID());var headers=command(UUID.randomUUID().toString());
  for(String raw:List.of("{\"name\":\"a\",\"themes\":[]}","{\"name\":\"a\",\"themes\":[1]}","{\"name\":\"a\",\"name\":\"b\",\"themes\":[\"c\"]}","{\"name\":\"a\",\"themes\":[\"b\"],\"extra\":true}","{\"name\":\"a\",\"themes\":[\"b\"]} {}"))
   accepted(call("POST","/templates",owner,raw.getBytes(StandardCharsets.UTF_8),headers),400);
  accepted(call("GET","/templates?extra=true",owner,new byte[0],Map.of()),400);
  accepted(call("GET","/templates?limit=2&limit=3",owner,new byte[0],Map.of()),400);
  accepted(call("POST","/templates",owner,new byte[131073],headers),413);
  assertTrue(accepted(call("GET","/templates",owner,new byte[0],Map.of()),200).path("items").isEmpty());
 }
}
