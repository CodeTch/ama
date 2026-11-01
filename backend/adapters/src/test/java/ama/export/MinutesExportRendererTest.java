package ama.export;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MinutesExportRendererTest {
 private byte[] render(String title,String body,MinutesExportRenderer.Format format)throws IOException{
  return MinutesExportRenderer.render(new MinutesExportDocument(title,body),format,new MinutesExportRenderer.Fonts(null,null),()->false);
 }
 @Test void markdownRetainsExactSavedUnicodeAndBody()throws Exception{
  String body="## 会议决定😀\n\n保留 **原稿** 与 https://example.invalid/。\n";
  assertEquals("# 已保存纪要\n\n"+body,new String(render("已保存纪要",body,MinutesExportRenderer.Format.MD),StandardCharsets.UTF_8));
 }
 @Test void wordContainsRealHeadingsAndTableWithoutExternalRelationships()throws Exception{
  byte[] output=render("会议纪要😀","## 已定事项\n文字 <>& 与特殊字符\n\n| 负责人 | 事项 |\n| --- | --- |\n| 张三 | 按期交付 |",MinutesExportRenderer.Format.DOCX);
  var entries=zip(output);assertTrue(entries.keySet().containsAll(Set.of("[Content_Types].xml","_rels/.rels","word/document.xml")));
  var document=parse(entries.get("word/document.xml"));
  assertTrue(document.getDocumentElement().getTextContent().contains("会议纪要😀"));
  assertTrue(document.getDocumentElement().getTextContent().contains("文字 <>& 与特殊字符"));
  assertEquals(1,document.getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main","tbl").getLength());
  assertEquals(2,document.getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main","tr").getLength());
  assertTrue(new String(entries.get("word/document.xml"),StandardCharsets.UTF_8).contains("Heading2"));
  for(var entry:entries.entrySet())if(entry.getKey().endsWith(".rels"))assertFalse(new String(entry.getValue(),StandardCharsets.UTF_8).contains("TargetMode=\"External\""));
 }
 @Test void pdfExtractsAllContentAcrossRealPages()throws Exception{
  String body=java.util.stream.IntStream.range(0,180).mapToObj(i->"Saved paragraph "+i+" contains original meeting content.").collect(java.util.stream.Collectors.joining("\n\n"));
  byte[] bytes=render("Saved meeting",body,MinutesExportRenderer.Format.PDF);
  try(var pdf=Loader.loadPDF(bytes)){
   assertTrue(pdf.getNumberOfPages()>1);String text=new PDFTextStripper().getText(pdf);
   assertTrue(text.contains("Saved meeting"));
   for(int i=0;i<180;i++)assertTrue(text.contains("Saved paragraph "+i+" contains original meeting content."),"Missing paragraph "+i);
  }
 }
 @Test void repeatedConversionOfTheSameSavedDocumentProducesTheSameBytes()throws Exception{
  for(var format:MinutesExportRenderer.Format.values()){
   byte[] first=render("Saved meeting","## Decision\n\n| Owner | Action |\n| --- | --- |\n| Alice | Verify source |",format);
   byte[] second=render("Saved meeting","## Decision\n\n| Owner | Action |\n| --- | --- |\n| Alice | Verify source |",format);
   assertArrayEquals(first,second,format+" must retain exact bytes for retry validation");
  }
 }
 @Test void externalResourcesStayLiteralInWord()throws Exception{
  String body="![private](file:///etc/passwd)\n<img src=\"http://127.0.0.1:1/private\">\n<script>doNotRun()</script>";
  var parts=zip(render("Untrusted text",body,MinutesExportRenderer.Format.DOCX));
  String text=parse(parts.get("word/document.xml")).getDocumentElement().getTextContent();
  assertTrue(text.contains("file:///etc/passwd"));assertTrue(text.contains("<script>doNotRun()</script>"));assertFalse(text.contains("root:x:"));
  assertFalse(parts.keySet().stream().anyMatch(name->name.startsWith("word/media/")||name.contains("vbaProject")));
 }
 @Test void invalidScalarsAndInputBudgetAreRejectedBeforeRendering(){
  assertThrows(IllegalArgumentException.class,()->new MinutesExportDocument("bad\ud800","body"));
  assertThrows(IllegalArgumentException.class,()->new MinutesExportDocument("title","bad\u0000body"));
  assertThrows(IllegalArgumentException.class,()->new MinutesExportDocument("title","字".repeat(400000)));
 }
 @Test void cancellationAndMissingGlyphNeverReturnPartialOutput(){
  assertThrows(java.util.concurrent.CancellationException.class,()->MinutesExportRenderer.render(new MinutesExportDocument("title","body"),MinutesExportRenderer.Format.DOCX,new MinutesExportRenderer.Fonts(null,null),()->true));
  assertThrows(IOException.class,()->render("中文","不得静默丢字",MinutesExportRenderer.Format.PDF));
 }
 private static Map<String,byte[]> zip(byte[] bytes)throws IOException{
  var result=new LinkedHashMap<String,byte[]>();try(var input=new ZipInputStream(new ByteArrayInputStream(bytes))){for(ZipEntry entry;(entry=input.getNextEntry())!=null;)assertNull(result.put(entry.getName(),input.readAllBytes()));}return result;
 }
 private static org.w3c.dom.Document parse(byte[] bytes)throws Exception{
  var factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
 }
}
