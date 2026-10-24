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
class Task09InitialSettingsIT extends Task09PipelineFixture {
 String template,revision;Ref prompt,model;final AtomicBoolean archiveUnavailable=new AtomicBoolean();
 void install()throws Exception {
  template=UUID.randomUUID().toString();revision=UUID.randomUUID().toString();var installed=new ControlledMinutesTemplateInstaller(source,"CONTROLLED_INSTALLATION").install(template,revision);String release="task09.controlled."+UUID.randomUUID();
  var canonical=new CanonicalBudget(16777216,32,1000000,4194304,250000);var output=new ValidationBudget(8388608,32,400000,128,4194304,4194304,4194304,16777216,1000,1000,1000,10000,10000,20000);
  var raw=ControlledMinutesArchives.build("CONTROLLED_INSTALLATION",installed.revisionId(),release,"Controlled synthetic minutes.",canonical,output);var current=snapshot();var existing=new HashMap<Identity,String>();for(var entry:current.index().entries())existing.put(entry.ref().identity(),entry.ref().sha256());var appended=new ArrayList<RawArchive>();
  for(var archive:raw){String prior=existing.get(archive.claimedIdentity());if(prior==null)appended.add(archive);else assertEquals(prior,java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(archive.bytes())));}
  pin=new ControlledArchiveReleaseWriter().publish(archive,pin,appended,new ConfigArchiveIndex.IndexBudget(4194304,4096,67108864,128),B,()->false);var next=snapshot();prompt=next.index().entries().stream().map(ConfigArchiveIndex.Entry::ref).filter(r->r.artifactType()==ArtifactType.PROMPT_CONFIG).findFirst().orElseThrow();model=next.index().entries().stream().map(ConfigArchiveIndex.Entry::ref).filter(r->r.artifactType()==ArtifactType.MODEL_CONFIG).findFirst().orElseThrow();
  java.nio.file.Files.writeString(receipt.resolve("controlled-template-archive-installation.json"),json.writeValueAsString(Map.of("template",template,"revision",revision,"indexSha",pin,"promptVersion",prompt.version(),"modelVersion",model.version())));
 }
 ConfigArchiveSource.Snapshot snapshot(){assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());if(archiveUnavailable.get())throw ama.contract.MinutesContracts.fail(FailureCode.PROFILE_UNAVAILABLE);return new ControlledConfigArchiveSource().open(archive,pin,new ConfigArchiveIndex.IndexBudget(4194304,4096,67108864,128),B,()->false);}
 Object service()throws Exception {Class<?> type=assertDoesNotThrow(()->Class.forName("ama.transcript.JdbcMinutesInitialSettings"));return type.getConstructor(JdbcTranscriptAuthority.class,Supplier.class,Ref.class,Ref.class).newInstance(ama.api.Task09RuntimeProbe.components(app).authority(),(Supplier<ConfigArchiveSource.Snapshot>)this::snapshot,prompt,model);}
 @SuppressWarnings("unchecked") Map<String,Object> initialize(Object service,String key,long expected)throws Exception {try{return (Map<String,Object>)service.getClass().getMethod("initialize",Actor.class,String.class,String.class,long.class,String.class,String.class).invoke(service,actor(),meeting,key,expected,revision,UUID.randomUUID().toString());}catch(java.lang.reflect.InvocationTargetException e){if(e.getCause() instanceof RuntimeException r)throw r;throw e;}}
 @Test void firstSettingsPersistRealTemplateEmptyExclusionAndAdvanceExactlyOnce()throws Exception {
  install();var before=j.queryForMap("SELECT * FROM meeting WHERE id=?",meeting);var result=initialize(service(),"initial",0);var stable=(Map<?,?>)result.get("result");assertEquals("INITIAL_MINUTES_SETTINGS",stable.get("kind"));assertEquals(revision,stable.get("templateRevisionId"));String exclusion=(String)stable.get("exclusionRevisionId");
  assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM sensitive_exclusion WHERE revision_id=?",Integer.class,exclusion));assertEquals(1,j.queryForObject("SELECT revision FROM exclusion_revision WHERE id=? AND meeting_id=?",Integer.class,exclusion,meeting));assertEquals(1,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Integer.class,meeting));assertEquals(1,j.queryForObject("SELECT business_version FROM task_subject WHERE subject_id=? AND subject_kind='MEETING'",Integer.class,meeting));assertEquals(1,j.queryForObject("SELECT needs_generation FROM meeting WHERE id=?",Integer.class,meeting));assertEquals(before.get("cancel_epoch"),j.queryForMap("SELECT cancel_epoch FROM meeting WHERE id=?",meeting).get("cancel_epoch"));
  assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM task_outbox WHERE subject_id=? AND event_type='MINUTES_SETTINGS_INITIALIZED' AND reference_id=?",Integer.class,meeting,exclusion));assertEquals(0,j.queryForObject("SELECT intent_epoch FROM meeting_minutes_editor_guard WHERE meeting_id=?",Integer.class,meeting));
 }
 @Test void committedReplayPrecedesNowUnavailableArchivesAndCurrentCas()throws Exception {
  install();Object service=service();var first=initialize(service,"same-key",0);archiveUnavailable.set(true);var replay=initialize(service,"same-key",0);assertEquals(first.get("result"),replay.get("result"));assertEquals(true,replay.get("replayed"));assertEquals(1,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Integer.class,meeting));assertThrows(RuntimeException.class,()->initialize(service,"same-key",1));
 }
}
