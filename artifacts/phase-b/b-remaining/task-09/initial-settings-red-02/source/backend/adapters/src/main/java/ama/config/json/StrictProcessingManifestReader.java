package ama.config.json;
import ama.config.*;
import ama.contract.ConfigArchiveContracts.*;
import ama.canonical.CanonicalValue;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
public final class StrictProcessingManifestReader implements ProcessingManifestReader {
 public CanonicalValue.ObjectValue read(ArchiveBytes bytes,ArchiveReadState state){
  var value=StrictConfigJson.parse(bytes,state);ConfigSchema.manifest(value,state);
  require(CanonicalJson.matches(value,bytes,state.cancelled),INVALID_SCHEMA);return value;
 }
}
