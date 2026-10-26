package ama.transcript;

import java.nio.charset.StandardCharsets;
import java.util.*;
import static ama.contract.MinutesContracts.uuid;

/** Strict data-only request/page protocol; neither a request nor a cursor grants authority. */
public final class PublicationDestinationPage {
 private PublicationDestinationPage() {}
 /** Decode the original query once; servlet replacement decoding must not hide malformed UTF8. */
 public static Map<String,String[]> parameters(String raw) {
  if(raw==null)return Map.of();if(raw.isEmpty()||raw.length()>8192)throw invalid();
  var values=new LinkedHashMap<String,String[]>();
  for(String pair:raw.split("&",-1)) {
   int at=pair.indexOf('=');if(at<=0||at!=pair.lastIndexOf('='))throw invalid();
   String key=decode(pair.substring(0,at),true),value=decode(pair.substring(at+1),true);
   if(values.putIfAbsent(key,new String[]{value})!=null)throw invalid();
  }
  return Collections.unmodifiableMap(values);
 }
 public static void path(String raw) {if(raw==null||raw.length()>8192)throw invalid();String decoded=decode(raw,false);if(decoded.codePoints().anyMatch(c->Character.isISOControl(c)||c==0xfffd)||raw.toLowerCase(Locale.ROOT).matches(".*%(?:2f|5c).*"))throw invalid();}
 private static String decode(String raw,boolean query) {
  var bytes=new java.io.ByteArrayOutputStream(raw.length());
  for(int i=0;i<raw.length();i++) {char c=raw.charAt(i);if(c>127)throw invalid();if(c=='%') {if(i+2>=raw.length())throw invalid();int a=Character.digit(raw.charAt(++i),16),b=Character.digit(raw.charAt(++i),16);if(a<0||b<0)throw invalid();bytes.write(a*16+b);}else bytes.write(c=='+'&&query?' ':c);}
  try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes.toByteArray())).toString();}catch(java.nio.charset.CharacterCodingException bad){throw invalid();}
 }
 public record Query(String meeting, String library, String parent, int limit, String after, String path) {}
 public static Query request(String meeting, String library, Map<String,String[]> parameters) {
  uuid(meeting);
  if(library!=null) id(library);
  var allowed=library==null?Set.of("limit","cursor"):Set.of("limit","cursor","parentFolderId");
  for(var entry:parameters.entrySet()) {
   if(!allowed.contains(entry.getKey())||entry.getValue()==null||entry.getValue().length!=1||entry.getValue()[0]==null||entry.getValue()[0].isEmpty())throw invalid();
  }
  String count=value(parameters,"limit");
  if(count!=null&&!count.matches("[1-9][0-9]?"))throw invalid();
  int limit=count==null?20:Integer.parseInt(count);if(limit>50)throw invalid();
  String parent=value(parameters,"parentFolderId");if(parent!=null)id(parent);
  String cursor=value(parameters,"cursor"),after=null;
  if(cursor!=null) {
   if(cursor.length()>4096)throw invalid();
   byte[] bytes=ControlledKnowledgeClient.unbase64(cursor);
   var decoded=ControlledKnowledgeClient.parse(bytes,3072);
   exact(decoded,Set.of("meetingId","libraryId","parentFolderId","after"));
   if(!Arrays.equals(bytes,ControlledKnowledgeClient.canonical(decoded))||!meeting.equals(decoded.get("meetingId"))||!Objects.equals(library,decoded.get("libraryId"))||!Objects.equals(parent,decoded.get("parentFolderId")))throw invalid();
   after=id(decoded.get("after"));
  }
  String path="/v1/knowledge/libraries"+(library==null?"":"/"+encode(library)+"/folders")+"?limit="+limit;
  if(parent!=null)path+="&parentFolderId="+encode(parent);
  if(after!=null)path+="&cursor="+encode(after);
  if(path.getBytes(StandardCharsets.UTF_8).length>2048)throw invalid();
  return new Query(meeting,library,parent,limit,after,path);
 }
 static Map<String,Object> validate(Query q, Map<String,Object> response, Map<String,String> identities) {
  exact(response,Set.of("providerId","storeInstanceId","items","cursor"));
  for(String field:List.of("providerId","storeInstanceId"))if(!Objects.equals(identities.get(field),response.get(field)))throw invalid();
  if(!(response.get("providerId") instanceof String provider)||!provider.matches("[A-Za-z0-9_.-]{1,64}"))throw invalid();
  if(!(response.get("items") instanceof List<?> items)||items.size()>q.limit())throw invalid();
  var result=new ArrayList<Map<String,Object>>();String previous=q.after();
  for(Object item:items) {
   if(!(item instanceof Map<?,?> raw))throw invalid();
   exact(raw,q.library()==null?Set.of("id","name","rootFolderId"):Set.of("id","libraryId","parentFolderId","isRoot","name"));
   String selected=id(raw.get("id"));text(raw.get("name"),255);
   if(previous!=null&&compare(selected,previous)<=0)throw invalid();previous=selected;
   var copy=new LinkedHashMap<String,Object>();raw.forEach((k,v)->copy.put((String)k,v));
   if(q.library()==null)id(raw.get("rootFolderId"));
   else {
    if(!q.library().equals(raw.get("libraryId"))||!Objects.equals(q.parent(),raw.get("parentFolderId")))throw invalid();
    Object root=raw.get("isRoot");boolean isRoot;
    if(root instanceof Boolean b)isRoot=b;
    else if((root instanceof Integer||root instanceof Long)&&(((Number)root).longValue()==0||((Number)root).longValue()==1))isRoot=((Number)root).longValue()==1;
    else throw invalid();
    if(isRoot!=(q.parent()==null))throw invalid();copy.put("isRoot",isRoot);
   }
   result.add(Collections.unmodifiableMap(copy));
  }
  Object next=response.get("cursor");String cursor=null;
  if(next!=null) {
   if(items.size()!=q.limit()||previous==null||!previous.equals(id(next)))throw invalid();
   var binding=new LinkedHashMap<String,Object>();binding.put("meetingId",q.meeting());binding.put("libraryId",q.library());binding.put("parentFolderId",q.parent());binding.put("after",next);
   cursor=ControlledKnowledgeClient.base64(ControlledKnowledgeClient.canonical(binding));
  }
  var page=new LinkedHashMap<String,Object>();page.put("providerId",response.get("providerId"));page.put("items",List.copyOf(result));page.put("nextCursor",cursor);page.put("hasMore",cursor!=null);return Collections.unmodifiableMap(page);
 }
 private static int compare(String a,String b) {return Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));}
 private static String value(Map<String,String[]> map,String key){return map.containsKey(key)?map.get(key)[0]:null;}
 static String text(Object value,int bound) {
  if(!(value instanceof String s)||s.isBlank()||s.codePointCount(0,s.length())>bound)throw invalid();
  for(int i=0;i<s.length();i++) {char c=s.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=s.length()||!Character.isLowSurrogate(s.charAt(i)))throw invalid();}else if(Character.isLowSurrogate(c)||Character.isISOControl(c))throw invalid();}
  return s;
 }
 static String id(Object value) {String s=text(value,255);if(s.equals(".")||s.equals("..")||s.indexOf('/')>=0||s.indexOf('\\')>=0||s.toLowerCase(Locale.ROOT).matches(".*%(?:2f|5c).*"))throw invalid();return s;}
 static String encode(String value) {var out=new StringBuilder();for(byte b:value.getBytes(StandardCharsets.UTF_8)){int c=b&255;if(c>='a'&&c<='z'||c>='A'&&c<='Z'||c>='0'&&c<='9'||c=='-'||c=='_')out.append((char)c);else out.append('%').append("0123456789ABCDEF".charAt(c>>>4)).append("0123456789ABCDEF".charAt(c&15));}return out.toString();}
 static void exact(Map<?,?> value,Set<String> keys){if(!value.keySet().equals(keys))throw invalid();}
 private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid publication destination page");}
}
