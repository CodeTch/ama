package ama.storage;
import ama.transcript.*;
import ama.config.*;
import ama.config.file.*;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.ConfigArchiveContracts.Ref;
import ama.contract.MinutesContracts.*;
import ama.contract.IdentityContracts.Actor;
import ama.minutes.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
/** Actual production command in noninstalled source-first composition, not combined Task10/11 UX. */
class Task09InitialSettingsIT extends Task09MinutesFixture {
 @Test void firstSettingsPersistRealTemplateEmptyExclusionAndAdvanceExactlyOnce()throws Exception {
  install();var before=j.queryForMap("SELECT * FROM meeting WHERE id=?",meeting);var result=initialize(service(),"initial",0);var stable=(Map<?,?>)result.get("result");assertEquals("INITIAL_MINUTES_SETTINGS",stable.get("kind"));assertEquals(revision,stable.get("templateRevisionId"));String exclusion=(String)stable.get("exclusionRevisionId");
  assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM sensitive_exclusion WHERE revision_id=?",Integer.class,exclusion));assertEquals(1,j.queryForObject("SELECT revision FROM exclusion_revision WHERE id=? AND meeting_id=?",Integer.class,exclusion,meeting));assertEquals(1,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Integer.class,meeting));assertEquals(1,j.queryForObject("SELECT business_version FROM task_subject WHERE subject_id=? AND subject_kind='MEETING'",Integer.class,meeting));assertEquals(1,j.queryForObject("SELECT needs_generation FROM meeting WHERE id=?",Integer.class,meeting));assertEquals(before.get("cancel_epoch"),j.queryForMap("SELECT cancel_epoch FROM meeting WHERE id=?",meeting).get("cancel_epoch"));
  assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM task_outbox WHERE subject_id=? AND event_type='MINUTES_SETTINGS_INITIALIZED' AND reference_id=?",Integer.class,meeting,exclusion));assertEquals(0,j.queryForObject("SELECT intent_epoch FROM meeting_minutes_editor_guard WHERE meeting_id=?",Integer.class,meeting));
 }
 @Test void committedReplayPrecedesNowUnavailableArchivesAndCurrentCas()throws Exception {
  install();Object service=service();var first=initialize(service,"same-key",0);archiveUnavailable.set(true);var replay=initialize(service,"same-key",0);assertEquals(first.get("result"),replay.get("result"));assertEquals(true,replay.get("replayed"));assertEquals(1,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Integer.class,meeting));assertThrows(RuntimeException.class,()->initialize(service,"same-key",1));
 }

 @Test void differentKeyAndDetachedHistoryCannotReinitialize()throws Exception {
  install();Object service=service();String detached=UUID.randomUUID().toString();j.update("INSERT INTO exclusion_revision(id,meeting_id,revision,created_at) VALUES (?,?,1,UTC_TIMESTAMP(6))",detached,meeting);
  assertThrows(RuntimeException.class,()->initialize(service,"detached",0));assertEquals(0,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Integer.class,meeting));assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM exclusion_revision WHERE meeting_id=?",Integer.class,meeting));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE resource_id=? AND operation='MINUTES_INITIAL_SETTINGS'",Integer.class,meeting));
 }
 @Test void partialSettingsAreNotRepaired()throws Exception {
  install();j.update("UPDATE meeting SET template_revision_id=? WHERE id=?",revision,meeting);assertThrows(RuntimeException.class,()->initialize(service(),"partial",0));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM exclusion_revision WHERE meeting_id=?",Integer.class,meeting));assertEquals(revision,j.queryForObject("SELECT template_revision_id FROM meeting WHERE id=?",String.class,meeting));
 }
 @Test void auditFailureRollsBackEverySettingsEffect()throws Exception {
  install();var authority=ama.api.Task09RuntimeProbe.components(app).authority();var service=new JdbcMinutesInitialSettings(authority,this::snapshot,prompt,model,new TranscriptAcquisitionBudget(16777216,16777216,16777216,268435456,134217728,8388608,128,256,256),new ama.task.JdbcSubjectFences(source,Set.of("MEETING")),new ama.task.JdbcOutbox(source,new MinutesEventReferences(source).validators()),event->{throw new IllegalStateException("injected audit failure");});var before=j.queryForMap("SELECT * FROM meeting WHERE id=?",meeting);
  assertThrows(IllegalStateException.class,()->initialize(service,"rollback",0));assertEquals(before,j.queryForMap("SELECT * FROM meeting WHERE id=?",meeting));assertEquals(0,j.queryForObject("SELECT business_version FROM task_subject WHERE subject_kind='MEETING' AND subject_id=?",Integer.class,meeting));for(String table:List.of("exclusion_revision","task_outbox","audit_event","idempotency_record")){String column=table.equals("exclusion_revision")?"meeting_id":table.equals("task_outbox")?"subject_id":"resource_id";String extra=table.equals("task_outbox")?" AND event_type='MINUTES_SETTINGS_INITIALIZED'":table.equals("audit_event")?" AND action='MINUTES_SETTINGS_INITIALIZED'":table.equals("idempotency_record")?" AND operation='MINUTES_INITIAL_SETTINGS'":"";assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE "+column+"=?"+extra,Integer.class,meeting));}
 }
 @Test void concurrentSameKeyHasOneCompleteWinnerAndOneReplay()throws Exception {
  install();Object service=service();var ready=new java.util.concurrent.CountDownLatch(2);var start=new java.util.concurrent.CountDownLatch(1);try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){java.util.concurrent.Callable<Map<String,Object>> call=()->{ready.countDown();assertTrue(start.await(5,java.util.concurrent.TimeUnit.SECONDS));return initialize(service,"concurrent",0);};var a=pool.submit(call);var b=pool.submit(call);assertTrue(ready.await(5,java.util.concurrent.TimeUnit.SECONDS));start.countDown();var first=a.get(20,java.util.concurrent.TimeUnit.SECONDS);var second=b.get(20,java.util.concurrent.TimeUnit.SECONDS);assertEquals(first.get("result"),second.get("result"));assertNotEquals(first.get("replayed"),second.get("replayed"));}assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM exclusion_revision WHERE meeting_id=?",Integer.class,meeting));assertEquals(1,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Integer.class,meeting));assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM task_outbox WHERE subject_id=? AND event_type='MINUTES_SETTINGS_INITIALIZED'",Integer.class,meeting));
 }
}
