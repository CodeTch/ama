package ama.config.json;
import ama.config.*;
import ama.contract.ConfigArchiveContracts.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
public final class StrictConfigArchiveReader implements ConfigArchiveReader {
 public ParsedArchive read(ArchiveBytes bytes,Identity expected,ArchiveReadState state){
  var value=StrictConfigJson.parse(bytes,state);var identity=ConfigSchema.archive(value,state);require(identity.equals(expected),PROFILE_UNAVAILABLE);
  require(CanonicalJson.matches(value,bytes,state.cancelled),INVALID_SCHEMA);
  return new ParsedArchive(identity,bytes.sha256(state.cancelled),value);
 }
}
