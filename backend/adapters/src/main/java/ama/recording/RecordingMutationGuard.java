package ama.recording;
import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.Actor;
import javax.sql.DataSource;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
/** Capture cannot mutate a current final/published meeting. Reads keep their own permission floor. */
public final class RecordingMutationGuard {
 public static final class Frozen extends RuntimeException {public Frozen(){super("Meeting is read-only");}}
 private final MeetingAccess access;private final JdbcTemplate j;
 public RecordingMutationGuard(DataSource source,MeetingAccess access){
  if(!(access instanceof ama.meeting.JdbcMeetingAccess actual))throw new IllegalArgumentException("Actual meeting gate required");actual.requireProjectionSource(source);this.access=access;j=new JdbcTemplate(source);j.setQueryTimeout(2);
 }
 public void require(Actor actor,String meeting,MeetingAccess.Locked held){
  access.requirePrepared(held,actor,meeting,List.of(actor.enterpriseId()),MeetingAccess.Action.EDIT);
  var rows=j.query("SELECT ever_published,final_confirmation_id FROM meeting WHERE id=?",(r,n)->r.getBoolean(1)||r.getString(2)!=null,meeting);
  if(rows.size()!=1||rows.getFirst())throw new Frozen();
 }
}
