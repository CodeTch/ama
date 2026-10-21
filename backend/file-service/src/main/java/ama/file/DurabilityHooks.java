package ama.file;

/** Dependency-injected IO observation/fault boundary. Production uses NONE. */
@FunctionalInterface
public interface DurabilityHooks {
    DurabilityHooks NONE = operation -> {};

    void before(String operation);
}
