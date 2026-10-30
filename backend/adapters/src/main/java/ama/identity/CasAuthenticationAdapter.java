package ama.identity;

import ama.audit.JdbcAuditAdapter;
import ama.contract.AuditContracts.AuditEvent;
import ama.contract.IdentityContracts.IssuedSession;
import ama.db.TransactionRunner;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** CAS ticket validator plus authoritative identity projection and local session issuance. */
public final class CasAuthenticationAdapter implements ama.identity.CasAuthenticationPort {
    private final CasClient cas;
    private final JdbcTemplate jdbc;
    private final TransactionRunner transactions;
    private final JdbcAuditAdapter audit;
    private final JdbcIdentitySessions sessions;
    private final Duration lifetime;

    public CasAuthenticationAdapter(DataSource source, URI origin, URI service, URI logoutService, URI applicationOrigin, Duration timeout, Duration lifetime) {
        this.cas = new CasClient(origin, service, logoutService, applicationOrigin, timeout);
        this.jdbc = new JdbcTemplate(source);
        this.transactions = new TransactionRunner(source);
        this.audit = new JdbcAuditAdapter(source);
        this.sessions = new JdbcIdentitySessions(source);
        this.lifetime = lifetime;
    }

    @Override public URI loginUri() { return cas.loginUri(); }
    @Override public URI logoutUri() { return cas.logoutUri(); }

    @Override public IssuedSession authenticate(String ticket) {
        CasXml.User user = cas.validate(ticket);
        String enterpriseId = user.username();
        ama.contract.IdentityContracts.stableId(enterpriseId);
        String displayName = bounded(user.attributes().getOrDefault("displayName", enterpriseId), 255, enterpriseId);
        String department = bounded(user.attributes().getOrDefault("department", ""), 255, "");
        syncIdentity(enterpriseId, displayName, department);
        return sessions.issueForVerifiedIdentity(enterpriseId, lifetime);
    }

    private void syncIdentity(String enterpriseId, String displayName, String department) {
        Instant verifiedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        transactions.required(() -> {
            var rows = jdbc.queryForList("SELECT status,session_generation,verified_at FROM enterprise_identity WHERE enterprise_id=? FOR UPDATE", enterpriseId);
            long generation;
            if (rows.isEmpty()) {
                generation = 0;
                jdbc.update("INSERT INTO enterprise_identity(enterprise_id,display_name,department,status,session_generation,verified_at) VALUES (?,?,?,'ACTIVE',0,?)",
                        enterpriseId, displayName, department, java.time.LocalDateTime.ofInstant(verifiedAt, java.time.ZoneOffset.UTC));
            } else {
                var row = rows.getFirst();
                generation = ((Number) row.get("session_generation")).longValue();
                if (!"ACTIVE".equals(row.get("status"))) generation = Math.addExact(generation, 1);
                jdbc.update("UPDATE enterprise_identity SET display_name=?,department=?,status='ACTIVE',session_generation=?,verified_at=? WHERE enterprise_id=?",
                        displayName, department, generation, java.time.LocalDateTime.ofInstant(verifiedAt, java.time.ZoneOffset.UTC), enterpriseId);
            }
            jdbc.update("INSERT IGNORE INTO role_assignment(enterprise_id,role,assigned_at) VALUES (?,'USER',?)",
                    enterpriseId, java.time.LocalDateTime.ofInstant(verifiedAt, java.time.ZoneOffset.UTC));
            audit.append(new AuditEvent("SYSTEM_CAS_SYNC", "CAS_IDENTITY_SYNC", enterpriseId, generation, "SUCCESS", UUID.randomUUID().toString(), verifiedAt));
            return null;
        });
    }

    private static String bounded(String value, int max, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        if (value.codePointCount(0, value.length()) > max) throw new IllegalArgumentException("Invalid CAS attribute");
        return value;
    }
}
