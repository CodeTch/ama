package ama.knowledge;

import ama.contract.ConfigArchiveContracts.ArchiveBytes;
import ama.contract.MinutesContracts.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import static ama.contract.MinutesContracts.*;
import static org.junit.jupiter.api.Assertions.*;

/** Pure saved-document projection; these rows do not attest a final confirmation or permit sending. */
class PersonalTemplateDocumentRendererTest {
 String id(int n){return "00000000-0000-4000-8000-"+String.format("%012d",n);}
 FullRow row(Table table,Object... values){var cells=new ArrayList<Cell>();for(Object value:values)cells.add(value==null?new NullCell():value instanceof Long n?cell(n):cell((String)value));return new FullRow(table,cells);}
 byte[] render(String renderer,String owner,long count)throws Exception{
  String time="2026-10-31T00:00:00.000000Z",hash="a".repeat(64);
  var rows=List.of(row(Table.TEMPLATE_REVISION,id(9),id(8),1L,owner,"自定义模板","{\"schemaVersion\":\"ama.personal-minutes-template.v1\",\"themes\":[\"决定\"]}",count,time),row(Table.MINUTES_REVISION,id(13),id(1),1L,null,"已保存标题","HUMAN","原文😀\n已保存。",null,"owner",time),row(Table.FINAL_CONFIRMATION,id(20),id(1),1L,id(21),id(22),id(7),id(13),null,id(11),id(9),id(10),hash,hash,"owner",time));
  byte[] raw="{}".getBytes(StandardCharsets.UTF_8);String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));var artifact=new Artifact("personal-render",sha,ArchiveBytes.capture(raw,65536,65536,()->false));
  var facts=new StoredProvenanceFacts(rows,List.of(),List.of(),List.of(),100000L);
  return new ControlledKnowledgeDocumentRenderer().render(new FinalFacts(id(20),facts),new ControlledKnowledgeDocumentRenderer.Meeting("会议","category",null,List.of()),renderer,id(99),artifact,8388608,()->false);
 }
 @Test void explicitPersonalRendererPreservesSavedTextWithoutRequiringTheDefaultTemplateId()throws Exception{
  String json=new String(assertDoesNotThrow(()->render("ama.controlled-minutes-render.v2","owner",1)),StandardCharsets.UTF_8);
  String encoded=new String(ama.config.CanonicalJson.encode(ama.canonical.CanonicalValue.object(ama.canonical.CanonicalValue.field("body",ama.canonical.CanonicalValue.string("原文😀\n已保存。"))),65536,()->false),StandardCharsets.UTF_8);
  assertTrue(json.contains(encoded.substring(1,encoded.length()-1)));assertTrue(json.contains("\"title\":\"已保存标题\""));
 }
 @Test void personalProjectionDoesNotRelaxLegacyOrForeignTemplateRules(){
  assertThrows(Failure.class,()->render(ControlledKnowledgeDocumentRenderer.VERSION,"owner",1));
  assertThrows(Failure.class,()->render("ama.controlled-minutes-render.v2","foreign",1));
  assertThrows(Failure.class,()->render("ama.controlled-minutes-render.v3","owner",1));
 }
 @Test void personalProjectionKeepsTheOneToEightThemeBound(){
  for(long count:List.of(0L,9L))assertThrows(Failure.class,()->render("ama.controlled-minutes-render.v2","owner",count));
 }
}
