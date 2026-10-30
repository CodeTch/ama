package ama.api;

import ama.contract.IdentityContracts.Actor;
import ama.transcript.JdbcMaterials;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.*;
import java.util.*;

@RestController
class MaterialController {
 private final ObjectProvider<JdbcMaterials> installed;
 private final ObjectMapper json=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(2).maxStringLength(1024).maxNameLength(32).build()).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
 MaterialController(ObjectProvider<JdbcMaterials> installed){this.installed=installed;}
 private JdbcMaterials service(){var service=installed.getIfAvailable();if(service==null)throw new ama.contract.TranscriptContracts.Failure(503,"MATERIAL_UNAVAILABLE");return service;}
 @GetMapping("/api/v1/meetings/{id}/materials") JdbcMaterials.Listing list(@PathVariable String id,HttpServletRequest request,HttpServletResponse response){response.setHeader("Cache-Control","private, no-store");return service().list(actor(request),id);}
 @PostMapping("/api/v1/meetings/{id}/materials") ResponseEntity<JdbcMaterials.Item> add(@PathVariable String id,HttpServletRequest request)throws IOException {var body=body(request);var fields=new HashSet<String>();body.fieldNames().forEachRemaining(fields::add);if(!fields.equals(Set.of("fileId"))&&!fields.equals(Set.of("libraryId","documentId","version")))throw new IllegalArgumentException("Exactly one material source required");return ResponseEntity.status(201).header("Cache-Control","private, no-store").body(service().add(actor(request),id,key(request),new JdbcMaterials.Selection(text(body,"fileId"),text(body,"libraryId"),text(body,"documentId"),text(body,"version")),UUID.randomUUID().toString()));}
 @PostMapping("/api/v1/meetings/{id}/materials/{material}/retry") JdbcMaterials.Item retry(@PathVariable String id,@PathVariable String material,HttpServletRequest request,HttpServletResponse response)throws IOException{if(request.getInputStream().read()!=-1)throw new IllegalArgumentException("Retry takes no body");response.setHeader("Cache-Control","private, no-store");return service().retry(actor(request),id,material,key(request),UUID.randomUUID().toString());}
 @DeleteMapping("/api/v1/meetings/{id}/materials/{material}") ResponseEntity<Void> remove(@PathVariable String id,@PathVariable String material,HttpServletRequest request){service().remove(actor(request),id,material);return ResponseEntity.noContent().header("Cache-Control","private, no-store").build();}
 @GetMapping("/api/v1/material-libraries") Map<String,Object> libraries(HttpServletRequest request,HttpServletResponse response)throws IOException{return page(null,request,response);}
 @GetMapping("/api/v1/material-libraries/{library}/documents") Map<String,Object> documents(@PathVariable String library,HttpServletRequest request,HttpServletResponse response)throws IOException{return page(library,request,response);}
 private Map<String,Object> page(String library,HttpServletRequest request,HttpServletResponse response)throws IOException{ama.transcript.PublicationDestinationPage.path(request.getRequestURI());var params=ama.transcript.PublicationDestinationPage.parameters(request.getQueryString());if(!Set.of("meetingId","search","cursor","limit").containsAll(params.keySet()))throw new IllegalArgumentException("Unknown material query field");String count=value(params,"limit");if(count!=null&&!count.matches("[1-9][0-9]?"))throw new IllegalArgumentException("Invalid material page size");response.setHeader("Cache-Control","private, no-store");return service().libraries(actor(request),value(params,"meetingId"),library,value(params,"search"),value(params,"cursor"),count==null?20:Integer.parseInt(count));}
 private JsonNode body(HttpServletRequest request)throws IOException{byte[] bytes=request.getInputStream().readNBytes(4097);if(bytes.length>4096)throw new IllegalArgumentException("Material request budget");JsonNode value=json.readTree(bytes);if(value==null||!value.isObject())throw new IllegalArgumentException("Material object required");return value;}
 private static String text(JsonNode body,String field){JsonNode value=body.get(field);if(value==null)return null;if(!value.isTextual()||value.textValue().isBlank())throw new IllegalArgumentException("Material source string required");return value.textValue();}
 private static String value(Map<String,String[]> values,String field){var v=values.get(field);return v==null?null:v[0];}
 private static String key(HttpServletRequest request){var values=request.getHeaders("Idempotency-Key");if(values==null||!values.hasMoreElements())throw new IllegalArgumentException("Idempotency-Key required");String value=values.nextElement();if(values.hasMoreElements())throw new IllegalArgumentException("One Idempotency-Key required");return value;}
 private static Actor actor(HttpServletRequest request){Object session=request.getAttribute(SessionSecurityFilter.SESSION);if(!(session instanceof ama.contract.IdentityContracts.ResolvedSession authenticated))throw new ama.access.MeetingAccess.AccessDeniedException();return authenticated.actor();}
}
