package ama.config;

import ama.contract.ConfigArchiveContracts.*;
import java.util.List;

/** Immutable metadata; the protected, explicitly pinned raw index SHA is the trust anchor. */
public record ConfigArchiveIndex(long generation, String previousIndexSha256, List<Entry> entries) {
 public ConfigArchiveIndex { entries = List.copyOf(entries); }
 public record Entry(Ref ref, long sizeBytes) {}
 public record IndexBudget(long maxIndexBytes, int maxEntries, long maxReleaseBytes, int maxGenerations) {
  public IndexBudget {
   ama.contract.MinutesContracts.require(maxIndexBytes > 0 && maxIndexBytes <= Integer.MAX_VALUE && maxEntries > 0 && maxReleaseBytes > 0 && maxGenerations > 0, ama.contract.MinutesContracts.FailureCode.INVALID_ARGUMENT);
  }
 }
}
