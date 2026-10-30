package ama.api;

import ama.identity.CasAuthenticationPort;
import ama.identity.SessionPort;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Browser entry points for CAS; credentials never pass through the AMA API. */
@RestController
@ConditionalOnProperty(name = "cas.enabled", havingValue = "false", matchIfMissing = true)
class CasController {
    private final CasAuthenticationPort cas;
    private final SessionPort sessions;
    private final String applicationOrigin;

    @Autowired
    CasController(CasAuthenticationPort cas, SessionPort sessions, Environment environment) {
        this(cas, sessions, environment.getRequiredProperty("identity.allowed-origin"));
    }

    CasController(CasAuthenticationPort cas, String applicationOrigin) {
        this(cas, null, applicationOrigin);
    }

    CasController(CasAuthenticationPort cas, SessionPort sessions, String applicationOrigin) {
        this.cas = cas;
        this.sessions = sessions;
        this.applicationOrigin = applicationOrigin;
    }

    @GetMapping("/auth/cas/login")
    ResponseEntity<Void> login(HttpServletRequest request) {
        if (!request.getMethod().equals("GET")) return ResponseEntity.status(405).build();
        return ResponseEntity.status(303).location(cas.loginUri()).header("Cache-Control", "no-store").build();
    }

    @GetMapping("/auth/cas/callback")
    ResponseEntity<Void> callback(@RequestParam(required = false) String ticket) {
        if (ticket == null || !ticket.matches("ST-[A-Za-z0-9_-]{8,512}"))
            return ResponseEntity.badRequest().header("Cache-Control", "no-store").build();
        var issued = cas.authenticate(ticket);
        return ResponseEntity.status(303)
                .location(java.net.URI.create(applicationOrigin + "/meetings"))
                .header(HttpHeaders.SET_COOKIE, SessionCookies.issue(issued, Instant.now()))
                .header("Cache-Control", "no-store")
                .build();
    }

    @GetMapping("/auth/cas/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        String token = null;
        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if (!SessionCookies.NAME.equals(cookie.getName())) continue;
                if (token != null) { token = null; break; }
                token = cookie.getValue();
            }
        }
        if (token != null && sessions != null) sessions.revoke(token, UUID.randomUUID().toString());
        return ResponseEntity.status(303).location(cas.logoutUri())
                .header(HttpHeaders.SET_COOKIE, SessionCookies.clear())
                .header("Cache-Control", "no-store")
                .build();
    }
}
