package ama.api;

import ama.contract.IdentityContracts.IssuedSession;
import ama.identity.SessionPort;
import ama.identity.SessionIssuer;
import jakarta.servlet.http.*;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name="cas.enabled", havingValue="true")
class CasAuthenticationController {
    private final CasClient cas;
    private final SessionIssuer sessions;
    private final SessionPort sessionPort;
    private final String identityAttribute;
    private final String allowedOrigin;
    private final Duration lifetime;
    CasAuthenticationController(CasClient cas, SessionIssuer sessions, SessionPort sessionPort, Environment env) {
        this.cas = cas; this.sessions = sessions; this.sessionPort = sessionPort;
        identityAttribute = env.getProperty("cas.identity-attribute", "user");
        allowedOrigin = env.getProperty("identity.allowed-origin", "");
        if (!identityAttribute.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")) throw new IllegalArgumentException("Invalid CAS identity attribute");
        lifetime = Duration.ofSeconds(env.getProperty("cas.session-lifetime-seconds", Long.class, 28800L));
        if (lifetime.isNegative() || lifetime.isZero() || lifetime.compareTo(Duration.ofHours(24)) > 0) throw new IllegalArgumentException("Invalid CAS session lifetime");
    }
    @GetMapping("/auth/cas/login") ResponseEntity<Void> login() {
        return ResponseEntity.status(HttpStatus.FOUND).location(cas.login()).header("Cache-Control", "no-store").build();
    }
    @PostMapping(value="/auth/cas/login", consumes="application/x-www-form-urlencoded")
    ResponseEntity<Void> credentialLogin(@RequestParam String username, @RequestParam String password, HttpServletRequest request) {
        if (allowedOrigin.isBlank()
                || java.util.Collections.list(request.getHeaders("Origin")).size() != 1
                || !allowedOrigin.equals(request.getHeader("Origin")))
            throw new CasProtocol.Failure("INVALID_ORIGIN");
        try {
            return ResponseEntity.status(HttpStatus.SEE_OTHER).location(cas.login(username, password)).header("Cache-Control", "no-store").build();
        } catch (CasProtocol.Failure failure) {
            return loginFailure(failure.code());
        }
    }
    @GetMapping("/auth/cas/callback") ResponseEntity<Void> callback(@RequestParam(required=false) String ticket) {
        try {
            var assertion = cas.validate(ticket);
            String enterpriseId = identityAttribute.equals("user") ? assertion.username() : assertion.attributes().get(identityAttribute);
            if (enterpriseId == null || enterpriseId.isBlank() || enterpriseId.length() > 255) throw new CasProtocol.Failure("IDENTITY_NOT_ASSERTED");
            IssuedSession issued = sessions.issueForVerifiedIdentity(enterpriseId, lifetime);
            var response = ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/meetings"));
            return response.header("Set-Cookie", SessionCookies.issue(issued, java.time.Instant.now())).header("Cache-Control", "no-store").build();
        } catch (CasProtocol.Failure failure) {
            return loginFailure(failure.code());
        } catch (ama.contract.IdentityContracts.ProviderUnavailableException unavailable) {
            return loginFailure("PROVIDER_UNAVAILABLE");
        } catch (IllegalArgumentException invalidIdentity) {
            return loginFailure("IDENTITY_NOT_ASSERTED");
        }
    }
    @GetMapping("/auth/cas/logout") ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        var token = token(request);
        if (token != null) sessionPort.revoke(token, UUID.randomUUID().toString());
        return ResponseEntity.status(HttpStatus.FOUND).location(cas.logout()).header("Set-Cookie", SessionCookies.clear()).header("Cache-Control", "no-store").build();
    }
    @GetMapping("/auth/cas/logout/callback") ResponseEntity<Void> logoutCallback() {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/meetings")).header("Set-Cookie", SessionCookies.clear()).header("Cache-Control", "no-store").build();
    }
    private static String token(HttpServletRequest request) {
        String value = null;
        if (request.getCookies() != null) for (var cookie : request.getCookies()) if (SessionCookies.NAME.equals(cookie.getName())) {
            if (value != null) throw new IllegalArgumentException("Duplicate session cookie");
            value = cookie.getValue();
        }
        return value;
    }
    private static ResponseEntity<Void> loginFailure(String code) {
        String safeCode = switch (code) {
            case "INVALID_CREDENTIALS" -> "INVALID_CREDENTIALS";
            case "PROVIDER_UNAVAILABLE" -> "PROVIDER_UNAVAILABLE";
            case "INVALID_ORIGIN" -> "FORBIDDEN";
            default -> "INVALID_CAS_ASSERTION";
        };
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .location(URI.create("/login?error=" + safeCode))
                .header("Cache-Control", "no-store").build();
    }
}
