package ama.worker;
import ama.storage.*;
import ama.storage.internal.ServiceTls;
import ama.transcript.ConfiguredProcessing;
import java.nio.file.*;
import java.net.*;
import java.util.Properties;
import org.springframework.core.env.*;
import com.zaxxer.hikari.HikariDataSource;
import ama.minutes.ControlledMinutesDataSource;
/** Explicit independent TXT process; legacy ServiceLoader entry remains unchanged. */
public final class TranscriptWorkerMain {
 private TranscriptWorkerMain(){}
 public static void main(String[] args)throws Exception{
  if(args.length!=1)throw new IllegalArgumentException("One private worker configuration path required");
  Path path=Path.of(args[0]);if(Files.isSymbolicLink(path)||!Files.isRegularFile(path)||Files.size(path)>65536)throw new IllegalArgumentException("Invalid worker configuration");
  var properties=new Properties();try(var in=Files.newInputStream(path)){properties.load(in);}
  var env=new StandardEnvironment();env.getPropertySources().addFirst(new PropertiesPropertySource("worker",properties));
  // A scanner, its job and its heartbeat may all need independent connections.
  // Reuse physical JDBC sessions across polls, with the same session contract as the API.
  try(var pool=new HikariDataSource()){
   pool.setPoolName("ama-worker");
   pool.setJdbcUrl(env.getRequiredProperty("database.url"));
   pool.setUsername(env.getRequiredProperty("database.user"));
   pool.setPassword(env.getRequiredProperty("database.password"));
   pool.setMaximumPoolSize(env.getProperty("worker.database-pool-maximum",Integer.class,16));
   pool.setMinimumIdle(2);
   pool.setConnectionTimeout(5000);
   boolean controlled=env.getProperty("minutes.enabled",Boolean.class,false);
   if(controlled)pool.setDataSourceProperties(ControlledMinutesDataSource.connectionProperties());
   javax.sql.DataSource source=controlled?new ControlledMinutesDataSource(pool):pool;
  var settings=ConfiguredProcessing.read(env);var definitions=ConfiguredBackends.read(env);
  if(env.getProperty("operations.enabled",Boolean.class,false)){ama.governance.GovernanceRuntimeVoice.configure(Path.of(env.getRequiredProperty("operations.encryption-key-file")));var consentProvider=new ama.governance.GovernanceHttpProvider(java.net.URI.create(env.getRequiredProperty("operations.integration-origin")),"harness-fixture-embedding-v1",256);ama.governance.GovernanceVoiceConsent.configure(consentProvider::consent);}
  var exports=env.getProperty("exports.enabled",Boolean.class,false)?new ama.export.MinutesExportProcess(Path.of(env.getRequiredProperty("exports.work-root")),new ama.export.MinutesExportRenderer.Fonts(Path.of(env.getRequiredProperty("exports.cjk-font")),Path.of(env.getRequiredProperty("exports.unicode-font"))),java.time.Duration.ofSeconds(30)):null;
  var provider=new TranscriptWorkerRuntimeProvider(source,definitions,settings.read(),settings.canonical(),settings.preparation(),env.getRequiredProperty("worker.id"),env.getRequiredProperty("worker.capacity",Integer.class),env.getProperty("minutes.enabled",Boolean.class,false)?ama.transcript.ConfiguredMinutes.read(env,settings.read(),settings.index()):null,env.getProperty("publication.enabled",Boolean.class,false)?ama.transcript.ConfiguredPublication.read(env,settings.read(),settings.index()):null,exports);
  var runtime=(TranscriptWorkerRuntimeProvider.Runtime)provider.create();
  try(runtime){
  var tls=ServiceTls.context(Path.of(env.getRequiredProperty("worker.health.key-store")),env.getRequiredProperty("worker.health.key-store-password").toCharArray(),Path.of(env.getRequiredProperty("worker.health.trust-store")),env.getRequiredProperty("worker.health.trust-store-password").toCharArray());
  try(var health=new WorkerHealthServer(new InetSocketAddress("127.0.0.1",env.getRequiredProperty("worker.health.port",Integer.class)),tls,env.getRequiredProperty("worker.health.peer"),runtime::observation)){
   health.start();System.getLogger(TranscriptWorkerMain.class.getName()).log(System.Logger.Level.INFO,"TXT worker management bound to loopback port "+health.port());WorkerMain.run(runtime);
  }
  }
  }
 }
}
