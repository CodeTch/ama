package ama.realtime;

import ama.contract.RecordingContracts;
import ama.recording.RecordingService;
import java.util.Objects;

/** Transport-neutral WebSocket handler core; framework binding can delegate headers here. */
public final class RecordingSocketHandler {
    private final RecordingService service;
    public RecordingSocketHandler(RecordingService service) { this.service = Objects.requireNonNull(service); }
    public RecordingContracts.Ack accept(RecordingContracts.Session session, RecordingContracts.Chunk chunk) {
        Objects.requireNonNull(session); return service.accept(session.sessionId(), session.writerEpoch(), chunk);
    }
}
