package ama.worker;
import ama.storage.*;
import ama.storage.internal.ServiceTls;
import ama.transcript.ConfiguredProcessing;
import java.nio.file.*;
import java.net.*;
import java.util.Properties;
import org.springframework.core.env.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
/** Explicit independent TXT process; legacy ServiceLoader entry remains unchanged. */
public final class TranscriptWorkerMain {
 private TranscriptWorkerMain(){}
 public static void main(String[] args)throws Exception{
  if(args.length!=1)throw new IllegalArgumentException("One private worker configuration path required");
  Path path=Path.of(args[0]);if(Files.isSymbolicLink(path)||!Files.isRegularFile(path)||Files.size(path)>65536)throw new IllegalArgumentException("Invalid worker configuration");
  var properties=new Properties();try(var in=Files.newInputStream(path)){properties.load(in);}
  var env=new StandardEnvironment();env.getPropertySources().addFirst(new PropertiesPropertySource("worker",properties));
  var source=new DriverManagerDataSource(env.getRequiredProperty("database.url"),env.getRequiredProperty("database.user"),env.getRequiredProperty("database.password"));
  var settings=ConfiguredProcessing.read(env);var definitions=ConfiguredBackends.read(env);
  var provider=new TranscriptWorkerRuntimeProvider(source,definitions,settings.read(),settings.canonical(),settings.preparation(),env.getRequiredProperty("worker.id"),env.getRequiredProperty("worker.capacity",Integer.class));
  var runtime=(TranscriptWorkerRuntimeProvider.Runtime)provider.create();
  try(runtime){
  var tls=ServiceTls.context(Path.of(env.getRequiredProperty("worker.health.key-store")),env.getRequiredProperty("worker.health.key-store-password").toCharArray(),Path.of(env.getRequiredProperty("worker.health.trust-store")),env.getRequiredProperty("worker.health.trust-store-password").toCharArray());
  try(var health=new WorkerHealthServer(new InetSocketAddress("127.0.0.1",env.getRequiredProperty("worker.health.port",Integer.class)),tls,env.getRequiredProperty("worker.health.peer"),runtime::observation)){
   health.start();System.getLogger(TranscriptWorkerMain.class.getName()).log(System.Logger.Level.INFO,"TXT worker management bound to loopback port "+health.port());WorkerMain.run(runtime);
  }
  }
 }
}
