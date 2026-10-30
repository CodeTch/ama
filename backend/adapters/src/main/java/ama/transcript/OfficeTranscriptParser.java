package ama.transcript;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.zip.*;
import javax.xml.stream.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import static ama.transcript.TranscriptDocumentParser.ErrorCode.*;

/** Explicit immutable Office profile. Original file bytes, never converted bytes, identify the source. */
public final class OfficeTranscriptParser implements MeasuredTranscriptDocumentParser {
 public static final String PROFILE_VERSION="ama.office-documents.v1";
 public static final List<String> FORMATS=List.of("TXT","DOC","DOCX","PDF","PPTX");
 private static final long SOURCE_LIMIT=32L*1024*1024,XML_LIMIT=64L*1024*1024;
 public Result parse(InputStream source,UUID fileId,String hash,ParseBudget budget,BooleanSupplier cancelled)throws ParseException{return parseMeasured(source,fileId,hash,budget,cancelled).result();}
 public MeasuredResult parseMeasured(InputStream source,UUID fileId,String hash,ParseBudget budget,BooleanSupplier cancelled)throws ParseException {
  if(source==null||fileId==null||hash==null||!hash.matches("[0-9a-f]{64}")||budget==null||budget.maxSourceBytes()<=0||budget.maxUtf16CodeUnits()<=0||budget.maxSegments()<=0||cancelled==null)throw new ParseException(INVALID_ARGUMENT);
  check(cancelled);byte[] bytes=read(source,Math.min(SOURCE_LIMIT,budget.maxSourceBytes()),cancelled);if(!hash(bytes).equals(hash))throw new ParseException(SOURCE_HASH_MISMATCH);
  try{
   String text;
   if(starts(bytes,new byte[]{'%','P','D','F','-'})){var out=new Text(budget.maxUtf16CodeUnits(),cancelled);try(var pdf=Loader.loadPDF(bytes)){if(pdf.isEncrypted()||!pdf.getCurrentAccessPermission().canExtractContent())throw new ParseException(INVALID_TEXT);if(pdf.getNumberOfPages()>budget.maxSegments())throw new ParseException(SEGMENTS_EXCEEDED);var stripper=new PDFTextStripper();stripper.setSortByPosition(true);for(int page=1;page<=pdf.getNumberOfPages();page++){check(cancelled);stripper.setStartPage(page);stripper.setEndPage(page);stripper.writeText(pdf,out);}}text=out.toString();}
   else if(starts(bytes,new byte[]{(byte)0xd0,(byte)0xcf,0x11,(byte)0xe0,(byte)0xa1,(byte)0xb1,0x1a,(byte)0xe1})){var out=new Text(budget.maxUtf16CodeUnits(),cancelled);try(var doc=new HWPFDocument(new ByteArrayInputStream(bytes))){var range=doc.getRange();for(int i=0;i<range.numParagraphs();i++){check(cancelled);out.write(range.getParagraph(i).text().replace('\u0007','\n'));out.write("\n");}}text=out.toString();}
   else if(starts(bytes,new byte[]{'P','K'}))text=officeXml(bytes,budget,cancelled);
   else {var legacy=new TextTranscriptParser().parseMeasured(new ByteArrayInputStream(bytes),fileId,hash,budget,cancelled);return remap(legacy,hash,fileId,bytes.length);}
   check(cancelled);byte[] extracted=text.getBytes(StandardCharsets.UTF_8);var parsed=new TextTranscriptParser().parseMeasured(new ByteArrayInputStream(extracted),fileId,hash(extracted),new ParseBudget(Math.max(1,extracted.length),budget.maxUtf16CodeUnits(),budget.maxSegments()),cancelled);
   return remap(parsed,hash,fileId,bytes.length);
  }catch(ParseException e){throw e;}catch(Bound e){throw new ParseException(e.code);}catch(InterruptedIOException e){Thread.currentThread().interrupt();throw new ParseException(CANCELLED);}catch(IOException|XMLStreamException|RuntimeException e){check(cancelled);throw new ParseException(INVALID_TEXT);}
 }
 private static MeasuredResult remap(MeasuredResult parsed,String sourceHash,UUID file,long sourceBytes){var segments=parsed.result().segments().stream().map(s->new Segment(stable(file,sourceHash,s.paragraphNo()),file,s.paragraphNo(),s.text(),null,null,null)).toList();return new MeasuredResult(new Result(segments,sourceHash,PROFILE_VERSION),new Usage(sourceBytes,parsed.usage().decodedUtf16CodeUnits(),segments.size()));}
 private static String officeXml(byte[] bytes,ParseBudget budget,BooleanSupplier cancelled)throws IOException,XMLStreamException,ParseException {
  var parts=new TreeMap<Integer,byte[]>();byte[] word=null;long used=0;int count=0;var names=new HashSet<String>();
  try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry entry;while((entry=zip.getNextEntry())!=null){check(cancelled);if(++count>4096||!names.add(entry.getName()))throw new ParseException(INVALID_TEXT);boolean document=entry.getName().equals("word/document.xml"),slide=entry.getName().matches("ppt/slides/slide[1-9][0-9]{0,4}\\.xml");byte[] part=read(zip,XML_LIMIT-used,cancelled);used+=part.length;if(document)word=part;else if(slide){int number=Integer.parseInt(entry.getName().replaceAll("\\D", ""));parts.put(number,part);}zip.closeEntry();}}
  if(word!=null&&!parts.isEmpty()||word==null&&parts.isEmpty())throw new ParseException(INVALID_TEXT);
  var out=new Text(budget.maxUtf16CodeUnits(),cancelled);if(word!=null)xml(word,out,"http://schemas.openxmlformats.org/wordprocessingml/2006/main",cancelled);else{if(parts.size()>budget.maxSegments())throw new ParseException(SEGMENTS_EXCEEDED);for(var slide:parts.values())xml(slide,out,"http://schemas.openxmlformats.org/drawingml/2006/main",cancelled);}return out.toString();
 }
 private static void xml(byte[] bytes,Text out,String namespace,BooleanSupplier cancelled)throws XMLStreamException,IOException,ParseException {
  var factory=XMLInputFactory.newFactory();factory.setProperty(XMLInputFactory.SUPPORT_DTD,false);factory.setProperty("javax.xml.stream.isSupportingExternalEntities",false);factory.setXMLResolver((publicId,systemId,baseUri,n)->{throw new XMLStreamException("External source forbidden");});var reader=factory.createXMLStreamReader(new ByteArrayInputStream(bytes));boolean text=false;
  try{while(reader.hasNext()){check(cancelled);int event=reader.next();if(event==XMLStreamConstants.DTD||event==XMLStreamConstants.ENTITY_REFERENCE)throw new ParseException(INVALID_TEXT);if(event==XMLStreamConstants.START_ELEMENT&&namespace.equals(reader.getNamespaceURI())){String name=reader.getLocalName();if(name.equals("t"))text=true;else if(name.equals("tab"))out.write("\t");else if(name.equals("br"))out.write("\n");}else if((event==XMLStreamConstants.CHARACTERS||event==XMLStreamConstants.CDATA)&&text)out.write(reader.getText());else if(event==XMLStreamConstants.END_ELEMENT&&namespace.equals(reader.getNamespaceURI())){if(reader.getLocalName().equals("t"))text=false;else if(reader.getLocalName().equals("p"))out.write("\n");}}}finally{reader.close();}
 }
 private static byte[] read(InputStream source,long limit,BooleanSupplier cancelled)throws ParseException {if(limit<0)throw new ParseException(SOURCE_BYTES_EXCEEDED);var out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];long used=0;try{while(true){check(cancelled);int count=source.read(buffer,0,(int)Math.min(buffer.length,limit-used+1));check(cancelled);if(count<0)break;if(count>limit-used)throw new ParseException(SOURCE_BYTES_EXCEEDED);out.write(buffer,0,count);used+=count;}return out.toByteArray();}catch(InterruptedIOException e){Thread.currentThread().interrupt();throw new ParseException(CANCELLED);}catch(IOException e){check(cancelled);throw new ParseException(SOURCE_READ_FAILED);}}
 private static boolean starts(byte[] bytes,byte[] prefix){if(bytes.length<prefix.length)return false;for(int i=0;i<prefix.length;i++)if(bytes[i]!=prefix[i])return false;return true;}
 private static String hash(byte[] bytes){return HexFormat.of().formatHex(digest().digest(bytes));}
 private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private static UUID stable(UUID file,String hash,long paragraph){var d=digest();for(String field:List.of(PROFILE_VERSION,file.toString(),hash,Long.toString(paragraph))){byte[] value=field.getBytes(StandardCharsets.UTF_8);d.update(ByteBuffer.allocate(4).putInt(value.length).array());d.update(value);}byte[] result=d.digest();result[6]=(byte)((result[6]&15)|0x80);result[8]=(byte)((result[8]&63)|0x80);var b=ByteBuffer.wrap(result);return new UUID(b.getLong(),b.getLong());}
 private static void check(BooleanSupplier cancelled)throws ParseException{if(Thread.currentThread().isInterrupted()||cancelled.getAsBoolean())throw new ParseException(CANCELLED);}
 private static final class Bound extends IOException {final ErrorCode code;Bound(ErrorCode code){this.code=code;}}
 private static final class Text extends Writer {final long limit;final BooleanSupplier cancelled;final StringBuilder value=new StringBuilder();Text(long limit,BooleanSupplier cancelled){this.limit=limit;this.cancelled=cancelled;}public void write(char[] c,int offset,int length)throws IOException{if(Thread.currentThread().isInterrupted()||cancelled.getAsBoolean())throw new Bound(CANCELLED);if(length>limit-value.length())throw new Bound(UTF16_CODE_UNITS_EXCEEDED);value.append(c,offset,length);}public void flush(){}public void close(){}public String toString(){return value.toString();}}
}
