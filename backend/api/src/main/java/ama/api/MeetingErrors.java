package ama.api;

import ama.access.MeetingAccess.AccessDeniedException;
import ama.contract.MeetingContracts.*;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(basePackageClasses=MeetingController.class)
class MeetingErrors {
    @ExceptionHandler(AccessDeniedException.class) void forbidden(HttpServletResponse r)throws IOException{BusinessErrors.write(r,403,"MEETING_FORBIDDEN","Meeting access denied");}
    @ExceptionHandler(Conflict.class) void conflict(HttpServletResponse r)throws IOException{BusinessErrors.write(r,409,"MEETING_CONFLICT","Meeting changed or request conflicts");}
    @ExceptionHandler(CategoryUnavailable.class) void unavailable(HttpServletResponse r)throws IOException{BusinessErrors.write(r,503,"CATEGORY_DICTIONARY_UNAVAILABLE","Approved meeting category dictionary unavailable");}
    @ExceptionHandler(MeetingController.RequestTooLarge.class) void size(HttpServletResponse r)throws IOException{BusinessErrors.write(r,413,"REQUEST_TOO_LARGE","Request exceeds configured transport budget");}
}
