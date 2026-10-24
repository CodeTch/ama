package ama.task;
/** Read-only cancellation with ownership of at most one currently open source. */
public interface PreparationCancellation {
 boolean cancelled();
 /** Registration after cancellation aborts immediately. Closing registration detaches it once. */
 AutoCloseable register(Runnable abort);
}
