package ama.api;
import ama.export.*;
import ama.meeting.JdbcMeetingAccess;
import ama.storage.*;
import ama.task.*;
import ama.transcript.*;
import java.nio.file.Path;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
@Configuration(proxyBeanMethods=false) class ExportConfiguration {
 record Installation(MinutesExportRuntime.Dependencies dependencies){}
 @Bean Installation exportInstallation(ProcessingConfiguration.Runtime processing,JdbcMeetingAccess access,FileBackendCatalog backends,Environment env){
  if(!env.getProperty("exports.enabled",Boolean.class,false))return new Installation(null);
  if(processing.components()==null||!env.getProperty("minutes.enabled",Boolean.class,false))throw new IllegalStateException("Export source installation required");
  var authority=processing.components().authority();String pin=env.getRequiredProperty("minutes.index-sha");var archives=new ControlledMinutesArchiveAccess(Path.of(env.getRequiredProperty("minutes.archive-root")),pin,ConfiguredMinutes.indexBudget(env),ConfiguredMinutes.archiveBudget(env));
  var saved=authority.installExportSource(archives,pin,ConfiguredMinutes.acquisition(env));return new Installation(new MinutesExportRuntime.Dependencies(access,processing.components().registry(),backends,saved));
 }
 @Bean JdbcMeetingExports meetingExports(JdbcTaskQueue queue,FileRegistry registry){return new JdbcMeetingExports(queue,registry);}
}
