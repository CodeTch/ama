package ama.storage;

import ama.contract.FileContracts.*;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

import javax.sql.DataSource;

/** Explicit ready adapters. Configuration changes never rewrite a historical backend's meaning. */
public final class FileBackendCatalog {
    public record Backend(
            String id,
            String type,
            long revision,
            String status,
            FileStoragePort storage,
            ManagedBackendId.Definition definition) {
        public Backend {
            ManagedBackendId.require(id, definition);
            if (!definition.type().equals(type)
                    || revision < 1
                    || !Set.of("WRITABLE", "READ_ONLY", "DISABLED").contains(status)
                    || storage == null)
                throw new IllegalArgumentException("Invalid backend definition");
        }
    }

    private final JdbcTemplate j;
    private final Map<String, Backend> backends;
    private final String defaultId;

    public FileBackendCatalog(
            DataSource source, Collection<Backend> definitions, String defaultId) {
        j = new JdbcTemplate(source);
        var map = new HashMap<String, Backend>();
        for (var backend : definitions)
            if (map.put(backend.id(), backend) != null)
                throw new IllegalArgumentException("Duplicate backend");
        backends = Map.copyOf(map);
        this.defaultId = defaultId;
    }

    /**
     * Explicit configuration administration, outside domain transactions. No credentials persisted.
     */
    public void configure() {
        for (var b : backends.values()) {
            if (!b.status().equals("DISABLED")) b.storage().list(b.id(), "files", "", null, 1);
            var existing =
                    j.queryForList(
                            "SELECT storage_type,config_revision,status FROM storage_backend WHERE"
                                    + " id=?",
                            b.id());
            if (existing.isEmpty()) {
                try {
                    j.update(
                            "INSERT INTO storage_backend(id,storage_type,status,config_revision)"
                                + " VALUES (?,?,?,?)",
                            b.id(),
                            b.type(),
                            b.status(),
                            b.revision());
                    continue;
                } catch (DuplicateKeyException concurrentRegistration) {
                    // Another node may have accepted this exact definition after our empty read.
                    // Re-read and validate; a duplicate never authorizes overwriting its meaning.
                    existing =
                            j.queryForList(
                                    "SELECT storage_type,config_revision,status FROM"
                                        + " storage_backend WHERE id=?",
                                    b.id());
                    if (existing.size() != 1) throw concurrentRegistration;
                }
            }
            {
                var old = existing.getFirst();
                long rev = ((Number) old.get("config_revision")).longValue();
                if (!b.type().equals(old.get("storage_type"))
                        || rev > b.revision()
                        || (rev == b.revision() && !b.status().equals(old.get("status"))))
                    throw new IllegalStateException("Backend configuration revision mismatch");
                if (rev < b.revision()) {
                    int changed =
                            j.update(
                                    "UPDATE storage_backend SET status=?,config_revision=? WHERE"
                                        + " id=? AND config_revision=?",
                                    b.status(),
                                    b.revision(),
                                    b.id(),
                                    rev);
                    if (changed != 1) {
                        var current =
                                j.queryForList(
                                        "SELECT storage_type,config_revision,status FROM"
                                            + " storage_backend WHERE id=?",
                                        b.id());
                        if (current.size() != 1
                                || !b.type().equals(current.getFirst().get("storage_type"))
                                || b.revision()
                                        != ((Number) current.getFirst().get("config_revision"))
                                                .longValue()
                                || !b.status().equals(current.getFirst().get("status")))
                            throw new IllegalStateException(
                                    "Backend configuration changed concurrently");
                    }
                }
            }
        }
    }

    public Backend defaultWrite() {
        return require(defaultId, true);
    }

    public boolean ready() {
        try {
            var backend = defaultWrite();
            backend.storage().list(backend.id(), "files", "", null, 1);
            return true;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    /** READ_ONLY permits recovery of an accepted frozen upload; DISABLED refuses all I/O. */
    public Backend require(String id, boolean newWrite) {
        if (id == null) throw new Unavailable();
        var b = backends.get(id);
        if (b == null
                || b.status().equals("DISABLED")
                || (newWrite && !b.status().equals("WRITABLE"))) throw new Unavailable();
        ManagedBackendId.require(id, b.definition());
        var row =
                j.queryForList(
                        "SELECT storage_type,config_revision,status FROM storage_backend WHERE"
                                + " id=?",
                        id);
        if (row.size() != 1
                || !b.type().equals(row.getFirst().get("storage_type"))
                || b.revision() != ((Number) row.getFirst().get("config_revision")).longValue()
                || !b.status().equals(row.getFirst().get("status"))) throw new Unavailable();
        return b;
    }
}
