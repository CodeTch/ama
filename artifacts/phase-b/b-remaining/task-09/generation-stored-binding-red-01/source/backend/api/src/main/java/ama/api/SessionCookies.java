package ama.api;
import ama.contract.IdentityContracts.IssuedSession;
import java.time.Instant;
import java.time.Duration;
public final class SessionCookies {
    public static final String NAME = "__Host-AMA_SESSION";
    private SessionCookies() {}
    public static String issue(IssuedSession issued, Instant now) {
        long seconds = Duration.between(now, issued.expiresAt()).getSeconds();
        if (issued.token() == null || !issued.token().matches("[A-Za-z0-9_-]{43}") || seconds <= 0) throw new IllegalArgumentException("Invalid session cookie");
        return NAME + "=" + issued.token() + "; Path=/; Secure; HttpOnly; SameSite=Lax; Max-Age=" + seconds;
    }
    public static String clear() { return NAME + "=; Path=/; Secure; HttpOnly; SameSite=Lax; Max-Age=0"; }
}
