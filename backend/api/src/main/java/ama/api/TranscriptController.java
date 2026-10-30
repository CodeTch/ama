package ama.api;
import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.*;
import ama.contract.TranscriptContracts.*;
import ama.db.TransactionRunner;
import ama.storage.FileRegistry;
import ama.transcript.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/meetings/{meetingId}/transcript") class TranscriptController {
 private final TranscriptConfiguration.Runtime runtime;private final JdbcSavedTranscriptReceipts receipts;private final FileRegistry files;private final TransactionRunner tx;private final MeetingAccess access;
 TranscriptController(TranscriptConfiguration.Runtime runtime,JdbcSavedTranscriptReceipts receipts,FileRegistry files,TransactionRunner tx,MeetingAccess access){this.runtime=runtime;this.receipts=receipts;this.files=files;this.tx=tx;this.access=access;}
 @GetMapping ResponseEntity<byte[]> current(@PathVariable String meetingId,HttpServletRequest request)throws IOException{var result=new LinkedHashMap<>(service(actor(request),meetingId,false).readCurrent(actor(request),meetingId));result.put("saved",result.remove("selected"));return json(result);}
 @GetMapping("/history") ResponseEntity<byte[]> history(@PathVariable String meetingId,@RequestParam(defaultValue="20") int limit,@RequestParam(required=false) String beforeRevisionToken,HttpServletRequest request)throws IOException {
  Long before=null;if(beforeRevisionToken!=null){var node=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("expectedRevisionToken",beforeRevisionToken);before=HttpRevisionTokens.expected(node);}return json(service(actor(request),meetingId,true).history(actor(request),meetingId,limit,before));
 }
 @GetMapping("/revisions/{revisionId}") ResponseEntity<byte[]> revision(@PathVariable String meetingId,@PathVariable String revisionId,HttpServletRequest request)throws IOException{return json(service(actor(request),meetingId,true).readOwner(actor(request),meetingId,revisionId));}
 @GetMapping("/marks") ResponseEntity<byte[]> marks(@PathVariable String meetingId,HttpServletRequest request)throws IOException{return json(service(actor(request),meetingId,false).marks(actor(request),meetingId,null));}
 @GetMapping("/revisions/{revisionId}/marks") ResponseEntity<byte[]> historyMarks(@PathVariable String meetingId,@PathVariable String revisionId,HttpServletRequest request)throws IOException{return json(service(actor(request),meetingId,true).marks(actor(request),meetingId,revisionId));}
 @GetMapping("/confirmation") ResponseEntity<byte[]> confirmation(@PathVariable String meetingId,HttpServletRequest request)throws IOException{var full=service(actor(request),meetingId,false).readCurrent(actor(request),meetingId);var result=new LinkedHashMap<String,Object>();result.put("confirmation",full.get("confirmation"));result.put("observation",full.get("observation"));result.put("correction",full.get("correction"));return json(result);}
 @GetMapping("/confirmations/{confirmationId}") ResponseEntity<byte[]> oldConfirmation(@PathVariable String meetingId,@PathVariable String confirmationId,HttpServletRequest request)throws IOException{return json(service(actor(request),meetingId,true).readConfirmation(actor(request),meetingId,confirmationId));}
 @PutMapping ResponseEntity<byte[]> save(@PathVariable String meetingId,HttpServletRequest request)throws IOException{return mutation(meetingId,request,Operation.SAVE);}
 @PutMapping("/marks") ResponseEntity<byte[]> saveMarks(@PathVariable String meetingId,HttpServletRequest request)throws IOException{return mutation(meetingId,request,Operation.MARKS);}
 @PostMapping("/restorations") ResponseEntity<byte[]> restore(@PathVariable String meetingId,HttpServletRequest request)throws IOException{return mutation(meetingId,request,Operation.RESTORE);}
 @PostMapping("/confirmations") ResponseEntity<byte[]> confirm(@PathVariable String meetingId,HttpServletRequest request)throws IOException{return mutation(meetingId,request,Operation.CONFIRM);}
 private ResponseEntity<byte[]> mutation(String meeting,HttpServletRequest request,Operation operation)throws IOException {
  ama.contract.MinutesContracts.uuid(meeting);var keys=Collections.list(request.getHeaders("Idempotency-Key"));if(keys.size()!=1)throw new IllegalArgumentException("Exactly one idempotency key required");JdbcProcessingIntake.key(keys.getFirst());
  Command command;try(var input=request.getInputStream()){command=TranscriptWire.parse(input,request.getContentLengthLong(),operation);}var actor=actor(request);
  if(runtime.service()==null){var replay=receipts.lookup(actor,meeting,keys.getFirst(),command);if(replay.isPresent())return json(replay.orElseThrow());throw new Failure(503,"TRANSCRIPT_UNAVAILABLE");}
  return json(runtime.service().mutate(actor,meeting,keys.getFirst(),command,UUID.randomUUID().toString()));
 }
 private JdbcSavedTranscripts service(Actor actor,String meeting,boolean owner){ama.contract.MinutesContracts.uuid(meeting);if(runtime.service()!=null)return runtime.service();tx.required(()->{access.require(access.lock(actor,meeting),owner?MeetingAccess.Action.EDIT:MeetingAccess.Action.READ);return null;});throw new Failure(503,"TRANSCRIPT_UNAVAILABLE");}
 @GetMapping("/sources/{fileId}") void source(@PathVariable String meetingId,@PathVariable String fileId,HttpServletRequest request,HttpServletResponse response)throws IOException{download(meetingId,new FileRegistry.CurrentReadRequest(JdbcTranscriptAuthority.REFERENCE,fileId),request,response);}
 @GetMapping("/revisions/{revisionId}/sources/{fileId}") void historySource(@PathVariable String meetingId,@PathVariable String revisionId,@PathVariable String fileId,HttpServletRequest request,HttpServletResponse response)throws IOException{ama.contract.MinutesContracts.uuid(revisionId);download(meetingId,new ProcessingSourceReader.HistoryRequest(revisionId,fileId),request,response);}
 private void download(String meeting,FileRegistry.ReadRequest selector,HttpServletRequest request,HttpServletResponse response)throws IOException {
  ama.contract.MinutesContracts.uuid(meeting);ama.contract.MinutesContracts.uuid(selector instanceof FileRegistry.CurrentReadRequest c?c.fileId():((ProcessingSourceReader.HistoryRequest)selector).fileId());var ranges=Collections.list(request.getHeaders("Range"));if(ranges.size()>1)throw new IllegalArgumentException("One range required");String range=ranges.isEmpty()?null:ranges.getFirst();
  try(var download=files.read(actor(request),meeting,selector,range)){response.setStatus(range==null?200:206);response.setContentType("application/octet-stream");response.setHeader("Cache-Control","private, no-store");response.setHeader("Accept-Ranges","bytes");response.setHeader("X-Content-Type-Options","nosniff");response.setHeader("Content-Disposition",FileController.contentDisposition(download.file().displayName()));response.setContentLengthLong(download.length());if(range!=null)response.setHeader("Content-Range","bytes "+download.offset()+"-"+(download.offset()+download.length()-1)+"/"+download.file().size());download.body().transferTo(response.getOutputStream());}
 }
 private ResponseEntity<byte[]> json(Object body)throws IOException {long maximum=runtime.qualification().get("responseBytes") instanceof Number n?n.longValue():33554432;return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).header("Cache-Control","private, no-store").body(TranscriptWire.response(body,maximum));}
 private static Actor actor(HttpServletRequest request){return ((ResolvedSession)request.getAttribute(SessionSecurityFilter.SESSION)).actor();}
}
