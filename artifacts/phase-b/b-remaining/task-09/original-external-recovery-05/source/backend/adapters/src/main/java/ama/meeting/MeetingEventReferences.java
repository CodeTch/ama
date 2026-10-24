package ama.meeting;

import ama.contract.OutboxContracts.EventDraft;
import ama.task.OutboxReferenceValidator;
import javax.sql.DataSource;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/** Production domain references, validated immediately while the emitting transaction holds its root. */
public final class MeetingEventReferences {
    private final JdbcTemplate j;
    public MeetingEventReferences(DataSource source){j=new JdbcTemplate(source);}
    public Map<String,OutboxReferenceValidator> validators(){return Map.of("MEETING_INFO",this::info,"MEETING_ROSTER",this::roster);}
    private void common(EventDraft e){if(!e.subject().kind().equals("MEETING")||e.taskId()!=null)throw new IllegalArgumentException("Invalid domain event");}
    private void info(EventDraft e){
        common(e);
        if(!(e.eventType().equals("MEETING_CREATED")&&e.subjectRevision()==0) && !(e.eventType().equals("MEETING_INFO_SAVED")&&e.subjectRevision()>0))throw new IllegalArgumentException("Wrong info event meaning");
        var rows=j.queryForList("SELECT i.id FROM meeting_info_revision i JOIN meeting m ON m.id=i.meeting_id WHERE i.id=? AND i.meeting_id=? AND i.revision=? AND m.revision=i.revision AND m.info_revision_id=i.id",String.class,e.reference().id(),e.subject().id(),e.subjectRevision());
        if(rows.size()!=1)throw new IllegalArgumentException("Invalid immutable info provenance");
    }
    private void roster(EventDraft e){
        common(e);
        if(!e.eventType().equals("MEETING_ROSTER_CONFIRMED"))throw new IllegalArgumentException("Wrong roster event meaning");
        var rows=j.queryForList("SELECT r.id FROM roster_revision r JOIN meeting m ON m.id=r.meeting_id WHERE r.id=? AND r.meeting_id=? AND r.revision=? AND r.state='CONFIRMED' AND m.revision=r.revision AND m.roster_revision_id=r.id",String.class,e.reference().id(),e.subject().id(),e.subjectRevision());
        if(rows.size()!=1)throw new IllegalArgumentException("Invalid immutable confirmed-roster provenance");
    }
}
