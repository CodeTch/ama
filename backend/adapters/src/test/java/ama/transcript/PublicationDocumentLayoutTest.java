package ama.transcript;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static ama.transcript.Task11DocumentViewTest.*;
class PublicationDocumentLayoutTest {
 static Map<String,Object> metadata(){return fields("schema","ama.publication-configuration.v1","configurationVersionToken","1","providerId","kb","libraryId","library","folderId","folder","libraryName","研发知识库","description","研发资料","visibility","研发成员可见","pathNames",List.of("研发知识库","根目录","评审"),"pathIds",List.of("folder"),"tags",List.of("验收"),"tagRules",Map.of("maxCount",3,"maxLength",20,"allowEmpty",true,"trim",true,"caseSensitive",true));}
 @Test void frozenV2MetadataRendersPrototypeInformationAndHistoryWithoutInventedCounts(){
  var payload=PublicationConfigurationData.payload(payload(),metadata());var manifest=manifest(payload);manifest.put("publicationMetadata",metadata());
  var confirmation=finalRow();confirmation.put("confirmed_by","确认人");confirmation.put("confirmed_at","2026-11-03T01:02:03Z");
  String html=new String(PublicationDocumentView.render(envelope(payload,manifest),manifest,confirmation,"doc-one","result-one",new Task11AcceptanceBindingTest().budget(false),100000),StandardCharsets.UTF_8);
  for(String text:List.of("文档信息","历史版本","研发知识库 / 根目录 / 评审","研发成员可见","验收","确认人","2026-11-03T01:02:03Z","doc-one"))assertTrue(html.contains(text),text);
  assertFalse(html.contains("100%"));assertFalse(html.contains("截止日期：未填写"));assertTrue(html.contains("grid-template-columns:minmax(0,1.5fr) minmax(0,1fr)"));
 }
 @Test void payloadMetadataMustMatchTheSubmittedManifest(){var payload=PublicationConfigurationData.payload(payload(),metadata());var manifest=manifest(payload);var different=new LinkedHashMap<>(metadata());different.put("tags",List.of("other"));manifest.put("publicationMetadata",different);assertThrows(RuntimeException.class,()->new Task11DocumentViewTest().render(envelope(payload,manifest),manifest,100000));}
}
