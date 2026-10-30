package ama.material;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MaterialDocumentParserTest {
 private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
 @Test void textUsesOriginalHashAndParagraphLocations()throws Exception{
  byte[] bytes="背景一\n\n背景二".getBytes(StandardCharsets.UTF_8);
  var result=new MaterialDocumentParser().parse(new ByteArrayInputStream(bytes),"background.txt",sha(bytes),new MaterialParser.Budget(1024,1024,10),()->false);
  assertEquals("TXT",result.format());assertEquals(List.of(1L,3L),result.units().stream().map(MaterialParser.Unit::paragraphNo).toList());
  assertEquals(sha(bytes),result.sourceSha256());assertTrue(new String(result.textBytes(),StandardCharsets.UTF_8).contains("背景二"));
 }
 @Test void rejectsMismatchAndNeverReturnsPartialOutput()throws Exception{
  byte[] bytes="真实背景".getBytes(StandardCharsets.UTF_8);
  assertEquals("SOURCE_HASH_MISMATCH",assertThrows(MaterialParser.ParseFailure.class,()->new MaterialDocumentParser().parse(new ByteArrayInputStream(bytes),"a.txt","0".repeat(64),new MaterialParser.Budget(1024,1024,10),()->false)).code());
  assertEquals("SOURCE_BYTES_EXCEEDED",assertThrows(MaterialParser.ParseFailure.class,()->new MaterialDocumentParser().parse(new ByteArrayInputStream(bytes),"a.txt",sha(bytes),new MaterialParser.Budget(2,1024,10),()->false)).code());
 }
 @Test void doesNotGuessPdfFromTextOrIgnoreCancellation()throws Exception{
  byte[] bytes="not pdf".getBytes(StandardCharsets.UTF_8);
  assertEquals("INVALID_DOCUMENT",assertThrows(MaterialParser.ParseFailure.class,()->new MaterialDocumentParser().parse(new ByteArrayInputStream(bytes),"a.pdf",sha(bytes),new MaterialParser.Budget(1024,1024,10),()->false)).code());
  assertEquals("CANCELLED",assertThrows(MaterialParser.ParseFailure.class,()->new MaterialDocumentParser().parse(new ByteArrayInputStream(bytes),"a.txt",sha(bytes),new MaterialParser.Budget(1024,1024,10),()->true)).code());
 }
}
