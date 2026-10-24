package ama.minutes;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class Task09TemplateIT {
 static Class<?> installer(){return assertDoesNotThrow(()->Class.forName("ama.minutes.ControlledMinutesTemplateInstaller"),"Explicit installer must persist real immutable template and revision");}
 static Object create(DataSource source,String mode)throws Exception{return installer().getConstructor(DataSource.class,String.class).newInstance(source,mode);}
 static Object install(Object installer,String id,String revision)throws Exception{return installer.getClass().getMethod("install",String.class,String.class).invoke(installer,id,revision);}
 @Test void explicitInstallCreatesActualParentRevisionAndExactSevenThemes()throws Exception{
  var source=Task09Source.open();var j=new JdbcTemplate(source);String id=UUID.randomUUID().toString(),revision=UUID.randomUUID().toString();var installer=create(source,"CONTROLLED_INSTALLATION");
  assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM minutes_template WHERE id=?",Integer.class,id));install(installer,id,revision);
  var parent=j.queryForMap("SELECT * FROM minutes_template WHERE id=?",id);var child=j.queryForMap("SELECT * FROM template_revision WHERE id=?",revision);
  assertNull(parent.get("owner_id"));assertEquals(revision,parent.get("current_revision_id"));assertEquals(1L,((Number)parent.get("revision")).longValue());assertEquals(id,child.get("template_id"));assertEquals(7L,((Number)child.get("theme_count")).longValue());assertEquals("Controlled minutes",parent.get("name_normalized"));
  assertEquals("{\"schemaVersion\":\"ama.controlled-minutes-template.v1\",\"themes\":[\"OVERVIEW\",\"DISCUSSION\",\"CONCLUSION\",\"ACTION\",\"RISK\",\"OPEN_QUESTION\",\"DISAGREEMENT\"]}",child.get("themes"));
  install(installer,id,revision);assertEquals(parent,j.queryForMap("SELECT * FROM minutes_template WHERE id=?",id));assertEquals(child,j.queryForMap("SELECT * FROM template_revision WHERE id=?",revision));
 }
 @Test void conflictingAssignedIdentityCannotOverwriteOrLeavePartialRevision()throws Exception{
  var source=Task09Source.open();var j=new JdbcTemplate(source);String id=UUID.randomUUID().toString(),revision=UUID.randomUUID().toString(),other=UUID.randomUUID().toString();var installer=create(source,"CONTROLLED_INSTALLATION");install(installer,id,revision);
  var before=j.queryForMap("SELECT * FROM minutes_template WHERE id=?",id);assertThrows(Exception.class,()->install(installer,id,other));assertEquals(before,j.queryForMap("SELECT * FROM minutes_template WHERE id=?",id));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM template_revision WHERE id=?",Integer.class,other));
 }
 @Test void unselectedModeCannotConstructAnAdministrativeInstaller()throws Exception{
  var source=Task09Source.open();var type=installer();assertThrows(java.lang.reflect.InvocationTargetException.class,()->type.getConstructor(DataSource.class,String.class).newInstance(source,"PRODUCTION"));
 }
}
