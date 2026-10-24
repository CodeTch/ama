package ama.storage;
import ama.api.*;import ama.contract.IdentityContracts.*;import ama.support.MysqlTestDatabase;
import org.springframework.boot.builder.SpringApplicationBuilder;import org.springframework.context.support.GenericApplicationContext;
import java.nio.file.*;import java.util.*;import com.fasterxml.jackson.databind.ObjectMapper;
/** New JVM: real production Spring configuration, retained transports and original archives. */
public final class Task06Process {
 public static void main(String[] args)throws Exception {
  var props=new Properties();try(var in=Files.newInputStream(Path.of(args[0]))){props.load(in);}var bounds=new HashMap<String,Object>();for(String key:props.stringPropertyNames())if(key.startsWith("processing.")||key.startsWith("transcript."))bounds.put(key,props.getProperty(key));
  FileBackendCatalog.Backend backend;
  if(props.getProperty("mode").equals("s3"))backend=ManagedBackendAdapters.s3(Task06Fixture.admissionTwo(ama.storage.s3.S3Fixture.config(props.getProperty("id"),java.net.URI.create(props.getProperty("endpoint")),props.getProperty("data"),props.getProperty("control"))),1,"WRITABLE");
  else {var binding=new ManagedBackendId.Local(props.getProperty("origin"),props.getProperty("volume"),props.getProperty("root"),props.getProperty("filesystem"));backend=ManagedBackendAdapters.local(props.getProperty("id"),binding,Task05TransportIT.tls("writer"),1,"WRITABLE");}
  var definitions=new FileBackendDefinitions(List.of(backend),backend.id());var source=MysqlTestDatabase.dataSource();
  try(var app=new SpringApplicationBuilder(ApiApplication.class).properties(bounds).initializers(context->{var c=(GenericApplicationContext)context;c.registerBean("task06RestartSource",javax.sql.DataSource.class,()->source);c.registerBean("task06RestartBackends",FileBackendDefinitions.class,()->definitions);}).run("--server.address=127.0.0.1","--server.port=0","--management.server.port=0","--logging.level.root=ERROR","--identity.allowed-origin=https://task06.example.invalid")){
   if(!Task06RuntimeProbe.installed(app))throw new IllegalStateException("Real transcript component must be installed");
   var actual=Task06RuntimeProbe.actual(app);var actor=new Actor(props.getProperty("owner"),Status.ACTIVE,Set.of(Role.USER));var result=actual.service().readOwner(actor,props.getProperty("meeting"),props.getProperty("working"));
   var old=(Map<?,?>)result.get("selectedConfirmation");if(!props.getProperty("hash").equals(old.get("contentHash")))throw new IllegalStateException("Original confirmation hash changed");
   var output=new LinkedHashMap<String,Object>();output.put("pid",ProcessHandle.current().pid());output.put("maxMemory",Runtime.getRuntime().maxMemory());output.put("processors",Runtime.getRuntime().availableProcessors());output.put("currentDefault",app.getEnvironment().getProperty("processing.config-version"));output.put("qualification",actual.qualification());output.put("inspection",result);
   Files.writeString(Path.of(args[1]),new ObjectMapper().writeValueAsString(output));
  }
 }
}
