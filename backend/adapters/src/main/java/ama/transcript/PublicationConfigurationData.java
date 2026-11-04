package ama.transcript;

import java.util.*;

/** Data-only provider rules and immutable publication metadata; no authority is minted here. */
final class PublicationConfigurationData {
 private PublicationConfigurationData(){}
 static Map<String,Object> rules(Object value){
  var rules=object(value);PublicationDestinationPage.exact(rules,Set.of("maxCount","maxLength","allowEmpty","trim","caseSensitive"));
  for(String key:List.of("maxCount","maxLength")){Object n=rules.get(key);if(!(n instanceof Integer||n instanceof Long)||((Number)n).longValue()<1||((Number)n).longValue()>256)throw invalid();}
  for(String key:List.of("allowEmpty","trim","caseSensitive"))if(!(rules.get(key) instanceof Boolean))throw invalid();
  return rules;
 }
 static List<String> tags(Object value,Map<String,Object> rule){
  rules(rule);if(!(value instanceof List<?> items)||items.size()>256)throw invalid();
  var result=new ArrayList<String>();var seen=new HashSet<String>();
  for(Object item:items){if(!(item instanceof String text))throw invalid();String tag=Boolean.TRUE.equals(rule.get("trim"))?text.strip():text;
   PublicationDestinationPage.text(tag,((Number)rule.get("maxLength")).intValue());
   String identity=Boolean.TRUE.equals(rule.get("caseSensitive"))?tag:tag.toLowerCase(Locale.ROOT);
   if(seen.add(identity))result.add(tag);
  }
  if(result.size()>((Number)rule.get("maxCount")).intValue()||result.isEmpty()&&!Boolean.TRUE.equals(rule.get("allowEmpty")))throw invalid();
  return List.copyOf(result);
 }
 static Map<String,Object> location(Map<String,Object> raw,Map<String,String> identities,String library,String folder){
  PublicationDestinationPage.exact(raw,Set.of("providerId","storeInstanceId","libraryId","folderId","libraryName","description","visibility","pathNames","pathIds","tagRules"));
  for(String key:List.of("providerId","storeInstanceId"))if(!Objects.equals(identities.get(key),raw.get(key)))throw invalid();
  if(!Objects.equals(library,raw.get("libraryId"))||!Objects.equals(folder,raw.get("folderId")))throw invalid();
  PublicationDestinationPage.id(library);PublicationDestinationPage.id(folder);PublicationDestinationPage.text(raw.get("libraryName"),255);PublicationDestinationPage.optionalText(raw.get("description"),2048);PublicationDestinationPage.text(raw.get("visibility"),1024);
  if(!(raw.get("pathNames") instanceof List<?> names)||!(raw.get("pathIds") instanceof List<?> ids)||ids.size()>127||names.size()!=ids.size()+2||!raw.get("libraryName").equals(names.getFirst()))throw invalid();
  names.forEach(x->PublicationDestinationPage.text(x,255));ids.forEach(PublicationDestinationPage::id);
  if(new HashSet<>(ids).size()!=ids.size()||!ids.isEmpty()&&!folder.equals(ids.getLast()))throw invalid();rules(raw.get("tagRules"));return Collections.unmodifiableMap(new TreeMap<>(raw));
 }
 static Map<String,Object> metadata(Map<String,Object> location,String version,Object tags){
  var result=new TreeMap<String,Object>(location);result.remove("storeInstanceId");result.put("schema","ama.publication-configuration.v1");result.put("configurationVersionToken",version);result.put("tags",tags(tags,rules(location.get("tagRules"))));validate(result);return Collections.unmodifiableMap(result);
 }
 static Map<String,Object> validate(Object value){
  var m=object(value);PublicationDestinationPage.exact(m,Set.of("schema","configurationVersionToken","providerId","libraryId","folderId","libraryName","description","visibility","pathNames","pathIds","tagRules","tags"));
  if(!"ama.publication-configuration.v1".equals(m.get("schema"))||!(m.get("configurationVersionToken") instanceof String version)||!version.matches("[1-9][0-9]{0,18}")||Long.parseLong(version)>=Long.MAX_VALUE)throw invalid();
  var loc=new TreeMap<>(m);for(String key:List.of("schema","configurationVersionToken","tags"))loc.remove(key);loc.put("storeInstanceId","metadata");
  location(loc,Map.of("providerId",PublicationDestinationPage.text(m.get("providerId"),64),"storeInstanceId","metadata"),(String)m.get("libraryId"),(String)m.get("folderId"));
  if(!tags(m.get("tags"),rules(m.get("tagRules"))).equals(m.get("tags")))throw invalid();return Collections.unmodifiableMap(m);
 }
 static Map<String,Object> payload(Map<String,Object> frozen,Object metadata){
  if(metadata==null)return frozen;var result=new TreeMap<String,Object>(frozen);result.put("schemaVersion","ama.controlled-knowledge-document.v2");result.put("publicationMetadata",validate(metadata));return Collections.unmodifiableMap(result);
 }
 @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value){if(!(value instanceof Map<?,?> map)||map.keySet().stream().anyMatch(k->!(k instanceof String)))throw invalid();return (Map<String,Object>)map;}
 private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid publication configuration or tags");}
}
