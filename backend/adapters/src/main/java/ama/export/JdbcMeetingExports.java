package ama.export;
import ama.contract.IdentityContracts.Actor;
import ama.storage.FileRegistry;
import ama.task.*;
import java.util.Objects;
/** API facade over the installed queue and common fresh-authorized file registry. */
public final class JdbcMeetingExports {
 private final JdbcTaskQueue queue;private final FileRegistry registry;
 public JdbcMeetingExports(JdbcTaskQueue queue,FileRegistry registry){this.queue=Objects.requireNonNull(queue);this.registry=Objects.requireNonNull(registry);}
 public MinutesExportRuntime.Status accept(Actor actor,String meeting,String key,String revision,String format){return MinutesExportRuntime.accept(queue,actor,meeting,key,revision,format);}
 public MinutesExportRuntime.Status status(Actor actor,String task){return MinutesExportRuntime.status(queue,actor,task);}
 public FileRegistry.Download download(Actor actor,String task,String range){var result=status(actor,task);if(!"SUCCEEDED".equals(result.state())||result.fileId()==null)throw new ama.contract.TranscriptContracts.Failure(409,"EXPORT_NOT_AVAILABLE");return registry.download(actor,result.fileId(),range);}
}
