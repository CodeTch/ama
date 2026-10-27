package ama.transcript;

import ama.access.MeetingAccess;
import ama.contract.TaskContracts.*;
import ama.storage.FileRegistry;
import ama.task.TaskDomainPolicy;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real import-policy discovery/acquisition/completion with substituted JDBC rows and source adapter. */
class Task11ImportPublicationGuardTest {
 static final String M=Task11PublicationWriteGuardBoundaryTest.MEETING,I="12345678-1234-4234-8234-123456789ac2",B="12345678-1234-4234-8234-123456789ac3",F="12345678-1234-4234-8234-123456789ac4",T="12345678-1234-4234-8234-123456789ac5",P="12345678-1234-4234-8234-123456789ac6";
 static Map<String,Object> row(Object... cells){var r=new LinkedHashMap<String,Object>();for(int n=0;n<cells.length;n+=2)r.put((String)cells[n],cells[n+1]);return r;}
 static final class Case {
  final Task11PublicationWriteGuardBoundaryTest.Fixture f;final ImportTaskDomainPolicy policy;final Map<String,Map<String,Object>> tables=new HashMap<>();final FileRegistry registry=mock(FileRegistry.class);final TaskSpec spec;final FileRegistry.LockedMeeting gate=mock(FileRegistry.LockedMeeting.class);
  Case(boolean publication,boolean stale)throws Exception{
   f=new Task11PublicationWriteGuardBoundaryTest.Fixture();String hash="a".repeat(64);String batchHash=new BatchFingerprint().compute(M,B,"TRANSCRIPT",1,List.of(new BatchFingerprint.Member(1,F,hash,8)),f.authority.canonicalBudget(),()->false);String producer=TranscriptImportProducer.compute(M,I,hash,1,0,f.authority.canonicalBudget(),()->false);
   spec=new TaskSpec("TRANSCRIPT_IMPORT_V1","TRANSCRIPT_IMPORT",new SubjectRef("MEETING",M),producer,new TypedRef("TRANSCRIPTION_INPUT",I),hash,1,0,0,3);
   tables.put("meeting",row("id",M,"owner_id","owner","revision",stale?2L:1L,"cancel_epoch",0L,"stage","ENTRY","lifecycle","ACTIVE","governance_state","ACTIVE","ever_published",0L,"transcript_revision_id",null));
   tables.put("transcription_input",row("id",I,"meeting_id",M,"batch_id",B,"kind","IMPORT","material_mode","EMPTY","processing_config_version","config","parser_version","parser","manifest_schema_version",1L,"input_fingerprint",hash,"base_revision",1L,"cancel_epoch",0L));
   tables.put("input_batch",row("id",B,"meeting_id",M,"kind","TRANSCRIPT","input_fingerprint",batchHash,"base_revision",1L));tables.put("input_batch_fingerprint_profile",row("batch_id",B,"profile",BatchFingerprint.PROFILE));tables.put("input_batch_file",row("batch_id",B,"ordinal",1L,"file_id",F,"sha256",hash));tables.put("file_object",row("id",F,"meeting_id",M,"sha256",hash,"size_bytes",8L));
   tables.put("durable_task",row("id",T,"task_type",spec.type(),"pool",spec.pool(),"subject_kind","MEETING","subject_id",M,"producer_key",producer,"input_kind",spec.input().kind(),"input_id",I,"input_fingerprint",hash,"business_version",1L,"priority",0L,"cancel_epoch",0L,"max_attempts",3L,"state","RUNNING","attempt",1L,"created_at","2026-10-26T00:00:00"));
   tables.put("task_attempt",row("task_id",T,"attempt",1L,"owner","worker","started_at","2026-10-26T00:00:00"));
   if(publication)tables.put("publication",row("id",P,"meeting_id",M,"confirmation_id",P,"business_key","knowledge:"+P,"library_id","library","folder_id","folder","state","READY","revision",1L));
   f.responder=this::query;
   when(f.authority.meetingAccess()).thenReturn(f.access);when(f.authority.fileRegistry()).thenReturn(registry);when(f.authority.publicationWriteGuard()).thenReturn(f.guard);when(f.authority.publicationBudget()).thenAnswer(x->new TranscriptAcquisitionBudget(16777216,16777216,16777216,268435456,134217728,33554432,128,256,256).ledger(f.authority.readBudget(),f.authority.canonicalBudget(),()->false));
   when(f.authority.producerBeforeRoots(M,I,hash,1,0)).thenReturn(producer);var minutes=mock(MinutesTaskDomainPolicy.class);when(minutes.taskLimit()).thenReturn(256);when(f.authority.minutesDomain()).thenReturn(minutes);
   var ids=mock(MeetingAccess.LockedIdentities.class);when(f.access.tryLockIdentities(any(),anyList(),anyBoolean())).thenReturn(Optional.of(ids));when(f.access.lockPrepared(ids,M,false)).thenReturn(Optional.of(f.root));when(registry.adopt(any(),eq(M),eq(f.root),eq(MeetingAccess.Action.EDIT))).thenReturn(gate);when(registry.access(gate)).thenReturn(f.root);
   var sources=mock(FileRegistry.LockedSources.class);when(registry.prelockSources(gate,List.of(F),false)).thenReturn(Optional.of(sources));var original=mock(FileRegistry.CompletedSource.class);var file=mock(FileRegistry.FileRecord.class);when(file.fileId()).thenReturn(F);when(file.size()).thenReturn(8L);when(file.sha256()).thenReturn(hash);when(original.file()).thenReturn(file);when(original.originalPurpose()).thenReturn(JdbcTranscriptAuthority.PURPOSE);when(original.uploadCancelEpoch()).thenReturn(0L);when(registry.sources(gate,sources,List.of(F))).thenReturn(List.of(original));policy=new ImportTaskDomainPolicy(f.authority,f.ds);
  }
  List<Map<String,Object>> query(String sql){
   if(sql.contains(" FROM file_reference "))return List.of(row("count",1L));
   var matcher=java.util.regex.Pattern.compile(" FROM (meeting|transcription_input|input_batch_fingerprint_profile|input_batch_file|input_batch|file_object|durable_task|publication|final_confirmation|generation_input|generation_candidate|task_attempt|external_operation|external_execution|publication_submission|publication_attempt|external_document_mapping)(?: |\\b)").matcher(sql);
   if(!matcher.find())throw new AssertionError("Unexpected test SQL: "+sql);String table=matcher.group(1);var raw=tables.get(table);
   if(sql.endsWith(") metadata_budget"))return List.of(row("budget_rows",raw==null?0L:1L,"budget_bytes",raw==null?0L:512L,"budget_points",raw==null?0L:512L,"budget_units",raw==null?0L:512L));
   if(sql.startsWith("SELECT COUNT(*)"))return List.of(row("count",raw==null?0L:1L));if(raw==null)return List.of();var result=new LinkedHashMap<String,Object>();
   if(sql.contains(" AS __admitted")){var columns=java.util.regex.Pattern.compile("END AS ([a-z_0-9]+)").matcher(sql);while(columns.find())result.put(columns.group(1),raw.get(columns.group(1)));result.put("__admitted",true);result.put("__row_bytes",512L);result.put("__row_points",512L);}
   else{String selected=sql.substring(7,sql.indexOf(" FROM "));for(String column:selected.split(","))result.put(column.replace("f.",""),raw.get(column.replace("f.","")));}
   return List.of(result);
  }
  TaskDomainPolicy.PreparedDomain acquire(){var prepared=policy.prepare(List.of(spec),TaskDomainPolicy.Acquisition.TASK_MUTATION,false).orElseThrow();assertTrue(policy.lockRoots(List.of(spec),false,prepared));return prepared;}
 }
 @Test void pendingPublicationDeniesRealRawCompletion()throws Exception{var c=new Case(true,false);assertThrows(RuntimeException.class,()->c.f.tx.required(()->{var p=c.acquire();c.policy.requireCompletion(List.of(c.spec),p);return null;}));}
 @Test void noPublicationAllowsActualCompletionCapabilityWithoutLateSql()throws Exception{var c=new Case(false,false);c.f.tx.required(()->{var p=c.acquire();int before=c.f.sql.size();c.policy.requireCompletion(List.of(c.spec),p);assertEquals(before,c.f.sql.size());return null;});}
 @Test void staleTaskCanBeAcquiredForRetirementButCannotWriteRaw()throws Exception{var c=new Case(true,true);c.f.tx.required(()->{var p=c.acquire();assertThrows(RuntimeException.class,()->c.policy.requireCompletion(List.of(c.spec),p));verify(c.registry,never()).prelockSources(any(),anyList(),anyBoolean());return null;});}

