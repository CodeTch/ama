package ama.minutes;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.*;
import ama.contract.MeetingContracts.Conflict;
import ama.db.*;
import ama.meeting.JdbcMeetingAccess;
import ama.task.JdbcSubjectFences;
import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Actual owned MySQL; mutations create their own append-only template fixtures. */
class PersonalTemplateIT {
 DataSource source; JdbcTemplate j; JdbcPersonalTemplates templates; Actor actor;
 @BeforeEach void start(){
  String url=System.getenv("AMA_C_RECORDING_DATABASE_URL");
  if(url==null||!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/ama_c_recording_[0-9a-f]{12}"))throw new IllegalArgumentException("Owned integration schema required");
  source=MysqlDataSources.fromEnvironment("AMA_C_RECORDING_DATABASE_");j=new JdbcTemplate(source);
  Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
  actor=identity("template-",Set.of(Role.USER));templates=repository();
 }
 JdbcPersonalTemplates repository(){var tx=new TransactionRunner(source);return new JdbcPersonalTemplates(source,tx,new JdbcMeetingAccess(source,tx,new JdbcSubjectFences(source,Set.of("MEETING"))));}
 Actor identity(String prefix,Set<Role> roles){var value=new Actor(prefix+UUID.randomUUID(),Status.ACTIVE,roles);j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",value.enterpriseId(),value.enterpriseId());return value;}
 String key(){return UUID.randomUUID().toString();}
 @Test void acceptsOneAndEightThemesAndRecoversExactOriginalAcknowledgements(){
  String key=key();var created=templates.create(actor,key,"每周例会😀",List.of("概述"));
  assertEquals(List.of("概述"),created.themes());assertEquals("1",created.revisionToken());
  var eight=List.of("背景","讨论","结论","行动","风险","问题","分歧","后续");String edit=key();
  var updated=templates.update(actor,created.id(),edit,1,"八主题",eight);
  assertEquals("2",updated.revisionToken());assertNotEquals(created.revisionId(),updated.revisionId());
  assertEquals(updated,repository().read(actor,created.id()));assertEquals(created,repository().create(actor,key,"每周例会😀",List.of("概述")));
  assertEquals(updated,repository().update(actor,created.id(),edit,1,"八主题",eight));
  assertEquals(2,j.queryForObject("SELECT COUNT(*) FROM template_revision WHERE template_id=?",Integer.class,created.id()));
  assertEquals(created.name(),j.queryForObject("SELECT name FROM template_revision WHERE id=?",String.class,created.revisionId()));
  assertThrows(Conflict.class,()->templates.create(actor,key,"不同请求",List.of("概述")));
 }
 @Test void rejectsZeroNineBlankInvalidUnicodeAndOversizeWithoutPartialRows(){
  for(var themes:List.of(List.<String>of(),Collections.nCopies(9,"主题"),List.of(" "),List.of("\uD800"),List.of("x".repeat(65537))))
   assertThrows(IllegalArgumentException.class,()->templates.create(actor,key(),"模板",themes));
  assertThrows(IllegalArgumentException.class,()->templates.create(actor,key(),"\u00a0",List.of("主题")));
  assertThrows(IllegalArgumentException.class,()->templates.create(actor,"","模板",List.of("主题")));
  assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM minutes_template WHERE owner_id=?",Integer.class,actor.enterpriseId()));
  assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM personal_template_state WHERE owner_id=?",Integer.class,actor.enterpriseId()));
 }
 @Test void staleEditsAndNormalizedDuplicateNamesLeaveBothHistoryAndPointerUntouched(){
  var first=templates.create(actor,key(),"Cafe\u0301",List.of("概述"));var second=templates.create(actor,key(),"第二个",List.of("讨论"));
  assertThrows(Conflict.class,()->templates.create(actor,key(),"  Café\u00a0",List.of("行动")));
  assertThrows(Conflict.class,()->templates.update(actor,second.id(),key(),1,"Café",List.of("行动")));
  var changed=templates.update(actor,first.id(),key(),1,"新版本",List.of("概述"));
  assertThrows(Conflict.class,()->templates.update(actor,first.id(),key(),1,"过期",List.of("行动")));
  assertEquals(changed,templates.read(actor,first.id()));assertEquals(second,templates.read(actor,second.id()));
  assertEquals(3,j.queryForObject("SELECT COUNT(*) FROM template_revision WHERE owner_id=?",Integer.class,actor.enterpriseId()));
 }
 @Test void deletionRetainsImmutableRevisionsAndReplaysButNeverRevivesOldCommands(){
  String create=key();var first=templates.create(actor,create,"删除测试",List.of("行动"));String delete=key();
  var deleted=templates.delete(actor,first.id(),delete,1);assertTrue(deleted.deleted());assertEquals("2",deleted.revisionToken());
  assertEquals(deleted,repository().delete(actor,first.id(),delete,1));assertTrue(templates.list(actor,20,null).items().isEmpty());
  assertThrows(MeetingAccess.AccessDeniedException.class,()->templates.read(actor,first.id()));
  assertEquals(first,templates.create(actor,create,"删除测试",List.of("行动")));
  assertTrue(templates.list(actor,20,null).items().isEmpty());
  assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM template_revision WHERE id=?",Integer.class,first.revisionId()));
  assertEquals(first.revisionId(),j.queryForObject("SELECT current_revision_id FROM minutes_template WHERE id=?",String.class,first.id()));
  assertNotEquals(first.id(),templates.create(actor,key(),"删除测试",List.of("行动")).id());
 }
 @Test void privateTemplatesDenyForeignAdminsAndRecheckDisabledOwnersBeforeReplay(){
  String key=key();var first=templates.create(actor,key,"私有正文",List.of("私有主题"));var foreign=identity("template-admin-",Set.of(Role.USER,Role.PLATFORM_ADMIN));
  assertTrue(templates.list(foreign,20,null).items().isEmpty());
  assertThrows(MeetingAccess.AccessDeniedException.class,()->templates.read(foreign,first.id()));
  assertThrows(MeetingAccess.AccessDeniedException.class,()->templates.update(foreign,first.id(),key(),1,"越权",List.of("越权")));
  assertThrows(MeetingAccess.AccessDeniedException.class,()->templates.delete(foreign,first.id(),key(),1));
  j.update("UPDATE enterprise_identity SET status='DISABLED' WHERE enterprise_id=?",actor.enterpriseId());
  assertThrows(MeetingAccess.AccessDeniedException.class,()->templates.create(actor,key,"私有正文",List.of("私有主题")));
  assertThrows(MeetingAccess.AccessDeniedException.class,()->templates.list(actor,20,null));
 }
 @Test void boundedPagesReturnEveryOwnedTemplateExactlyOnce(){
  var ids=new HashSet<String>();for(int n=0;n<5;n++)ids.add(templates.create(actor,key(),"分页"+n,List.of("主题")).id());
  var seen=new HashSet<String>();String cursor=null;int pages=0;
  do{var page=templates.list(actor,2,cursor);assertTrue(page.items().size()<=2);for(var item:page.items())assertTrue(seen.add(item.id()));cursor=page.nextCursor();assertTrue(++pages<=3);}while(cursor!=null);
  assertEquals(ids,seen);assertEquals(3,pages);
  assertThrows(IllegalArgumentException.class,()->templates.list(actor,51,null));
 }
 @Test void composedCatalogMustAcquireTextBudgetBeforeReleasingAnyPersonalTemplate(){
  templates.create(actor,key(),"目录预算",List.of("需要预算的私有章节"));var charged=new java.util.concurrent.atomic.AtomicLong();
  class BudgetUnavailable extends RuntimeException {}
  assertThrows(BudgetUnavailable.class,()->templates.list(actor,20,null,bytes->{charged.addAndGet(bytes);throw new BudgetUnavailable();}));
  assertTrue(charged.get()>0);assertEquals(1,templates.list(actor,20,null).items().size());
 }
}
