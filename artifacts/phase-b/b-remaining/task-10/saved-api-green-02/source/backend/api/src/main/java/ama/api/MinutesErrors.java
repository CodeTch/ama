package ama.api;
import jakarta.servlet.http.HttpServletResponse;import java.io.IOException;import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(assignableTypes={MinutesController.class,MinutesSavedController.class}) @org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE) class MinutesErrors {
 private void write(HttpServletResponse response,int status,String code)throws IOException {BusinessErrors.write(response,status,code,"Minutes request could not be completed");response.setHeader("Cache-Control","private, no-store");}
 @ExceptionHandler(ama.contract.TranscriptContracts.Failure.class) void explicit(ama.contract.TranscriptContracts.Failure error,HttpServletResponse response)throws IOException{write(response,error.status(),error.code());}
 @ExceptionHandler(ama.contract.MinutesContracts.Failure.class) void domain(ama.contract.MinutesContracts.Failure error,HttpServletResponse response)throws IOException{switch(error.code()){case INVALID_ARGUMENT,INVALID_SCALAR,INVALID_ID->write(response,400,"INVALID_ARGUMENT");case BUDGET_EXCEEDED->write(response,413,"REQUEST_TOO_LARGE");default->write(response,503,"MINUTES_UNAVAILABLE");}}
 @ExceptionHandler(ama.access.MeetingAccess.AccessDeniedException.class) void denied(HttpServletResponse response)throws IOException{write(response,403,"MEETING_FORBIDDEN");}
 @ExceptionHandler(ama.contract.FileContracts.NotFound.class) void missing(HttpServletResponse response)throws IOException{write(response,404,"MINUTES_NOT_FOUND");}
 @ExceptionHandler(IllegalArgumentException.class) void invalid(HttpServletResponse response)throws IOException{write(response,400,"INVALID_ARGUMENT");}
 @ExceptionHandler(Exception.class) void unavailable(HttpServletResponse response)throws IOException{write(response,503,"MINUTES_UNAVAILABLE");}
}
