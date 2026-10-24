package ama.api;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/** Public errors intentionally contain no provider, SQL or exception details. */
public final class BusinessErrors {
    private BusinessErrors() {}
    public static void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"error\":{\"code\":\"" + code + "\",\"message\":\"" + message
                + "\",\"traceId\":\"" + UUID.randomUUID() + "\"}}");
    }
}
