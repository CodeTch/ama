package ama.task;
import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.Actor;
import ama.meeting.JdbcMeetingAccess;
import ama.task.ExternalOperationPort.ExecutionContext;
import ama.task.TaskDomainPolicy.PreparedDomain;
import java.util.Optional;
import javax.sql.DataSource;
/** Generation retention issuer; no consumer registration is installed by construction. */
public final class JdbcGenerationRetention {
 public JdbcGenerationRetention(DataSource source,JdbcTaskPreparations preparations,JdbcMeetingAccess access) {}
 public Optional<GenerationRetentionPolicy.Retention> prepare(PreparedDomain held,ExecutionContext context,MeetingAccess.Locked root,Actor originalOwner){return Optional.empty();}
}
