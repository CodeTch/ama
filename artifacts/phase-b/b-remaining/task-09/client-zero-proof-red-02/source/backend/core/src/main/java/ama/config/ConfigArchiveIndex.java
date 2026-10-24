package ama.config;

import ama.contract.ConfigArchiveContracts.*;
import java.util.List;
import ama.canonical.CanonicalValue;
import static ama.canonical.CanonicalValue.*;

/** Immutable metadata; the protected, explicitly pinned raw index SHA is the trust anchor. */
public record ConfigArchiveIndex(long generation, String previousIndexSha256, List<Entry> entries) {
 public ConfigArchiveIndex { entries = List.copyOf(entries); }
 public record Entry(Ref ref, long sizeBytes) {}
 public CanonicalValue.ObjectValue wire(){
  var values=new java.util.ArrayList<CanonicalValue>();
  for(var e:entries)values.add(object(field("artifactType",string(e.ref.artifactType().name())),field("version",string(e.ref.version())),field("sha256",string(e.ref.sha256())),field("sizeBytes",number(e.sizeBytes))));
  return object(field("indexSchema",string("ama.config-index.v1")),field("generation",number(generation)),field("previousIndexSha256",previousIndexSha256==null?nil():string(previousIndexSha256)),field("entries",new Sequence(values)));
 }
 public record IndexBudget(long maxIndexBytes, int maxEntries, long maxReleaseBytes, int maxGenerations) {
  public IndexBudget {
   ama.contract.MinutesContracts.require(maxIndexBytes > 0 && maxIndexBytes <= 4194304 && maxEntries > 0 && maxEntries <= 4096 && maxReleaseBytes > 0 && maxReleaseBytes <= 67108864 && maxGenerations > 0 && maxGenerations <= 128, ama.contract.MinutesContracts.FailureCode.INVALID_ARGUMENT);
  }
 }
}