 @Test void rawGraphMustUseSharedOperationBudgetBeforeTextTransfer()throws Exception{
  var c=new Case(false,false);var archive=c.f.authority.readBudget();var canonical=c.f.authority.canonicalBudget();doAnswer(x->{var budget=new TranscriptAcquisitionBudget(16777216,16777216,16777216,268435456,134217728,33554432,128,256,256).ledger(archive,canonical,()->false);budget.capture(16777200,0,0,1);return budget;}).when(c.f.authority).publicationBudget();
  assertThrows(ama.contract.MinutesContracts.Failure.class,()->c.f.tx.required(()->c.policy.prepare(List.of(c.spec),TaskDomainPolicy.Acquisition.TASK_MUTATION,false)));
  assertTrue(c.f.sql.stream().noneMatch(sql->sql.contains(" AS __admitted")),"exhausted shared budget must reject RAW graph before text projection");
 }

 @Test void separateRawReadersCannotResetTheSharedAcquisitionBudget()throws Exception{
  var c=new Case(false,false);var budget=new TranscriptAcquisitionBudget(900,2000,2000,20000,20000,10000,128,256,256).ledger(c.f.authority.readBudget(),c.f.authority.canonicalBudget(),()->false);var j=new org.springframework.jdbc.core.JdbcTemplate(c.f.ds);
  assertEquals(M,new BoundedTranscriptMetadata(j,c.f.authority.readBudget(),false,false,budget).one("meeting",BoundedTranscriptMetadata.ROOT,M).get("id"));
  long before=c.f.sql.stream().filter(sql->sql.contains(" AS __admitted")).count();
  var failure=assertThrows(ama.contract.MinutesContracts.Failure.class,()->new BoundedTranscriptMetadata(j,c.f.authority.readBudget(),true,false,budget).one("meeting",BoundedTranscriptMetadata.ROOT,M));
  assertEquals(ama.contract.MinutesContracts.FailureCode.BUDGET_EXCEEDED,failure.code());assertEquals(before,c.f.sql.stream().filter(sql->sql.contains(" AS __admitted")).count());
 }
 @Test void rawConcurrentGrowthMaskedBySqlCannotBecomeReadableMetadata()throws Exception{
  var c=new Case(false,false);var budget=new TranscriptAcquisitionBudget(10000,10000,10000,100000,100000,10000,128,256,256).ledger(c.f.authority.readBudget(),c.f.authority.canonicalBudget(),()->false);
  c.f.responder=sql->sql.contains(" AS __admitted")?List.of(row("__admitted",false)):c.query(sql);
  var failure=assertThrows(ama.contract.MinutesContracts.Failure.class,()->new BoundedTranscriptMetadata(new org.springframework.jdbc.core.JdbcTemplate(c.f.ds),c.f.authority.readBudget(),false,false,budget).one("meeting",BoundedTranscriptMetadata.ROOT,M));
  assertEquals(ama.contract.MinutesContracts.FailureCode.BUDGET_EXCEEDED,failure.code());assertTrue(c.f.updates.isEmpty());
 }
}
