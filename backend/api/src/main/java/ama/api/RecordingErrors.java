package ama.api;

import ama.recording.RecordingService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.bind.annotation.*;

@ControllerAdvice(assignableTypes={RecordingController.class,RecordingLeaseController.class})
class RecordingErrors {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(RecordingErrors.class);
    @ExceptionHandler(RecordingIngressBudget.RateLimited.class)
    void rateLimited(HttpServletResponse response)throws IOException { response.setHeader("Retry-After","1");BusinessErrors.write(response,429,"RECORDING_RATE_LIMITED","Recording upload is temporarily limited"); }
    @ExceptionHandler(ama.recording.RecordingMutationGuard.Frozen.class)
    void frozen(HttpServletResponse response)throws IOException { BusinessErrors.write(response,409,"RECORDING_READ_ONLY","Meeting is read-only"); }
    @ExceptionHandler(RecordingJson.TooLarge.class)
    void metadataTooLarge(HttpServletResponse response)throws IOException { BusinessErrors.write(response,413,"REQUEST_TOO_LARGE","Recording control message exceeds its limit"); }
    @ExceptionHandler(ama.access.MeetingAccess.AccessDeniedException.class)
    void denied(HttpServletResponse response) throws IOException { BusinessErrors.write(response,403,"FORBIDDEN","Access denied"); }
    @ExceptionHandler(RecordingController.TooLarge.class)
    void tooLarge(HttpServletResponse response) throws IOException { BusinessErrors.write(response,413,"REQUEST_TOO_LARGE","Recording chunk exceeds two seconds"); }
    @ExceptionHandler({RecordingService.StaleWriter.class, RecordingService.HashConflict.class, RecordingService.Gap.class, RecordingService.Closed.class})
    void conflict(RuntimeException error, HttpServletResponse response) throws IOException { BusinessErrors.write(response, 409, error instanceof RecordingService.Gap ? "RECORDING_GAP" : "RECORDING_CONFLICT", "Recording session changed or has an unpersisted gap"); }
    @ExceptionHandler(RecordingService.Paused.class)
    void paused(HttpServletResponse response) throws IOException { BusinessErrors.write(response, 409, "RECORDING_PAUSED", "Recording session is paused"); }
    @ExceptionHandler(IllegalArgumentException.class)
    void invalid(HttpServletResponse response) throws IOException { BusinessErrors.write(response, 400, "INVALID_ARGUMENT", "Invalid recording request"); }
    @ExceptionHandler(Exception.class)
    void unavailable(Exception failure,HttpServletResponse response) throws IOException {
        Throwable cause=failure;for(int i=0;i<8&&cause.getCause()!=null;i++)cause=cause.getCause();
        // Diagnostic classes/state only: never log request bodies, credentials, tokens or SQL parameters.
        if(cause instanceof java.sql.SQLException sql)LOG.warn("Recording unavailable: type={} sqlState={} vendorCode={}",cause.getClass().getSimpleName(),sql.getSQLState(),sql.getErrorCode());
        else LOG.warn("Recording unavailable: type={}",cause.getClass().getSimpleName());
        BusinessErrors.write(response,503,"RECORDING_UNAVAILABLE","Recording service unavailable");
    }
}
