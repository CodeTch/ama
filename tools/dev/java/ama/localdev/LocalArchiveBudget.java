package ama.localdev;
import ama.contract.ConfigArchiveContracts.ArchiveReadBudget;
import ama.minutes.ControlledMinutesAdmin;
/** Explicit local catalog growth: the full retained release now has 258 dependency edges.
 * Only the edge budget changes; bytes, artifacts, depth and every other bound stay fixed.
 */
final class LocalArchiveBudget {
 static final ArchiveReadBudget READ=read();
 private static ArchiveReadBudget read(){var b=ControlledMinutesAdmin.READ;return new ArchiveReadBudget(b.maxArtifactBytes(),b.maxTotalBytes(),b.maxArtifacts(),384,b.maxGraphDepth(),b.maxTokens(),b.maxJsonDepth(),b.maxNameUtf16(),b.maxStringUtf16(),b.maxStringUtf8Bytes(),b.maxAggregateScalars(),b.maxManifestBytes(),b.maxBatchFiles(),b.maxFactRows(),b.maxFactEdges());}
 private LocalArchiveBudget(){}
}
