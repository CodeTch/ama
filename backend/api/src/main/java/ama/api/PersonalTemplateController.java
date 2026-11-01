package ama.api;

import ama.contract.IdentityContracts.*;
import ama.minutes.JdbcPersonalTemplates;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/templates")
class PersonalTemplateController {
 static final int MAX_BODY=131072;
 static final class TooLarge extends RuntimeException {}
 private final JdbcPersonalTemplates templates;
 private final ObjectMapper json=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
 PersonalTemplateController(JdbcPersonalTemplates templates){this.templates=templates;}

 @GetMapping ResponseEntity<?> list(HttpServletRequest request){
  query(request,Set.of("limit","cursor"));int limit=20;
  if(request.getParameter("limit")!=null){String value=request.getParameter("limit");if(!value.matches("[1-9][0-9]?"))throw new IllegalArgumentException("Invalid page limit");limit=Integer.parseInt(value);}
  return result(200,templates.list(actor(request),limit,request.getParameter("cursor")));
 }
 @GetMapping("/{id}") ResponseEntity<?> read(@PathVariable String id,HttpServletRequest request){query(request,Set.of());return result(200,templates.read(actor(request),id));}
 @PostMapping ResponseEntity<?> create(HttpServletRequest request)throws IOException{
  var body=body(request,Set.of("name","themes"));return result(201,templates.create(actor(request),key(request),text(body,"name"),themes(body)));
 }
 @PutMapping("/{id}") ResponseEntity<?> update(@PathVariable String id,HttpServletRequest request)throws IOException{
  var body=body(request,Set.of("expectedRevisionToken","name","themes"));return result(200,templates.update(actor(request),id,key(request),HttpRevisionTokens.expected(body),text(body,"name"),themes(body)));
 }
 @DeleteMapping("/{id}") ResponseEntity<?> delete(@PathVariable String id,HttpServletRequest request)throws IOException{
  var body=body(request,Set.of("expectedRevisionToken"));return result(200,templates.delete(actor(request),id,key(request),HttpRevisionTokens.expected(body)));
 }
 private JsonNode body(HttpServletRequest request,Set<String> fields)throws IOException{
  query(request,Set.of());if(request.getContentLengthLong()>MAX_BODY)throw new TooLarge();byte[] bytes=request.getInputStream().readNBytes(MAX_BODY+1);if(bytes.length>MAX_BODY)throw new TooLarge();
  try{var value=json.readTree(bytes);if(value==null||!value.isObject()||value.size()!=fields.size())throw new IllegalArgumentException("Exact template body required");value.fieldNames().forEachRemaining(name->{if(!fields.contains(name))throw new IllegalArgumentException("Unknown template field");});return value;}catch(JsonProcessingException invalid){throw new IllegalArgumentException("Invalid template JSON");}
 }
 private static List<String> themes(JsonNode body){var value=body.get("themes");if(value==null||!value.isArray()||value.isEmpty()||value.size()>8)throw new IllegalArgumentException("One to eight themes required");var result=new ArrayList<String>();for(var entry:value){if(!entry.isTextual())throw new IllegalArgumentException("Text theme required");result.add(entry.textValue());}return result;}
 private static String text(JsonNode value,String name){if(!value.path(name).isTextual())throw new IllegalArgumentException("Template text required");return value.path(name).textValue();}
 private static String key(HttpServletRequest request){var keys=Collections.list(request.getHeaders("Idempotency-Key"));if(keys.size()!=1)throw new IllegalArgumentException("One idempotency key required");return keys.getFirst();}
 private static Actor actor(HttpServletRequest request){var value=request.getAttribute(SessionSecurityFilter.SESSION);if(!(value instanceof ResolvedSession session))throw new ama.access.MeetingAccess.AccessDeniedException();return session.actor();}
 private static void query(HttpServletRequest request,Set<String> allowed){request.getParameterMap().forEach((name,values)->{if(!allowed.contains(name)||values.length!=1)throw new IllegalArgumentException("Invalid template query");});}
 private static ResponseEntity<?> result(int status,Object value){return ResponseEntity.status(status).header("Cache-Control","private, no-store").body(value);}
}
