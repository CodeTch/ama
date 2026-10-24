package ama.api;

import ama.identity.*;
import ama.contract.IdentityContracts.*;
import jakarta.servlet.http.*;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
class IdentityController {
    private final SessionPort sessions;
    private final DirectoryPort directory;
    IdentityController(SessionPort sessions, DirectoryPort directory) { this.sessions = sessions; this.directory = directory; }
    @GetMapping("/api/v1/session") ResolvedSession session(HttpServletRequest request) { return resolved(request); }
    @GetMapping("/api/v1/directory/people") PeoplePage people(@RequestParam(defaultValue="") String query,
            @RequestParam(required=false) String cursor, @RequestParam(defaultValue="20") int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid page size");
        return directory.search(query, cursor, limit);
    }
    @PostMapping("/api/v1/session/logout") ResponseEntity<Void> logout(HttpServletRequest request) {
        sessions.revoke((String) request.getAttribute(SessionSecurityFilter.TOKEN), UUID.randomUUID().toString());
        return cleared();
    }
    @PostMapping("/api/v1/session/logout-all") ResponseEntity<Void> logoutAll(HttpServletRequest request) {
        sessions.revokeAll(resolved(request).actor().enterpriseId(), UUID.randomUUID().toString());
        return cleared();
    }
    private ResolvedSession resolved(HttpServletRequest request) { return (ResolvedSession) request.getAttribute(SessionSecurityFilter.SESSION); }
    private ResponseEntity<Void> cleared() { return ResponseEntity.noContent().header("Set-Cookie", SessionCookies.clear()).header("Cache-Control","no-store").build(); }
}
