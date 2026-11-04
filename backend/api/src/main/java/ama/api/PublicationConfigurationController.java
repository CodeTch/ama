package ama.api;

import ama.contract.IdentityContracts.ResolvedSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.WebAsyncTask;
import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/meetings/{meetingId}/publication-configuration")
final class PublicationConfigurationController {
 private final MinutesConfiguration.Runtime runtime;private final PublicationReadRequests reads;
 PublicationConfigurationController(MinutesConfiguration.Runtime runtime,PublicationReadRequests reads){this.runtime=runtime;this.reads=reads;}
 @GetMapping WebAsyncTask<ResponseEntity<byte[]>> get(@PathVariable String meetingId,HttpServletRequest request){return read(meetingId,null,request);}
 @PutMapping WebAsyncTask<ResponseEntity<byte[]>> put(@PathVariable String meetingId,HttpServletRequest request)throws IOException{
  byte[] raw;try(var input=request.getInputStream()){raw=input.readNBytes(65537);}if(raw.length>65536)throw new ama.contract.TranscriptContracts.Failure(413,"REQUEST_TOO_LARGE");
  try{
   var mapper=new com.fasterxml.jackson.databind.ObjectMapper(com.fasterxml.jackson.core.JsonFactory.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).streamReadConstraints(com.fasterxml.jackson.core.StreamReadConstraints.builder().maxNestingDepth(6).maxStringLength(8192).build()).build()).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
   Map<String,Object> body=mapper.readValue(raw,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});if(body==null)throw new IllegalArgumentException("Object required");return read(meetingId,body,request);
  }catch(com.fasterxml.jackson.core.JsonProcessingException malformed){throw new IllegalArgumentException("Invalid configuration request");}
 }
 private WebAsyncTask<ResponseEntity<byte[]>> read(String meeting,Map<String,Object> body,HttpServletRequest request){
  ama.contract.MinutesContracts.uuid(meeting);if(request.getQueryString()!=null)throw new IllegalArgumentException("Unexpected query");
  var actor=((ResolvedSession)request.getAttribute(SessionSecurityFilter.SESSION)).actor();if(runtime.publications()==null)throw PublicationReadRequests.unavailable();
  return reads.start(cancellation->{var value=body==null?runtime.publications().configuration(actor,meeting,cancellation):runtime.publications().saveConfiguration(actor,meeting,body,cancellation);return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).header("Cache-Control","private, no-store").body(TranscriptWire.response(value,65536));});
 }
}
