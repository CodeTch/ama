package ama.api;
import ama.contract.IdentityContracts.ProviderUnavailableException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
@RestControllerAdvice(basePackages="ama.api")
class IdentityErrorHandler {
    @ExceptionHandler(CasProtocol.Failure.class)
    void cas(CasProtocol.Failure failure, HttpServletResponse response) throws IOException {
        int status = switch (failure.code()) {
            case "PROVIDER_UNAVAILABLE" -> 503;
            case "IDENTITY_NOT_ASSERTED", "INVALID_TICKET", "INVALID_CREDENTIALS" -> 401;
            case "INVALID_ORIGIN" -> 403;
            default -> 400;
        };
        BusinessErrors.write(response, status,
                status == 503 ? "IDENTITY_PROVIDER_UNAVAILABLE" : status == 403 ? "FORBIDDEN" : "INVALID_CAS_ASSERTION",
                status == 503 ? "Enterprise identity provider unavailable" : status == 403 ? "Access denied" : "CAS assertion is invalid");
    }
    @ExceptionHandler(ProviderUnavailableException.class)
    void provider(HttpServletResponse response) throws IOException { BusinessErrors.write(response,503,"IDENTITY_PROVIDER_UNAVAILABLE","Enterprise identity provider unavailable"); }
    @ExceptionHandler({IllegalArgumentException.class,MethodArgumentTypeMismatchException.class})
    void invalid(HttpServletResponse response) throws IOException { BusinessErrors.write(response,400,"INVALID_ARGUMENT","Invalid request"); }
    @ExceptionHandler(DataAccessException.class)
    void database(HttpServletResponse response) throws IOException { BusinessErrors.write(response,503,"IDENTITY_UNAVAILABLE","Identity service unavailable"); }
}
