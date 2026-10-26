package ama.api;
import ama.access.MeetingAccess;
import ama.audit.JdbcAuditAdapter;
import ama.config.*;
import ama.config.file.ControlledConfigArchiveSource;
import ama.contract.ConfigArchiveContracts.ArchiveReadBudget;
import ama.db.TransactionRunner;
import ama.task.*;
import ama.transcript.*;
import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.*;
import org.springframework.context.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
@Configuration(proxyBeanMethods=false) class TranscriptConfiguration {
 record Runtime(JdbcSavedTranscripts service,CorrectionInstallation correction,Map<String,Object> qualification) {}
 @Bean JdbcSavedTranscriptReceipts savedTranscriptReceipts(DataSource source,TransactionRunner tx,MeetingAccess access){return new JdbcSavedTranscriptReceipts(source,tx,access);}
 @Bean Runtime transcriptRuntime(ProcessingConfiguration.Runtime processing,DataSource source,JdbcSubjectFences fences,JdbcOutbox outbox,Environment env,ApplicationContext context){
  if(!env.getProperty("transcript.enabled",Boolean.class,false))return new Runtime(null,null,Map.of());
  try{return assemble(processing,source,fences,outbox,env,context);}catch(IllegalArgumentException|IllegalStateException|ama.contract.MinutesContracts.Failure invalid){return new Runtime(null,null,Map.of());}
 }
 static Runtime assemble(ProcessingConfiguration.Runtime processing,DataSource source,JdbcSubjectFences fences,JdbcOutbox outbox,Environment env,ApplicationContext context){
  if(processing.components()==null)throw new IllegalStateException("Shared processing authority unavailable");
  var limits=new TranscriptAcquisitionBudget(n(env,"utf8"),n(env,"scalars"),n(env,"utf16"),n(env,"copy-bytes"),n(env,"copy-utf16"),n(env,"response-bytes"),i(env,"revision-depth"),i(env,"revision-nodes"),i(env,"tasks"));
  if(limits.responseBytes()>33554432)throw new IllegalArgumentException("Response exceeds stable protocol envelope");
  var read=new ArchiveReadBudget(p(env,"artifact-bytes"),p(env,"archive-total-bytes"),pi(env,"artifacts"),p(env,"dependencies"),pi(env,"graph-depth"),p(env,"json-tokens"),pi(env,"json-depth"),pi(env,"name-utf16"),pi(env,"string-utf16"),p(env,"string-utf8"),p(env,"aggregate-scalars"),p(env,"manifest-bytes"),pi(env,"batch-files"),p(env,"fact-rows"),p(env,"fact-edges"));
  var index=new ConfigArchiveIndex.IndexBudget(p(env,"index-bytes"),pi(env,"index-entries"),p(env,"release-bytes"),pi(env,"index-generations"));
  var providers=context.getBeansOfType(CorrectionInstallation.Provider.class).entrySet().stream().map(e->e.getKey()+":"+e.getValue().getClass().getName()).sorted().toList();
  String selected=env.getProperty("transcript.correction.mode"),declared=env.getProperty("transcript.correction.installation"),version=env.getProperty("transcript.correction.installation-version");
  String boot=UUID.randomUUID().toString();String qualification="saved-correction-v1-"+boot;
  var declaration=Objects.equals(selected,declared)&&"DISABLED_NOT_INSTALLED".equals(declared)?new CorrectionInstallation.Declaration(selected,version,qualification):null;
  var correction=CorrectionInstallation.compose(declaration,providers);
  var candidateProviders=context.getBeansOfType(SavedTranscriptTransitions.CandidateProvider.class).entrySet().stream().map(e->e.getKey()+":"+e.getValue().getClass().getName()).sorted().toList();var publicationProviders=context.getBeansOfType(SavedTranscriptTransitions.PublicationProvider.class).entrySet().stream().map(e->e.getKey()+":"+e.getValue().getClass().getName()).sorted().toList();
  var transitions=new SavedTranscriptTransitions(env.getProperty("transcript.candidates.installation"),env.getProperty("transcript.publication.installation"),env.getProperty("transcript.transitions.installation-version"),candidateProviders,publicationProviders);
  var service=new JdbcSavedTranscripts(processing.components().authority(),()->new ControlledConfigArchiveSource().open(Path.of(env.getRequiredProperty("processing.archive-root")),env.getRequiredProperty("processing.index-sha"),index,read,()->false),limits,correction,fences,outbox,new JdbcAuditAdapter(source),transitions);
  var receipt=new LinkedHashMap<String,Object>();receipt.put("configuredAcquisition",limits);receipt.put("sharedArchive",read);receipt.put("index",index);receipt.put("admission",service.admission());receipt.put("requestBytes",8388608);receipt.put("responseBytes",limits.responseBytes());receipt.put("selectedCorrectionMode",selected==null?"UNKNOWN":selected);receipt.put("declaredInstallation",declared==null?"UNKNOWN":declared);receipt.put("declarationVersion",version==null?"UNKNOWN":version);receipt.put("providerInventory",providers);receipt.put("correction",correction.projection());receipt.put("bootObservation",boot);receipt.put("futureDomainInstallations",transitions.observation());receipt.put("actualDomainInstallations",service.transitionInstallation());receipt.put("cooperativeOperationDeadlineMillis",30000);
  var effective=new LinkedHashMap<String,Object>();effective.put("acquisitionUtf8Bytes",Math.min(limits.utf8(),read.maxTotalBytes()));effective.put("acquisitionScalars",Math.min(limits.scalars(),read.maxAggregateScalars()));effective.put("acquisitionUtf16Units",limits.utf16());effective.put("textUtf8Bytes",Math.min(read.maxStringUtf8Bytes(),service.admission().canonical().maxStringBytes()));effective.put("textUtf16Units",read.maxStringUtf16());effective.put("copyUtf8Bytes",limits.copyBytes());effective.put("copyUtf16Units",limits.copyUtf16());effective.put("facts",Math.min(read.maxFactRows(),service.admission().canonical().maxContainerEntries()));effective.put("edges",read.maxFactEdges());effective.put("files",Math.min(64,read.maxBatchFiles()));effective.put("tasks",limits.tasks());effective.put("revisionDepth",limits.revisionDepth());effective.put("revisionNodes",limits.revisionNodes());effective.put("manifestBytes",Math.min(65536,read.maxManifestBytes()));effective.put("artifactBytes",Math.min(65536,read.maxArtifactBytes()));effective.put("closureBytesWithManifest",Math.min(1048576,read.maxTotalBytes()));effective.put("canonical",service.admission().canonical());receipt.put("effectiveAdmission",effective);
  return new Runtime(service,correction,Collections.unmodifiableMap(receipt));
 }
 private static long n(Environment env,String key){long n=env.getRequiredProperty("transcript.bounds."+key,Long.class);if(n<=0||n==Long.MAX_VALUE)throw new IllegalArgumentException("Finite positive saved transcript bound required");return n;}
 private static int i(Environment env,String key){return Math.toIntExact(n(env,key));}
 private static long p(Environment env,String key){return env.getRequiredProperty("processing.bounds."+key,Long.class);}
 private static int pi(Environment env,String key){return Math.toIntExact(p(env,key));}
}
