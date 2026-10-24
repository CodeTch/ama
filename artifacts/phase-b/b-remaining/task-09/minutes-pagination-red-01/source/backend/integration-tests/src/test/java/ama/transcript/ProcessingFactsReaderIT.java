package ama.transcript;
import ama.access.MeetingAccess.AccessDeniedException;
import ama.contract.ConfigArchiveContracts.ArchiveReadBudget;
import ama.contract.IdentityContracts.*;
import ama.contract.MinutesContracts.*;
import ama.db.TransactionRunner;
import java.util.*;
import org.junit.jupiter.api.Test;
import static ama.transcript.ArchiveProcessingFixture.*;
import static ama.contract.MinutesContracts.FailureCode.*;
import static org.junit.jupiter.api.Assertions.*;

class ProcessingFactsReaderIT {
 @Test void readsCompleteStoredProcessingFromActualRows(){
  var f=new ArchiveProcessingFixture();String revision=f.revision(f.task);var reader=new JdbcProcessingFactsReader(f.source);
  var draft=reader.readBatch(f.actor,f.meeting,f.batch,B,()->false);
  assertEquals(2,draft.rows().size(),"complete batch and member must be read from MySQL");assertEquals(f.meeting,draft.files().getFirst().meetingId());
  assertEquals(f.fingerprint,service().computeProcessing(f.manifest.getBytes(java.nio.charset.StandardCharsets.UTF_8),draft,f.archives,B,()->false).fingerprint());
  var facts=reader.readStored(f.actor,f.meeting,f.input,B,()->false);
  assertEquals(4,facts.rows().size());assertEquals(1,facts.taskFacts().size());assertTrue(facts.taskSpecs().isEmpty());
  assertEquals(f.task,facts.taskFacts().getFirst().taskId());var spec=facts.taskFacts().getFirst().spec();
  assertEquals(9,spec.businessVersion());assertEquals(2,spec.cancelEpoch());assertEquals(-3,spec.priority());assertEquals(3,spec.maxAttempts());assertEquals(hash(f.task),spec.producerKey());
  assertEquals(f.manifest,input(facts).string("config_manifest"));assertEquals(TIME,input(facts).string("created_at"));assertEquals(7,input(facts).number("base_revision"));
  assertEquals(revision,facts.rows().stream().filter(r->r.table()==Table.TRANSCRIPT_REVISION).findFirst().orElseThrow().string("id"));
  assertEquals(f.fingerprint,service().verifyProcessing(input(facts),facts,f.archives,B,()->false).fingerprint());
  assertThrows(UnsupportedOperationException.class,()->facts.rows().clear());
 }
 @Test void extraTaskAndWrongProducerCannotDisappear(){
  var f=new ArchiveProcessingFixture();String extra=id();f.addTask(extra,f.input,f.fingerprint,10,2);var reader=new JdbcProcessingFactsReader(f.source);
  var facts=reader.readStored(f.actor,f.meeting,f.input,B,()->false);assertEquals(2,facts.taskFacts().size(),"contradictory task must remain visible");
  assertThrows(Failure.class,()->service().verifyProcessing(input(facts),facts,f.archives,B,()->false));
  var wrong=new ArchiveProcessingFixture();String producer=id();wrong.addTask(producer,id(),hash("wrong"),9,2);wrong.revision(producer);
  var producerFacts=new JdbcProcessingFactsReader(wrong.source).readStored(wrong.actor,wrong.meeting,wrong.input,B,()->false);
  assertEquals(Set.of(producer,wrong.task),new HashSet<>(producerFacts.taskFacts().stream().map(StoredTask::taskId).toList()));
  assertThrows(Failure.class,()->service().verifyProcessing(input(producerFacts),producerFacts,wrong.archives,B,()->false));
 }
 @Test void boundedMetadataBeforeMaterialization(){
  var f=new ArchiveProcessingFixture();var reader=new JdbcProcessingFactsReader(f.source);
  var small=budget(64,32,100,100);
  assertEquals(BUDGET_EXCEEDED,assertThrows(Failure.class,()->reader.readStored(f.actor,f.meeting,f.input,small,()->false)).code());
  assertEquals(BUDGET_EXCEEDED,assertThrows(Failure.class,()->reader.readStored(f.actor,f.meeting,f.input,budget(65536,32,3,100),()->false)).code());
 }
 @Test void missingTasksAndNullProducerRemainExplicit() {
  var f=new ArchiveProcessingFixture();f.j.update("DELETE FROM durable_task WHERE id=?",f.task);f.revision(null);
  var facts=new JdbcProcessingFactsReader(f.source).readStored(f.actor,f.meeting,f.input,B,()->false);
  assertTrue(facts.taskFacts().isEmpty());assertEquals(4,facts.rows().size());
  assertNull(facts.rows().stream().filter(r->r.table()==Table.TRANSCRIPT_REVISION).findFirst().orElseThrow().nullableString("producer_task_id"));
  assertThrows(Failure.class,()->service().verifyProcessing(input(facts),facts,f.archives,B,()->false));
 }
 @Test void ordinalGapExtraMembersAndForeignFileRemainVisible() {
  var gap=new ArchiveProcessingFixture();gap.j.update("UPDATE input_batch_file SET ordinal=2 WHERE batch_id=?",gap.batch);
  var gapFacts=new JdbcProcessingFactsReader(gap.source).readStored(gap.actor,gap.meeting,gap.input,B,()->false);
  var gapMembers=gapFacts.rows().stream().filter(r->r.table()==Table.INPUT_BATCH_FILE).toList();assertEquals(1,gapMembers.size());assertEquals(2,gapMembers.getFirst().number("ordinal"));
  assertThrows(Failure.class,()->service().verifyProcessing(input(gapFacts),gapFacts,gap.archives,B,()->false));
  var extra=new ArchiveProcessingFixture();var other=new ArchiveProcessingFixture();
  extra.j.update("INSERT INTO input_batch_file(batch_id,ordinal,file_id,sha256) VALUES (?,2,?,?)",extra.batch,other.file,SHA);
  var reader=new JdbcProcessingFactsReader(extra.source);var facts=reader.readStored(extra.actor,extra.meeting,extra.input,B,()->false);
  assertEquals(2,facts.files().size());assertTrue(facts.files().stream().anyMatch(f->f.meetingId().equals(other.meeting)));
  assertThrows(Failure.class,()->service().verifyProcessing(input(facts),facts,extra.archives,B,()->false));
  assertEquals(BUDGET_EXCEEDED,assertThrows(Failure.class,()->reader.readStored(extra.actor,extra.meeting,extra.input,budget(65536,1,100,100),()->false)).code());
 }
 @Test void currentIdentityAndEditAuthorizationAreRequired() {
  var f=new ArchiveProcessingFixture();var reader=new JdbcProcessingFactsReader(f.source);
  var staleDisabled=new Actor(f.owner,Status.DISABLED,Set.of());
  assertEquals(2,reader.readBatch(staleDisabled,f.meeting,f.batch,B,()->false).rows().size(),"current DB ACTIVE identity governs");
  f.j.update("UPDATE enterprise_identity SET status='DISABLED' WHERE enterprise_id=?",f.owner);
  assertThrows(AccessDeniedException.class,()->reader.readStored(f.actor,f.meeting,f.input,B,()->false));
  var owner=new ArchiveProcessingFixture();var other=new ArchiveProcessingFixture();
  assertThrows(AccessDeniedException.class,()->new JdbcProcessingFactsReader(owner.source).readBatch(other.actor,owner.meeting,owner.batch,B,()->false));
  assertThrows(Failure.class,()->new JdbcProcessingFactsReader(owner.source).readStored(owner.actor,owner.meeting,other.input,B,()->false));
 }
 @Test void ambientTransactionsAndCancellationFailClosed() {
  var f=new ArchiveProcessingFixture();var reader=new JdbcProcessingFactsReader(f.source);
  new TransactionRunner(f.source).required(()->{assertThrows(IllegalStateException.class,()->reader.readStored(f.actor,f.meeting,f.input,B,()->false));return null;});
  assertEquals(CANCELLED,assertThrows(Failure.class,()->reader.readStored(f.actor,f.meeting,f.input,B,()->true)).code());
  Thread.currentThread().interrupt();try{assertEquals(CANCELLED,assertThrows(Failure.class,()->reader.readBatch(f.actor,f.meeting,f.batch,B,()->false)).code());assertTrue(Thread.currentThread().isInterrupted());}finally{Thread.interrupted();}
  assertEquals(20,f.j.queryForObject("SELECT revision FROM meeting WHERE id=?",Long.class,f.meeting));
  assertEquals("READY",f.j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,f.task));
 }
 @Test void octetLengthGatePrecedesLongtextTransferAndRowsUseCurrentReads() {
  var f=new ArchiveProcessingFixture();var probe=new ProbeSource(f.source);var reader=new JdbcProcessingFactsReader(probe);
  f.j.update("UPDATE transcription_input SET config_manifest=? WHERE id=?","界".repeat(30),f.input);
  assertEquals(BUDGET_EXCEEDED,assertThrows(Failure.class,()->reader.readStored(f.actor,f.meeting,f.input,budget(64,32,100,100),()->false)).code());
  assertTrue(probe.sql.stream().anyMatch(q->q.contains("OCTET_LENGTH(config_manifest)")&&q.contains("CHAR_LENGTH(config_manifest)")&&q.contains("FOR UPDATE")));
  assertFalse(probe.sql.stream().anyMatch(q->q.contains("config_manifest")&&!q.contains("OCTET_LENGTH(config_manifest)")),"oversize LONGTEXT must never be selected");
  f.j.update("UPDATE transcription_input SET config_manifest=? WHERE id=?",f.manifest,f.input);probe.sql.clear();f.revision(f.task);
  var facts=reader.readStored(f.actor,f.meeting,f.input,B,()->false);assertEquals(f.fingerprint,service().verifyProcessing(input(facts),facts,f.archives,B,()->false).fingerprint());
  int revision=index(probe.sql,"FROM transcript_revision"),file=index(probe.sql,"FROM file_object"),task=index(probe.sql,"FROM durable_task");
  assertTrue(revision>=0&&file>revision&&task>file,"all domain rows precede files, then complete task union");
  for(String q:probe.sql)if(q.contains("FROM input_batch")||q.contains("FROM transcription_input")||q.contains("FROM transcript_revision")||q.contains("FROM file_object")||q.contains("FROM durable_task"))assertTrue(q.contains("FOR UPDATE"),q);
  assertEquals(BUDGET_EXCEEDED,assertThrows(Failure.class,()->reader.readStored(f.actor,f.meeting,f.input,budget(65536,32,100,1),()->false)).code());
 }
 @Test void legalLongMaxFactBudgetsReadSmallDataWithoutOverflow() {
  var f=new ArchiveProcessingFixture();var reader=new JdbcProcessingFactsReader(f.source);
  var facts=assertDoesNotThrow(()->reader.readStored(f.actor,f.meeting,f.input,budget(65536,32,Long.MAX_VALUE,Long.MAX_VALUE),()->false));
  assertEquals(3,facts.rows().size());assertEquals(1,facts.files().size());assertEquals(1,facts.taskFacts().size());
  assertEquals(f.fingerprint,service().verifyProcessing(input(facts),facts,f.archives,B,()->false).fingerprint());
  assertEquals("2efd9361bb610198a2a59c3fb7256870229a14a1aeb72751dd8f3f4155c13f8e",ref(f.archives.get(3)).get("sha256"));
  f.addTask(id(),f.input,f.fingerprint,9,2);
  assertEquals(BUDGET_EXCEEDED,assertThrows(Failure.class,()->reader.readStored(f.actor,f.meeting,f.input,budget(65536,32,5,100),()->false)).code(),"extra task exceeds remaining facts instead of disappearing at LIMIT");
 }
 @Test void cancellationAtRealReadCommitCannotReturnSuccess() {
  var f=new ArchiveProcessingFixture();var probe=new ProbeSource(f.source);var cancelled=new java.util.concurrent.atomic.AtomicBoolean();
  probe.afterCommit=()->cancelled.set(true);
  assertEquals(CANCELLED,assertThrows(Failure.class,()->new JdbcProcessingFactsReader(probe).readStored(f.actor,f.meeting,f.input,B,cancelled::get)).code());
  assertTrue(cancelled.get(),"probe observed the actual read transaction commit");
  assertEquals(f.manifest,f.j.queryForObject("SELECT config_manifest FROM transcription_input WHERE id=?",String.class,f.input));
  assertEquals("READY",f.j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,f.task));
 }
 @Test void supplementaryIdentityUtf16LimitPrecedesSharedAccessProjection() {
  var f=new ArchiveProcessingFixture();var probe=new ProbeSource(f.source);var reader=new JdbcProcessingFactsReader(probe);
  String emoji="\ud83d\ude00";
  f.j.update("UPDATE enterprise_identity SET display_name=? WHERE enterprise_id=?",emoji.repeat(40),f.owner);
  assertEquals(40,f.j.queryForObject("SELECT CHAR_LENGTH(display_name) FROM enterprise_identity WHERE enterprise_id=?",Long.class,f.owner));
  assertEquals(80,f.j.queryForObject("SELECT OCTET_LENGTH(CONVERT(display_name USING utf16)) DIV 2 FROM enterprise_identity WHERE enterprise_id=?",Long.class,f.owner));
  assertAll(
   ()->assertEquals(BUDGET_EXCEEDED,assertThrows(Failure.class,()->reader.readBatch(f.actor,f.meeting,f.batch,utf16Budget(64,B.maxAggregateScalars()),()->false)).code()),
   ()->assertFalse(probe.sql.stream().anyMatch(q->q.contains("display_name")&&!q.contains("OCTET_LENGTH(display_name)")),"over-budget identity must never reach shared LONGTEXT projection")
  );
  f.j.update("UPDATE enterprise_identity SET display_name=? WHERE enterprise_id=?",emoji.repeat(32),f.owner);probe.sql.clear();
  assertEquals(2,reader.readBatch(f.actor,f.meeting,f.batch,utf16Budget(64,B.maxAggregateScalars()),()->false).rows().size(),"32 supplementary scalars are exactly64 legal UTF16 units");
  probe.sql.clear();
  assertEquals(BUDGET_EXCEEDED,assertThrows(Failure.class,()->reader.readBatch(f.actor,f.meeting,f.batch,utf16Budget(64,32),()->false)).code());
  assertTrue(probe.sql.stream().anyMatch(q->q.contains("FROM input_batch")),"identity uses32 aggregate scalars, allowing domain acquisition before later row scalars exceed32");
 }
 @Test void supplementaryManifestUtf16LimitPrecedesLongtextProjection() {
  var f=new ArchiveProcessingFixture();var probe=new ProbeSource(f.source);var reader=new JdbcProcessingFactsReader(probe);
  String emoji="\ud83d\ude00";
  f.j.update("UPDATE transcription_input SET config_manifest=? WHERE id=?",emoji.repeat(40),f.input);
  assertEquals(160,f.j.queryForObject("SELECT OCTET_LENGTH(config_manifest) FROM transcription_input WHERE id=?",Long.class,f.input));
  assertEquals(80,f.j.queryForObject("SELECT OCTET_LENGTH(CONVERT(config_manifest USING utf16)) DIV 2 FROM transcription_input WHERE id=?",Long.class,f.input));
  assertAll(
   ()->assertEquals(BUDGET_EXCEEDED,assertThrows(Failure.class,()->reader.readStored(f.actor,f.meeting,f.input,utf16Budget(64,B.maxAggregateScalars()),()->false)).code()),
   ()->assertFalse(probe.sql.stream().anyMatch(q->q.contains("config_manifest")&&!q.contains("OCTET_LENGTH(config_manifest)")),"over-budget supplementary manifest must never be selected")
  );
  String boundary=emoji.repeat(32);f.j.update("UPDATE transcription_input SET config_manifest=? WHERE id=?",boundary,f.input);probe.sql.clear();
  var facts=reader.readStored(f.actor,f.meeting,f.input,utf16Budget(64,B.maxAggregateScalars()),()->false);
  assertEquals(boundary,input(facts).string("config_manifest"));assertEquals(64,input(facts).string("config_manifest").length());
  assertTrue(probe.sql.stream().anyMatch(q->q.contains("config_manifest")&&!q.contains("OCTET_LENGTH(config_manifest)")),"exact legal UTF16 boundary transfers original bytes");
  // This intentionally non-JSON value tests acquisition only, not a valid processing binding.
 }
 static ArchiveReadBudget utf16Budget(int utf16,long aggregate){return new ArchiveReadBudget(B.maxArtifactBytes(),B.maxTotalBytes(),B.maxArtifacts(),B.maxDependencies(),B.maxGraphDepth(),B.maxTokens(),B.maxJsonDepth(),B.maxNameUtf16(),utf16,B.maxStringUtf8Bytes(),aggregate,B.maxManifestBytes(),B.maxBatchFiles(),B.maxFactRows(),B.maxFactEdges());}
 static int index(List<String> values,String marker){for(int i=0;i<values.size();i++)if(values.get(i).contains(marker))return i;return -1;}
 /** Delegates every actual connection/result; observes SQL only, never substitutes a DB return. */
 static final class ProbeSource extends org.springframework.jdbc.datasource.AbstractDataSource {
  final javax.sql.DataSource actual;final List<String> sql=new ArrayList<>();Runnable afterCommit=()->{};ProbeSource(javax.sql.DataSource actual){this.actual=actual;}
  public java.sql.Connection getConnection() throws java.sql.SQLException{return wrap(actual.getConnection());}
  public java.sql.Connection getConnection(String user,String password) throws java.sql.SQLException{return wrap(actual.getConnection(user,password));}
  java.sql.Connection wrap(java.sql.Connection actual){return (java.sql.Connection)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{java.sql.Connection.class},(proxy,method,args)->{
   if(method.getName().equals("prepareStatement"))sql.add((String)args[0]);
   try{Object result=method.invoke(actual,args);if(method.getName().equals("commit"))afterCommit.run();return result;}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
  });}
 }
 static ArchiveReadBudget budget(long manifest,int files,long rows,long edges){return new ArchiveReadBudget(B.maxArtifactBytes(),B.maxTotalBytes(),B.maxArtifacts(),B.maxDependencies(),B.maxGraphDepth(),B.maxTokens(),B.maxJsonDepth(),B.maxNameUtf16(),B.maxStringUtf16(),B.maxStringUtf8Bytes(),B.maxAggregateScalars(),manifest,files,rows,edges);}
}
