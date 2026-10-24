package ama.identity;

import ama.contract.IdentityContracts.*;
import ama.contract.AuditContracts.AuditEvent;
import ama.audit.JdbcAuditAdapter;
import ama.db.TransactionRunner;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static ama.contract.IdentityContracts.stableId;

/** No issuance API. Every resolution is one fresh shared-DB query; no authorization cache. */
public final class JdbcIdentitySessions implements SessionPort, IdentityLifecyclePort {
    private final JdbcTemplate jdbc;
    private final TransactionRunner transactions;
    private final JdbcAuditAdapter audit;
    public JdbcIdentitySessions(DataSource source) {
        jdbc = new JdbcTemplate(source); transactions = new TransactionRunner(source); audit = new JdbcAuditAdapter(source);
    }
    @Override public Optional<Actor> resolveSession(String token) { return resolve(token).map(ResolvedSession::actor); }
    @Override public Optional<ResolvedSession> resolve(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return Optional.empty();
        return jdbc.query("""
                SELECT i.enterprise_id,i.status,r.role,s.csrf_token,s.expires_at
                FROM identity_session s JOIN enterprise_identity i ON i.enterprise_id=s.enterprise_id
                LEFT JOIN role_assignment r ON r.enterprise_id=i.enterprise_id
                WHERE s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at>UTC_TIMESTAMP(6)
                  AND s.issued_at<=UTC_TIMESTAMP(6) AND i.status='ACTIVE'
                  AND s.session_generation=i.session_generation
                """, result -> {
            if (!result.next()) return Optional.empty();
            String id = result.getString("enterprise_id"), csrf = result.getString("csrf_token");
            Instant expires = result.getObject("expires_at", LocalDateTime.class).toInstant(ZoneOffset.UTC);
            var roles = EnumSet.noneOf(Role.class);
            do { String role = result.getString("role"); if (role != null) roles.add(Role.valueOf(role)); } while (result.next());
            return Optional.of(new ResolvedSession(new Actor(id, Status.ACTIVE, roles), csrf, expires));
        }, hash(token));
    }
    @Override public void revoke(String token, String traceId) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return;
        // Read candidate identity without a lock, then respect identity -> session -> audit lock order.
        var ids = jdbc.queryForList("SELECT enterprise_id FROM identity_session WHERE token_hash=?", String.class, hash(token));
        if (ids.isEmpty()) return;
        transactions.required(() -> {
            var identity = lockIdentity(ids.getFirst());
            int updated = jdbc.update("UPDATE identity_session SET revoked_at=UTC_TIMESTAMP(6) WHERE token_hash=? AND enterprise_id=? AND revoked_at IS NULL", hash(token), ids.getFirst());
            if (updated == 1) append(ids.getFirst(), "SESSION_REVOKE", ((Number) identity.get("session_generation")).longValue(), traceId, Instant.now());
            return null;
        });
    }
    @Override public void revokeAll(String enterpriseId, String traceId) {
        stableId(enterpriseId);
        transactions.required(() -> {
            var row = lockIdentity(enterpriseId);
            if (!"ACTIVE".equals(row.get("status"))) throw new IllegalArgumentException("Identity is inactive");
            long generation = Math.addExact(((Number) row.get("session_generation")).longValue(), 1);
            jdbc.update("UPDATE enterprise_identity SET session_generation=? WHERE enterprise_id=?", generation, enterpriseId);
            append(enterpriseId, "SESSION_REVOKE_ALL", generation, traceId, Instant.now());
            return null;
        });
    }
    @Override public void setStatus(String enterpriseId, Status status, Instant verifiedAt, String traceId) {
        stableId(enterpriseId);
        if (status == null || verifiedAt == null) throw new IllegalArgumentException("Invalid identity state");
        Instant time = verifiedAt.truncatedTo(ChronoUnit.MICROS);
        transactions.required(() -> {
            var row = lockIdentity(enterpriseId);
            Instant previous = ((LocalDateTime) row.get("verified_at")).toInstant(ZoneOffset.UTC);
            if (time.isBefore(previous)) throw new IllegalArgumentException("Stale identity verification");
            long generation = ((Number) row.get("session_generation")).longValue();
            if (!status.name().equals(row.get("status"))) generation = Math.addExact(generation, 1);
            jdbc.update("UPDATE enterprise_identity SET status=?,session_generation=?,verified_at=? WHERE enterprise_id=?", status.name(), generation, LocalDateTime.ofInstant(time, ZoneOffset.UTC), enterpriseId);
            // This is a server verification event, not an action performed by the affected employee.
            audit.append(new AuditEvent("SYSTEM_IDENTITY_SYNC", "IDENTITY_STATUS", enterpriseId,
                    generation, "SUCCESS", traceId, time));
            return null;
        });
    }
    private Map<String,Object> lockIdentity(String id) {
        var rows = jdbc.queryForList("SELECT status,session_generation,verified_at FROM enterprise_identity WHERE enterprise_id=? FOR UPDATE", id);
        if (rows.isEmpty()) throw new IllegalArgumentException("Unknown identity");
        return rows.getFirst();
    }
    private void append(String id, String action, long revision, String traceId, Instant time) {
        audit.append(new AuditEvent(id, action, id, revision, "SUCCESS", traceId, time.truncatedTo(ChronoUnit.MICROS)));
    }
    private static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }
}
