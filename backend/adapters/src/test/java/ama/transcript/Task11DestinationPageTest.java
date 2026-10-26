package ama.transcript;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class Task11DestinationPageTest {
 private static final String M="12345678-1234-4234-8234-123456789abc";
 @Test void canonicalPathEncodesOnceAndRejectsAmbiguity() {
  var q=PublicationDestinationPage.request(M,"库%名",Map.of("parentFolderId",new String[]{"层 &"},"limit",new String[]{"1"}));
  assertEquals("/v1/knowledge/libraries/%E5%BA%93%25%E5%90%8D/folders?limit=1&parentFolderId=%E5%B1%82%20%26",q.path());
  for(String bad:List.of("", "a/b", "a\\b", "a%2Fb", ".", "..", "x\uD800", "x\n", "a".repeat(256)))assertThrows(IllegalArgumentException.class,()->PublicationDestinationPage.request(M,bad,Map.of()),bad);
 }
 @Test void queryIsExactAndLossless() {
  assertEquals("/v1/knowledge/libraries?limit=20",PublicationDestinationPage.request(M,null,Map.of()).path());
  for(String value:List.of("0","51","01","+1","1.0","", " 1"))assertThrows(IllegalArgumentException.class,()->PublicationDestinationPage.request(M,null,Map.of("limit",new String[]{value})));
  for(var query:List.of(Map.of("actorId",new String[]{"victim"}),Map.of("limit",new String[]{"1","2"}),Map.of("cursor",new String[]{""}),Map.of("parentFolderId",new String[]{"p"})))assertThrows(IllegalArgumentException.class,()->PublicationDestinationPage.request(M,null,query));
 }
 @Test void exactPageAndCursorBindRouteAndParent() {
  var q=PublicationDestinationPage.request(M,"lib",Map.of("limit",new String[]{"1"},"parentFolderId",new String[]{"p"}));
  var item=new LinkedHashMap<String,Object>();item.put("id","f");item.put("libraryId","lib");item.put("parentFolderId","p");item.put("isRoot",0);item.put("name","Folder");
  var raw=new LinkedHashMap<String,Object>();raw.put("providerId","kb");raw.put("storeInstanceId","store");raw.put("items",List.of(item));raw.put("cursor","f");
  var result=PublicationDestinationPage.validate(q,raw,Map.of("providerId","kb","storeInstanceId","store"));
  assertEquals(false,((Map<?,?>)((List<?>)result.get("items")).getFirst()).get("isRoot"));
  String cursor=(String)result.get("nextCursor");assertNotNull(cursor);
  assertTrue(PublicationDestinationPage.request(M,"lib",Map.of("limit",new String[]{"1"},"parentFolderId",new String[]{"p"},"cursor",new String[]{cursor})).path().endsWith("&cursor=f"));
  assertThrows(IllegalArgumentException.class,()->PublicationDestinationPage.request(M,"lib",Map.of("parentFolderId",new String[]{"other"},"cursor",new String[]{cursor})));
  item.put("libraryId","foreign");assertThrows(IllegalArgumentException.class,()->PublicationDestinationPage.validate(q,raw,Map.of("providerId","kb","storeInstanceId","store")));
 }
 @Test void rejectsWrongEnvelopeUnknownFieldsAndOversizedOrUnorderedPage() {
  var q=PublicationDestinationPage.request(M,null,Map.of("limit",new String[]{"1"}));
  var row=Map.of("id","lib","name","Library","rootFolderId","root");
  var raw=new LinkedHashMap<String,Object>();raw.put("providerId","kb");raw.put("storeInstanceId","store");raw.put("items",List.of(row));raw.put("cursor",null);
  assertEquals(1,((List<?>)PublicationDestinationPage.validate(q,raw,Map.of("providerId","kb","storeInstanceId","store")).get("items")).size());
  raw.put("items",List.of(row,row));assertThrows(IllegalArgumentException.class,()->PublicationDestinationPage.validate(q,raw,Map.of("providerId","kb","storeInstanceId","store")));
  raw.put("items",List.of(row));raw.put("url","https://bad");assertThrows(IllegalArgumentException.class,()->PublicationDestinationPage.validate(q,raw,Map.of("providerId","kb","storeInstanceId","store")));
  raw.remove("url");raw.put("providerId","foreign");assertThrows(IllegalArgumentException.class,()->PublicationDestinationPage.validate(q,raw,Map.of("providerId","kb","storeInstanceId","store")));
 }
}
