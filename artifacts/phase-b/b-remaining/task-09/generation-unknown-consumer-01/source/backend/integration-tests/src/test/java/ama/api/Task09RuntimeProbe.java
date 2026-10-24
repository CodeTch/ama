package ama.api;
import ama.transcript.*;import ama.task.*;import org.springframework.context.ConfigurableApplicationContext;import java.util.Map;
/** Noninstalled source-first composition probe. Does not replace or register the HTTP runtime bean. */
public final class Task09RuntimeProbe {
 public static JdbcTranscriptAuthority.Components components(ConfigurableApplicationContext app){return app.getBean(ProcessingConfiguration.Runtime.class).components();}
 public record Composed(JdbcSavedTranscripts service,CorrectionInstallation correction,Map<String,Object> qualification) {}
 public static Composed compose(ConfigurableApplicationContext app){var runtime=TranscriptConfiguration.assemble(app.getBean(ProcessingConfiguration.Runtime.class),app.getBean(javax.sql.DataSource.class),app.getBean(JdbcSubjectFences.class),app.getBean(JdbcOutbox.class),app.getEnvironment(),app);return new Composed(runtime.service(),runtime.correction(),runtime.qualification());}
 public static Composed actual(ConfigurableApplicationContext app){var runtime=app.getBean(TranscriptConfiguration.Runtime.class);return new Composed(runtime.service(),runtime.correction(),runtime.qualification());}
 public static boolean installed(ConfigurableApplicationContext app){return app.getBean(TranscriptConfiguration.Runtime.class).service()!=null;}
}
