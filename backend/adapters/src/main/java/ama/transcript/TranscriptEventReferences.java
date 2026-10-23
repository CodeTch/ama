package ama.transcript;
import ama.task.OutboxReferenceValidator;
import ama.contract.OutboxContracts.EventDraft;
import javax.sql.DataSource;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
/** DB-only references keep outbox independent from authority, queue and intake construction. */
public final class TranscriptEventReferences {
    private final JdbcTemplate j;
    private static final Set<String> EVENTS=Set.of("TRANSCRIPT_IMPORT_ACCEPTED","TASK_ENQUEUED","TASK_RETRY","TASK_SUCCEEDED","TASK_FAILED","TASK_CANCELLED","TASK_STALE");
    public TranscriptEventReferences(DataSource source) {
        j=new JdbcTemplate(source);
        j.setQueryTimeout(5);
    }
    public Map<String,OutboxReferenceValidator> validators() {
        return Map.of("TRANSCRIPTION_INPUT",this::validate);
    }
    private void validate(EventDraft event) {
        if(!EVENTS.contains(event.eventType())||event.taskId()==null||!event.subject().kind().equals("MEETING"))throw new IllegalArgumentException("Invalid processing event");
        var rows=j.queryForList("SELECT t.id FROM durable_task t JOIN transcription_input i ON i.id=t.input_id JOIN input_batch b ON b.id=i.batch_id JOIN input_batch_fingerprint_profile p ON p.batch_id=b.id WHERE t.id=? AND i.id=? AND i.meeting_id=? AND t.subject_kind='MEETING' AND t.subject_id=i.meeting_id AND t.task_type='TRANSCRIPT_IMPORT_V1' AND t.pool='TRANSCRIPT_IMPORT' AND t.input_kind='TRANSCRIPTION_INPUT' AND t.business_version=? AND t.business_version=i.base_revision AND t.cancel_epoch=i.cancel_epoch AND t.input_fingerprint=i.input_fingerprint AND b.meeting_id=i.meeting_id AND b.base_revision=i.base_revision AND b.kind='TRANSCRIPT' AND i.kind='IMPORT' AND i.material_mode='EMPTY' AND p.profile='ama.input-batch.v1'",String.class,event.taskId(),event.reference().id(),event.subject().id(),event.subjectRevision());
        if(rows.size()!=1)throw new IllegalArgumentException("Invalid processing event provenance");
    }
}
