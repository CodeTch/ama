package ama.api;
import jakarta.servlet.http.HttpServletResponse;import java.io.IOException;import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(assignableTypes={MeetingListController.class,WorkspaceTasksController.class,WorkspaceNavigationController.class})
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
class MeetingListErrors {
 private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(MeetingListErrors.class);
 @ExceptionHandler(ama.access.MeetingAccess.AccessDeniedException.class) void denied(HttpServletResponse r)throws IOException{write(r,403,"MEETING_FORBIDDEN","Meeting access denied");}
 @ExceptionHandler(IllegalArgumentException.class) void invalid(HttpServletResponse r)throws IOException{write(r,400,"INVALID_ARGUMENT","Invalid list query; restart pagination if cursor expired");}
 @ExceptionHandler(Exception.class) void unavailable(Exception failure,HttpServletResponse r)throws IOException{LOG.warn("Workspace list unavailable: type={} location={}",failure.getClass().getName(),java.util.Arrays.stream(failure.getStackTrace()).limit(8).toList());write(r,503,"MEETING_UNAVAILABLE","Meeting list unavailable");}
 private static void write(HttpServletResponse r,int status,String code,String message)throws IOException{BusinessErrors.write(r,status,code,message);r.setHeader("Cache-Control","private, no-store");}
}
