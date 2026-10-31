package ama.transcript;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PublicationLibraryContextTest {
 static final String DOC="doc-"+"a".repeat(32),RESULT="result-"+"b".repeat(32);
 Map<String,Object> manifest(){return Map.of("providerId","provider","storeInstanceId","store","publicationId",Task11DocumentViewTest.ID,"payloadSha256",Task11DocumentViewTest.HASH,"libraryId","library","folderId","folder");}
 Map<String,Object> response(){var raw=new LinkedHashMap<String,Object>(manifest());raw.putAll(Map.of("viewerId","viewer","documentId",DOC,"resultId",RESULT,"libraryName","真实知识库","libraryDescription","","folderPath",List.of("真实根目录","真实子目录"),"readGrantVersionToken","2"));raw.put("locationVersion",ControlledKnowledgeClient.digest(ControlledKnowledgeClient.canonical(raw)));return raw;}
 void rehash(Map<String,Object> raw){raw.remove("locationVersion");raw.put("locationVersion",ControlledKnowledgeClient.digest(ControlledKnowledgeClient.canonical(raw)));}
 Map<String,Object> validate(Map<String,Object> raw){return PublicationLibraryContext.validate(raw,manifest(),"viewer",DOC,RESULT,new Task11AcceptanceBindingTest().budget(false));}
 @Test void onlyExactViewerAndPublishedDestinationYieldActualMetadataAndVersion(){var raw=response();var view=validate(raw);assertEquals("真实知识库",view.get("libraryName"));assertEquals(raw.get("locationVersion"),view.get("versionToken"));assertEquals(List.of("真实根目录","真实子目录"),view.get("folderPath"));assertFalse(view.containsKey("readGrantVersionToken"));assertFalse(view.containsKey("payload"));}
 @Test void everyImmutableAndViewerBindingIsRequiredEvenWithRecomputedVersion(){for(String key:List.of("providerId","storeInstanceId","publicationId","payloadSha256","libraryId","folderId","viewerId","documentId","resultId")){var raw=response();raw.put(key,"foreign");rehash(raw);assertThrows(RuntimeException.class,()->validate(raw),key);}}
 @Test void unknownFieldsOrUnverifiedVersionAreRejected(){var extra=response();extra.put("extra",true);rehash(extra);assertThrows(RuntimeException.class,()->validate(extra));var wrong=response();wrong.put("libraryName","Changed after version");assertThrows(RuntimeException.class,()->validate(wrong));}
 @Test void boundedNonemptyPathAndLosslessCurrentGrantVersionAreRequired(){for(Object path:List.of(List.of(),Collections.nCopies(33,"folder"),List.of(""),List.of("x".repeat(1025)),"folder")){var raw=response();raw.put("folderPath",path);rehash(raw);assertThrows(RuntimeException.class,()->validate(raw));}for(Object version:List.of("0","02","-1","9223372036854775808",2)){var raw=response();raw.put("readGrantVersionToken",version);rehash(raw);assertThrows(RuntimeException.class,()->validate(raw));}}
 @Test void cancellationAndCumulativeBudgetApplyToCanonicalMetadata(){var raw=response();assertThrows(RuntimeException.class,()->PublicationLibraryContext.validate(raw,manifest(),"viewer",DOC,RESULT,new Task11AcceptanceBindingTest().budget(true)));}
}
