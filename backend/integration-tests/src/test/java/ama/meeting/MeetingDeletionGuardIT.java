package ama.meeting;

import ama.contract.IdentityContracts.*;
import ama.contract.MeetingContracts.*;
import ama.db.*;
import ama.task.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Real owner/meeting deletion transaction; recording/publication rows are explicit negative fixtures. */
class MeetingDeletionGuardIT {
 JdbcTemplate j;MeetingService meetings;Actor actor;
 @BeforeEach void fixture(){
  String url=System.getenv("AMA_C_RECORDING_DATABASE_URL");if(url==null||!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/ama_c_recording_[0-9a-f]{12}"))throw new IllegalArgumentException("Owned integration schema required");
  var source=MysqlDataSources.fromEnvironment("AMA_C_RECORDING_DATABASE_");j=new JdbcTemplate(source);Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
  actor=new Actor("deletion-guard-"+UUID.randomUUID(),Status.ACTIVE,Set.of(Role.USER));j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",actor.enterpriseId(),actor.enterpriseId());
  var tx=new TransactionRunner(source);var fences=new JdbcSubjectFences(source,Set.of("MEETING"));var access=new JdbcMeetingAccess(source,tx,fences);
  meetings=new MeetingService(new JdbcMeetingRepository(source,tx,access,fences,new JdbcOutbox(source,new MeetingEventReferences(source).validators()),new ama.audit.JdbcAuditAdapter(source),ignored->{}));
 }
 String create(){return meetings.create(actor,UUID.randomUUID().toString(),UUID.randomUUID().toString()).id();}
 @Test void activePausedAndUnrecognizedRecordingStatesCannotDeleteOrConsumeTheOriginalKey(){
  for(String state:List.of("OPEN","PAUSED","UNKNOWN")){
   String meeting=create(),session=UUID.randomUUID().toString(),key=UUID.randomUUID().toString();j.update("INSERT INTO recording_session(session_id,meeting_id,part_id,writer_epoch,backend_id,state,created_at) VALUES (?,?,?,1,'guard-fixture',?,UTC_TIMESTAMP(6))",session,meeting,UUID.randomUUID().toString(),state);
   assertThrows(Conflict.class,()->meetings.delete(actor,meeting,key,new DeleteMeeting(0L),"recording-denial"),state);assertEquals("ACTIVE",j.queryForObject("SELECT lifecycle FROM meeting WHERE id=?",String.class,meeting));
   assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE resource_id=? AND operation='DELETE_MEETING'",Integer.class,meeting));
   j.update("UPDATE recording_session SET state='FINISHED' WHERE session_id=?",session);var deleted=meetings.delete(actor,meeting,key,new DeleteMeeting(0L),"after-finish");assertEquals(1,deleted.revision());assertEquals(deleted,meetings.delete(actor,meeting,key,new DeleteMeeting(0L),"original-replay"));
  }
 }
 @Test void everPublishedAndNonActiveGovernanceRemainProtected(){
  String meeting=create();j.update("UPDATE meeting SET ever_published=1 WHERE id=?",meeting);assertThrows(Conflict.class,()->meetings.delete(actor,meeting,"published",new DeleteMeeting(0L),"denial"));
  for(String state:List.of("WITHDRAW_PENDING","WITHDRAWN","OBSOLETE_PENDING","OBSOLETE")){String selected=create();j.update("UPDATE meeting SET governance_state=? WHERE id=?",state,selected);assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->meetings.delete(actor,selected,"governance",new DeleteMeeting(0L),"denial"));}
 }
}
