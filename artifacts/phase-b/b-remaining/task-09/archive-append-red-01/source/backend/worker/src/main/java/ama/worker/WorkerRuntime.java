package ama.worker;
/** Explicit process-owned lifecycle; deployments may compose ordinary workers and independent reconcilers. */
public interface WorkerRuntime extends AutoCloseable { void start(); void close(); }
