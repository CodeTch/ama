package ama.api;

import ama.contract.IdentityContracts.*;
import ama.task.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Collections;
import org.springframework.web.bind.annotation.*;

@RestController
public final class RecordingArchiveController {
 private final JdbcTaskQueue queue;
 public RecordingArchiveController(JdbcTaskQueue queue){this.queue=java.util.Objects.requireNonNull(queue);}
 @PostMapping("/api/v1/recordings/{sessionId}/archive")
 RecordingArchiveRuntime.Status prepare(@PathVariable String sessionId,HttpServletRequest request){return RecordingArchiveRuntime.accept(queue,actor(request),sessionId);}
 @GetMapping("/api/v1/recordings/{sessionId}/archive")
 RecordingArchiveRuntime.Status status(@PathVariable String sessionId,HttpServletRequest request){return RecordingArchiveRuntime.status(queue,actor(request),sessionId);}
 @GetMapping("/api/v1/meetings/{meetingId}/recording-archives/{inputId}")
 RecordingArchiveRuntime.Status input(@PathVariable String meetingId,@PathVariable String inputId,HttpServletRequest request){return RecordingArchiveRuntime.statusInput(queue,actor(request),meetingId,inputId);}
 @GetMapping("/api/v1/recordings/{sessionId}/audio")
 void audio(@PathVariable String sessionId,HttpServletRequest request,HttpServletResponse response)throws IOException {
  var actor=actor(request);var ranges=Collections.list(request.getHeaders("Range"));if(ranges.size()>1)throw new IllegalArgumentException("One range required");String range=ranges.isEmpty()?null:ranges.getFirst();
  try(var download=RecordingArchiveRuntime.download(queue,actor,sessionId,range)){
   response.setStatus(range==null?200:206);response.setContentType("audio/wav");response.setHeader("Accept-Ranges","bytes");response.setHeader("X-Content-Type-Options","nosniff");response.setHeader("Cache-Control","private, no-store");response.setHeader("Content-Disposition","inline; filename=recording.wav");response.setContentLengthLong(download.length());
   if(range!=null)response.setHeader("Content-Range","bytes "+download.offset()+"-"+(download.offset()+download.length()-1)+"/"+download.file().size());download.body().transferTo(response.getOutputStream());
  }
 }
 private static Actor actor(HttpServletRequest request){if(!(request.getAttribute(SessionSecurityFilter.SESSION) instanceof ResolvedSession session))throw new ama.access.MeetingAccess.AccessDeniedException();return session.actor();}
 @ExceptionHandler(ama.access.MeetingAccess.AccessDeniedException.class)
 void denied(HttpServletResponse response)throws IOException{BusinessErrors.write(response,403,"FORBIDDEN","Access denied");}
 @ExceptionHandler(ama.contract.FileContracts.NotFound.class)
 void missing(HttpServletResponse response)throws IOException{BusinessErrors.write(response,404,"RECORDING_ARCHIVE_NOT_READY","Recording archive unavailable");}
 @ExceptionHandler(ama.storage.JdbcFileRegistry.RangeFailure.class)
 void range(ama.storage.JdbcFileRegistry.RangeFailure failure,HttpServletResponse response)throws IOException{response.setHeader("Content-Range","bytes */"+failure.size());BusinessErrors.write(response,416,"INVALID_RANGE","Requested range unavailable");}
 @ExceptionHandler(ama.contract.FileContracts.Conflict.class)
 void conflict(HttpServletResponse response)throws IOException{BusinessErrors.write(response,409,"RECORDING_ARCHIVE_CONFLICT","Recording archive changed");}
 @ExceptionHandler(IllegalArgumentException.class)
 void invalid(HttpServletResponse response)throws IOException{BusinessErrors.write(response,400,"INVALID_ARGUMENT","Invalid recording archive request");}
 @ExceptionHandler(Exception.class)
 void unavailable(HttpServletResponse response)throws IOException{BusinessErrors.write(response,503,"RECORDING_ARCHIVE_UNAVAILABLE","Recording archive unavailable");}
}
