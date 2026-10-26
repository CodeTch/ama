package ama.api;

import ama.contract.FileContracts.*;
import ama.contract.StorageContracts.StorageException;
import ama.storage.FilePurposePolicy;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestControllerAdvice(assignableTypes = FileController.class)
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
class FileErrors {
    @ExceptionHandler(FilePurposePolicy.Unavailable.class)
    void purpose(HttpServletResponse response) throws IOException {
        BusinessErrors.write(response, 503, "FILE_PURPOSE_UNAVAILABLE", "File purpose unavailable");
    }

    @ExceptionHandler(ama.contract.MinutesContracts.Failure.class)
    void publication(ama.contract.MinutesContracts.Failure failure,HttpServletResponse response) throws IOException {
        if(failure.code()==ama.contract.MinutesContracts.FailureCode.CAPABILITY_UNAVAILABLE)conflict(response);
        else unavailable(response);
    }
    @ExceptionHandler(Conflict.class)
    void conflict(HttpServletResponse r) throws IOException {
        BusinessErrors.write(r, 409, "FILE_CONFLICT", "File request conflicts");
    }

    @ExceptionHandler(NotFound.class)
    void missing(HttpServletResponse r) throws IOException {
        BusinessErrors.write(r, 404, "FILE_NOT_FOUND", "File not found");
    }

    @ExceptionHandler(Unavailable.class)
    void unavailable(HttpServletResponse r) throws IOException {
        BusinessErrors.write(r, 503, "FILE_STORAGE_UNAVAILABLE", "File storage unavailable");
    }

    @ExceptionHandler(org.springframework.transaction.TransactionException.class)
    void transaction(HttpServletResponse r) throws IOException {
        unavailable(r);
    }

    @ExceptionHandler({
        org.springframework.dao.DataAccessException.class,
        IllegalStateException.class
    })
    void failed(HttpServletResponse r) throws IOException {
        unavailable(r);
    }

    @ExceptionHandler(ArithmeticException.class)
    void invalid(HttpServletResponse r) throws IOException {
        BusinessErrors.write(r, 400, "INVALID_ARGUMENT", "Invalid request");
    }

    @ExceptionHandler(FileController.RequestTooLarge.class)
    void size(HttpServletResponse r) throws IOException {
        BusinessErrors.write(
                r, 413, "REQUEST_TOO_LARGE", "Request exceeds configured transport budget");
    }

    @ExceptionHandler(ama.storage.JdbcFileRegistry.RangeFailure.class)
    void range(ama.storage.JdbcFileRegistry.RangeFailure e, HttpServletResponse r)
            throws IOException {
        r.setHeader("Content-Range", "bytes */" + e.size());
        BusinessErrors.write(r, 416, "FILE_INVALID_RANGE", "Requested range unavailable");
    }

    @ExceptionHandler(StorageException.class)
    void storage(StorageException failure, HttpServletResponse r) throws IOException {
        switch (failure.code()) {
            case CONFLICT, INVALID_UPLOAD_STATE -> conflict(r);
            case NOT_FOUND -> missing(r);
            case STORAGE_UNAVAILABLE -> unavailable(r);
            case INVALID_RANGE ->
                    BusinessErrors.write(
                            r, 416, "FILE_INVALID_RANGE", "Requested range unavailable");
            default ->
                    BusinessErrors.write(
                            r, 400, "INVALID_ARGUMENT", "Invalid file content or checksum");
        }
    }
}
