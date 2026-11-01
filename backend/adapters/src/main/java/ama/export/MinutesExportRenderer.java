package ama.export;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.zip.*;

/** Pure conversion, invoked in the confined child; the caller owns all authorization. */
public final class MinutesExportRenderer {
 public enum Format {MD,PDF,DOCX}
 public record Fonts(Path cjkCollection,Path unicodeTrueType) {}
 public static final int MAX_OUTPUT_BYTES=32*1024*1024,MAX_PAGES=1000;
 private MinutesExportRenderer(){}
 public static byte[] render(MinutesExportDocument document,Format format,Fonts fonts,BooleanSupplier cancelled)throws IOException {
  Objects.requireNonNull(document);Objects.requireNonNull(format);Objects.requireNonNull(fonts);Objects.requireNonNull(cancelled);check(cancelled);
  if(format==Format.MD)return ("# "+document.title()+"\n\n"+document.body()+(document.body().endsWith("\n")?"":"\n")).getBytes(StandardCharsets.UTF_8);
  var blocks=MinutesExportBlocks.parse(document.body(),cancelled);
  if(format==Format.PDF)return MinutesExportPdf.render(document,blocks,fonts,cancelled);
  var xml=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>");
  paragraph(xml,document.title(),"Title");
  for(var block:blocks){check(cancelled);
   if(block instanceof MinutesExportBlocks.Paragraph p)paragraph(xml,p.text(),p.heading()==0?null:"Heading"+p.heading());
   else if(block instanceof MinutesExportBlocks.Table table){
    xml.append("<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/><w:tblBorders>");
    for(String edge:List.of("top","left","bottom","right","insideH","insideV"))xml.append("<w:").append(edge).append(" w:val=\"single\" w:sz=\"4\" w:color=\"999999\"/>");
    xml.append("</w:tblBorders></w:tblPr>");boolean first=true;
    for(var row:table.rows()){check(cancelled);xml.append("<w:tr>");if(first)xml.append("<w:trPr><w:tblHeader/></w:trPr>");for(String cell:row){xml.append("<w:tc>");paragraph(xml,cell,null);xml.append("</w:tc>");}xml.append("</w:tr>");first=false;}
    xml.append("</w:tbl>");
   }
  }
  xml.append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1000\" w:right=\"1000\" w:bottom=\"1000\" w:left=\"1000\"/></w:sectPr></w:body></w:document>");
  var output=new LimitedOutput();try(var zip=new ZipOutputStream(output,StandardCharsets.UTF_8)){
   entry(zip,"[Content_Types].xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/><Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/></Types>");
   entry(zip,"_rels/.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>");
   entry(zip,"word/_rels/document.xml.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rIdStyles\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>");
   entry(zip,"word/styles.xml",styles());entry(zip,"word/document.xml",xml.toString());
  }
  check(cancelled);return output.toByteArray();
 }
 private static String styles(){
  var xml=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Arial\" w:hAnsi=\"Arial\" w:eastAsia=\"Microsoft YaHei\"/><w:sz w:val=\"22\"/></w:rPr></w:rPrDefault></w:docDefaults>");
  for(int i=0;i<=6;i++){String id=i==0?"Title":"Heading"+i;xml.append("<w:style w:type=\"paragraph\" w:styleId=\"").append(id).append("\"><w:name w:val=\"").append(id).append("\"/><w:pPr><w:keepNext/><w:spacing w:before=\"180\" w:after=\"120\"/></w:pPr><w:rPr><w:b/><w:sz w:val=\"").append(i==0?36:Math.max(24,34-i*2)).append("\"/></w:rPr></w:style>");}
  return xml.append("</w:styles>").toString();
 }
 private static void paragraph(StringBuilder xml,String text,String style){
  xml.append("<w:p>");if(style!=null)xml.append("<w:pPr><w:pStyle w:val=\"").append(style).append("\"/></w:pPr>");
  xml.append("<w:r><w:t xml:space=\"preserve\">");escape(xml,text);xml.append("</w:t></w:r></w:p>");
 }
 private static void escape(StringBuilder out,String text){for(int i=0;i<text.length();i++){char c=text.charAt(i);switch(c){case '&'->out.append("&amp;");case '<'->out.append("&lt;");case '>'->out.append("&gt;");case '\r'->out.append("&#13;");default->out.append(c);}}}
 private static void entry(ZipOutputStream zip,String name,String body)throws IOException{var entry=new ZipEntry(name);entry.setTime(0);zip.putNextEntry(entry);zip.write(body.getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
 static void check(BooleanSupplier cancelled){if(Thread.currentThread().isInterrupted()||cancelled.getAsBoolean())throw new CancellationException("Export cancelled");}
 static final class LimitedOutput extends ByteArrayOutputStream {
  private void reserve(int size){if(size<0||size>MAX_OUTPUT_BYTES-count)throw new IllegalArgumentException("Export output budget");}
  @Override public synchronized void write(int value){reserve(1);super.write(value);}
  @Override public synchronized void write(byte[] bytes,int offset,int length){reserve(length);super.write(bytes,offset,length);}
 }
}
