package ama.api;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.*;
import ama.db.TransactionRunner;
import ama.recording.*;
import ama.storage.RevocableInputStream;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
class RecordingPartController {
 private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(RecordingPartController.class);
 private final JdbcRecordingPersistence persistence;private final MeetingAccess access;private final TransactionRunner tx;
 RecordingPartController(JdbcRecordingPersistence persistence,MeetingAccess access,TransactionRunner tx){this.persistence=persistence;this.access=access;this.tx=tx;}
 record Item(String partId,int partNo,long startSample,long sampleCount,String state){}
 record Listing(String sessionId,List<Item> parts){}
 private record Captured(List<RecordingPartWave.Part> parts,java.time.Instant deadline){}
 private Captured capture(Actor actor,String sid){
  ama.contract.MinutesContracts.uuid(sid);
  String meeting=persistence.meetingId(sid);
  return tx.required(()->{var gate=access.lock(actor,meeting);access.require(gate,MeetingAccess.Action.EDIT);var deadline=access.readAuthorityDeadline(gate).orElse(java.time.Instant.MAX);return new Captured(persistence.withinMeeting(sid,meeting,()->RecordingPartWave.parts(persistence.load(sid))),deadline);});
 }
 @GetMapping("/api/v1/recordings/{sessionId}/part-sources")
 Listing list(@PathVariable String sessionId,HttpServletRequest request){return new Listing(sessionId,capture(actor(request),sessionId).parts().stream().map(p->new Item(p.partId(),p.partNo(),p.startSample(),p.sampleCount(),p.sampleCount()==0?"EMPTY":p.available()?"AVAILABLE":"CAPTURING")).toList());}
 private record Selected(RecordingPartWave.Part part,java.time.Instant deadline){}
 private Selected part(Actor actor,String sid,String partId){ama.contract.MinutesContracts.uuid(partId);var captured=capture(actor,sid);return new Selected(captured.parts().stream().filter(p->p.partId().equals(partId)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown recording part")),captured.deadline());}
 @GetMapping("/api/v1/recordings/{sessionId}/parts/{partId}/audio")
 void audio(@PathVariable String sessionId,@PathVariable String partId,HttpServletRequest request,HttpServletResponse response)throws IOException{
  var original=part(actor(request),sessionId,partId);var selected=original.part();if(!selected.available())throw new RecordingService.Paused();
  var ranges=Collections.list(request.getHeaders("Range"));if(ranges.size()>1)throw new IllegalArgumentException("One range required");String range=ranges.isEmpty()?null:ranges.getFirst();
  var identity=SessionSecurityFilter.readIdentity(request);
  java.util.function.Supplier<java.time.Instant> validate=()->{var fresh=part(identity.get(),sessionId,partId);if(!selected.equals(fresh.part()))throw new MeetingAccess.AccessDeniedException();return fresh.deadline();};
  try(var download=RecordingPartWave.open(selected,range,p->persistence.readChunk(sessionId,partId,p.seq()))){try(var guarded=new RevocableInputStream(download.body(),validate.get(),validate)){
   response.setStatus(range==null?200:206);response.setContentType("audio/wav");response.setHeader("Accept-Ranges","bytes");response.setHeader("Cache-Control","private, no-store");response.setHeader("X-Content-Type-Options","nosniff");response.setHeader("Content-Disposition","inline; filename=recording-part-"+selected.partNo()+".wav");response.setContentLengthLong(download.length());
   if(range!=null)response.setHeader("Content-Range","bytes "+download.offset()+"-"+(download.offset()+download.length()-1)+"/"+download.size());guarded.transferTo(response.getOutputStream());
  }}catch(IOException|RuntimeException failure){
   // Before commitment reset both the binary headers and output mode, so advice can
   // return a finite business error. After commitment close the truncated response;
   // never append JSON to a WAV or wait for its declared remaining bytes.
   if(!response.isCommitted()){response.reset();throw failure;}
   LOG.warn("Recording part stream terminated: type={}",failure.getClass().getSimpleName());
   try{response.getOutputStream().close();}catch(IOException ignored){/* The client already disconnected. */}
  }
 }
 @ExceptionHandler(RecordingPartWave.InvalidRange.class)void range(RecordingPartWave.InvalidRange error,HttpServletResponse response)throws IOException{response.setHeader("Content-Range","bytes */"+error.size);BusinessErrors.write(response,416,"INVALID_RANGE","Requested range unavailable");}
 private static Actor actor(HttpServletRequest request){if(!(request.getAttribute(SessionSecurityFilter.SESSION) instanceof ResolvedSession session))throw new MeetingAccess.AccessDeniedException();return session.actor();}
}
