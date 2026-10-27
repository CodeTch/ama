package ama.api;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps publication-transition refusals only at the meeting HTTP boundary. */
@RestControllerAdvice(assignableTypes = MeetingController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class MeetingTransitionErrors {
    @ExceptionHandler(ama.contract.MinutesContracts.Failure.class)
    void publication(ama.contract.MinutesContracts.Failure failure, HttpServletResponse response)
            throws IOException {
        write(response, failure.code() == ama.contract.MinutesContracts.FailureCode.CAPABILITY_UNAVAILABLE);
    }

    @ExceptionHandler(ama.contract.TranscriptContracts.Failure.class)
    void transition(ama.contract.TranscriptContracts.Failure failure, HttpServletResponse response)
            throws IOException {
        write(response, failure.status() == 409);
    }

    private void write(HttpServletResponse response, boolean conflict) throws IOException {
        BusinessErrors.write(response, conflict ? 409 : 503,
                conflict ? "MEETING_CONFLICT" : "MEETING_UNAVAILABLE",
                conflict ? "Meeting changed or request conflicts" : "Meeting transition unavailable");
    }
}
