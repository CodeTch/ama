package ama.minutes.contract;
import ama.config.ConfigBindings;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.MinutesContracts.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Internal bridge only. No caller implementation or success constructor can assert a verified profile. */
final class ArchivedProfileResolver {
 private final ConfigBindings bindings;private final List<RawArchive> raw;private final ArchiveReadBudget budget;private final ConfigBindings.Context context;
 private ArchivedProfileResolver(ConfigBindings bindings,List<RawArchive> raw,ArchiveReadBudget budget,ConfigBindings.Context context){this.bindings=bindings;this.raw=raw;this.budget=budget;this.context=context;}
 static ArchivedProfileResolver unavailable(){return new ArchivedProfileResolver(null,List.of(),null,null);}
 static ArchivedProfileResolver configured(ConfigBindings bindings,List<RawArchive> raw,ArchiveReadBudget budget){
  require(bindings!=null&&raw!=null&&budget!=null,INVALID_ARGUMENT);require(raw.size()<=budget.maxArtifacts(),BUDGET_EXCEEDED);return new ArchivedProfileResolver(bindings,List.copyOf(raw),budget,null);
 }
 record Scope(ArchivedProfileResolver resolver,boolean owner){ConfigBindings.ValidatedOperation finish(){return owner?resolver.context.finish():null;}}
 Scope open(StoredProvenanceFacts facts,BooleanSupplier cancelled){
  check(cancelled);if(context!=null)return new Scope(this,false);if(bindings==null)return new Scope(this,false);
  return new Scope(new ArchivedProfileResolver(bindings,raw,budget,bindings.begin(facts,raw,budget,cancelled)),true);
 }
 private void available(BooleanSupplier cancelled){check(cancelled);require(context!=null,PROFILE_UNAVAILABLE);}
 Artifact confirmation(FullRow processing,StoredProvenanceFacts facts,GraphBudget ignored,BooleanSupplier cancelled){available(cancelled);require(context.facts()==facts,INPUT_INVALID);return context.confirmation(processing);}
 GenerationConfiguration generation(FullRow input,StoredProvenanceFacts facts,GraphBudget ignored,BooleanSupplier cancelled){available(cancelled);require(context.facts()==facts,INPUT_INVALID);return context.generation(input);}
 GenerationConfiguration generation(GenerationDraft draft,StoredProvenanceFacts facts,BooleanSupplier cancelled){available(cancelled);require(context.facts()==facts,INPUT_INVALID);return context.generation(draft);}
 Artifact review(FullRow review,StoredProvenanceFacts facts,GraphBudget ignored,BooleanSupplier cancelled){available(cancelled);require(context.facts()==facts,INPUT_INVALID);return context.review(review.string("hash_profile"));}
 Artifact reviewProfile(String version,BooleanSupplier cancelled){available(cancelled);return context.review(version);}
 CanonicalBudget processingBudget(FullRow input,BooleanSupplier cancelled){available(cancelled);return context.processingBudget(input);}
 CanonicalBudget generationBudget(FullRow input,BooleanSupplier cancelled){available(cancelled);return context.generationBudget(input);}

 CanonicalBudget generationBudget(GenerationDraft draft,BooleanSupplier cancelled){available(cancelled);return context.generationBudget(draft);}

 ama.canonical.CanonicalValue.ObjectValue promptDefinition(GenerationConfiguration config,BooleanSupplier cancelled){available(cancelled);return context.generationDefinition(ArtifactType.PROMPT_CONFIG,config.prompt().version());}
 ama.canonical.CanonicalValue.ObjectValue modelDefinition(GenerationConfiguration config,BooleanSupplier cancelled){available(cancelled);return context.generationDefinition(ArtifactType.MODEL_CONFIG,config.model().version());}
 ValidationBudget outputBudget(GenerationConfiguration config,BooleanSupplier cancelled){available(cancelled);return context.outputBudget(config);}
}
