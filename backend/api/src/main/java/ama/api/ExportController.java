package ama.api;

import ama.contract.IdentityContracts.*;
import ama.export.JdbcMeetingExports;
import ama.task.MinutesExportRuntime;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController final class ExportController {
 private final JdbcMeetingExports exports;
 private final ObjectMapper json=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
 ExportController(JdbcMeetingExports exports){this.exports=exports;}
 @PostMapping("/api/v1/meetings/{meetingId}/exports") ResponseEntity<MinutesExportRuntime.Status> create(@PathVariable String meetingId,HttpServletRequest request)throws IOException{
  query(request);if(request.getContentLengthLong()>4096)throw new ama.contract.TranscriptContracts.Failure(413,"EXPORT_REQUEST_TOO_LARGE");byte[] bytes=request.getInputStream().readNBytes(4097);if(bytes.length>4096)throw new ama.contract.TranscriptContracts.Failure(413,"EXPORT_REQUEST_TOO_LARGE");
  JsonNode body;try{body=json.readTree(bytes);}catch(JsonProcessingException invalid){throw new IllegalArgumentException("Invalid export JSON");}
  if(body==null||!body.isObject()||body.size()!=2||!body.path("sourceRevision").isTextual()||!body.path("format").isTextual())throw new IllegalArgumentException("Exact export request required");
  String revision=body.path("sourceRevision").textValue(),format=body.path("format").textValue();ama.contract.MinutesContracts.uuid(meetingId);ama.contract.MinutesContracts.uuid(revision);if(!Set.of("md","pdf","docx").contains(format))throw new IllegalArgumentException("Export format required");
  var keys=Collections.list(request.getHeaders("Idempotency-Key"));if(keys.size()!=1)throw new IllegalArgumentException("One export request key required");
  return ResponseEntity.accepted().header("Cache-Control","private, no-store").body(exports.accept(actor(request),meetingId,keys.getFirst(),revision,format));
 }
 @GetMapping("/api/v1/exports/{id}") ResponseEntity<MinutesExportRuntime.Status> status(@PathVariable String id,HttpServletRequest request){query(request);return ResponseEntity.ok().header("Cache-Control","private, no-store").body(exports.status(actor(request),id));}
 @GetMapping("/api/v1/exports/{id}/download") void download(@PathVariable String id,HttpServletRequest request,HttpServletResponse response)throws IOException{
  query(request);var ranges=Collections.list(request.getHeaders("Range"));if(ranges.size()>1)throw new IllegalArgumentException("One range required");String range=ranges.isEmpty()?null:ranges.getFirst();
  var identity=SessionSecurityFilter.readIdentity(request);
  try(var download=AuthorizedDownloads.guard(exports.download(actor(request),id,range),identity,(actor,probe)->exports.download(actor,id,probe))){
   response.setStatus(range==null?200:206);response.setContentType("application/octet-stream");response.setHeader("Cache-Control","private, no-store");response.setHeader("Accept-Ranges","bytes");response.setHeader("X-Content-Type-Options","nosniff");response.setHeader("Content-Disposition",FileController.contentDisposition(download.file().displayName()));response.setContentLengthLong(download.length());
   if(range!=null)response.setHeader("Content-Range","bytes "+download.offset()+"-"+(download.offset()+download.length()-1)+"/"+download.file().size());
   download.body().transferTo(response.getOutputStream());
  }
 }
 private static Actor actor(HttpServletRequest request){if(!(request.getAttribute(SessionSecurityFilter.SESSION) instanceof ResolvedSession session))throw new ama.access.MeetingAccess.AccessDeniedException();return session.actor();}
 private static void query(HttpServletRequest request){if(!request.getParameterMap().isEmpty())throw new IllegalArgumentException("Export query unsupported");}
}
