package ama.identity;

import ama.contract.IdentityContracts.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static ama.contract.IdentityContracts.stableId;

/** Canonical projection reader, activated only by an explicitly controlled test configuration so far.
 * This is not evidence of company directory synchronization or its paging protocol. */
public final class JdbcDirectoryProjection implements DirectoryPort {
    private final JdbcTemplate jdbc;
    public JdbcDirectoryProjection(DataSource source) { jdbc = new JdbcTemplate(source); }
    @Override public PeoplePage search(String query, String cursor, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid page size");
        String after = "";
        if (cursor != null) {
            try {
                after = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                stableId(after);
                if (!Base64.getUrlEncoder().withoutPadding().encodeToString(after.getBytes(StandardCharsets.UTF_8)).equals(cursor)) throw new IllegalArgumentException();
            } catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Invalid directory cursor"); }
        }
        String pattern = "%" + (query == null ? "" : query).replace("=", "==").replace("%", "=%").replace("_", "=_") + "%";
        var items = jdbc.query("""
                SELECT i.enterprise_id,i.display_name,d.display_name AS department,i.status
                FROM enterprise_identity i LEFT JOIN department d ON d.enterprise_id=i.department_id
                WHERE i.enterprise_id>? AND (i.display_name LIKE ? ESCAPE '=' OR d.display_name LIKE ? ESCAPE '=')
                ORDER BY i.enterprise_id LIMIT ?
                """, (row, n) -> new Person(row.getString("enterprise_id"), row.getString("display_name"), row.getString("department"), "ACTIVE".equals(row.getString("status"))), after, pattern, pattern, limit + 1);
        String next = items.size() > limit ? Base64.getUrlEncoder().withoutPadding().encodeToString(items.get(limit-1).stableId().getBytes(StandardCharsets.UTF_8)) : null;
        return new PeoplePage(java.util.List.copyOf(items.subList(0, Math.min(limit, items.size()))), next);
    }
}
