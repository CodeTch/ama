package ama.config.json;
import ama.contract.MinutesContracts.*;
import ama.contract.ConfigArchiveContracts.*;
import ama.minutes.contract.TranscriptConfirmationHash;
import java.util.*;
import org.junit.jupiter.api.Test;
import static ama.config.json.ConfigBindingsTest.*;
import static ama.config.json.RawHashChainTest.*;
import static org.junit.jupiter.api.Assertions.*;

class DraftConfirmationHashTest {
 @Test void persistedWorkingWithoutConfirmationUsesOriginalArchiveGolden() throws Exception {
  var confirmed=beforeGeneration();
  var saved=new StoredProvenanceFacts(confirmed.rows().stream().filter(r->r.table()!=Table.TRANSCRIPT_CONFIRMATION).toList(),confirmed.files(),List.of(),List.of(),confirmed.taskSpecs(),100000);
  var helper=new TranscriptConfirmationHash(service(),archives(),B);
  assertEquals(resource("full-confirmation.sha256"),helper.hashDraft(new ConfirmationDraft(MEETING,R,saved),profile(archives(),ArtifactType.CONFIRMATION_HASH),GRAPH,()->false));
  assertEquals(0,saved.rows().stream().filter(r->r.table()==Table.TRANSCRIPT_CONFIRMATION).count());
 }
 @Test void completeMarksAndPermutationMatchIndependentManualGolden()throws Exception {
  var p=beforeGeneration();var rows=new ArrayList<>(p.rows());rows.removeIf(r->r.table()==Table.TRANSCRIPT_CONFIRMATION);
  rows.add(row(Table.TRANSCRIPT_REVISION,uid(54),MEETING,2L,"WORKING",BATCH,R,expected(),"human",TIME,INPUT,null));
  rows.add(row(Table.TRANSCRIPT_SEGMENT,uid(54),uid(55),1L,"manual\n",FILE,1L,null,null,null));for(var mark:Mark.values())rows.add(row(Table.TRANSCRIPT_SEGMENT_MARK,uid(54),uid(55),mark.name()));Collections.reverse(rows);
  var facts=new StoredProvenanceFacts(rows,p.files(),List.of(),List.of(),p.taskSpecs(),100000L);var originals=new ArrayList<>(archives());Collections.reverse(originals);
  assertEquals(resource("full-manual-confirmation.sha256"),new TranscriptConfirmationHash(service(),originals,B).hashDraft(new ConfirmationDraft(MEETING,uid(54),facts),profile(originals,ArtifactType.CONFIRMATION_HASH),GRAPH,()->false));
 }
 @Test void missingOrCorruptOriginalAndCancellationNeverProduceDraftHash()throws Exception {
  var p=beforeGeneration();var rows=p.rows().stream().filter(r->r.table()!=Table.TRANSCRIPT_CONFIRMATION).toList();var facts=new StoredProvenanceFacts(rows,p.files(),List.of(),List.of(),p.taskSpecs(),100000L);var draft=new ConfirmationDraft(MEETING,R,facts);var profile=profile(archives(),ArtifactType.CONFIRMATION_HASH);
  for(var missing:ArtifactType.values())if(Set.of(ArtifactType.PROCESSING_CONFIG,ArtifactType.PARSER_CONFIG,ArtifactType.CANONICAL_CODEC,ArtifactType.CONFIRMATION_HASH).contains(missing)){var raw=archives().stream().filter(a->a.claimedArtifactType()!=missing).toList();assertThrows(Failure.class,()->new TranscriptConfirmationHash(service(),raw,B).hashDraft(draft,profile,GRAPH,()->false));}
  var corrupt=archives();corrupt.getFirst().bytes()[0]='!';assertThrows(Failure.class,()->new TranscriptConfirmationHash(service(),corrupt,B).hashDraft(draft,profile,GRAPH,()->false));
  assertEquals(ama.contract.MinutesContracts.FailureCode.CANCELLED,assertThrows(Failure.class,()->new TranscriptConfirmationHash(service(),archives(),B).hashDraft(draft,profile,GRAPH,()->true)).code());
 }
}
