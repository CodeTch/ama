package ama.storage;
import ama.contract.TranscriptContracts.*;import org.junit.jupiter.api.Test;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class Task06GuardsIT extends Task06Fixture {
 @Test void futureInstallationClosesAdvertisedWritesAndAcceptedReplaySurvives()throws Exception {
  String raw=raw();var service=isolated();var ids=j.queryForList("SELECT segment_id FROM transcript_segment WHERE revision_id=? ORDER BY ordinal",String.class,raw);var command=new Command(Operation.SAVE,1,raw,ids.stream().map(id->new Edit(id,"Guard😀",List.of())).toList());var saved=service.mutate(actor(),meeting,"saved",command,UUID.randomUUID().toString());String working=(String)((Map<?,?>)saved.get("result")).get("revisionId");
  ((org.springframework.core.env.ConfigurableEnvironment)app.getEnvironment()).getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("task06-incomplete-candidate",Map.of("transcript.candidates.installation","ENABLED")));
  var unavailable=isolated();var view=unavailable.readOwner(actor(),meeting,working);var capabilities=(Map<?,?>)view.get("capabilities");assertEquals(false,capabilities.get("canSave"));assertEquals(false,capabilities.get("canConfirm"));assertEquals(false,capabilities.get("canRestore"));
  var replay=unavailable.mutate(actor(),meeting,"saved",command,UUID.randomUUID().toString());assertEquals(saved.get("result"),replay.get("result"));assertEquals(true,replay.get("replayed"));assertThrows(ama.contract.MinutesContracts.Failure.class,()->unavailable.mutate(actor(),meeting,"new",new Command(Operation.RESTORE,2,working,List.of()),UUID.randomUUID().toString()));
 }
 @Test void ownerHistoryUsesBoundedExclusiveCursorWithoutFullGraphReads()throws Exception {
  String raw=raw();var service=isolated();var ids=j.queryForList("SELECT segment_id FROM transcript_segment WHERE revision_id=? ORDER BY ordinal",String.class,raw);var saved=service.mutate(actor(),meeting,"saved",new Command(Operation.SAVE,1,raw,ids.stream().map(id->new Edit(id,"Guard😀",List.of())).toList()),UUID.randomUUID().toString());String working=(String)((Map<?,?>)saved.get("result")).get("revisionId");
  var count=new java.util.concurrent.atomic.AtomicInteger();probe.before=sql->{if(sql.startsWith("SELECT id,parent_id,processing_input_id"))count.incrementAndGet();};
  var history=service.history(actor(),meeting,1,null);assertEquals(true,history.get("hasMore"));assertEquals("2",history.get("nextCursor"));var next=service.history(actor(),meeting,1,2L);assertEquals(false,next.get("hasMore"));assertEquals(raw,((Map<?,?>)((List<?>)next.get("items")).getFirst()).get("id"));assertEquals(0,count.get());
 }
 @Test void markInsertedBetweenSnapshotAndFinalCannotEscapeCompleteSetComparison()throws Exception {
  String raw=raw();var composed=ama.api.Task06RuntimeProbe.compose(app);var ids=j.queryForList("SELECT segment_id FROM transcript_segment WHERE revision_id=? ORDER BY ordinal",String.class,raw);var saved=composed.service().mutate(actor(),meeting,"saved",new Command(Operation.SAVE,1,raw,ids.stream().map(id->new Edit(id,"Guard😀",List.of())).toList()),UUID.randomUUID().toString());String working=(String)((Map<?,?>)saved.get("result")).get("revisionId");
  var change=new java.util.concurrent.atomic.AtomicBoolean();var tx=app.getBean(ama.db.TransactionRunner.class);var access=app.getBean(ama.access.MeetingAccess.class);
  java.util.function.Supplier<ama.config.ConfigArchiveSource.Snapshot> archives=()->{if(change.compareAndSet(true,false))tx.required(()->{access.require(access.lock(actor(),meeting),ama.access.MeetingAccess.Action.EDIT);j.update("INSERT INTO transcript_segment_mark(revision_id,segment_id,mark) VALUES (?,?,'KEY')",working,ids.getFirst());return null;});return settings().archives().get();};
  var reader=new ama.transcript.JdbcSavedTranscripts(ama.api.Task05RuntimeProbe.components(app).authority(),archives,new ama.transcript.TranscriptAcquisitionBudget(16777216,16777216,16777216,67108864,67108864,33554432,128,256,64),composed.correction());change.set(true);
  assertEquals(ama.contract.MinutesContracts.FailureCode.PROVENANCE_UNAVAILABLE,assertThrows(ama.contract.MinutesContracts.Failure.class,()->reader.readOwner(actor(),meeting,working)).code());
  tx.required(()->{access.require(access.lock(actor(),meeting),ama.access.MeetingAccess.Action.EDIT);j.update("DELETE FROM transcript_segment_mark WHERE revision_id=? AND segment_id=? AND mark='KEY'",working,ids.getFirst());return null;});
 }
}
