package ama.task;

/** Logical discovery bounds per call; deployment tuning, never task or send authority. */
public record ReconciliationScanBudget(int pageSize, int maxRowsPerCall) {
    public ReconciliationScanBudget {
        if (pageSize <= 0 || maxRowsPerCall < pageSize)
            throw new IllegalArgumentException("Positive page size no larger than row budget required");
    }
}
