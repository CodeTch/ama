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
        Throwable failure=null;
        boolean interrupted=Thread.currentThread().isInterrupted();
        try {
            for (var backend : backends)
                if (backend.storage() instanceof AutoCloseable closeable)
                    try {closeable.close();}
                    catch(Exception|Error error) {
                        if(error instanceof InterruptedException)interrupted=true;
                        if(failure==null)failure=error;
                        else if(failure!=error&&failure.getSuppressed().length<8)failure.addSuppressed(error);
                    }
        } finally {if(interrupted)Thread.currentThread().interrupt();}
        if(failure instanceof Exception error)throw error;
        if(failure instanceof Error error)throw error;
    }
}
