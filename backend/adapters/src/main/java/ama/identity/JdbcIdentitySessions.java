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

/** Every resolution checks current shared-DB facts; no authorization cache or JVM-clock validity. */
public final class JdbcIdentitySessions implements SessionPort, IdentityLifecyclePort, IdentityProvisioner {
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();
    private final JdbcTemplate jdbc;
    private final TransactionRunner transactions;
    private final JdbcAuditAdapter audit;
    public JdbcIdentitySessions(DataSource source) {
        jdbc = new JdbcTemplate(source); transactions = new TransactionRunner(source); audit = new JdbcAuditAdapter(source);
    }
    @Override public Optional<Actor> resolveSession(String token) { return resolve(token).map(ResolvedSession::actor); }

    @Override public Optional<ResolvedSession> resolve(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return Optional.empty();
        String tokenHash=hash(token);
        var identities=jdbc.queryForList("SELECT enterprise_id FROM identity_session WHERE token_hash=?",String.class,tokenHash);
        if(identities.isEmpty())return Optional.empty();
        return transactions.required(()->{
            lockIdentity(identities.getFirst());
            Instant now=advanceClock(identities.getFirst());
            return jdbc.query("""
                SELECT i.enterprise_id,i.status,r.role,s.csrf_token,s.expires_at
                FROM identity_session s JOIN enterprise_identity i ON i.enterprise_id=s.enterprise_id
                LEFT JOIN role_assignment r ON r.enterprise_id=i.enterprise_id
                WHERE s.token_hash=? AND i.enterprise_id=? AND s.revoked_at IS NULL AND s.expires_at>?
                  AND s.issued_at<=? AND i.status='ACTIVE'
                  AND s.session_generation=i.session_generation
                FOR SHARE
                """, result -> {
            if (!result.next()) return Optional.empty();
            String id = result.getString("enterprise_id"), csrf = result.getString("csrf_token");
            Instant expires = result.getObject("expires_at", LocalDateTime.class).toInstant(ZoneOffset.UTC);
            var roles = EnumSet.of(Role.USER);
            do { String role = result.getString("role"); if (role != null) roles.add(Role.valueOf(role)); } while (result.next());
            return Optional.of(new ResolvedSession(new Actor(id, Status.ACTIVE, roles), csrf, expires));
            }, tokenHash,identities.getFirst(),LocalDateTime.ofInstant(now,ZoneOffset.UTC),LocalDateTime.ofInstant(now,ZoneOffset.UTC));
        });
    }
    public IssuedSession issueForVerifiedIdentity(String enterpriseId, Duration lifetime) {
        stableId(enterpriseId);
        if (lifetime == null || lifetime.isZero() || lifetime.isNegative() || lifetime.compareTo(Duration.ofHours(24)) > 0)
            throw new IllegalArgumentException("Invalid session lifetime");
        byte[] tokenBytes = new byte[32], csrfBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(tokenBytes);
        new java.security.SecureRandom().nextBytes(csrfBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        String csrf = Base64.getUrlEncoder().withoutPadding().encodeToString(csrfBytes);
        Instant expires = transactions.required(() -> {
            var identity = lockIdentity(enterpriseId);
            if (!"ACTIVE".equals(identity.get("status"))) throw new ProviderUnavailableException();
            // Resolution already uses this shared DB clock. A JVM clock ahead of MySQL must
            // not mint a session that is immediately rejected as issued in the future.
            Instant now = advanceClock(enterpriseId);
            Instant expiration = now.plus(lifetime).truncatedTo(ChronoUnit.MICROS);
            jdbc.update("INSERT INTO identity_session(token_hash,enterprise_id,session_generation,csrf_token,issued_at,expires_at) VALUES (?,?,?,?,?,?)",
                    hash(token), enterpriseId, identity.get("session_generation"), csrf, LocalDateTime.ofInstant(now, ZoneOffset.UTC), LocalDateTime.ofInstant(expiration, ZoneOffset.UTC));
            append(enterpriseId, "SESSION_ISSUE_CAS", ((Number) identity.get("session_generation")).longValue(), UUID.randomUUID().toString(), now);
            return expiration;
        });
        return new IssuedSession(token, csrf, expires);
    }
    @Override public void ensureCasIdentity(String enterpriseId, String displayName, String department) {
        stableId(enterpriseId);
        String safeName = displayName == null || displayName.isBlank() ? enterpriseId : displayName;
        String safeDepartment = department == null ? "" : department;
        if (safeName.codePointCount(0, safeName.length()) > 255 || safeDepartment.codePointCount(0, safeDepartment.length()) > 255)
            throw new IllegalArgumentException("Invalid CAS identity attributes");
        Instant verifiedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        transactions.required(() -> {
            var rows = jdbc.queryForList("SELECT status,session_generation,verified_at FROM enterprise_identity WHERE enterprise_id=? FOR UPDATE", enterpriseId);
            long generation = 0;
            if (rows.isEmpty()) {
                jdbc.update("INSERT INTO enterprise_identity(enterprise_id,display_name,department,status,session_generation,verified_at) VALUES (?,?,?,'ACTIVE',0,?)",
                        enterpriseId, safeName, safeDepartment, LocalDateTime.ofInstant(verifiedAt, ZoneOffset.UTC));
            } else {
                var row = rows.getFirst();
                generation = ((Number) row.get("session_generation")).longValue();
                if (!"ACTIVE".equals(row.get("status"))) generation = Math.addExact(generation, 1);
                jdbc.update("UPDATE enterprise_identity SET display_name=?,department=?,status='ACTIVE',session_generation=?,verified_at=? WHERE enterprise_id=?",
                        safeName, safeDepartment, generation, LocalDateTime.ofInstant(verifiedAt, ZoneOffset.UTC), enterpriseId);
            }
            audit.append(new AuditEvent("SYSTEM_CAS_SYNC", "CAS_IDENTITY_SYNC", enterpriseId, generation, "SUCCESS", UUID.randomUUID().toString(), verifiedAt));
            return null;
        });
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
    /** Identity is already locked. Advancing on every observation prevents expiry resurrection. */
    private Instant advanceClock(String id){
        jdbc.update("UPDATE enterprise_identity SET session_time_floor=GREATEST(COALESCE(session_time_floor,UTC_TIMESTAMP(6)),UTC_TIMESTAMP(6)) WHERE enterprise_id=?",id);
        return jdbc.queryForObject("SELECT session_time_floor FROM enterprise_identity WHERE enterprise_id=? FOR UPDATE",LocalDateTime.class,id).toInstant(ZoneOffset.UTC);
    }
    private void append(String id, String action, long revision, String traceId, Instant time) {
        audit.append(new AuditEvent(id, action, id, revision, "SUCCESS", traceId, time.truncatedTo(ChronoUnit.MICROS)));
    }
    private static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
