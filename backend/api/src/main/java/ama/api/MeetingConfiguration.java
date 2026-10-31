package ama.api;

import ama.audit.JdbcAuditAdapter;
import ama.db.TransactionRunner;
import ama.meeting.*;
import ama.contract.MeetingContracts.CategoryUnavailable;
import ama.task.*;
import javax.sql.DataSource;
import java.util.Set;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods=false)
class MeetingConfiguration {
    @Bean TransactionRunner meetingTransactions(DataSource source,org.springframework.core.env.Environment env){return new TransactionRunner(source,env.getProperty("minutes.enabled",Boolean.class,false)?30:-1);}
    @Bean JdbcSubjectFences meetingSubjectFences(DataSource source){return new JdbcSubjectFences(source,Set.of("MEETING"));}
    @Bean JdbcOutbox meetingOutbox(DataSource source){
        var validators=new java.util.HashMap<String,OutboxReferenceValidator>(new MeetingEventReferences(source).validators());
        new ama.transcript.TranscriptEventReferences(source).validators().forEach((key,value)->{if(validators.putIfAbsent(key,value)!=null)throw new IllegalStateException("Duplicate event validator");});
        new ama.transcript.MinutesEventReferences(source).validators().forEach((key,value)->{if(validators.putIfAbsent(key,value)!=null)throw new IllegalStateException("Duplicate event validator");});
        ama.task.RecordingArchiveRuntime.eventReferences(source).forEach((key,value)->{if(validators.putIfAbsent(key,value)!=null)throw new IllegalStateException("Duplicate event validator");});
        return new JdbcOutbox(source,validators);
    }
    @Bean JdbcMeetingAccess meetingAccess(DataSource source,TransactionRunner tx,JdbcSubjectFences fences){return new JdbcMeetingAccess(source,tx,fences);}
    @Bean WorkspaceCursor workspaceCursors(){return new WorkspaceCursor();}
    @Bean WorkspaceReadBudget.Limits workspaceReadLimits(org.springframework.core.env.Environment env){
        var d=WorkspaceReadBudget.DEFAULT;
        long response=Math.min(d.responseBytes(),env.getProperty("workspace.read.max-response-bytes",Long.class,d.responseBytes()));
        response=Math.min(response,env.getProperty("meeting.http.max-response-bytes",Long.class,d.responseBytes()));
        return new WorkspaceReadBudget.Limits(Math.min(d.bytes(),env.getProperty("workspace.read.max-acquisition-bytes",Long.class,d.bytes())),response,
            Math.min(d.scalars(),env.getProperty("workspace.read.max-scalars",Long.class,d.scalars())),Math.min(d.units(),env.getProperty("workspace.read.max-utf16-units",Long.class,d.units())),
            Math.min(d.rows(),env.getProperty("workspace.read.max-rows",Long.class,d.rows())),Math.min(d.edges(),env.getProperty("workspace.read.max-edges",Long.class,d.edges())),
            java.time.Duration.ofMillis(Math.min(d.timeout().toMillis(),env.getProperty("workspace.read.timeout-ms",Long.class,d.timeout().toMillis()))));
    }
    @Bean JdbcCategoryCatalog meetingCategoryCatalog(TransactionRunner tx,JdbcMeetingAccess access,CategoryDictionary categories,WorkspaceCursor cursors,WorkspaceReadBudget.Limits limits){return new JdbcCategoryCatalog(tx,access,categories,cursors,()->new WorkspaceReadBudget(limits,()->Thread.currentThread().isInterrupted()));}
    @Bean JdbcWebMeetingProjectionReader webMeetingProjections(DataSource source,TransactionRunner tx,JdbcMeetingAccess access,WorkspaceCursor cursors,WorkspaceReadBudget.Limits limits){return new JdbcWebMeetingProjectionReader(source,tx,access,cursors,limits);}
    @Bean JdbcWorkspaceNavigationReader workspaceNavigation(DataSource source,TransactionRunner tx,JdbcMeetingAccess access,WorkspaceReadBudget.Limits limits){return new JdbcWorkspaceNavigationReader(source,tx,access,limits);}
    @Bean JdbcMeetingCommandDiscovery meetingCommandDiscovery(DataSource source,TransactionRunner tx,JdbcMeetingAccess access,WorkspaceReadBudget.Limits limits){return new JdbcMeetingCommandDiscovery(source,tx,access,limits);}
    @Bean CategoryDictionary companyMeetingCategories(){return id->{throw new CategoryUnavailable();};}
    @Bean JdbcMeetingRepository meetingRepository(DataSource source,TransactionRunner tx,JdbcMeetingAccess access,
                                                 JdbcSubjectFences fences,JdbcOutbox outbox,CategoryDictionary categories,ProcessingConfiguration.Runtime processing,WorkspaceReadBudget.Limits limits){
        return new JdbcMeetingRepository(source,tx,access,fences,outbox,new JdbcAuditAdapter(source),categories,processing.components()==null?null:processing.components().authority(),limits);
    }
    @Bean MeetingService meetings(JdbcMeetingRepository repository){return new MeetingService(repository);}
    @Bean RosterService rosters(JdbcMeetingRepository repository){return new RosterService(repository);}
    @Bean JdbcTopics topics(DataSource source,TransactionRunner tx,JdbcMeetingAccess access){return new JdbcTopics(source,tx,access);}
    @Bean JdbcFeedback feedback(DataSource source,TransactionRunner tx,JdbcMeetingAccess access){return new JdbcFeedback(source,tx,access);}
}
