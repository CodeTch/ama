package ama.api;
import ama.contract.GovernanceContracts.ModelConfig;
import ama.contract.ConfigArchiveContracts.ArtifactType;
import ama.config.file.ControlledConfigArchiveSource;
import ama.governance.GovernanceCatalog;
import ama.transcript.ConfiguredMinutes;
import org.springframework.core.env.Environment;
import java.nio.file.Path;
/** Publication verifies the same immutable archives that new minutes tasks will load. */
final class GovernanceRuntimeAvailability implements java.util.function.Consumer<ModelConfig> {
 private final Environment env;
 GovernanceRuntimeAvailability(Environment env){this.env=env;}
 @Override public void accept(ModelConfig config){
  GovernanceCatalog.require(config);if(!env.getProperty("minutes.enabled",Boolean.class,false))throw new IllegalStateException("Minutes runtime is not installed");
  var budget=ConfiguredMinutes.archiveBudget(env);var snapshot=new ControlledConfigArchiveSource().open(Path.of(env.getRequiredProperty("minutes.archive-root")),env.getRequiredProperty("minutes.index-sha"),ConfiguredMinutes.indexBudget(env),budget,()->false);
  var installed=snapshot.index().entries().stream().map(e->e.ref()).filter(r->r.artifactType()==ArtifactType.PROMPT_CONFIG).map(r->r.version()).collect(java.util.stream.Collectors.toSet());
  String version=GovernanceCatalog.installedPromptVersion(env.getRequiredProperty("minutes.prompt-sha"),config.promptVersion(),installed);var refs=snapshot.index().entries().stream().map(e->e.ref()).filter(r->r.artifactType()==ArtifactType.PROMPT_CONFIG&&r.version().equals(version)).toList();if(refs.size()!=1)throw new IllegalStateException("Governed prompt is not installed");snapshot.loadClosure(refs,budget,0,()->false);
 }
}
