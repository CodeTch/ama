package ama.storage;
import ama.config.*;
import ama.config.file.*;
import ama.contract.MinutesContracts.CanonicalBudget;
import ama.storage.s3.*;
import ama.support.MysqlTestDatabase;
import ama.task.*;
import ama.transcript.*;
import ama.worker.*;
import java.nio.file.*;
import java.util.*;
import java.time.Duration;
import static ama.transcript.ArchiveProcessingFixture.B;
/** Controlled transport definitions only; genuine production provider and WorkerMain own execution. */
public final class Task05WorkerProcess {
 public static void main(String[] args)throws Exception{
  var p=new Properties();try(var in=Files.newInputStream(Path.of(args[0]))){p.load(in);}
  FileBackendCatalog.Backend backend;
  if(p.getProperty("mode").equals("s3"))backend=ManagedBackendAdapters.s3(S3Fixture.config(p.getProperty("id"),java.net.URI.create(p.getProperty("endpoint")),p.getProperty("data"),p.getProperty("control")),2,"READ_ONLY");
  else{var b=new ManagedBackendId.Local(p.getProperty("origin"),p.getProperty("volume"),p.getProperty("root"),p.getProperty("filesystem"));backend=ManagedBackendAdapters.local(p.getProperty("id"),b,Task05TransportIT.tls("writer"),2,"READ_ONLY");}
  var definitions=new FileBackendDefinitions(List.of(backend),null);
  var settings=new TranscriptImportRuntime.Settings(()->new ControlledConfigArchiveSource().open(Path.of(p.getProperty("archive")),p.getProperty("pin"),new ConfigArchiveIndex.IndexBudget(4194304,4096,67108864,128),B,()->false),B,new TranscriptDocumentParser.ParseBudget(1048576,1048576,4096),Duration.ofSeconds(60));
  var provider=new TranscriptWorkerRuntimeProvider(MysqlTestDatabase.dataSource(),definitions,B,new CanonicalBudget(16777216,32,1000000,2097152,250000),settings,"TASK05_FRESH_JVM",1);
  System.out.println("PROCESS pid="+ProcessHandle.current().pid()+" maxMemory="+Runtime.getRuntime().maxMemory()+" processors="+Runtime.getRuntime().availableProcessors()+" currentDefault="+p.getProperty("default-processing-version"));
  WorkerMain.run(provider.create());
 }
}
