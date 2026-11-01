package ama.material;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class MaterialParseProcessTest {
 @TempDir Path root;
 final MaterialParser.Budget budget=new MaterialParser.Budget(1024*1024,1024*1024,100);
 @Test void confinedProcessRetainsExactHashTextAndCoordinatesAndCleansItsWorkspace()throws Exception{
  byte[] bytes="背景一\n\n背景二".getBytes(java.nio.charset.StandardCharsets.UTF_8);String hash=MaterialDocumentParser.sha(bytes);
  var result=new MaterialParseProcess(root,Duration.ofSeconds(10)).parse(new ByteArrayInputStream(bytes),"background.txt",hash,budget,()->false);
  assertEquals(hash,result.sourceSha256());assertEquals("TXT",result.format());assertEquals(List.of(1L,3L),result.units().stream().map(MaterialParser.Unit::paragraphNo).toList());assertEquals("背景一\n\n背景二",new String(result.textBytes(),java.nio.charset.StandardCharsets.UTF_8));try(var paths=Files.list(root)){assertEquals(0,paths.count());}
 }
 @Test void cancellationDeadlineAndWrongHashNeverReturnPartialText()throws Exception{
  byte[] bytes="source".getBytes();var parser=new MaterialParseProcess(root,Duration.ofSeconds(10));
  assertEquals("CANCELLED",assertThrows(MaterialParser.ParseFailure.class,()->parser.parse(new ByteArrayInputStream(bytes),"a.txt",MaterialDocumentParser.sha(bytes),budget,()->true)).code());
  assertEquals("SOURCE_HASH_MISMATCH",assertThrows(MaterialParser.ParseFailure.class,()->parser.parse(new ByteArrayInputStream(bytes),"a.txt","0".repeat(64),budget,()->false)).code());
  assertEquals("PARSE_TIMEOUT",assertThrows(MaterialParser.ParseFailure.class,()->new MaterialParseProcess(root,Duration.ofMillis(1)).parse(new ByteArrayInputStream(bytes),"a.txt",MaterialDocumentParser.sha(bytes),budget,()->false)).code());
  try(var paths=Files.list(root)){assertEquals(0,paths.count());}
 }
 @Test void actualChildCannotReadAmbientFilesConnectOrSpawn()throws Exception{
  Path input=root.resolve("input"),output=root.resolve("output"),secret=root.resolve("secret"),policy=root.resolve("policy");Files.writeString(input,"OWNED_INPUT");Files.writeString(secret,"not readable");
  String cp=MaterialParseProcess.classpath()+File.pathSeparator+Path.of(MaterialParseIsolationProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI());Files.writeString(policy,MaterialParseProcess.policy(cp,input,output));
  var builder=new ProcessBuilder(MaterialParseProcess.command(cp,policy,MaterialParseIsolationProbe.class.getName(),List.of(input.toString(),output.toString(),secret.toString()))).redirectError(ProcessBuilder.Redirect.DISCARD).redirectOutput(ProcessBuilder.Redirect.DISCARD);builder.environment().clear();var process=builder.start();try{assertTrue(process.waitFor(10,java.util.concurrent.TimeUnit.SECONDS));assertEquals(0,process.exitValue());assertEquals("OWNED_INPUT|FILE_DENIED|NETWORK_DENIED|EXEC_DENIED|ENV_DENIED",Files.readString(output));}finally{if(process.isAlive())process.destroyForcibly();}
 }
 @Test void pdfAndStructuredOfficePackagesKeepOriginalPageAndParagraphLocators()throws Exception{
  var parser=new MaterialParseProcess(root,Duration.ofSeconds(10));byte[] pdf;
  try(var document=new org.apache.pdfbox.pdmodel.PDDocument();var bytes=new ByteArrayOutputStream()){
   for(String text:List.of("First page","Second page")){var page=new org.apache.pdfbox.pdmodel.PDPage();document.addPage(page);try(var stream=new org.apache.pdfbox.pdmodel.PDPageContentStream(document,page)){stream.beginText();stream.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),12);stream.newLineAtOffset(60,700);stream.showText(text);stream.endText();}}
   document.save(bytes);pdf=bytes.toByteArray();}
  var pages=parse(parser,pdf,"sample.pdf");assertEquals("PDF",pages.format());assertEquals(List.of(1L,2L),pages.units().stream().map(MaterialParser.Unit::pageNo).toList());assertTrue(pages.units().get(1).body().contains("Second page"));
  String word="<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>第一段</w:t></w:r></w:p><w:p><w:r><w:t>第二段</w:t></w:r></w:p></w:body></w:document>";
  byte[] docx=zip(Map.of("word/document.xml",word));var paragraphs=parse(parser,docx,"sample.docx");assertEquals("WORD",paragraphs.format());assertEquals(List.of(1L,2L),paragraphs.units().stream().map(MaterialParser.Unit::paragraphNo).toList());assertEquals("第二段",paragraphs.units().get(1).body());
  String rel="http://schemas.openxmlformats.org/officeDocument/2006/relationships",presentation="<p:presentation xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\" xmlns:r=\""+rel+"\"><p:sldIdLst><p:sldId id=\"256\" r:id=\"rId2\"/><p:sldId id=\"257\" r:id=\"rId1\"/></p:sldIdLst></p:presentation>";
  String relationships="<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\""+rel+"/slide\" Target=\"slides/slide1.xml\"/><Relationship Id=\"rId2\" Type=\""+rel+"/slide\" Target=\"slides/slide2.xml\"/></Relationships>";
  String slide="<p:sld xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"><a:p><a:r><a:t>TEXT</a:t></a:r></a:p></p:sld>";
  byte[] pptx=zip(Map.of("ppt/presentation.xml",presentation,"ppt/_rels/presentation.xml.rels",relationships,"ppt/slides/slide1.xml",slide.replace("TEXT","第二页"),"ppt/slides/slide2.xml",slide.replace("TEXT","第一页")));var slides=parse(parser,pptx,"sample.pptx");assertEquals("PPT",slides.format());assertEquals(List.of(1L,2L),slides.units().stream().map(MaterialParser.Unit::pageNo).toList());assertEquals(List.of("第一页","第二页"),slides.units().stream().map(MaterialParser.Unit::body).toList());
 }
 @Test void emptyScansMalformedXmlAndExcessiveExpansionNeverBecomeSuccessfulEmptyText()throws Exception{
  var parser=new MaterialParseProcess(root,Duration.ofSeconds(10));byte[] scan;try(var document=new org.apache.pdfbox.pdmodel.PDDocument();var bytes=new ByteArrayOutputStream()){document.addPage(new org.apache.pdfbox.pdmodel.PDPage());document.save(bytes);scan=bytes.toByteArray();}
  assertEquals("NO_TEXT",assertThrows(MaterialParser.ParseFailure.class,()->parse(parser,scan,"scan.pdf")).code());
  byte[] malformed=zip(Map.of("word/document.xml","<!DOCTYPE x [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><x>&x;</x>"));assertEquals("INVALID_DOCUMENT",assertThrows(MaterialParser.ParseFailure.class,()->parse(parser,malformed,"bad.docx")).code());
  byte[] bomb=bomb();var failure=assertThrows(MaterialParser.ParseFailure.class,()->parse(parser,bomb,"bomb.docx"));assertEquals("ARCHIVE_EXPANSION_EXCEEDED",failure.code());
 }
 MaterialParser.Result parse(MaterialParser parser,byte[] bytes,String name)throws Exception{return parser.parse(new ByteArrayInputStream(bytes),name,MaterialDocumentParser.sha(bytes),budget,()->false);}
 static byte[] bomb()throws IOException{var bytes=new ByteArrayOutputStream();byte[] block=new byte[8192];Arrays.fill(block,(byte)'x');try(var zip=new java.util.zip.ZipOutputStream(bytes)){zip.putNextEntry(new java.util.zip.ZipEntry("word/document.xml"));for(int i=0;i<8193;i++)zip.write(block);zip.closeEntry();}return bytes.toByteArray();}
 static byte[] zip(Map<String,String> files)throws IOException{var bytes=new ByteArrayOutputStream();try(var zip=new java.util.zip.ZipOutputStream(bytes)){for(var entry:files.entrySet()){zip.putNextEntry(new java.util.zip.ZipEntry(entry.getKey()));zip.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();}}return bytes.toByteArray();}

}
