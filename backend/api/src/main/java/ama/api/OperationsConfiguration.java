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
 @Bean GovernanceHttpProvider governanceProvider(Environment env){return new GovernanceHttpProvider(URI.create(env.getRequiredProperty("operations.integration-origin")),env.getProperty("operations.embedding-model","harness-embed-v1"),env.getProperty("operations.embedding-dimension",Integer.class,256));}
 @Bean EncryptedEmbeddingStore encryptedEmbeddings(Environment env)throws java.io.IOException{Path path=Path.of(env.getRequiredProperty("operations.encryption-key-file"));if(Files.isSymbolicLink(path)||!Files.isRegularFile(path))throw new IllegalArgumentException("Operations secret key file required");return new EncryptedEmbeddingStore(Files.readAllBytes(path));}
 @Bean JdbcGovernance operations(DataSource source,TransactionRunner tx,JdbcMeetingAccess access,GovernanceHttpProvider provider,EncryptedEmbeddingStore crypto){return new JdbcGovernance(source,tx,access,provider,crypto);}
 @Bean JdbcCorpusFeedback corpusFeedback(DataSource source,TransactionRunner tx,JdbcMeetingAccess access,JdbcFeedback feedback){return new JdbcCorpusFeedback(source,tx,access,feedback);}
 @Bean JdbcMailJobs minutesMail(DataSource source,TransactionRunner tx,JdbcMeetingAccess access,GovernanceHttpProvider provider,Environment env){return new JdbcMailJobs(source,tx,access,provider,provider::email,URI.create(env.getRequiredProperty("operations.web-origin")));}
 @Bean(initMethod="start",destroyMethod="close") OperationsMaintenance operationsMaintenance(JdbcGovernance operations,JdbcMailJobs mail,DataSource source){return new OperationsMaintenance(operations,mail,source);}
 /** Dedicated single worker; durable leases permit restart without a second send. */
 static final class OperationsMaintenance implements AutoCloseable {
  final JdbcGovernance operations;final JdbcMailJobs mail;final org.springframework.jdbc.core.JdbcTemplate j;final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"ama-governance-worker");t.setDaemon(true);return t;});
  OperationsMaintenance(JdbcGovernance operations,JdbcMailJobs mail,DataSource source){this.operations=operations;this.mail=mail;j=new org.springframework.jdbc.core.JdbcTemplate(source);j.setQueryTimeout(2);}
  public void start(){worker.scheduleWithFixedDelay(()->{try{mail.process();operations.cleanExpired();
   // Admission is postponed while any recording or offline speech work is active.
   int live=j.queryForObject("SELECT COUNT(*) FROM recording_session WHERE state IN ('OPEN','PAUSED') AND writer_lease_expires_at>UTC_TIMESTAMP(6)",Integer.class);
   int speech=j.queryForObject("SELECT COUNT(*) FROM speech_execution WHERE state IN ('RESERVED','UNKNOWN')",Integer.class);
   if(live==0&&speech==0)operations.processEvaluation();
  }catch(RuntimeException unavailable){System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,"Operations maintenance unavailable: "+unavailable.getClass().getSimpleName());}},2,2,TimeUnit.SECONDS);}
  public void close(){worker.shutdownNow();try{worker.awaitTermination(15,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
 }
}
