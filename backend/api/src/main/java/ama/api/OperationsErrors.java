package ama.api;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.*;
@Order(0) @RestControllerAdvice(assignableTypes={OperationsController.class,MeetingVoiceprintController.class,GovernanceWorkbenchController.class})
class OperationsErrors {
 @ExceptionHandler(ama.access.MeetingAccess.AccessDeniedException.class) void denied(HttpServletResponse r)throws IOException{BusinessErrors.write(r,403,"MEETING_FORBIDDEN","Operations access denied");}
 @ExceptionHandler(ama.contract.MeetingContracts.Conflict.class) void conflict(HttpServletResponse r)throws IOException{BusinessErrors.write(r,409,"MEETING_CONFLICT","Original request or resource version changed");}
 @ExceptionHandler(OperationsController.TooLarge.class) void size(HttpServletResponse r)throws IOException{BusinessErrors.write(r,413,"REQUEST_TOO_LARGE","Operations request exceeds bound");}
 @ExceptionHandler({IllegalArgumentException.class,ama.contract.MinutesContracts.Failure.class}) void invalid(HttpServletResponse r)throws IOException{BusinessErrors.write(r,400,"INVALID_ARGUMENT","Invalid operations request");}
 @ExceptionHandler(Exception.class) void unavailable(Exception cause,jakarta.servlet.http.HttpServletRequest request,HttpServletResponse r)throws IOException{System.getLogger(OperationsErrors.class.getName()).log(System.Logger.Level.WARNING,"Operations unavailable: "+request.getMethod()+" "+request.getRequestURI(),cause);BusinessErrors.write(r,503,"MEETING_UNAVAILABLE","Operations temporarily unavailable");}
}
