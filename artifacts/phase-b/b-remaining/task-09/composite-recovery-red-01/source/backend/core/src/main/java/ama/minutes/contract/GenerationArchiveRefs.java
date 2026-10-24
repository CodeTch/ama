package ama.minutes.contract;
import ama.canonical.CanonicalValueCodec;
import ama.config.*;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.ConfigArchiveContracts.Ref;
import ama.contract.MinutesContracts.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
import static ama.config.WireValues.*;
/** Exact original root selection for one computation. Roots are data, never provenance authority. */
public final class GenerationArchiveRefs {
 private final StoredFactsIndex facts;private final ConfigArchiveIndex index;private final ProcessingManifestReader parser;private final ArchiveReadState state;private final Map<Identity,Ref> roots=new HashMap<>();
 public GenerationArchiveRefs(StoredProvenanceFacts facts,ConfigArchiveIndex index,ProcessingManifestReader parser,ArchiveReadBudget read,GraphBudget graph,BooleanSupplier cancelled){this.facts=new StoredFactsIndex(facts,graph,cancelled);this.index=index;this.parser=parser;state=new ArchiveReadState(read,cancelled);}
 public List<Ref> binding(String input){generation(facts.one(Table.GENERATION_INPUT,input,PROVENANCE_UNAVAILABLE));return sorted();}
 public List<Ref> generation(String input){var row=facts.one(Table.GENERATION_INPUT,input,PROVENANCE_UNAVAILABLE);generation(row);set(row.nullableString("element_set_id"),row.string("meeting_id"));return sorted();}
 public List<Ref> draft(GenerationDraft draft){add(find(ArtifactType.PROMPT_CONFIG,draft.promptVersion()));add(find(ArtifactType.MODEL_CONFIG,draft.modelVersion()));confirmation(draft.transcriptConfirmationId());set(draft.elementSetId(),draft.meetingId());return sorted();}
 public List<Ref> review(ReviewFacts review,String profile){add(find(ArtifactType.REVIEW_HASH,profile));String set=review.elementId()==null?null:facts.one(Table.MINUTES_ELEMENT,review.elementId(),PROVENANCE_UNAVAILABLE).string("set_id");var graph=facts.sources.graph(new GraphSelection(review.meetingId(),review.minutesRevisionId(),set,null,null),new HashSet<>(review.selectedEvidenceIds()));walk(graph,review.meetingId());return sorted();}
 public List<Ref> finalSources(String id){var row=facts.one(Table.FINAL_CONFIRMATION,id,PROVENANCE_UNAVAILABLE);var body=facts.one(Table.MINUTES_REVISION,row.string("minutes_revision_id"),PROVENANCE_UNAVAILABLE);String input=body.string("generation_input_id");generation(facts.one(Table.GENERATION_INPUT,input,PROVENANCE_UNAVAILABLE));for(var member:facts.matching(Table.FINAL_RESULT_REVIEW,"confirmation_id",id))add(find(ArtifactType.REVIEW_HASH,facts.one(Table.RESULT_REVIEW,member.string("review_id"),PROVENANCE_UNAVAILABLE).string("hash_profile")));var graph=facts.sources.graph(new GraphSelection(row.string("meeting_id"),body.string("id"),row.string("element_set_id"),row.string("exclusion_revision_id"),input),null);facts.sources.walkInputs(List.of(input),row.string("meeting_id"));walk(graph,row.string("meeting_id"));return sorted();}
 private void set(String set,String meeting){if(set!=null)walk(facts.sources.graph(new GraphSelection(meeting,null,set,null,null),null),meeting);}
 private void walk(SourceGraph graph,String meeting){facts.sources.walk(graph.sourceUsages(),meeting);usages(graph);for(var entry:facts.sources.inputGraphs.entrySet()){generation(facts.one(Table.GENERATION_INPUT,entry.getKey(),PROVENANCE_UNAVAILABLE));usages(entry.getValue());}}
 private void usages(SourceGraph graph){for(var usage:graph.sourceUsages()){check(state.cancelled);if(usage.sourceVersion() instanceof TranscriptVersion t)confirmation(t.confirmationId());}}
 private void generation(FullRow input){add(find(ArtifactType.PROMPT_CONFIG,input.string("prompt_version")));add(find(ArtifactType.MODEL_CONFIG,input.string("model_config_version")));confirmation(input.string("transcript_confirmation_id"));}
 private void confirmation(String id){var confirmation=facts.one(Table.TRANSCRIPT_CONFIRMATION,id,PROVENANCE_UNAVAILABLE);var revision=facts.one(Table.TRANSCRIPT_REVISION,confirmation.string("revision_id"),PROVENANCE_UNAVAILABLE);var processing=facts.one(Table.TRANSCRIPTION_INPUT,revision.string("processing_input_id"),PROVENANCE_UNAVAILABLE);byte[] bytes=processing.string("config_manifest").getBytes(java.nio.charset.StandardCharsets.UTF_8);var manifest=fields(parser.read(state.capture(bytes,true),state),"schemaVersion","profile","meetingId","inputId","kind","batch","sourceRevisionId","materials","configs","base","steps");var configs=fields(manifest.get("configs"),"processing","parser","model","prompt","profiles");var ref=ref(configs.get("processing"));require(ref.artifactType()==ArtifactType.PROCESSING_CONFIG&&ref.version().equals(processing.string("processing_config_version")),PROFILE_UNAVAILABLE);add(ref);}
 private Ref find(ArtifactType type,String version){var found=index.entries().stream().map(ConfigArchiveIndex.Entry::ref).filter(r->r.artifactType()==type&&r.version().equals(version)).toList();require(found.size()==1,PROFILE_UNAVAILABLE);return found.getFirst();}
 private void add(Ref ref){check(state.cancelled);require(index.entries().stream().filter(e->e.ref().equals(ref)).count()==1,PROFILE_UNAVAILABLE);var old=roots.putIfAbsent(ref.identity(),ref);require(old==null||old.equals(ref),PROFILE_UNAVAILABLE);require(roots.size()<=state.budget.maxArtifacts(),BUDGET_EXCEEDED);}
 private List<Ref> sorted(){return roots.values().stream().sorted((a,b)->{int type=a.artifactType().name().compareTo(b.artifactType().name());return type!=0?type:CanonicalValueCodec.compareUtf8(a.version(),b.version(),state.cancelled);}).toList();}
}
