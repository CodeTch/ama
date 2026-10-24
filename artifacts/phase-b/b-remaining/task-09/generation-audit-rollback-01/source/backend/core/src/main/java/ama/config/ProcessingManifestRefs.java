package ama.config;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.ConfigArchiveContracts.Ref;
import java.util.List;
import java.util.function.BooleanSupplier;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
import static ama.config.WireValues.*;
import java.util.LinkedHashSet;
/** Bounded strict discovery only. ConfigBindings must still validate the original bytes and facts. */
public final class ProcessingManifestRefs {
 private final ProcessingManifestReader reader;
 public ProcessingManifestRefs(ProcessingManifestReader reader){require(reader!=null,INVALID_ARGUMENT);this.reader=reader;}
 public List<Ref> roots(byte[] original,ArchiveReadBudget b,BooleanSupplier c){
  require(b!=null&&original!=null&&c!=null,INVALID_ARGUMENT);check(c);
  require(original.length<=65536&&original.length<=b.maxManifestBytes()&&original.length<=b.maxTotalBytes(),BUDGET_EXCEEDED);
  var state=new ArchiveReadState(b,c);var owned=state.capture(original,true);
  var manifest=reader.read(owned,state);ConfigSchema.manifest(manifest,state);
  require(CanonicalJson.matches(manifest,owned,c),INVALID_SCHEMA);
  var f=fields(manifest,"schemaVersion","profile","meetingId","inputId","kind","batch","sourceRevisionId","materials","configs","base","steps");
  var configs=fields(f.get("configs"),"processing","parser","model","prompt","profiles");
  var roots=new LinkedHashSet<Ref>();roots.add(ref(configs.get("processing")));roots.add(ref(configs.get("parser")));
  for(var profile:array(configs.get("profiles")))roots.add(ref(fields(profile,"kind","ref").get("ref")));
  require(roots.size()<=b.maxArtifacts(),BUDGET_EXCEEDED);check(c);return List.copyOf(roots);
 }
}
