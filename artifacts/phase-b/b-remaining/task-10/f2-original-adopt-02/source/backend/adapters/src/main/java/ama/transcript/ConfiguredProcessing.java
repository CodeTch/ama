package ama.transcript;
import ama.config.*;
import ama.config.file.ControlledConfigArchiveSource;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.MinutesContracts.CanonicalBudget;
import ama.task.TranscriptImportRuntime;
import org.springframework.core.env.PropertyResolver;
import java.nio.file.Path;
import java.time.Duration;
/** Explicit finite settings shared by the independent import process. */
public record ConfiguredProcessing(ArchiveReadBudget read,CanonicalBudget canonical,ConfigArchiveIndex.IndexBudget index,TranscriptImportRuntime.Settings preparation) {
 public static ConfiguredProcessing read(PropertyResolver env){
  var b=new ArchiveReadBudget(n(env,"artifact-bytes"),n(env,"archive-total-bytes"),i(env,"artifacts"),n(env,"dependencies"),i(env,"graph-depth"),n(env,"json-tokens"),i(env,"json-depth"),i(env,"name-utf16"),i(env,"string-utf16"),n(env,"string-utf8"),n(env,"aggregate-scalars"),n(env,"manifest-bytes"),i(env,"batch-files"),n(env,"fact-rows"),n(env,"fact-edges"));
  var c=new CanonicalBudget(n(env,"canonical-bytes"),i(env,"canonical-depth"),n(env,"canonical-nodes"),n(env,"canonical-string-utf8"),n(env,"canonical-scalars"));
  var ix=new ConfigArchiveIndex.IndexBudget(n(env,"index-bytes"),i(env,"index-entries"),n(env,"release-bytes"),i(env,"index-generations"));
  var aggregate=new TranscriptDocumentParser.ParseBudget(n(env,"total-source-bytes"),n(env,"total-utf16"),i(env,"total-segments"));
  var settings=new TranscriptImportRuntime.Settings(()->new ControlledConfigArchiveSource().open(Path.of(env.getRequiredProperty("processing.archive-root")),env.getRequiredProperty("processing.index-sha"),ix,b,()->false),b,aggregate,Duration.ofMillis(n(env,"batch-deadline-ms")));
  for(String key:new String[]{"lease-seconds","heartbeat-seconds","poll-seconds"})if(env.getRequiredProperty("worker."+key,Integer.class)!=(key.equals("lease-seconds")?20:5))throw new IllegalArgumentException("Unsupported worker timing profile");
  return new ConfiguredProcessing(b,c,ix,settings);
 }
 private static long n(PropertyResolver env,String name){long value=env.getRequiredProperty("processing.bounds."+name,Long.class);if(value<=0)throw new IllegalArgumentException("Positive processing bounds required");return value;}
 private static int i(PropertyResolver env,String name){return Math.toIntExact(n(env,name));}
}
