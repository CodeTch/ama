package ama.api;

import ama.access.MeetingAccess.AuthorityUnavailableException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.bind.annotation.*;

/** A short-lived proof outage denies content without claiming that permission was revoked. */
@RestControllerAdvice
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
final class AuthorityErrors {
 @ExceptionHandler(AuthorityUnavailableException.class)
 void unavailable(AuthorityUnavailableException failure,HttpServletResponse response)throws IOException{
  var detail=new StringBuilder("Authority proof unavailable");Throwable cause=failure;
  for(int n=0;cause!=null&&n<4;n++,cause=cause.getCause()){
   detail.append(" type=").append(cause.getClass().getName());
   if(cause instanceof ama.contract.MinutesContracts.Failure invalid)detail.append(" code=").append(invalid.code());
   for(var frame:java.util.Arrays.stream(cause.getStackTrace()).filter(f->f.getClassName().startsWith("ama.")).limit(4).toList())detail.append(" at ").append(frame);
  }
  java.util.logging.Logger.getLogger(AuthorityErrors.class.getName()).warning(detail.toString());
  BusinessErrors.write(response,503,"AUTHORITY_UNAVAILABLE","Current authority could not be verified; retry the original request");
  response.setHeader("Cache-Control","private, no-store");
  response.setHeader("Retry-After","1");
 }
}
