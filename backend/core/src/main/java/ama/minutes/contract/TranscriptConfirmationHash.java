package ama.minutes.contract;
import ama.contract.MinutesContracts.*;
import ama.config.ConfigBindings;
import ama.contract.ConfigArchiveContracts.*;
import ama.canonical.CanonicalValue;
import java.util.*;
import java.util.function.BooleanSupplier;
import static ama.canonical.CanonicalValue.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

public final class TranscriptConfirmationHash {
 public static final String PROFILE="ama.transcript-confirmation.v1";
 private final ArchivedProfileResolver resolver;
 public TranscriptConfirmationHash(){this(ArchivedProfileResolver.unavailable());}
 public TranscriptConfirmationHash(ConfigBindings bindings,List<RawArchive> archives,ArchiveReadBudget budget){this(ArchivedProfileResolver.configured(bindings,archives,budget));}
 TranscriptConfirmationHash(ArchivedProfileResolver resolver){require(resolver!=null,INVALID_ARGUMENT);this.resolver=resolver;}
 /** Projection only: caller must use hash() for the raw archive/profile-chain prerequisite. */
 public CanonicalValue data(ConfirmationFacts facts,GraphBudget budget,BooleanSupplier cancelled){
  return data(facts.confirmationId(),new StoredFactsIndex(facts.stored(),budget,cancelled));
 }
 static CanonicalValue data(String confirmationId,StoredFactsIndex index){
  var c=index.one(Table.TRANSCRIPT_CONFIRMATION,confirmationId,PROVENANCE_UNAVAILABLE);String meeting=c.string("meeting_id");
  var revision=index.one(Table.TRANSCRIPT_REVISION,c.string("revision_id"),PROVENANCE_UNAVAILABLE);index.meeting(revision,meeting);
  var segments=new ArrayList<>(index.matching(Table.TRANSCRIPT_SEGMENT,"revision_id",revision.string("id")));
  segments.sort(Comparator.comparingLong(r->r.number("ordinal")));require(!segments.isEmpty(),INPUT_INVALID);
  var session=index.sources;var output=new ArrayList<CanonicalValue>();
  for(var segment:segments){
   check(index.cancelled);session.transcript(c,segment.string("segment_id"),meeting);
   var marks=new ArrayList<Mark>();for(var mark:index.matching(Table.TRANSCRIPT_SEGMENT_MARK,"revision_id",revision.string("id"))){
    check(index.cancelled);if(mark.string("segment_id").equals(segment.string("segment_id")))marks.add(Mark.valueOf(mark.string("mark")));
   }marks.sort(Comparator.naturalOrder());require(new HashSet<>(marks).size()==marks.size(),SOURCE_MEMBERSHIP_MISMATCH);
   var values=new ArrayList<CanonicalValue>();for(var mark:marks){check(index.cancelled);values.add(string(mark.name()));}
   output.add(segment(segment,new Sequence(values),true));
  }
  check(index.cancelled);return object(field("meetingId",string(meeting)),field("revisionId",string(revision.string("id"))),field("batchId",string(revision.string("batch_id"))),field("segments",new Sequence(output)));
 }
 static CanonicalValue segment(FullRow row,CanonicalValue marks,boolean body){
  var fields=new ArrayList<Entry>();fields.add(field("segmentId",string(row.string("segment_id"))));fields.add(field("ordinal",number(row.number("ordinal"))));
  if(body)fields.add(field("body",string(row.string("body"))));
  fields.add(field("sourceFileId",string(row.string("source_file_id"))));fields.add(field("sourceParagraph",ContractValues.nullable(row.nullableNumber("source_paragraph"))));
  fields.add(field("startMs",ContractValues.nullable(row.nullableNumber("start_ms"))));fields.add(field("endMs",ContractValues.nullable(row.nullableNumber("end_ms"))));
  fields.add(field("speakerId",ContractValues.nullable(row.nullableString("speaker_id"))));fields.add(field("marks",marks));return new ObjectValue(fields);
 }
 public String hash(ConfirmationFacts facts,Artifact profile,GraphBudget budget,BooleanSupplier cancelled){
  var operation=resolver.open(facts.stored(),cancelled);var index=new StoredFactsIndex(facts.stored(),budget,cancelled);String result=new TranscriptConfirmationHash(operation.resolver()).hashBound(facts.confirmationId(),profile,index);operation.finish();check(cancelled);return result;
 }
 String hashBound(String confirmationId,Artifact profile,StoredFactsIndex index){
  var budget=index.budget;var cancelled=index.cancelled;var c=index.one(Table.TRANSCRIPT_CONFIRMATION,confirmationId,PROVENANCE_UNAVAILABLE);
  var revision=index.one(Table.TRANSCRIPT_REVISION,c.string("revision_id"),PROVENANCE_UNAVAILABLE);
  var processing=index.one(Table.TRANSCRIPTION_INPUT,revision.string("processing_input_id"),PROFILE_UNAVAILABLE);
  index.meeting(processing,c.string("meeting_id"));require(processing.string("batch_id").equals(revision.string("batch_id")),PROFILE_UNAVAILABLE);
  var bound=resolver.confirmation(processing,index.facts,budget,cancelled);
  require(profile!=null&&profile.equals(bound),PROFILE_UNAVAILABLE);
  return DomainHash.hash(profile,PROFILE,"TRANSCRIPT_CONFIRMATION",data(confirmationId,index),minimum(budget.canonical(),resolver.processingBudget(processing,cancelled)),cancelled);
 }

 static CanonicalBudget minimum(CanonicalBudget a,CanonicalBudget b){return new CanonicalBudget(Math.min(a.maxEncodedBytes(),b.maxEncodedBytes()),Math.min(a.maxDepth(),b.maxDepth()),Math.min(a.maxNodes(),b.maxNodes()),Math.min(a.maxStringBytes(),b.maxStringBytes()),Math.min(a.maxContainerEntries(),b.maxContainerEntries()));}
}
