package ama.storage;

import java.util.List;

/** Complete immutable physical definitions retained for every historical managed backend. */
public record FileBackendDefinitions(List<FileBackendCatalog.Backend> backends, String defaultId)
        implements AutoCloseable {
    public FileBackendDefinitions {
        backends = List.copyOf(backends);
        if (defaultId != null && backends.stream().noneMatch(b -> b.id().equals(defaultId)))
            throw new IllegalArgumentException("Unknown default backend");
    }

    public void close() throws Exception {
        for (var backend : backends)
            if (backend.storage() instanceof AutoCloseable closeable) closeable.close();
    }
}
