package ama.api;
import ama.governance.*;
import ama.mail.*;
import ama.db.TransactionRunner;
import ama.meeting.*;
import java.nio.file.*;
import java.net.URI;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="operations.enabled",havingValue="true")
class OperationsConfiguration {
 @Bean GovernanceHttpProvider governanceProvider(Environment env){var provider=new GovernanceHttpProvider(URI.create(env.getRequiredProperty("operations.integration-origin")),env.getProperty("operations.embedding-model","harness-fixture-embedding-v1"),env.getProperty("operations.embedding-dimension",Integer.class,256));GovernanceVoiceConsent.configure(provider::consent);return provider;}
 @Bean EncryptedEmbeddingStore encryptedEmbeddings(Environment env)throws java.io.IOException{Path path=Path.of(env.getRequiredProperty("operations.encryption-key-file"));if(Files.isSymbolicLink(path)||!Files.isRegularFile(path))throw new IllegalArgumentException("Operations secret key file required");return new EncryptedEmbeddingStore(Files.readAllBytes(path));}
 @Bean JdbcGovernance operations(DataSource source,TransactionRunner tx,JdbcMeetingAccess access,GovernanceHttpProvider provider,EncryptedEmbeddingStore crypto){return new JdbcGovernance(source,tx,access,provider,crypto);}
 @Bean JdbcGovernanceWorkbench governanceWorkbench(DataSource source,TransactionRunner tx,JdbcMeetingAccess access,JdbcGovernance governance){return new JdbcGovernanceWorkbench(source,tx,access,governance);}
 @Bean JdbcMeetingVoiceprintApplications meetingVoiceprintApplications(DataSource source,TransactionRunner tx,JdbcMeetingAccess access,GovernanceHttpProvider provider,EncryptedEmbeddingStore crypto,ama.storage.FileBackendCatalog backends){return new JdbcMeetingVoiceprintApplications(source,tx,access,provider,crypto,backends);}
 @Bean JdbcCorpusAdoption corpusAdoption(DataSource source,TransactionRunner tx,JdbcMeetingAccess access,GovernanceHttpProvider provider){return new JdbcCorpusAdoption(source,tx,access,provider);}
 @Bean JdbcCorpusFeedback corpusFeedback(DataSource source,TransactionRunner tx,JdbcMeetingAccess access,JdbcFeedback feedback){return new JdbcCorpusFeedback(source,tx,access,feedback);}
 @Bean JdbcMailJobs minutesMail(DataSource source,TransactionRunner tx,JdbcMeetingAccess access,GovernanceHttpProvider provider,Environment env){return new JdbcMailJobs(source,tx,access,provider,provider::email,URI.create(env.getRequiredProperty("operations.web-origin")));}
 @Bean(initMethod="start",destroyMethod="close") OperationsMaintenance operationsMaintenance(JdbcGovernance operations,JdbcMailJobs mail,DataSource source,JdbcMeetingVoiceprintApplications voices,JdbcCorpusAdoption adoption){return new OperationsMaintenance(operations,mail,source,voices,adoption);}
 /** Dedicated single worker; durable leases permit restart without a second send. */
 static final class OperationsMaintenance implements AutoCloseable {
  final JdbcMeetingVoiceprintApplications voices;final JdbcCorpusAdoption adoption;final JdbcGovernance operations;final JdbcMailJobs mail;final org.springframework.jdbc.core.JdbcTemplate j;final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"ama-governance-worker");t.setDaemon(true);return t;});
  OperationsMaintenance(JdbcGovernance operations,JdbcMailJobs mail,DataSource source,JdbcMeetingVoiceprintApplications voices,JdbcCorpusAdoption adoption){this.voices=voices;this.adoption=adoption;this.operations=operations;this.mail=mail;j=new org.springframework.jdbc.core.JdbcTemplate(source);j.setQueryTimeout(2);}
  final ScheduledExecutorService evaluations=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"ama-quality-evaluation");t.setDaemon(true);return t;});
  public void start(){worker.scheduleWithFixedDelay(()->{try{mail.process();operations.cleanExpired();voices.cleanExpired();for(int i=0;i<5;i++)adoption.process();
  }catch(RuntimeException unavailable){System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,"Operations maintenance unavailable: "+unavailable.getClass().getSimpleName());}},2,2,TimeUnit.SECONDS);
   evaluations.scheduleWithFixedDelay(()->{try{operations.processEvaluation();}catch(RuntimeException unavailable){System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,"Evaluation unavailable: "+unavailable.getClass().getSimpleName());}},2,1,TimeUnit.SECONDS);
  }
  public void close(){worker.shutdownNow();evaluations.shutdownNow();try{worker.awaitTermination(15,TimeUnit.SECONDS);evaluations.awaitTermination(15,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
 }
}
