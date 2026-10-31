package ama.transcript;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Revisioned correction journal; accepted text is applied only after an explicit decision. */
public final class CorrectionService {
    public record Operation(String operationId, String before, String after, long baseRevision, String state) {}
    public static final class Conflict extends RuntimeException { public Conflict() { super("Correction revision conflict"); } }
    private String text;
    private long revision;
    private final Map<String, Operation> operations = new LinkedHashMap<>();
    public CorrectionService(String text) { this.text = requireText(text); }
    public synchronized Operation replace(String occurrence, String replacement, long expectedRevision) {
        checkRevision(expectedRevision);
        if (occurrence == null || occurrence.isEmpty() || replacement == null) throw new IllegalArgumentException("Invalid replacement");
        if (!text.contains(occurrence)) throw new IllegalArgumentException("Occurrence not found");
        var op = new Operation(UUID.randomUUID().toString(), text, text.replace(occurrence, replacement), revision, "PENDING");
        operations.put(op.operationId(), op);
        revision++;
        return op;
    }
    public synchronized void decide(String operationId, boolean accept, long expectedRevision) {
        checkRevision(expectedRevision);
        var op = Objects.requireNonNull(operations.get(operationId), "Unknown operation");
        if (!"PENDING".equals(op.state())) throw new IllegalStateException("Operation already decided");
        if (accept) text = op.after();
        operations.put(operationId, new Operation(op.operationId(), op.before(), op.after(), op.baseRevision(), accept ? "ACCEPTED" : "REJECTED"));
        revision++;
    }
    public synchronized void undo(String operationId, long expectedRevision) {
        checkRevision(expectedRevision);
        var op = Objects.requireNonNull(operations.get(operationId), "Unknown operation");
        if (!"ACCEPTED".equals(op.state())) throw new IllegalStateException("Only accepted operation can be undone");
        text = op.before(); revision++;
        operations.put(operationId, new Operation(op.operationId(), op.before(), op.after(), op.baseRevision(), "UNDONE"));
    }
    public synchronized String text() { return text; }
    public synchronized long revision() { return revision; }
    public synchronized long pendingCorrections() { return operations.values().stream().filter(o -> "PENDING".equals(o.state())).count(); }
    private void checkRevision(long expected) { if (expected != revision) throw new Conflict(); }
    private static String requireText(String value) { if (value == null) throw new IllegalArgumentException("Text required"); return value; }
}
