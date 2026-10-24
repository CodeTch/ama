package ama.api;
import ama.contract.ProcessingContracts.Failure;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(assignableTypes= {
    ProcessingController.class,ProcessingDiscoveryController.class
}) @org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE) class ProcessingErrors {
    @ExceptionHandler( {
        org.springframework.dao.DataAccessException.class,org.springframework.transaction.TransactionException.class,IllegalStateException.class
    }) void unavailable(HttpServletResponse response)throws IOException {
        BusinessErrors.write(response,503,"PROCESSING_UNAVAILABLE","Processing is unavailable");
        response.setHeader("Cache-Control","private, no-store");
    }
    @ExceptionHandler(Failure.class) void failure(Failure failure,HttpServletResponse response)throws IOException {
        BusinessErrors.write(response,failure.status(),failure.code(),"Processing request could not be completed");
        response.setHeader("Cache-Control","private, no-store");
    }
    @ExceptionHandler(ama.contract.MinutesContracts.Failure.class) void provenance(ama.contract.MinutesContracts.Failure failure,HttpServletResponse response)throws IOException {
        boolean conflict=java.util.Set.of(ama.contract.MinutesContracts.FailureCode.FINGERPRINT_MISMATCH,ama.contract.MinutesContracts.FailureCode.PROVENANCE_UNAVAILABLE).contains(failure.code());
        BusinessErrors.write(response,conflict?409:503,conflict?"PROCESSING_CONFLICT":"PROCESSING_UNAVAILABLE","Processing request could not be completed");
        response.setHeader("Cache-Control","private, no-store");
    }
    @ExceptionHandler(ama.contract.FileContracts.Conflict.class) void conflict(HttpServletResponse r)throws IOException {
        BusinessErrors.write(r,409,"PROCESSING_CONFLICT","Processing request conflicts");
    }
    @ExceptionHandler(ama.contract.FileContracts.NotFound.class) void source(HttpServletResponse r)throws IOException {
        BusinessErrors.write(r,409,"PROCESSING_CONFLICT","Processing source changed");
    }
    @ExceptionHandler(ama.contract.FileContracts.Unavailable.class) void route(HttpServletResponse r)throws IOException {
        unavailable(r);
    }
    @ExceptionHandler(ama.access.MeetingAccess.AccessDeniedException.class) void denied(HttpServletResponse r)throws IOException {
        BusinessErrors.write(r,403,"MEETING_FORBIDDEN","Meeting access denied");
    }
    @ExceptionHandler(IllegalArgumentException.class) void invalid(HttpServletResponse r)throws IOException {
        BusinessErrors.write(r,400,"INVALID_ARGUMENT","Invalid request");
    }
    @ExceptionHandler(Exception.class) void unexpected(HttpServletResponse r)throws IOException {
        unavailable(r);
    }
}
