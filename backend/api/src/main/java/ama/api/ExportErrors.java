package ama.api;
import jakarta.servlet.http.HttpServletResponse;import java.io.IOException;import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(assignableTypes=ExportController.class) @org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
final class ExportErrors {
 private void write(HttpServletResponse response,int status,String code)throws IOException{BusinessErrors.write(response,status,code,"Export request could not be completed");response.setHeader("Cache-Control","private, no-store");}
 @ExceptionHandler(ama.contract.TranscriptContracts.Failure.class) void explicit(ama.contract.TranscriptContracts.Failure error,HttpServletResponse response)throws IOException{write(response,error.status(),error.code());}
 @ExceptionHandler(ama.access.MeetingAccess.AccessDeniedException.class) void denied(HttpServletResponse response)throws IOException{write(response,403,"MEETING_FORBIDDEN");}
 @ExceptionHandler(ama.contract.FileContracts.NotFound.class) void missing(HttpServletResponse response)throws IOException{write(response,404,"EXPORT_NOT_FOUND");}
 @ExceptionHandler(ama.contract.FileContracts.Conflict.class) void conflict(HttpServletResponse response)throws IOException{write(response,409,"EXPORT_SOURCE_CHANGED");}
 @ExceptionHandler(IllegalArgumentException.class) void invalid(HttpServletResponse response)throws IOException{write(response,400,"INVALID_EXPORT_REQUEST");}
 @ExceptionHandler(ama.contract.MinutesContracts.Failure.class) void source(ama.contract.MinutesContracts.Failure error,HttpServletResponse response)throws IOException{boolean invalid=SetHolder.INVALID.contains(error.code());write(response,invalid?400:503,invalid?"INVALID_EXPORT_REQUEST":"EXPORT_SOURCE_UNAVAILABLE");}
 @ExceptionHandler(ama.storage.JdbcFileRegistry.RangeFailure.class) void range(ama.storage.JdbcFileRegistry.RangeFailure error,HttpServletResponse response)throws IOException{response.setHeader("Content-Range","bytes */"+error.size());write(response,416,"FILE_INVALID_RANGE");}
 @ExceptionHandler(Exception.class) void unavailable(HttpServletResponse response)throws IOException{write(response,503,"EXPORT_UNAVAILABLE");}
 private static final class SetHolder {static final java.util.Set<ama.contract.MinutesContracts.FailureCode> INVALID=java.util.Set.of(ama.contract.MinutesContracts.FailureCode.INVALID_ID,ama.contract.MinutesContracts.FailureCode.INVALID_ARGUMENT,ama.contract.MinutesContracts.FailureCode.INVALID_SCALAR);}
}
