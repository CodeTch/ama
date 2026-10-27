package ama.transcript;
import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.Actor;
import ama.contract.MinutesContracts.*;
import ama.task.*;
import java.util.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
/** Atomic enqueue primitive. Only the combined installed runtime exposes new generation commands. */
public final class JdbcMinutesGenerationCommands {
 private final JdbcTranscriptAuthority authority;private final JdbcMinutesGenerationPreparation preparation;private final TaskQueue queue;private final OutboxPort outbox;private final ama.audit.AuditPort audit;private final JdbcMinutesGenerationReceipts receipts;
 public JdbcMinutesGenerationCommands(JdbcTranscriptAuthority authority,JdbcMinutesGenerationPreparation preparation,TaskQueue queue,OutboxPort outbox,ama.audit.AuditPort audit){this.authority=Objects.requireNonNull(authority);this.preparation=Objects.requireNonNull(preparation);this.queue=Objects.requireNonNull(queue);this.outbox=Objects.requireNonNull(outbox);this.audit=Objects.requireNonNull(audit);receipts=new JdbcMinutesGenerationReceipts(authority.dataSource(),authority.transactionRunner(),authority.savedAccess());}
 void requireQueue(ama.task.JdbcTaskQueue expected){if(queue!=expected)throw new IllegalArgumentException("Different publication/generation queue");}
 void requireConfiguration(JdbcTranscriptAuthority expected,java.util.function.Supplier<ama.config.ConfigArchiveSource.Snapshot> archives,String pin,ama.contract.ConfigArchiveContracts.Ref prompt,ama.contract.ConfigArchiveContracts.Ref model,TranscriptAcquisitionBudget limits){if(authority!=expected||!(queue instanceof ama.task.JdbcTaskQueue actual))throw new IllegalArgumentException("Foreign generation assembly");preparation.requireConfiguration(expected,archives,pin,prompt,model,limits);ama.task.TranscriptImportRuntime.requireCombined(actual,expected);}
 /** Read-only prospective S/A/F; never inserts the returned input or consumes a write scope. */
 void verifyCurrentReadiness(Actor actor,String meeting,long expected,TranscriptAcquisitionBudget.Ledger budget){preparation.prepare(actor,meeting,expected,budget);}
 public Map<String,Object> enqueue(Actor actor,String meeting,String key,long expected,String trace){
  JdbcMinutesFactsReader.independent();uuid(trace);var id=JdbcMinutesGenerationReceipts.identity(actor,meeting,key,expected);var replay=receipts.lookup(actor,meeting,id,expected);if(replay.isPresent())return replay.orElseThrow();JdbcMinutesGenerationPreparation.Prepared prepared;
  try{prepared=preparation.prepareForCommand(actor,meeting,expected);}catch(RuntimeException failure){if(Thread.currentThread().isInterrupted()||!ordinary(failure))throw failure;var winner=receipts.lookup(actor,meeting,id,expected);if(winner.isPresent())return winner.orElseThrow();throw failure;}
  return authority.transactionRunner().required(()->{
   var stored=receipts.claim(actor,meeting,id);var proof=stored.task()==null?null:receipts.observe(meeting,stored,expected);var gate=stored.task()==null?preparation.lockPrepared(actor,meeting,prepared):authority.fileRegistry().lock(actor,meeting,MeetingAccess.Action.EDIT);JdbcMinutesGenerationReceipts.match(stored);if(stored.task()!=null)return receipts.restore(meeting,stored,proof,true);
   var held=preparation.insert(actor,gate,prepared);String task=queue.enqueue(prepared.spec(),held);receipts.accepted(id,task);var response=receipts.written(meeting,task,prepared.spec());
   audit.append(new ama.contract.AuditContracts.AuditEvent(actor.enterpriseId(),"MINUTES_GENERATION_ACCEPTED",meeting,expected,"SUCCEEDED",trace,java.time.Instant.now()));outbox.append(new ama.contract.OutboxContracts.EventDraft("MINUTES_GENERATION_ACCEPTED",task,prepared.spec().subject(),expected,new ama.contract.OutboxContracts.Reference("GENERATION_INPUT",prepared.spec().input().id())));outbox.flush();return response;
  });
 }
 private static boolean ordinary(RuntimeException failure){return failure instanceof Failure f&&Set.of(ARCHIVE_IO_UNAVAILABLE,CAPABILITY_UNAVAILABLE,BUDGET_EXCEEDED,READ_FAILED).contains(f.code())||failure instanceof ama.contract.TranscriptContracts.Failure t&&Set.of("REVISION_CONFLICT","REVISION_EXHAUSTED").contains(t.code())||failure instanceof ama.contract.FileContracts.Unavailable||failure instanceof ama.contract.FileContracts.NotFound||failure instanceof ama.contract.FileContracts.Conflict;}
}
