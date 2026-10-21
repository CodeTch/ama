package ama.api;

import ama.identity.SessionPort;
import ama.contract.IdentityContracts.ResolvedSession;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

final class SessionSecurityFilter extends OncePerRequestFilter {
    static final String SESSION = SessionSecurityFilter.class.getName() + ".session";
    static final String TOKEN = SessionSecurityFilter.class.getName() + ".token";
    private final SessionPort sessions;
    private final Environment environment;
    SessionSecurityFilter(SessionPort sessions, Environment environment) { this.sessions = sessions; this.environment = environment; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        int publicPort = environment.getProperty("local.server.port", Integer.class, -1);
        if (request.getLocalPort() != publicPort) { BusinessErrors.write(response, 403, "FORBIDDEN", "Access denied"); return; }
        String token = null;
        if (request.getCookies() != null) for (var cookie : request.getCookies()) {
            if (SessionCookies.NAME.equals(cookie.getName())) {
                if (token != null) { BusinessErrors.write(response, 401, "UNAUTHENTICATED", "Authentication required"); return; }
                token = cookie.getValue();
            }
        }
        ResolvedSession session;
        try { session = sessions.resolve(token).orElse(null); }
        catch (DataAccessException unavailable) { BusinessErrors.write(response, 503, "IDENTITY_UNAVAILABLE", "Identity service unavailable"); return; }
        if (session == null) { BusinessErrors.write(response, 401, "UNAUTHENTICATED", "Authentication required"); return; }
        response.setHeader("Cache-Control", "no-store");
        if (!List.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())) {
            String allowed = environment.getProperty("identity.allowed-origin", "");
            String origin = request.getHeader("Origin"), csrf = request.getHeader("X-CSRF-Token");
            if (java.util.Collections.list(request.getHeaders("Origin")).size() != 1
                    || java.util.Collections.list(request.getHeaders("X-CSRF-Token")).size() != 1
                    || allowed.isBlank() || !allowed.equals(origin) || csrf == null
                    || !MessageDigest.isEqual(session.csrfToken().getBytes(StandardCharsets.US_ASCII), csrf.getBytes(StandardCharsets.US_ASCII))) {
                BusinessErrors.write(response, 403, "FORBIDDEN", "Access denied"); return;
            }
        }
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(session.actor(), null, List.of()));
        SecurityContextHolder.setContext(context);
        request.setAttribute(SESSION, session); request.setAttribute(TOKEN, token);
        try { chain.doFilter(request, response); }
        finally { SecurityContextHolder.clearContext(); }
    }
}
