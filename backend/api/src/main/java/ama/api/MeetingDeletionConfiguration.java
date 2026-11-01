package ama.api;
import ama.db.TransactionRunner;
import ama.meeting.*;
import ama.storage.FileBackendCatalog;
import javax.sql.DataSource;
import org.springframework.context.annotation.*;
@Configuration(proxyBeanMethods=false) class MeetingDeletionConfiguration {
 @Bean(initMethod="start",destroyMethod="close") JdbcMeetingDeletion meetingDeletions(DataSource source,TransactionRunner tx,JdbcMeetingAccess access,FileBackendCatalog backends){return new JdbcMeetingDeletion(source,tx,access,backends);}
}
