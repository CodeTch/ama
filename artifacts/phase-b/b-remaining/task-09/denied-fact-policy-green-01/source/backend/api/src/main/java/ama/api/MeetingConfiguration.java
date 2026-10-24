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
    @Bean TransactionRunner meetingTransactions(DataSource source){return new TransactionRunner(source);}
    @Bean JdbcSubjectFences meetingSubjectFences(DataSource source){return new JdbcSubjectFences(source,Set.of("MEETING"));}
    @Bean JdbcOutbox meetingOutbox(DataSource source){
        var validators=new java.util.HashMap<String,OutboxReferenceValidator>(new MeetingEventReferences(source).validators());
        new ama.transcript.TranscriptEventReferences(source).validators().forEach((key,value)->{if(validators.putIfAbsent(key,value)!=null)throw new IllegalStateException("Duplicate event validator");});
        new ama.transcript.MinutesEventReferences(source).validators().forEach((key,value)->{if(validators.putIfAbsent(key,value)!=null)throw new IllegalStateException("Duplicate event validator");});
        return new JdbcOutbox(source,validators);
    }
    @Bean JdbcMeetingAccess meetingAccess(DataSource source,TransactionRunner tx,JdbcSubjectFences fences){return new JdbcMeetingAccess(source,tx,fences);}
    @Bean CategoryDictionary companyMeetingCategories(){return id->{throw new CategoryUnavailable();};}
    @Bean JdbcMeetingRepository meetingRepository(DataSource source,TransactionRunner tx,JdbcMeetingAccess access,
                                                 JdbcSubjectFences fences,JdbcOutbox outbox,CategoryDictionary categories){
        return new JdbcMeetingRepository(source,tx,access,fences,outbox,new JdbcAuditAdapter(source),categories);
    }
    @Bean MeetingService meetings(JdbcMeetingRepository repository){return new MeetingService(repository);}
    @Bean RosterService rosters(JdbcMeetingRepository repository){return new RosterService(repository);}
}
