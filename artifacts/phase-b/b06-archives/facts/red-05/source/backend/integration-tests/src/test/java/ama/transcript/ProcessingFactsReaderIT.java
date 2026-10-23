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
 static ArchiveReadBudget budget(long manifest,int files,long rows,long edges){return new ArchiveReadBudget(B.maxArtifactBytes(),B.maxTotalBytes(),B.maxArtifacts(),B.maxDependencies(),B.maxGraphDepth(),B.maxTokens(),B.maxJsonDepth(),B.maxNameUtf16(),B.maxStringUtf16(),B.maxStringUtf8Bytes(),B.maxAggregateScalars(),manifest,files,rows,edges);}
}
