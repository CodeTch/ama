package ama.transcript;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class Task11DocumentViewTest {
 static final String ID="12345678-1234-4234-8234-123456789abc",HASH="a".repeat(64);
 static Map<String,Object> fields(Object... pairs){var m=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m;}
 static Map<String,Object> payload(){var sections=new ArrayList<Map<String,Object>>();for(String c:List.of("OVERVIEW","DISCUSSION","CONCLUSION","ACTION","RISK","OPEN_QUESTION","DISAGREEMENT"))sections.add(fields("category",c,"items",List.of()));return fields("schemaVersion","ama.controlled-knowledge-document.v1","title","Title <script>alert(1)</script>","meeting",fields("name","Meeting & 😀","categoryId","category","actualDate",null,"roster",List.of(fields("memberKind","EMPLOYEE","displayName","Person"))),"minutes",fields("revisionId",ID,"title","Title <script>alert(1)</script>","body","</pre><img src=x onerror=alert(1)>\n\"quoted\""),"sections",sections,"provenance",fields("confirmationId",ID,"minutesRevisionId",ID,"elementSetId",ID,"transcriptConfirmationId",ID,"inputFingerprint",HASH,"sourceManifestHash",HASH,"renderArtifact",Map.of("version","v1","sha256",HASH)));}
 static Map<String,Object> manifest(Map<String,Object> payload){return fields("providerId","kb","storeInstanceId","store","publicationId",ID,"confirmationId",ID,"payloadSha256",ControlledKnowledgeClient.digest(ControlledKnowledgeClient.canonical(payload)),"renderArtifact",Map.of("version","v1","sha256",HASH));}
 static Map<String,Object> finalRow(){return fields("id",ID,"minutes_revision_id",ID,"element_set_id",ID,"transcript_confirmation_id",ID,"input_fingerprint",HASH,"source_manifest_hash",HASH);}
 static Map<String,Object> envelope(Map<String,Object> payload,Map<String,Object> manifest){return fields("providerId","kb","storeInstanceId","store","documentId","doc-one","resultId","result-one","publicationId",ID,"payloadSha256",manifest.get("payloadSha256"),"payload",payload);}
 byte[] render(Map<String,Object> response,Map<String,Object> manifest,long limit){return PublicationDocumentView.render(response,manifest,finalRow(),"doc-one","result-one",new Task11AcceptanceBindingTest().budget(false),limit);}
 static Map<String,Object> metadata(){return fields("schema","ama.publication-configuration.v1","configurationVersionToken","9007199254740993","providerId","kb","libraryId","lib","folderId","root","libraryName","纪要库","description","发布验证","visibility","参会人","pathNames",List.of("纪要库","根目录"),"pathIds",List.of(),"tagRules",fields("maxCount",8,"maxLength",64,"allowEmpty",true,"trim",true,"caseSensitive",true),"tags",List.of("中文😀"));}
 @Test void providerLongAndArchivedIntegerConfigurationRenderTheExactSameDocument(){
  var p=PublicationConfigurationData.payload(payload(),metadata());var m=manifest(p);m.put("publicationMetadata",metadata());
  var raw=ControlledKnowledgeClient.canonical(envelope(p,m));var parsed=ControlledKnowledgeReadJson.parse(raw,100000,new Task11AcceptanceBindingTest().budget(false));
  assertNotEquals(p,parsed.get("payload"),"Exercise the real parser's Long versus archived Integer values");
  String html=new String(render(parsed,m,100000),StandardCharsets.UTF_8);assertTrue(html.contains("中文😀"));assertTrue(html.contains("纪要库 / 根目录"));
 }
 @Test void canonicalConfigurationComparisonStillRejectsChangedRulesTagsAndVersion(){
  for(String field:List.of("tagRules","tags","configurationVersionToken","visibility")){
   var original=metadata();var changed=metadata();changed.put(field,switch(field){case "tagRules"->fields("maxCount",9L,"maxLength",64L,"allowEmpty",true,"trim",true,"caseSensitive",true);case "tags"->List.of("中文");case "configurationVersionToken"->"9007199254740992";default->"其他人";});
   var p=PublicationConfigurationData.payload(payload(),changed);var m=manifest(p);m.put("publicationMetadata",original);
   var raw=ControlledKnowledgeClient.canonical(envelope(p,m));var parsed=ControlledKnowledgeReadJson.parse(raw,100000,new Task11AcceptanceBindingTest().budget(false));
   assertThrows(ama.contract.MinutesContracts.Failure.class,()->render(parsed,m,100000),field);
  }
 }
 @Test void immutablePayloadIsEscapedWithoutActiveContentOrRemoteResources(){var payload=payload();var manifest=manifest(payload);String html=new String(render(envelope(payload,manifest),manifest,100000),StandardCharsets.UTF_8);assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));assertTrue(html.contains("&lt;/pre&gt;&lt;img src=x onerror=alert(1)&gt;"));assertTrue(html.contains("Meeting &amp; 😀"));assertFalse(html.contains("<script>"));assertFalse(html.contains("<img"));assertFalse(html.contains("href="));assertFalse(html.contains("storeInstanceId"));}
 @Test void wrongDocumentResultProviderPublicationHashOrUnknownEnvelopeFails(){var p=payload();var m=manifest(p);for(String key:List.of("providerId","storeInstanceId","documentId","resultId","publicationId","payloadSha256","unexpected")){var r=envelope(p,m);r.put(key,"foreign");assertThrows(RuntimeException.class,()->render(r,m,100000),key);}}
 @Test void changedContentAndProvenanceNeverRender(){var p=payload();var m=manifest(p);p.put("title","Changed");var contentChanged=p;assertThrows(RuntimeException.class,()->render(envelope(contentChanged,m),m,100000));p=payload();((Map<String,Object>)p.get("provenance")).put("confirmationId","12345678-1234-4234-8234-123456789abd");var changed=p;var changedManifest=manifest(changed);assertThrows(RuntimeException.class,()->render(envelope(changed,changedManifest),changedManifest,100000));}
 @Test void escapedOutputIsBoundedBeforeEmissionAndPayloadShapeIsExact(){var p=payload();var m=manifest(p);var r=envelope(p,m);assertThrows(ama.contract.MinutesContracts.Failure.class,()->render(r,m,64));p.put("script","evil");var wrong=manifest(p);assertThrows(RuntimeException.class,()->render(envelope(p,wrong),wrong,100000));}
 @Test void cancellationStopsValidationAndRendering(){var p=payload();var m=manifest(p);assertThrows(ama.contract.MinutesContracts.Failure.class,()->PublicationDocumentView.render(envelope(p,m),m,finalRow(),"doc-one","result-one",new Task11AcceptanceBindingTest().budget(true),100000));}
 @Test void escapeExpansionExceedsLimitEvenWhenSameLengthPlainBodyFits(){var p=payload();((Map<String,Object>)p.get("minutes")).put("body","x".repeat(1000));var m=manifest(p);long limit=render(envelope(p,m),m,100000).length+1000;assertDoesNotThrow(()->render(envelope(p,m),m,limit));((Map<String,Object>)p.get("minutes")).put("body","&".repeat(1000));var escaped=manifest(p);assertThrows(ama.contract.MinutesContracts.Failure.class,()->render(envelope(p,escaped),escaped,limit));}
}
