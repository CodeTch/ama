package ama.api;
import jakarta.servlet.http.HttpServletResponse;import java.io.IOException;import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(assignableTypes=MeetingCommandDiscoveryController.class)
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
class MeetingCommandDiscoveryErrors {
 @ExceptionHandler(ama.access.MeetingAccess.AccessDeniedException.class) void denied(HttpServletResponse r)throws IOException{write(r,403,"MEETING_FORBIDDEN","Meeting access denied");}
 @ExceptionHandler({IllegalArgumentException.class,org.springframework.http.converter.HttpMessageNotReadableException.class}) void invalid(HttpServletResponse r)throws IOException{write(r,400,"INVALID_ARGUMENT","Invalid command discovery request");}
 @ExceptionHandler(Exception.class) void unavailable(HttpServletResponse r)throws IOException{write(r,503,"MEETING_UNAVAILABLE","Command association unavailable");}
 private static void write(HttpServletResponse r,int status,String code,String message)throws IOException{BusinessErrors.write(r,status,code,message);r.setHeader("Cache-Control","private, no-store");}
}
