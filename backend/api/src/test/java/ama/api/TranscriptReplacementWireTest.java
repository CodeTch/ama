package ama.api;

import ama.contract.TranscriptContracts.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TranscriptReplacementWireTest {
 static final String REV="00000000-0000-4000-8000-000000000001";
 static final String PREFIX="\"expectedRevisionToken\":\"9007199254740993\",\"sourceRevisionId\":\""+REV+"\"";
 Command parse(String operation,String body)throws Exception {byte[] raw=body.getBytes(StandardCharsets.UTF_8);return TranscriptWire.parse(new ByteArrayInputStream(raw),raw.length,Operation.valueOf(operation));}
 @Test void selectedReplacementHasExactRevisionLiteralAndStableOccurrenceIdentifiers()throws Exception {
  var command=parse("REPLACE","{"+PREFIX+",\"find\":\"最大回车😀\",\"replacement\":\"最大回撤\",\"occurrenceIds\":[\""+"a".repeat(64)+"\"]}");
  assertEquals(9007199254740993L,command.expectedRevision());assertEquals(REV,command.sourceRevisionId());assertTrue(command.segments().isEmpty());
  assertEquals("最大回车😀",command.replacement().find());assertEquals("最大回撤",command.replacement().replacement());assertEquals(java.util.List.of("a".repeat(64)),command.replacement().occurrenceIds());
  var empty=parse("REPLACE","{"+PREFIX+",\"find\":\"甲\",\"replacement\":\"\",\"occurrenceIds\":[\""+"b".repeat(64)+"\"]}");assertEquals("",empty.replacement().replacement());
 }
 @Test void replacementRejectsUnknownDuplicateMissingOrForeignShape()throws Exception {
  String good="{"+PREFIX+",\"find\":\"甲\",\"replacement\":\"乙\",\"occurrenceIds\":[\""+"a".repeat(64)+"\"]}";
  for(String bad:new String[]{good.replace("\"find\":\"甲\"","\"find\":\"\""),good.replace("\"find\":\"甲\"","\"find\":\"甲\",\"find\":\"甲\""),good.replace(",\"occurrenceIds\":[\""+"a".repeat(64)+"\"]",""),good.replace("\"occurrenceIds\":[\""+"a".repeat(64)+"\"]","\"occurrenceIds\":[\""+"a".repeat(64)+"\",\""+"a".repeat(64)+"\"]"),good.replace("\"occurrenceIds\":[\""+"a".repeat(64)+"\"]","\"occurrenceIds\":[]"),good.replace("\"find\":\"甲\"","\"segments\":[]"),good+"{}"}){
   var failure=assertThrows(Exception.class,()->parse("REPLACE",bad),bad);assertTrue(failure instanceof IllegalArgumentException||failure instanceof ama.contract.MinutesContracts.Failure||failure instanceof Failure,failure.toString());
  }
  assertThrows(Exception.class,()->parse("RESTORE",good));assertThrows(Exception.class,()->parse("UNDO",good));
 }
 @Test void undoCarriesOnlyTheCurrentOperationRevisionAndExpectedVersion()throws Exception {
  var command=parse("UNDO","{"+PREFIX+"}");assertEquals(REV,command.sourceRevisionId());assertNull(command.replacement());assertTrue(command.segments().isEmpty());
 }
}
