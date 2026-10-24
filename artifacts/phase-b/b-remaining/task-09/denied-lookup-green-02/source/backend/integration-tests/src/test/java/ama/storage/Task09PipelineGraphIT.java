package ama.storage;
import ama.transcript.*;
import ama.config.*;
import ama.config.file.*;
import ama.contract.IdentityContracts.Actor;
import ama.contract.TranscriptContracts.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
class Task09PipelineGraphIT extends Task09PipelineFixture {
 @Test void realImportSavedConfirmationPassesOriginalMinutesSourcePreparation()throws Exception {
  String raw=raw();var saved=isolated();var ids=j.queryForList("SELECT segment_id FROM transcript_segment WHERE revision_id=? ORDER BY ordinal",String.class,raw);
  var result=saved.mutate(actor(),meeting,"save",new Command(Operation.SAVE,1,raw,ids.stream().map(id->new Edit(id,"Actual original 😀",List.of())).toList()),UUID.randomUUID().toString());String working=(String)((Map<?,?>)result.get("result")).get("revisionId");
  saved.mutate(actor(),meeting,"confirm",new Command(Operation.CONFIRM,2,working,List.of()),UUID.randomUUID().toString());
  Class<?> type=assertDoesNotThrow(()->Class.forName("ama.transcript.JdbcMinutesFactsReader"));
  Supplier<ConfigArchiveSource.Snapshot> archives=()->new ControlledConfigArchiveSource().open(archive,pin,new ConfigArchiveIndex.IndexBudget(4194304,4096,67108864,128),B,()->false);
  Object reader=type.getConstructor(JdbcTranscriptAuthority.class,Supplier.class,String.class,TranscriptAcquisitionBudget.class).newInstance(ama.api.Task09RuntimeProbe.components(app).authority(),archives,pin,new TranscriptAcquisitionBudget(16777216,16777216,16777216,67108864,67108864,33554432,128,256,256));
  Object prepared;try{prepared=type.getMethod("readOwner",Actor.class,String.class,String.class,String.class).invoke(reader,actor(),meeting,"CURRENT_GENERATION",null);}catch(java.lang.reflect.InvocationTargetException e){throw new AssertionError("actual pipeline preparation",e.getCause());}
  var facts=(ama.contract.MinutesContracts.StoredProvenanceFacts)prepared.getClass().getMethod("facts").invoke(prepared);
  assertEquals(2,facts.rows().stream().filter(r->r.table()==ama.contract.MinutesContracts.Table.TRANSCRIPT_REVISION).count());assertEquals(1,facts.files().size());assertEquals(1,facts.taskFacts().size());
  java.nio.file.Files.writeString(receipt.resolve("minutes-source-proof.json"),json.writeValueAsString(Map.of("meeting",meeting,"raw",raw,"working",working,"rows",facts.rows().size(),"files",facts.files().size(),"tasks",facts.taskFacts().size())));
 }
 String confirmed()throws Exception {
  String raw=raw();var service=isolated();var ids=j.queryForList("SELECT segment_id FROM transcript_segment WHERE revision_id=? ORDER BY ordinal",String.class,raw);
  var saved=service.mutate(actor(),meeting,"save",new Command(Operation.SAVE,1,raw,ids.stream().map(id->new Edit(id,"Verified original",List.of())).toList()),UUID.randomUUID().toString());String working=(String)((Map<?,?>)saved.get("result")).get("revisionId");service.mutate(actor(),meeting,"confirm",new Command(Operation.CONFIRM,2,working,List.of()),UUID.randomUUID().toString());return working;
 }
 JdbcMinutesFactsReader reader(Supplier<ConfigArchiveSource.Snapshot> supplier){return new JdbcMinutesFactsReader(ama.api.Task09RuntimeProbe.components(app).authority(),supplier,pin,new TranscriptAcquisitionBudget(16777216,16777216,16777216,67108864,67108864,33554432,128,256,256));}
 ConfigArchiveSource.Snapshot snapshot(){assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());return new ControlledConfigArchiveSource().open(archive,pin,new ConfigArchiveIndex.IndexBudget(4194304,4096,67108864,128),B,()->false);}
 @Test void alteredConfirmedBodyCannotPassByMatchingStoredFingerprintAndTask()throws Exception {
  String working=confirmed();new ama.db.TransactionRunner(source,10).required(()->{j.queryForMap("SELECT id FROM meeting WHERE id=? FOR UPDATE",meeting);j.update("UPDATE transcript_segment SET body='Changed after confirmation' WHERE revision_id=?",working);return null;});
  var failure=assertThrows(ama.contract.MinutesContracts.Failure.class,()->reader(this::snapshot).readOwner(actor(),meeting,"CURRENT_GENERATION",null));assertEquals(ama.contract.MinutesContracts.FailureCode.FINGERPRINT_MISMATCH,failure.code());
 }
 @Test void freshFinalAuthorizationTakesPrecedenceOverDeferredArchiveFailure()throws Exception {
  confirmed();var reader=reader(()->{assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());j.update("UPDATE enterprise_identity SET status='DISABLED' WHERE enterprise_id=?",owner);throw ama.contract.MinutesContracts.fail(ama.contract.MinutesContracts.FailureCode.PROFILE_UNAVAILABLE);});
  assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->reader.readOwner(actor(),meeting,"CURRENT_GENERATION",null));
 }

}
