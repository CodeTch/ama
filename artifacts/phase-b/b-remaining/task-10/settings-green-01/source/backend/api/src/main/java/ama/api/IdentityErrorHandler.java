package ama.api;
import ama.contract.IdentityContracts.ProviderUnavailableException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
@RestControllerAdvice(basePackages="ama.api")
class IdentityErrorHandler {
    @ExceptionHandler(ProviderUnavailableException.class)
    void provider(HttpServletResponse response) throws IOException { BusinessErrors.write(response,503,"IDENTITY_PROVIDER_UNAVAILABLE","Enterprise identity provider unavailable"); }
    @ExceptionHandler({IllegalArgumentException.class,MethodArgumentTypeMismatchException.class})
    void invalid(HttpServletResponse response) throws IOException { BusinessErrors.write(response,400,"INVALID_ARGUMENT","Invalid request"); }
    @ExceptionHandler(DataAccessException.class)
    void database(HttpServletResponse response) throws IOException { BusinessErrors.write(response,503,"IDENTITY_UNAVAILABLE","Identity service unavailable"); }
}
