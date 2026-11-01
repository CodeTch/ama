package ama.meeting;
import ama.contract.IdentityContracts.*;
import ama.contract.MeetingContracts.*;
import ama.contract.WorkspaceContracts.*;
import ama.db.*;import ama.task.*;
import java.time.*;import java.util.*;import javax.sql.DataSource;
import org.flywaydb.core.Flyway;import org.junit.jupiter.api.*;import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
/** Actual MySQL; new genuine meetings with explicit saved-info/roster row fixtures, no model quality claim. */
class MeetingQueryIT {
 DataSource ds;JdbcTemplate j;MeetingService service;RosterService rosters;Actor owner,other;JdbcWebMeetingProjectionReader reader;
 String id(){return UUID.randomUUID().toString();}
 Actor person(){var a=new Actor("query-"+id(),Status.ACTIVE,Set.of(Role.USER));j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",a.enterpriseId(),a.enterpriseId());return a;}
 @BeforeEach void setup(){String url=System.getenv("AMA_C_RECORDING_DATABASE_URL");if(url==null||!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/ama_c_recording_[0-9a-f]{12}"))throw new IllegalArgumentException("Owned schema required");ds=MysqlDataSources.fromEnvironment("AMA_C_RECORDING_DATABASE_");j=new JdbcTemplate(ds);Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();owner=person();other=person();var tx=new TransactionRunner(ds);var fences=new JdbcSubjectFences(ds,Set.of("MEETING"));var access=new JdbcMeetingAccess(ds,tx,fences);var repository=new JdbcMeetingRepository(ds,tx,access,fences,new JdbcOutbox(ds,new MeetingEventReferences(ds).validators()),new ama.audit.JdbcAuditAdapter(ds),x->{});service=new MeetingService(repository);rosters=new RosterService(repository);reader=new JdbcWebMeetingProjectionReader(ds,tx,access,new WorkspaceCursor(),WorkspaceReadBudget.DEFAULT);}
 String meeting(Actor who,String name,LocalDate date,List<String> people,List<String> external,boolean confirmed){
  String m=service.create(who,id(),id()).id();j.update("UPDATE meeting_info_revision SET name=?,actual_meeting_date=? WHERE meeting_id=?",name,date,m);j.update("UPDATE meeting SET actual_meeting_date=? WHERE id=?",date,m);j.update("UPDATE meeting_search_projection SET name_search=? WHERE meeting_id=?",name,m);roster(m,who,people,external,confirmed,1);return m;
 }
 void roster(String m,Actor who,List<String> people,List<String> external,boolean confirmed,int revision){
  String r=id();j.update("INSERT INTO roster_revision(id,meeting_id,revision,state,confirmed_at,confirmed_by) VALUES (?,?,?,?,?,?)",r,m,revision,confirmed?"CONFIRMED":"DRAFT",confirmed?LocalDateTime.now():null,confirmed?who.enterpriseId():null);
  for(String person:people){String member=id();j.update("INSERT INTO roster_member(roster_id,member_id,enterprise_id,display_name,member_kind) VALUES (?,?,?,?,'ENTERPRISE')",r,member,person,person);if(confirmed)j.update("INSERT INTO meeting_person_search(meeting_id,roster_id,member_id,name_search) VALUES (?,?,?,?)",m,r,member,person);}
  for(String name:external){String member=id();j.update("INSERT INTO roster_member(roster_id,member_id,display_name,member_kind) VALUES (?,?,?,'EXTERNAL')",r,member,name);if(confirmed)j.update("INSERT INTO meeting_person_search(meeting_id,roster_id,member_id,name_search) VALUES (?,?,?,?)",m,r,member,name);}
  if(confirmed){j.update("DELETE FROM meeting_access_grant WHERE meeting_id=?",m);for(String person:people)j.update("INSERT INTO meeting_access_grant(meeting_id,enterprise_id,action,roster_id,confirmed_at) VALUES (?,?,'READ',?,UTC_TIMESTAMP(6))",m,person,r);j.update("UPDATE meeting SET roster_revision_id=? WHERE id=?",r,m);j.update("UPDATE meeting_search_projection SET roster_revision_id=? WHERE meeting_id=?",r,m);}
 }

 MeetingListFilter filter(String q,LocalDate from,LocalDate to){return new MeetingListFilter(q,Set.of(),Set.of(),from,to);}
 List<String> ids(MeetingPage p){return p.items().stream().map(MeetingItem::id).toList();}
 // Reflection lets the RED run exercise old query behavior before adding its aggregate contract.
 long total(MeetingPage p)throws Exception{return ((Number)p.getClass().getMethod("total").invoke(p)).longValue();}
 @Test void literalSearchPrecedesPaginationAndAllCountsUseIdenticalFilter()throws Exception{
  String a=meeting(owner,"季度 A%_= 中文",null,List.of(),List.of(),false),b=meeting(owner,"quarter a%_= 中文",LocalDate.of(2026,10,31),List.of(),List.of(),false);
  meeting(owner,"newest nonmatching",null,List.of(),List.of(),false);meeting(other,"季度 A%_= 中文",null,List.of(),List.of(),false);
  j.update("UPDATE meeting SET updated_at='2026-10-31 00:00:01' WHERE id=?",a);j.update("UPDATE meeting SET updated_at='2026-10-31 00:00:02' WHERE id=?",b);
  var f=filter("a%_=",null,null);var first=reader.list(owner,"owned",1,null,f);assertEquals(List.of(b),ids(first));assertTrue(first.hasMore());assertEquals(2,total(first));assertEquals(0,first.items().getFirst().materialCount());assertTrue(first.items().getFirst().allowedActions().contains("DELETE"));assertEquals("/meeting/"+b+"/processing",first.items().getFirst().destination());
  var next=reader.list(owner,"owned",1,first.nextCursor(),f);assertEquals(List.of(a),ids(next));assertFalse(next.hasMore());assertEquals(2,total(next));assertNull(next.items().getFirst().occurredOn());
  var stages=(Map<?,?>)first.getClass().getMethod("stageCounts").invoke(first);assertEquals(2L,((Number)stages.get("ENTRY")).longValue());
  var topics=(Map<?,?>)first.getClass().getMethod("topicGroups").invoke(first);assertEquals(2L,((Number)topics.get("__none")).longValue());
  assertEquals(List.of(b),ids(reader.list(owner,"owned",10,null,filter("%_=",LocalDate.of(2026,10,31),LocalDate.of(2026,10,31)))));
  assertEquals(0,total(reader.list(owner,"owned",10,null,filter("A%_==",null,null))));
  assertEquals(List.of(a),ids(reader.list(owner,"owned",10,null,filter(a.toUpperCase(Locale.ROOT),null,null))));
 }
 @Test void confirmedCurrentPeopleAndParticipationNeverIncludeDraftsOrUnrelatedOwners()throws Exception{
  String confirmed=meeting(owner,"confirmed",null,List.of(other.enterpriseId()),List.of("同名研发","同名研发"),true);
  meeting(owner,"draft",null,List.of(other.enterpriseId()),List.of("草稿独有"),false);meeting(person(),"unrelated 同名研发",null,List.of(),List.of(),false);
  assertEquals(List.of(confirmed),ids(reader.list(owner,"owned",10,null,filter("同名研发",null,null))));
  assertEquals(0,total(reader.list(owner,"owned",10,null,filter("草稿独有",null,null))));
  var joined=reader.list(other,"joined",10,null,MeetingListFilter.NONE);assertEquals(List.of(confirmed),ids(joined));assertEquals(1,total(joined));
  roster(confirmed,owner,List.of(),List.of("新名单"),true,2);
  assertEquals(0,total(reader.list(other,"joined",10,null,MeetingListFilter.NONE)));assertEquals(0,total(reader.list(owner,"owned",10,null,filter("同名研发",null,null))));
 }
}
