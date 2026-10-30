package ama.material;

import java.io.*;
import java.nio.charset.*;
import java.security.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.zip.*;
import javax.xml.stream.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

/** Bounded document parser retaining real page/paragraph coordinates. Scans without OCR fail honestly. */
public final class MaterialDocumentParser implements MaterialParser {
 private static final String WORD="http://schemas.openxmlformats.org/wordprocessingml/2006/main",DRAW="http://schemas.openxmlformats.org/drawingml/2006/main",REL="http://schemas.openxmlformats.org/officeDocument/2006/relationships";
 public Result parse(InputStream source,String name,String expected,Budget budget,BooleanSupplier stop)throws ParseFailure {
  if(source==null||name==null||expected==null||!expected.matches("[0-9a-f]{64}")||budget==null||stop==null)throw new IllegalArgumentException("Exact material source required");
  check(stop);byte[] bytes=read(source,Math.min(32L*1024*1024,budget.sourceBytes()),stop);String hash=sha(bytes);if(!hash.equals(expected))throw new ParseFailure("SOURCE_HASH_MISMATCH");
  String ext=name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);var out=new Units(budget,stop);String format;
  try {
   switch(ext){
    case "txt"->{format="TXT";String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();if(text.startsWith("\ufeff"))text=text.substring(1);out.paragraphs(text);}
    case "pdf"->{format="PDF";if(bytes.length<5||!new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-"))throw new ParseFailure("INVALID_DOCUMENT");try(var pdf=Loader.loadPDF(bytes)){if(pdf.isEncrypted()||!pdf.getCurrentAccessPermission().canExtractContent())throw new ParseFailure("DOCUMENT_ENCRYPTED");if(pdf.getNumberOfPages()>budget.units())throw new ParseFailure("UNITS_EXCEEDED");var stripper=new PDFTextStripper();stripper.setSortByPosition(true);for(int page=1;page<=pdf.getNumberOfPages();page++){check(stop);stripper.setStartPage(page);stripper.setEndPage(page);var writer=new BoundedWriter(out);stripper.writeText(pdf,writer);out.add("PAGE",(long)page,null,writer.value.toString(),false);}}}
    case "docx"->{format="WORD";var zip=zip(bytes,stop);byte[] document=zip.get("word/document.xml");if(document==null)throw new ParseFailure("INVALID_DOCUMENT");var paragraphs=paragraphs(document,WORD,out);long number=0;for(String text:paragraphs)out.add("PARAGRAPH",null,++number,text,false);}
    case "pptx"->{format="PPT";var zip=zip(bytes,stop);var relationships=relationships(zip.get("ppt/_rels/presentation.xml.rels"),out);var slideIds=slideIds(zip.get("ppt/presentation.xml"),out);long page=0;for(String id:slideIds){String target=relationships.get(id);if(target==null||!target.matches("slides/slide[1-9][0-9]*\\.xml"))throw new ParseFailure("INVALID_DOCUMENT");byte[] slide=zip.get("ppt/"+target);if(slide==null)throw new ParseFailure("INVALID_DOCUMENT");var paragraphs=paragraphs(slide,DRAW,out);out.add("PAGE",++page,null,String.join("\n",paragraphs),false);}}
    default->throw new ParseFailure("FORMAT_UNSUPPORTED");
   }
   check(stop);if(out.values.isEmpty())throw new ParseFailure("NO_TEXT");
   // This deterministic UTF8 representation is stored through the genuine file upload service.
   byte[] text=String.join("\n\n",out.values.stream().map(Unit::body).toList()).getBytes(StandardCharsets.UTF_8);
   return new Result(format,hash,out.values,text);
  }catch(ParseFailure e){throw e;}catch(IOException|XMLStreamException|RuntimeException e){check(stop);throw new ParseFailure("INVALID_DOCUMENT");}
 }
 private static List<String> paragraphs(byte[] bytes,String ns,Units out)throws XMLStreamException,ParseFailure {
  var reader=xml(bytes);var result=new ArrayList<String>();StringBuilder text=null;boolean inText=false;
  try{while(reader.hasNext()){check(out.stop);int event=reader.next();unsafe(event);if(event==XMLStreamConstants.START_ELEMENT&&ns.equals(reader.getNamespaceURI())){String n=reader.getLocalName();if(n.equals("p")){if(text!=null)throw new ParseFailure("INVALID_DOCUMENT");text=new StringBuilder();}else if(n.equals("t"))inText=true;else if(text!=null&&(n.equals("tab")||n.equals("br"))){out.charge(1);text.append(n.equals("tab")?'\t':'\n');}}else if((event==XMLStreamConstants.CHARACTERS||event==XMLStreamConstants.CDATA)&&inText&&text!=null){out.charge(reader.getTextLength());text.append(reader.getText());}else if(event==XMLStreamConstants.END_ELEMENT&&ns.equals(reader.getNamespaceURI())){if(reader.getLocalName().equals("t"))inText=false;else if(reader.getLocalName().equals("p")){if(result.size()>=out.budget.units())throw new ParseFailure("UNITS_EXCEEDED");result.add(text==null?"":text.toString());text=null;}}}return result;}finally{reader.close();}
 }
 private static Map<String,String> relationships(byte[] bytes,Units out)throws XMLStreamException,ParseFailure {
  var r=xml(bytes);var values=new HashMap<String,String>();try{while(r.hasNext()){check(out.stop);int e=r.next();unsafe(e);if(e==XMLStreamConstants.START_ELEMENT&&r.getLocalName().equals("Relationship")){String type=r.getAttributeValue(null,"Type");if(type!=null&&type.equals(REL+"/slide")){String id=r.getAttributeValue(null,"Id"),target=r.getAttributeValue(null,"Target");if(!"External".equals(r.getAttributeValue(null,"TargetMode"))&&id!=null&&target!=null&&values.putIfAbsent(id,target)==null)continue;throw new ParseFailure("INVALID_DOCUMENT");}}}return values;}finally{r.close();}
 }
 private static List<String> slideIds(byte[] bytes,Units out)throws XMLStreamException,ParseFailure {
  var r=xml(bytes);var result=new ArrayList<String>();var seen=new HashSet<String>();try{while(r.hasNext()){check(out.stop);int e=r.next();unsafe(e);if(e==XMLStreamConstants.START_ELEMENT&&r.getLocalName().equals("sldId")){String id=r.getAttributeValue(REL,"id");if(id==null||!seen.add(id))throw new ParseFailure("INVALID_DOCUMENT");if(result.size()>=out.budget.units())throw new ParseFailure("UNITS_EXCEEDED");result.add(id);}}return result;}finally{r.close();}
 }
 private static XMLStreamReader xml(byte[] bytes)throws XMLStreamException,ParseFailure {if(bytes==null)throw new ParseFailure("INVALID_DOCUMENT");var f=XMLInputFactory.newFactory();f.setProperty(XMLInputFactory.SUPPORT_DTD,false);f.setProperty("javax.xml.stream.isSupportingExternalEntities",false);f.setXMLResolver((p,s,b,n)->{throw new XMLStreamException("External entity forbidden");});return f.createXMLStreamReader(new ByteArrayInputStream(bytes));}
 private static void unsafe(int event)throws ParseFailure{if(event==XMLStreamConstants.DTD||event==XMLStreamConstants.ENTITY_REFERENCE)throw new ParseFailure("INVALID_DOCUMENT");}
 private static Map<String,byte[]> zip(byte[] bytes,BooleanSupplier stop)throws IOException,ParseFailure {var result=new HashMap<String,byte[]>();long used=0;int count=0;try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry entry;while((entry=zip.getNextEntry())!=null){check(stop);String name=entry.getName();if(++count>4096||name.startsWith("/")||name.contains("..")||name.contains("\\")||result.containsKey(name))throw new ParseFailure("INVALID_DOCUMENT");byte[] part=read(zip,64L*1024*1024-used,stop);used+=part.length;result.put(name,part);zip.closeEntry();}}return result;}
 private static byte[] read(InputStream in,long limit,BooleanSupplier stop)throws ParseFailure {var out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];long used=0;try{while(true){check(stop);int count=in.read(buffer,0,(int)Math.min(buffer.length,limit-used+1));if(count<0)break;if(count>limit-used)throw new ParseFailure("SOURCE_BYTES_EXCEEDED");out.write(buffer,0,count);used+=count;}return out.toByteArray();}catch(IOException e){check(stop);throw new ParseFailure("SOURCE_READ_FAILED");}}
 public static String sha(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private static void check(BooleanSupplier stop)throws ParseFailure{if(Thread.currentThread().isInterrupted()||stop.getAsBoolean())throw new ParseFailure("CANCELLED");}
 private static final class Units {
  final Budget budget;final BooleanSupplier stop;final List<Unit> values=new ArrayList<>();long used;
  Units(Budget budget,BooleanSupplier stop){this.budget=budget;this.stop=stop;}
  void charge(long n)throws ParseFailure{check(stop);if(n>budget.utf16()-used)throw new ParseFailure("TEXT_BUDGET_EXCEEDED");used+=n;}
  void paragraphs(String text)throws ParseFailure{charge(text.length());long paragraph=0;for(String line:text.split("\\r\\n|\\r|\\n",-1))add("PARAGRAPH",null,++paragraph,line,false);}
  void add(String kind,Long page,Long paragraph,String text,boolean charge)throws ParseFailure{check(stop);if(charge)charge(text.length());if(text.indexOf('\0')>=0)throw new ParseFailure("INVALID_DOCUMENT");if(text.isBlank())return;if(values.size()>=budget.units())throw new ParseFailure("UNITS_EXCEEDED");values.add(new Unit(values.size()+1L,kind,page,paragraph,null,text));}
 }
 private static final class BoundedWriter extends Writer {
  final Units units;final StringBuilder value=new StringBuilder();BoundedWriter(Units units){this.units=units;}
  public void write(char[] c,int start,int length)throws IOException{try{units.charge(length);value.append(c,start,length);}catch(ParseFailure e){throw new IOException(e);}}
  public void flush(){}public void close(){}
 }
}
