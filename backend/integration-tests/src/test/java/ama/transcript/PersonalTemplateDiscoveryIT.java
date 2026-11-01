package ama.transcript;

import ama.audit.JdbcAuditAdapter;
import ama.contract.IdentityContracts.*;
import ama.db.*;
import ama.meeting.*;
import ama.minutes.JdbcPersonalTemplates;
import ama.task.*;
import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Actual metadata closure of genuine personal CRUD, without fabricated transcript/model output. */
class PersonalTemplateDiscoveryIT {
 DataSource source;JdbcPersonalTemplates templates;Actor actor;String meeting;
 @BeforeEach void start(){
  String url=System.getenv("AMA_C_RECORDING_DATABASE_URL");if(url==null||!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/ama_c_recording_[0-9a-f]{12}"))throw new IllegalArgumentException("Owned schema required");
  source=MysqlDataSources.fromEnvironment("AMA_C_RECORDING_DATABASE_");var j=new JdbcTemplate(source);Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
  actor=new Actor("template-closure-"+UUID.randomUUID(),Status.ACTIVE,Set.of(Role.USER));j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",actor.enterpriseId(),actor.enterpriseId());
  var tx=new TransactionRunner(source);var fences=new JdbcSubjectFences(source,Set.of("MEETING"));var access=new JdbcMeetingAccess(source,tx,fences);templates=new JdbcPersonalTemplates(source,tx,access);
  meeting=new MeetingService(new JdbcMeetingRepository(source,tx,access,fences,new JdbcOutbox(source,new MeetingEventReferences(source).validators()),new JdbcAuditAdapter(source),id->{})).create(actor,UUID.randomUUID().toString(),UUID.randomUUID().toString()).id();
 }
 @Test void mutablePersonalLifecycleIsPartOfEverySelectedRevisionDiscoveryButContainsNoThemeText(){
  var template=templates.create(actor,UUID.randomUUID().toString(),"私有章节",List.of("决定"));
  var before=MinutesGraphDiscovery.discover(source,meeting,"INITIAL_SETTINGS",template.revisionId(),1000);
  var states=before.tables().getOrDefault("personal_template_state",List.of());assertEquals(1,states.size());assertEquals(template.id(),states.getFirst().get("template_id"));assertEquals(1L,states.getFirst().get("command_revision"));assertNull(states.getFirst().get("deleted_at"));
  assertFalse(states.getFirst().containsKey("name"));assertFalse(states.getFirst().containsKey("themes"));
  templates.delete(actor,template.id(),UUID.randomUUID().toString(),1);
  var after=MinutesGraphDiscovery.discover(source,meeting,"INITIAL_SETTINGS",template.revisionId(),1000);var removed=after.tables().get("personal_template_state").getFirst();assertEquals(2L,removed.get("command_revision"));assertNotNull(removed.get("deleted_at"));assertNotEquals(before,after);
  assertEquals(template.revisionId(),after.tables().get("template_revision").getFirst().get("id"));
 }
}
