package ama.minutes;

import java.util.*;
import ama.contract.MinutesContracts.*;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;

/** Additional immutable system templates, separate from users' personal templates. */
public final class BuiltinMinutesTemplates {
 public record Definition(String templateId,String revisionId,String name,List<String> themes){public String document(){return new PersonalTemplateDocument(themes).json();}}
 private static final List<Definition> DEFINITIONS=List.of(
  new Definition("30110000-0000-4000-8000-000000000001","30110000-0000-4000-8000-000000000011","正式会议纪要",List.of("OVERVIEW","DISCUSSION","CONCLUSION","ACTION","RISK","OPEN_QUESTION","DISAGREEMENT")),
  new Definition("30110000-0000-4000-8000-000000000002","30110000-0000-4000-8000-000000000012","简明总结",List.of("OVERVIEW","CONCLUSION","ACTION")));
 public static List<Definition> definitions(){return DEFINITIONS;}
 public static boolean matches(FullRow row){return row.table()==Table.TEMPLATE_REVISION&&row.nullableString("owner_id")==null&&row.number("revision")==1&&DEFINITIONS.stream().anyMatch(d->d.revisionId().equals(row.string("id"))&&d.templateId().equals(row.string("template_id"))&&d.name().equals(row.string("name"))&&d.document().equals(row.string("themes"))&&d.themes().size()==row.number("theme_count"));}
 public static void install(DataSource source){
  var j=new JdbcTemplate(source);j.setQueryTimeout(2);new ama.db.TransactionRunner(source,10).required(()->{
   for(var d:DEFINITIONS){
    var parents=j.queryForList("SELECT id FROM minutes_template WHERE id=? FOR UPDATE",d.templateId());
    if(parents.isEmpty()){
     var now=j.queryForObject("SELECT UTC_TIMESTAMP(6)",java.time.LocalDateTime.class);
     j.update("INSERT INTO minutes_template(id,owner_id,name,name_normalized,name_hash,current_revision_id,revision,created_at) VALUES (?,NULL,?,?,?,?,1,?)",d.templateId(),d.name(),d.name(),PersonalTemplateDocument.sha(d.name()),d.revisionId(),now);
     j.update("INSERT INTO template_revision(id,template_id,revision,owner_id,name,themes,theme_count,created_at) VALUES (?,?,1,NULL,?,?,?,?)",d.revisionId(),d.templateId(),d.name(),d.document(),d.themes().size(),now);
    }
    var count=j.queryForObject("SELECT COUNT(*) FROM minutes_template t JOIN template_revision r ON r.id=t.current_revision_id WHERE t.id=? AND t.owner_id IS NULL AND t.revision=1 AND r.id=? AND r.template_id=t.id AND r.owner_id IS NULL AND r.revision=1 AND t.name=? AND r.name=? AND r.themes=? AND r.theme_count=?",Integer.class,d.templateId(),d.revisionId(),d.name(),d.name(),d.document(),d.themes().size());
    if(count!=1)throw new IllegalStateException("Immutable builtin template conflict");
   }return null;
  });
 }
 public static List<Map<String,Object>> installedChoices(DataSource source){
  var j=new JdbcTemplate(source);j.setQueryTimeout(2);var choices=new ArrayList<Map<String,Object>>();
  for(var d:DEFINITIONS){var count=j.queryForObject("SELECT COUNT(*) FROM minutes_template t JOIN template_revision r ON r.id=t.current_revision_id WHERE t.id=? AND t.owner_id IS NULL AND r.id=? AND r.name=? AND r.themes=? AND r.theme_count=?",Integer.class,d.templateId(),d.revisionId(),d.name(),d.document(),d.themes().size());if(count==1)choices.add(Map.of("templateId",d.templateId(),"revisionId",d.revisionId(),"name",d.name(),"themes",d.themes(),"themeCount",d.themes().size(),"selectable",true,"default",false));}return choices;
 }
 private BuiltinMinutesTemplates(){}
}
