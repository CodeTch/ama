package ama.minutes.contract;
import ama.contract.MinutesContracts.*;
import java.util.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Verifies every represented origin before digest success; profile binding cannot substitute for source provenance. */
final class OriginChainVerifier {
 static void verify(StoredFactsIndex index,StoredSourceGraph.Session session,SourceGraph graph,ArchivedProfileResolver resolver){
  if(!graph.sourceUsages().isEmpty())session.walk(graph.sourceUsages(),graph.locatorRows().getFirst().string("meeting_id"));
  var confirmations=new HashSet<String>();
  for(var usage:graph.sourceUsages()){check(index.cancelled);if(usage.sourceVersion() instanceof TranscriptVersion t)confirmations.add(t.confirmationId());}
  var inputs=new ArrayList<>(session.catalogs.keySet());inputs.sort(String::compareTo);
  for(var id:inputs){
   check(index.cancelled);var input=index.one(Table.GENERATION_INPUT,id,PROVENANCE_UNAVAILABLE);
   var bound=resolver.generation(input,index.facts,index.budget,index.cancelled);require(bound.equals(index.config(input)),PROFILE_UNAVAILABLE);
   confirmations.add(input.string("transcript_confirmation_id"));
   var fence=GenerationFingerprint.historicalFence(input,index.facts,index.cancelled);
   var computed=new GenerationFingerprint(resolver).recomputeStored(new GenerationFacts(id,index.facts),fence,bound.profiles(),index.budget,index.cancelled);
   require(computed.fingerprint().equals(input.string("input_fingerprint")),FINGERPRINT_MISMATCH);
  }
  for(var id:confirmations){
   check(index.cancelled);var confirmation=index.one(Table.TRANSCRIPT_CONFIRMATION,id,PROVENANCE_UNAVAILABLE);
   var revision=index.one(Table.TRANSCRIPT_REVISION,confirmation.string("revision_id"),PROVENANCE_UNAVAILABLE);var processing=index.one(Table.TRANSCRIPTION_INPUT,revision.string("processing_input_id"),PROFILE_UNAVAILABLE);
   var profile=resolver.confirmation(processing,index.facts,index.budget,index.cancelled);
   String hash=new TranscriptConfirmationHash(resolver).hash(new ConfirmationFacts(id,index.facts),profile,index.budget,index.cancelled);
   require(hash.equals(confirmation.string("content_hash")),FINGERPRINT_MISMATCH);
  }
  check(index.cancelled);
 }
}
