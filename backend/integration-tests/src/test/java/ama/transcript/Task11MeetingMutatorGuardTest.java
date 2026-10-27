package ama.transcript;

import ama.contract.MeetingContracts.*;
import ama.meeting.*;
import ama.task.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual B03 command + access + authority + transaction; JDBC rows, fence and output sinks are substitutes. */
class Task11MeetingMutatorGuardTest {
 static final String M=Task11PublicationWriteGuardBoundaryTest.MEETING,R="12345678-1234-4234-8234-123456789ad1";
 static final class Case {
  final Task11PublicationWriteGuardBoundaryTest.Fixture f;final JdbcMeetingRepository repository;final AtomicLong version;final Map<String,Object> receipt=new LinkedHashMap<>();final Map<String,Object> root=new LinkedHashMap<>();
  Case(String history)throws Exception{
   var h=history.equals("ordinary")?null:history.equals("local")?new Task11ClosedHistoryFixture.Fixture().local():new Task11ClosedHistoryFixture.Fixture().remote();
   if(history.equals("unknown"))h.rows.get("external_execution").getFirst().put("state","UNKNOWN");
   f=h==null?new Task11PublicationWriteGuardBoundaryTest.Fixture():Task11ClosedHistoryGuardTest.sql(h,new AtomicBoolean());f.allowUpdates=true;version=new AtomicLong(h==null?1:15);
   root.putAll(Task11ImportPublicationGuardTest.row("id",M,"owner_id","owner","revision",version.get(),"access_revision",1L,"cancel_epoch",0L,"stage",h==null?"ENTRY":"PUBLISH_FAILED","lifecycle","ACTIVE","governance_state","ACTIVE","ever_published",0L,"info_revision_id",R,"roster_revision_id",null,"final_confirmation_id",null));
   var prior=f.responder;
   f.responder=sql->{
    if(sql.contains("task_type IN ('MINUTES_GENERATE_V1','TRANSCRIPT_IMPORT_V1')")||sql.contains("operation_kind='MINUTES_GENERATE'")||sql.contains(" WHERE 1=0 ")||sql.contains(" WHERE (1=0) OR (1=0) "))return sql.startsWith("SELECT COUNT(*)")?List.of(Map.of("count",0L)):List.of();
    if(sql.contains(" FROM enterprise_identity "))return List.of(sql.startsWith("SELECT status ")?Map.of("status","ACTIVE"):sql.startsWith("SELECT enterprise_id,status ")?Task11ImportPublicationGuardTest.row("enterprise_id","owner","status","ACTIVE"):Task11ImportPublicationGuardTest.row("enterprise_id","owner","display_name","Owner","status","ACTIVE"));
    if(sql.contains(" FROM idempotency_record "))return List.of(receipt);
    if(sql.contains(" FROM task_subject "))return List.of(Map.of("business_version",version.get()));
    if(sql.contains(" FROM meeting_access_grant ")||sql.contains(" FROM roster_member "))return List.of();
    if(sql.contains(" FROM roster_revision "))return List.of(Task11ImportPublicationGuardTest.row("id",R,"revision",version.get(),"state","DRAFT"));
    if(sql.contains(" FROM meeting ")){root.put("revision",version.get());if(sql.startsWith("SELECT COUNT(*)"))return List.of(Map.of("count",1L));String columns=sql.substring(7,sql.indexOf(" FROM meeting "));if(columns.equals("*"))return List.of(new LinkedHashMap<>(root));var projected=new LinkedHashMap<String,Object>();for(String field:columns.split(","))projected.put(field,root.get(field));return List.of(projected);}
    return prior==null?null:prior.apply(sql);
   };
   f.updateObserver=(sql,params)->{if(sql.startsWith("INSERT INTO idempotency_record")){receipt.putAll(Task11ImportPublicationGuardTest.row("scope_hash",params.get(1),"actor_id",params.get(2),"resource_id",params.get(3),"operation",params.get(4),"key_hash",params.get(5),"request_hash",params.get(6),"result_id",null,"http_status",0));}if(sql.startsWith("UPDATE meeting SET revision="))version.set(((Number)params.get(1)).longValue());};
   var fences=mock(SubjectFencePort.class);var subject=mock(SubjectFencePort.LockedSubject.class);when(subject.businessVersion()).thenAnswer(x->version.get());when(fences.lock(any(),eq(false))).thenReturn(Optional.of(subject));var access=new JdbcMeetingAccess(f.ds,f.tx,fences);
   var authority=JdbcTranscriptAuthority.create(f.ds,f.tx,access,(a,m,p)->{},mock(ama.storage.FileBackendCatalog.class),Map.of(),f.authority.readBudget(),f.authority.canonicalBudget()).authority();
   repository=new JdbcMeetingRepository(f.ds,f.tx,access,fences,mock(OutboxPort.class),mock(ama.audit.AuditPort.class),id->{},authority);
  }
  void invoke(boolean roster){long expected=version.get();if(roster)repository.confirm(Task11PublicationWriteGuardBoundaryTest.ACTOR,M,"matrix-roster",new ConfirmRoster(expected),M);else repository.save(Task11PublicationWriteGuardBoundaryTest.ACTOR,M,"matrix-info",new SaveInfo(expected,"Updated",null,null,null,List.of(),List.of()),M);}
 }
 @Test void actualInfoAndRosterCommandsPermitOrdinaryAndClosedHistories()throws Exception{
  for(String history:List.of("ordinary","local","remote"))for(boolean roster:List.of(false,true)){var c=new Case(history);long before=c.version.get();assertDoesNotThrow(()->c.invoke(roster),history+" roster="+roster);assertEquals(before+1,c.version.get());assertEquals(history.equals("ordinary")?1:2,c.f.commits.get());assertEquals(0,c.f.rollbacks.get());assertTrue(c.f.updates.stream().anyMatch(sql->sql.startsWith(roster?"INSERT INTO roster_revision":"INSERT INTO meeting_info_revision")));assertTrue(c.f.updates.stream().noneMatch(sql->sql.startsWith("UPDATE publication")||sql.startsWith("UPDATE external_")||sql.startsWith("UPDATE final_confirmation")));}
 }
 @Test void actualInfoAndRosterCommandsRejectOlderUnknownBeforeBusinessWrites()throws Exception{
  for(boolean roster:List.of(false,true)){var c=new Case("unknown");assertThrows(ama.contract.MinutesContracts.Failure.class,()->c.invoke(roster));assertEquals(0,c.f.commits.get());assertEquals(1,c.f.rollbacks.get());assertTrue(c.f.updates.stream().allMatch(sql->sql.startsWith("INSERT INTO idempotency_record")));assertEquals(15,c.version.get());}
 }
}
