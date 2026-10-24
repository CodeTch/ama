package ama.storage;
import ama.transcript.*;
import ama.config.*;
import ama.config.file.*;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.ConfigArchiveContracts.Ref;
import ama.contract.MinutesContracts.*;
import ama.contract.IdentityContracts.Actor;
import ama.minutes.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
/** Actual production command in noninstalled source-first composition, not combined Task10/11 UX. */
class Task09MinutesFixture extends Task09PipelineFixture {
 String instruction(){return "Controlled synthetic minutes.";}
 String template,revision;Ref prompt,model;final AtomicBoolean archiveUnavailable=new AtomicBoolean();
 void install()throws Exception {
  template=UUID.randomUUID().toString();revision=UUID.randomUUID().toString();var installed=new ControlledMinutesTemplateInstaller(source,"CONTROLLED_INSTALLATION").install(template,revision);String release="task09.controlled."+UUID.randomUUID();
  var canonical=new CanonicalBudget(16777216,32,1000000,4194304,250000);var output=new ValidationBudget(8388608,32,400000,128,4194304,4194304,4194304,16777216,1000,1000,1000,10000,10000,20000);
  var raw=ControlledMinutesArchives.build("CONTROLLED_INSTALLATION",installed.revisionId(),release,instruction(),canonical,output);var current=snapshot();var existing=new HashMap<Identity,String>();for(var entry:current.index().entries())existing.put(entry.ref().identity(),entry.ref().sha256());var appended=new ArrayList<RawArchive>();
  for(var archive:raw){String prior=existing.get(archive.claimedIdentity());if(prior==null)appended.add(archive);else assertEquals(prior,java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(archive.bytes())));}
  pin=new ControlledArchiveReleaseWriter().publish(archive,pin,appended,new ConfigArchiveIndex.IndexBudget(4194304,4096,67108864,128),B,()->false);var next=snapshot();prompt=next.index().entries().stream().map(ConfigArchiveIndex.Entry::ref).filter(r->r.artifactType()==ArtifactType.PROMPT_CONFIG).findFirst().orElseThrow();model=next.index().entries().stream().map(ConfigArchiveIndex.Entry::ref).filter(r->r.artifactType()==ArtifactType.MODEL_CONFIG).findFirst().orElseThrow();
  java.nio.file.Files.writeString(receipt.resolve("controlled-template-archive-installation.json"),json.writeValueAsString(Map.of("template",template,"revision",revision,"indexSha",pin,"promptVersion",prompt.version(),"modelVersion",model.version())));
 }
 ConfigArchiveSource.Snapshot snapshot(){assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());if(archiveUnavailable.get())throw ama.contract.MinutesContracts.fail(FailureCode.PROFILE_UNAVAILABLE);return new ControlledConfigArchiveSource().open(archive,pin,new ConfigArchiveIndex.IndexBudget(4194304,4096,67108864,128),B,()->false);}
 Object service()throws Exception {Class<?> type=assertDoesNotThrow(()->Class.forName("ama.transcript.JdbcMinutesInitialSettings"));return type.getConstructor(JdbcTranscriptAuthority.class,Supplier.class,Ref.class,Ref.class).newInstance(ama.api.Task09RuntimeProbe.components(app).authority(),(Supplier<ConfigArchiveSource.Snapshot>)this::snapshot,prompt,model);}
 @SuppressWarnings("unchecked") Map<String,Object> initialize(Object service,String key,long expected)throws Exception {try{return (Map<String,Object>)service.getClass().getMethod("initialize",Actor.class,String.class,String.class,long.class,String.class,String.class).invoke(service,actor(),meeting,key,expected,revision,UUID.randomUUID().toString());}catch(java.lang.reflect.InvocationTargetException e){if(e.getCause() instanceof RuntimeException r)throw r;throw e;}}

 String confirmed()throws Exception {
  String raw=raw();var saved=isolated();var ids=j.queryForList("SELECT segment_id FROM transcript_segment WHERE revision_id=? ORDER BY ordinal",String.class,raw);
  var result=saved.mutate(actor(),meeting,"save",new ama.contract.TranscriptContracts.Command(ama.contract.TranscriptContracts.Operation.SAVE,1,raw,ids.stream().map(id->new ama.contract.TranscriptContracts.Edit(id,"Actual original 😀",List.of())).toList()),UUID.randomUUID().toString());String working=(String)((Map<?,?>)result.get("result")).get("revisionId");saved.mutate(actor(),meeting,"confirm",new ama.contract.TranscriptContracts.Command(ama.contract.TranscriptContracts.Operation.CONFIRM,2,working,List.of()),UUID.randomUUID().toString());return working;
 }
}
