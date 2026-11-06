package ama.api;
import jakarta.servlet.http.HttpServletResponse;import java.io.IOException;import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(assignableTypes={TranscriptController.class,MaterialEnhancementController.class,RecordingProcessingController.class}) @org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE) class TranscriptErrors {
 private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(TranscriptErrors.class);
 private static void diagnose(Exception failure){LOG.warn("Transcript read unavailable: type={} location={}",failure.getClass().getName(),java.util.Arrays.stream(failure.getStackTrace()).limit(8).toList());}
 private void write(HttpServletResponse r,int status,String code)throws IOException {
  if(r.containsHeader("Content-Disposition")){
   // A range may lose its grant between sealed upload parts. Never append JSON to audio.
   if(r.isCommitted()){
    try{r.getOutputStream().close();}catch(IOException disconnected){/* A closed transport cannot carry another response. */}
    return;
   }
   // Clear buffered bytes, their length and the binary output selection before writing the error.
   r.reset();
  }
  BusinessErrors.write(r,status,code,"Transcript request could not be completed");r.setHeader("Cache-Control","private, no-store");
 }
 @ExceptionHandler(ama.contract.TranscriptContracts.Failure.class) void explicit(ama.contract.TranscriptContracts.Failure failure,HttpServletResponse r)throws IOException{write(r,failure.status(),failure.code());}
 @ExceptionHandler(ama.contract.MinutesContracts.Failure.class) void provenance(ama.contract.MinutesContracts.Failure failure,HttpServletResponse r)throws IOException{if(java.util.Set.of(ama.contract.MinutesContracts.FailureCode.INVALID_ARGUMENT,ama.contract.MinutesContracts.FailureCode.INVALID_SCALAR,ama.contract.MinutesContracts.FailureCode.INVALID_ID).contains(failure.code()))write(r,400,"INVALID_ARGUMENT");else {diagnose(failure);write(r,503,"TRANSCRIPT_UNAVAILABLE");}}
 @ExceptionHandler(ama.access.MeetingAccess.AccessDeniedException.class) void denied(HttpServletResponse r)throws IOException{write(r,403,"MEETING_FORBIDDEN");}
 @ExceptionHandler(ama.contract.FileContracts.NotFound.class) void missing(HttpServletResponse r)throws IOException{write(r,404,"TRANSCRIPT_NOT_FOUND");}
 @ExceptionHandler(ama.contract.FileContracts.Conflict.class) void conflict(HttpServletResponse r)throws IOException{write(r,409,"TRANSCRIPT_CONFLICT");}
 @ExceptionHandler(IllegalArgumentException.class) void invalid(HttpServletResponse r)throws IOException{write(r,400,"INVALID_ARGUMENT");}
 @ExceptionHandler(ama.storage.JdbcFileRegistry.RangeFailure.class) void range(ama.storage.JdbcFileRegistry.RangeFailure failure,HttpServletResponse r)throws IOException{write(r,416,"FILE_INVALID_RANGE");if(!r.isCommitted())r.setHeader("Content-Range","bytes */"+failure.size());}
 // A cancelled media fetch has no writable response left; a JSON error would fail again.
 @ExceptionHandler(org.springframework.web.context.request.async.AsyncRequestNotUsableException.class) void disconnected(){}
 @ExceptionHandler(Exception.class) void unavailable(Exception failure,HttpServletResponse r)throws IOException{diagnose(failure);write(r,503,"TRANSCRIPT_UNAVAILABLE");}
}
