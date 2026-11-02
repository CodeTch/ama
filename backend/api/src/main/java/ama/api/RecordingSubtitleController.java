package ama.api;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.ResolvedSession;
import ama.db.TransactionRunner;
import ama.recording.RecordingService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
class RecordingSubtitleController {
    private final RecordingSubtitleRuntime runtime;
    private final RecordingService recordings;
    private final TransactionRunner transactions;
    private final MeetingAccess access;
    RecordingSubtitleController(RecordingSubtitleRuntime runtime,RecordingService recordings,TransactionRunner transactions,MeetingAccess access) {
        this.runtime=runtime;this.recordings=recordings;this.transactions=transactions;this.access=access;
    }
    @GetMapping("/api/v1/recordings/{sessionId}/subtitles")
    ResponseEntity<Map<String,Object>> read(@PathVariable String sessionId,@RequestParam(defaultValue="0") String afterEventId,
            @RequestParam(defaultValue="64") int limit,HttpServletRequest request) {
        if(!(request.getAttribute(SessionSecurityFilter.SESSION) instanceof ResolvedSession resolved))throw new MeetingAccess.AccessDeniedException();
        if(runtime.service()==null) {
            String meeting=recordings.meetingId(sessionId);
            transactions.required(()->{var gate=access.lock(resolved.actor(),meeting);access.require(gate,MeetingAccess.Action.READ);return null;});
            throw new IllegalStateException("Live subtitles unavailable");
        }
        return ResponseEntity.ok().header("Cache-Control","private, no-store").body(runtime.service().read(resolved.actor(),sessionId,afterEventId,limit));
    }
}
