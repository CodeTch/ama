package ama.transcript;
import ama.task.OutboxReferenceValidator;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Map;
/** Actual DB reference validation, before the shared final outbox flush. */
public final class MinutesEventReferences {
 private final JdbcTemplate j;
 public MinutesEventReferences(DataSource source){j=new JdbcTemplate(source);j.setQueryTimeout(2);}
 public Map<String,OutboxReferenceValidator> validators(){return Map.of("EXCLUSION_REVISION",event->{if(event.taskId()!=null||!"MINUTES_SETTINGS_INITIALIZED".equals(event.eventType())||!"MEETING".equals(event.subject().kind()))throw new IllegalArgumentException("Invalid initial minutes event");var found=j.queryForList("SELECT x.id FROM exclusion_revision x JOIN meeting m ON m.id=x.meeting_id JOIN template_revision t ON t.id=m.template_revision_id JOIN task_subject s ON s.subject_kind='MEETING' AND s.subject_id=m.id WHERE x.id=? AND m.id=? AND m.revision=? AND m.exclusion_revision_id=x.id AND x.revision=1 AND s.business_version=m.revision AND s.cancel_epoch=m.cancel_epoch AND s.deleted=0 AND m.needs_generation=1 AND t.owner_id IS NULL AND NOT EXISTS(SELECT 1 FROM sensitive_exclusion e WHERE e.revision_id=x.id)",String.class,event.reference().id(),event.subject().id(),event.subjectRevision());if(found.size()!=1)throw new IllegalArgumentException("Invalid initial minutes reference");});}
}
