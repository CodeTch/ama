package ama.api;

import ama.meeting.WorkspaceReadBudget;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.bind.annotation.*;

/** Only the meeting HTTP controller maps protected projection acquisition failures. */
@RestControllerAdvice(assignableTypes=MeetingController.class)
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
class MeetingProjectionErrors {
 @ExceptionHandler(WorkspaceReadBudget.Unavailable.class)
 void unavailable(HttpServletResponse response)throws IOException {
  BusinessErrors.write(response,503,"MEETING_UNAVAILABLE","Meeting data is unavailable");
  response.setHeader("Cache-Control","private, no-store");
 }
}
