package ama.minutes;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PersonalTemplateCatalogTest {
 String id(int number){return "00000000-0000-4000-8000-"+String.format("%012d",number);}
 Map<String,Object> builtin(){return Map.of("templateId",id(20),"revisionId",id(120),"name","Controlled minutes","themes",List.of("OVERVIEW","DISCUSSION","CONCLUSION","ACTION","RISK","OPEN_QUESTION","DISAGREEMENT"),"themeCount",7,"selectable",true,"default",true);}
 JdbcPersonalTemplates.View personal(int number){return new JdbcPersonalTemplates.View(id(number),id(number+100),"1","个人模板"+number,List.of("会议决定"),false);}
 @Test void mergedDefaultAndPersonalPagesRemainOrderedAndNeverLoseTheLastUnreturnedChoice(){
  var first=MinutesTemplateCatalog.merge(builtin(),new JdbcPersonalTemplates.Page(List.of(personal(10),personal(30)),null),2,null,true);
  assertEquals(List.of(id(10),id(20)),first.items().stream().map(row->row.get("templateId")).toList());assertEquals(id(20),first.nextCursor());
  var second=MinutesTemplateCatalog.merge(builtin(),new JdbcPersonalTemplates.Page(List.of(personal(30)),null),2,first.nextCursor(),true);
  assertEquals(List.of(id(30)),second.items().stream().map(row->row.get("templateId")).toList());assertNull(second.nextCursor());
 }
 @Test void personalChoicesStayUnselectableUnlessTheInstalledProtocolExplicitlySupportsThem(){
  for(boolean capable:List.of(false,true)){var page=MinutesTemplateCatalog.merge(builtin(),new JdbcPersonalTemplates.Page(List.of(personal(10)),null),20,null,capable);var personal=page.items().getFirst();assertEquals(capable,personal.get("selectable"));assertEquals(false,personal.get("default"));assertEquals(1,personal.get("themeCount"));assertEquals(List.of("会议决定"),personal.get("themes"));assertEquals(builtin(),page.items().getLast());}
 }
}
