package ama.minutes;

import java.util.*;

/** Pure bounded merge. The caller separately authorizes the meeting, archives and personal page. */
public final class MinutesTemplateCatalog {
 private MinutesTemplateCatalog(){}
 public record Page(List<Map<String,Object>> items,String nextCursor){public Page{items=List.copyOf(items);}}
 public static Page merge(Map<String,Object> builtin,JdbcPersonalTemplates.Page personal,int limit,String after,boolean capable){
  if(limit<1||limit>50||personal.items().size()>limit)throw new IllegalArgumentException("Bounded template page required");
  var choices=new TreeMap<String,Map<String,Object>>();String defaultId=(String)builtin.get("templateId");
  if(after==null||defaultId.compareTo(after)>0)choices.put(defaultId,Map.copyOf(builtin));
  for(var value:personal.items()){
   if(value.deleted()||after!=null&&value.id().compareTo(after)<=0)throw new IllegalArgumentException("Active forward template page required");
   var item=Map.<String,Object>of("templateId",value.id(),"revisionId",value.revisionId(),"name",value.name(),"themes",value.themes(),"themeCount",value.themes().size(),"selectable",capable,"default",false);
   if(choices.putIfAbsent(value.id(),item)!=null)throw new IllegalArgumentException("Duplicate template identity");
  }
  boolean more=choices.size()>limit||personal.nextCursor()!=null;var items=choices.values().stream().limit(limit).toList();
  return new Page(items,more?(String)items.getLast().get("templateId"):null);
 }
}
