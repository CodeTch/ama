package ama.api;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes=CategoryController.class)
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
class CategoryErrors {
 @ExceptionHandler(ama.access.MeetingAccess.AccessDeniedException.class) void denied(HttpServletResponse r)throws IOException{write(r,403,"MEETING_FORBIDDEN","Meeting access denied");}
 @ExceptionHandler(IllegalArgumentException.class) void invalid(HttpServletResponse r)throws IOException{write(r,400,"INVALID_ARGUMENT","Invalid request; restart pagination if cursor expired");}
 @ExceptionHandler(ama.contract.MeetingContracts.CategoryUnavailable.class) void absent(HttpServletResponse r)throws IOException{write(r,503,"CATEGORY_DICTIONARY_UNAVAILABLE","Approved category dictionary unavailable");}
 @ExceptionHandler(Exception.class) void unavailable(HttpServletResponse r)throws IOException{write(r,503,"MEETING_UNAVAILABLE","Meeting data unavailable");}
 private static void write(HttpServletResponse r,int status,String code,String message)throws IOException{BusinessErrors.write(r,status,code,message);r.setHeader("Cache-Control","private, no-store");}
}
