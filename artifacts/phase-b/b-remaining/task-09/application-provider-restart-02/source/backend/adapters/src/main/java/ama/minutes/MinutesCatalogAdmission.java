package ama.minutes;
import ama.config.*;
import ama.config.json.StrictConfigArchiveReader;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.ConfigArchiveContracts.Ref;
import ama.canonical.CanonicalValue;
import java.util.*;
import static ama.config.WireValues.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
/** Strict configuration-only catalog inspection; no synthetic generation input or business digest. */
public final class MinutesCatalogAdmission {
 private MinutesCatalogAdmission(){}
 public record Selection(String indexSha,String defaultTemplateRevisionId,Ref prompt,Ref model,List<RawArchive> archives) {public Selection{archives=List.copyOf(archives);}}
 public static Selection capture(ConfigArchiveSource.Snapshot snapshot,Ref prompt,Ref model,ArchiveReadBudget budget){
  if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Archive inspection requires no transaction");
  require(prompt.artifactType()==ArtifactType.PROMPT_CONFIG&&model.artifactType()==ArtifactType.MODEL_CONFIG,PROFILE_UNAVAILABLE);
  var raw=snapshot.loadClosure(List.of(prompt,model),budget,0,()->false);require(raw.size()==17,PROFILE_UNAVAILABLE);var state=new ArchiveReadState(budget,()->false);var definitions=new HashMap<Ref,CanonicalValue>();var dependencies=new HashMap<Ref,Map<String,Ref>>();
  for(var archive:raw){var parsed=new StrictConfigArchiveReader().read(state.capture(archive.bytes(),false),archive.claimedIdentity(),state);var ref=new Ref(parsed.identity().artifactType(),parsed.identity().version(),parsed.rawSha256());require(snapshot.index().entries().stream().filter(e->e.ref().equals(ref)&&e.sizeBytes()==archive.bytes().length).count()==1,PROFILE_UNAVAILABLE);var envelope=fields(parsed.wire(),"archiveSchema","artifactType","version","definitionSchema","definition","dependencies");require(definitions.put(ref,envelope.get("definition"))==null,PROFILE_UNAVAILABLE);dependencies.put(ref,ConfigSchema.dependencies(parsed.wire(),state));}
  var slots=dependencies.get(prompt);require(slots!=null&&slots.size()==15&&slots.equals(dependencies.get(model)),PROFILE_UNAVAILABLE);Ref codec=slots.get("CANONICAL_CODEC");
  for(var entry:slots.entrySet()){var ref=entry.getValue();require(ref.artifactType().name().equals(entry.getKey())&&definitions.containsKey(ref),PROFILE_UNAVAILABLE);require(dependencies.get(ref).equals(ref.equals(codec)?Map.of():Map.of("CANONICAL_CODEC",codec)),PROFILE_UNAVAILABLE);}
  var md=fields(definitions.get(model),"protocolVersion","modelIdentity","credentialRef","parameters");literal(md.get("protocolVersion"),"ama.controlled-minutes-http.v1");literal(md.get("modelIdentity"),"CONTROLLED_MINUTES_V1");nil(md.get("credentialRef"));var params=fields(md.get("parameters"),"temperatureMicros","maxOutputTokens","seed");require(number(params.get("temperatureMicros"))==0&&number(params.get("maxOutputTokens"))==4096&&number(params.get("seed"))==0,PROFILE_UNAVAILABLE);
  var render=fields(definitions.get(slots.get("RENDER")),"contract","rendererVersion","defaultTemplateRevisionId");literal(render.get("rendererVersion"),ControlledMinutesArchives.RENDERER);String template=text(render.get("defaultTemplateRevisionId"));uuid(template);return new Selection(snapshot.indexSha256(),template,prompt,model,raw);
 }
}
