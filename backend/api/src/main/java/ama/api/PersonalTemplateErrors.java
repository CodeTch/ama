package ama.api;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.*;

@Order(-40)
@RestControllerAdvice(assignableTypes=PersonalTemplateController.class)
class PersonalTemplateErrors {
 @ExceptionHandler(ama.access.MeetingAccess.AccessDeniedException.class) void denied(HttpServletResponse response)throws IOException{BusinessErrors.write(response,403,"MEETING_FORBIDDEN","Template access denied");}
 @ExceptionHandler(ama.contract.MeetingContracts.Conflict.class) void conflict(HttpServletResponse response)throws IOException{BusinessErrors.write(response,409,"MEETING_CONFLICT","Template or original request changed");}
 @ExceptionHandler(IllegalArgumentException.class) void invalid(HttpServletResponse response)throws IOException{BusinessErrors.write(response,400,"INVALID_ARGUMENT","Invalid template request");}
 @ExceptionHandler(ama.contract.MinutesContracts.Failure.class) void invalidId(ama.contract.MinutesContracts.Failure failure,HttpServletResponse response)throws IOException{
  if(java.util.Set.of(ama.contract.MinutesContracts.FailureCode.INVALID_ARGUMENT,ama.contract.MinutesContracts.FailureCode.INVALID_ID,ama.contract.MinutesContracts.FailureCode.INVALID_SCALAR).contains(failure.code()))invalid(response);else unavailable(response);
 }
 @ExceptionHandler(PersonalTemplateController.TooLarge.class) void size(HttpServletResponse response)throws IOException{BusinessErrors.write(response,413,"REQUEST_TOO_LARGE","Template request exceeds byte budget");}
 @ExceptionHandler(Exception.class) void unavailable(HttpServletResponse response)throws IOException{BusinessErrors.write(response,503,"MEETING_UNAVAILABLE","Template temporarily unavailable");}
}
