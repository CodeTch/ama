package ama.transcript;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PublicationDestinationMetadataTest {
 static final String MEETING="12345678-1234-4234-8234-123456789abc";
 static final Map<String,String> IDS=Map.of("providerId","provider","storeInstanceId","store");
 Map<String,Object> library(String id,boolean publish){return Map.of("id",id,"name","项目知识库","rootFolderId",id+"-root","description","项目会议纪要","visibility","项目成员可见","canPublish",publish,"unavailableReason",publish?"":"没有发布权限");}
 Map<String,Object> page(List<?> items,String cursor){var p=new LinkedHashMap<String,Object>();p.putAll(IDS);p.put("items",items);p.put("cursor",cursor);return p;}
 @Test void searchesDescriptionsAtProviderAndBindsCursorToTheSearch(){
  var query=assertDoesNotThrow(()->PublicationDestinationPage.request(MEETING,null,Map.of("search",new String[]{"项目"},"limit",new String[]{"1"})));
  assertTrue(query.path().contains("search=%E9%A1%B9%E7%9B%AE"));
  var result=PublicationDestinationPage.validate(query,page(List.of(library("a",true)),"a"),IDS);
  String cursor=(String)result.get("nextCursor");
  assertDoesNotThrow(()->PublicationDestinationPage.request(MEETING,null,Map.of("search",new String[]{"项目"},"cursor",new String[]{cursor})));
  assertThrows(IllegalArgumentException.class,()->PublicationDestinationPage.request(MEETING,null,Map.of("search",new String[]{"另一个"},"cursor",new String[]{cursor})));
 }
 @Test void preservesBrowsableButNonPublishableLibraryAndItsReason(){
  var query=PublicationDestinationPage.request(MEETING,null,Map.of());
  var result=assertDoesNotThrow(()->PublicationDestinationPage.validate(query,page(List.of(library("a",false)),null),IDS));
  var lib=(Map<?,?>)((List<?>)result.get("items")).getFirst();
  assertEquals(false,lib.get("canPublish"));assertEquals("没有发布权限",lib.get("unavailableReason"));assertEquals("项目成员可见",lib.get("visibility"));
 }
 @Test void refusesPermissionMetadataThatContradictsItsReason(){
  var bad=new HashMap<>(library("a",false));bad.put("unavailableReason","");
  assertThrows(IllegalArgumentException.class,()->PublicationDestinationPage.validate(PublicationDestinationPage.request(MEETING,null,Map.of()),page(List.of(bad),null),IDS));
 }
}
