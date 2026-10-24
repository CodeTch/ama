package ama.transcript;
import ama.config.*;
import ama.config.json.StrictConfigArchiveReader;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.ConfigArchiveContracts.Ref;
import ama.contract.MinutesContracts.*;
import java.util.*;
import static ama.config.WireValues.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
/** Immutable admission from every original processing closure in the selected pinned catalog. */
public record SavedTranscriptAdmission(String indexSha,CanonicalBudget canonical,List<Map<String,Object>> originals) {
 public SavedTranscriptAdmission {hash(indexSha);Objects.requireNonNull(canonical);originals=List.copyOf(originals);}
 public static SavedTranscriptAdmission capture(ConfigArchiveSource.Snapshot snapshot,ArchiveReadBudget read,CanonicalBudget runtime){
  if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Catalog admission requires no ambient transaction");
  var roots=snapshot.index().entries().stream().map(ConfigArchiveIndex.Entry::ref).filter(r->r.artifactType()==ArtifactType.PROCESSING_CONFIG).toList();require(!roots.isEmpty()&&roots.size()<=read.maxArtifacts(),PROFILE_UNAVAILABLE);
  var raw=snapshot.loadClosure(roots,read,0,()->false);var state=new ArchiveReadState(read,()->false);var originals=new ArrayList<Map<String,Object>>();var canonical=runtime;var refs=new HashSet<Ref>();
  for(var archive:raw){
   var owned=state.capture(archive.bytes(),false);var parsed=new StrictConfigArchiveReader().read(owned,archive.claimedIdentity(),state);var ref=new Ref(parsed.identity().artifactType(),parsed.identity().version(),parsed.rawSha256());require(refs.add(ref),PROFILE_UNAVAILABLE);
   var entries=snapshot.index().entries().stream().filter(e->e.ref().equals(ref)).toList();require(entries.size()==1&&entries.getFirst().sizeBytes()==archive.bytes().length,PROFILE_UNAVAILABLE);
   var item=new LinkedHashMap<String,Object>();item.put("artifactType",ref.artifactType().name());item.put("version",ref.version());item.put("sha256",ref.sha256());item.put("bytes",entries.getFirst().sizeBytes());
   String text=new String(archive.bytes(),java.nio.charset.StandardCharsets.UTF_8);item.put("utf16",(long)text.length());item.put("scalars",(long)text.codePointCount(0,text.length()));
   var dependencies=ConfigSchema.dependencies(parsed.wire(),state);state.dependencies(dependencies.size());item.put("dependencies",dependencies);
   if(ref.artifactType()==ArtifactType.PROCESSING_CONFIG){
    var envelope=fields(parsed.wire(),"archiveSchema","artifactType","version","definitionSchema","definition","dependencies");var definition=fields(envelope.get("definition"),"contract","manifestSchemaVersion","parseBudget","canonicalBudget");
    var original=ConfigSchema.canonicalBudget(definition.get("canonicalBudget"));canonical=minimum(canonical,original);item.put("originalCanonical",original);item.put("originalParser",ConfigSchema.parseBudget(definition.get("parseBudget")));
   }
   originals.add(Collections.unmodifiableMap(item));
  }
  for(var item:originals)for(var dependency:((Map<?,?>)item.get("dependencies")).values())require(refs.contains((Ref)dependency),PROFILE_UNAVAILABLE);
  require(refs.containsAll(roots),PROFILE_UNAVAILABLE);return new SavedTranscriptAdmission(snapshot.indexSha256(),canonical,originals);
 }
 public static CanonicalBudget minimum(CanonicalBudget a,CanonicalBudget b){return new CanonicalBudget(Math.min(a.maxEncodedBytes(),b.maxEncodedBytes()),Math.min(a.maxDepth(),b.maxDepth()),Math.min(a.maxNodes(),b.maxNodes()),Math.min(a.maxStringBytes(),b.maxStringBytes()),Math.min(a.maxContainerEntries(),b.maxContainerEntries()));}
}
