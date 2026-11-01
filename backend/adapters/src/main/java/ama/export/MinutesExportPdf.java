package ama.export;

import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;

final class MinutesExportPdf {
 static byte[] render(MinutesExportDocument source,List<MinutesExportBlocks.Block> blocks,MinutesExportRenderer.Fonts fonts,BooleanSupplier cancelled)throws IOException {
  String title=source.title();
  try(var document=new PDDocument();var collection=fonts.cjkCollection()==null?null:new TrueTypeCollection(fonts.cjkCollection().toFile())){
   // PDFBox otherwise derives the trailer ID from the current wall clock on every save.
   // These IDs are file-format metadata; business integrity uses the full output SHA-256.
   var ids=new org.apache.pdfbox.cos.COSArray();byte[] id=documentId(source);ids.add(new org.apache.pdfbox.cos.COSString(id));ids.add(new org.apache.pdfbox.cos.COSString(id));document.getDocument().setDocumentID(ids);
   var available=new ArrayList<PDFont>();available.add(new PDType1Font(Standard14Fonts.FontName.HELVETICA));
   if(collection!=null)collection.processAllFonts(font->{if(available.size()==1)available.add(PDType0Font.load(document,font,true));});
   if(fonts.unicodeTrueType()!=null)try(var input=java.nio.file.Files.newInputStream(fonts.unicodeTrueType())){available.add(PDType0Font.load(document,input,true));}
   document.getDocumentInformation().setTitle(title);document.getDocumentInformation().setCreator("AMA");
   try(var layout=new Layout(document,available,cancelled)){
    layout.paragraph(title,18);
    for(var block:blocks){MinutesExportRenderer.check(cancelled);
     if(block instanceof MinutesExportBlocks.Paragraph p)layout.paragraph(p.text(),p.heading()==0?11:Math.max(12,18-p.heading()));
     else if(block instanceof MinutesExportBlocks.Table table)layout.table(table.rows());
    }
   }
   var output=new MinutesExportRenderer.LimitedOutput();document.save(output);MinutesExportRenderer.check(cancelled);return output.toByteArray();
  }
 }
 private static byte[] documentId(MinutesExportDocument source){
  try{var digest=java.security.MessageDigest.getInstance("SHA-256");for(String value:List.of(source.title(),source.body())){byte[] bytes=value.getBytes(java.nio.charset.StandardCharsets.UTF_8);digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());digest.update(bytes);}return Arrays.copyOf(digest.digest(),16);}
  catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
 }
 private record Glyph(String text,PDFont font,float width){}
 private static final class Layout implements AutoCloseable {
  private static final float MARGIN=50,WIDTH=PDRectangle.A4.getWidth()-2*MARGIN;
  private final PDDocument document;private final List<PDFont> fonts;private final BooleanSupplier cancelled;
  private final Map<Integer,Glyph> glyphs=new HashMap<>();private PDPageContentStream stream;private float y;
  Layout(PDDocument document,List<PDFont> fonts,BooleanSupplier cancelled)throws IOException{this.document=document;this.fonts=fonts;this.cancelled=cancelled;page();}
  private void page()throws IOException{
   if(stream!=null)stream.close();stream=null;
   if(document.getNumberOfPages()>=MinutesExportRenderer.MAX_PAGES)throw new IOException("Export page budget");
   var page=new PDPage(PDRectangle.A4);document.addPage(page);stream=new PDPageContentStream(document,page);y=PDRectangle.A4.getHeight()-MARGIN;
  }
  private Glyph glyph(int codepoint)throws IOException{
   if(codepoint=='\t')return glyph(' ');
   Glyph found=glyphs.get(codepoint);if(found!=null)return found;
   String text=new String(Character.toChars(codepoint));
   for(var font:fonts)try{float width=font.getStringWidth(text)/1000f;var value=new Glyph(text,font,width);glyphs.put(codepoint,value);return value;}catch(IllegalArgumentException unavailable){/* Try the next explicitly installed font. */}
   throw new IOException("Installed export fonts do not cover U+"+Integer.toHexString(codepoint));
  }
  private List<List<Glyph>> wrap(String value,float width,float size)throws IOException{
   var lines=new ArrayList<List<Glyph>>();var line=new ArrayList<Glyph>();float used=0;
   for(int i=0;i<value.length();){MinutesExportRenderer.check(cancelled);int c=value.codePointAt(i);i+=Character.charCount(c);if(c=='\r'||c=='\n'){lines.add(List.copyOf(line));line.clear();used=0;continue;}
    var glyph=glyph(c);float next=glyph.width()*size;if(next>width)throw new IOException("Export column cannot fit a glyph");
    if(!line.isEmpty()&&used+next>width){lines.add(List.copyOf(line));line.clear();used=0;}line.add(glyph);used+=next;
   }
   lines.add(List.copyOf(line));return lines;
  }
  private void draw(List<Glyph> line,float x,float baseline,float size)throws IOException{
   if(line.isEmpty())return;stream.beginText();stream.newLineAtOffset(x,baseline);PDFont prior=null;var text=new StringBuilder();
   for(var glyph:line){if(glyph.font()!=prior){if(!text.isEmpty()){stream.showText(text.toString());text.setLength(0);}stream.setFont(glyph.font(),size);prior=glyph.font();}text.append(glyph.text());}
   if(!text.isEmpty())stream.showText(text.toString());stream.endText();
  }
  void paragraph(String text,float size)throws IOException{
   for(var line:wrap(text,WIDTH,size)){float height=size*1.4f;if(y-height<MARGIN)page();y-=height;draw(line,MARGIN,y,size);}y-=4;
  }
  void table(List<List<String>> rows)throws IOException{
   float column=WIDTH/rows.getFirst().size(),size=10,height=18;
   for(var row:rows){MinutesExportRenderer.check(cancelled);var columns=new ArrayList<List<List<Glyph>>>();int count=1;
    for(String cell:row){var lines=wrap(cell,column-8,size);columns.add(lines);count=Math.max(count,lines.size());}
    for(int n=0;n<count;n++){if(y-height<MARGIN)page();y-=height;
     for(int c=0;c<columns.size();c++){float x=MARGIN+c*column;stream.setLineWidth(.3f);stream.addRect(x,y,column,height);stream.stroke();if(n<columns.get(c).size())draw(columns.get(c).get(n),x+4,y+4,size);}
    }
   }
   y-=8;
  }
  @Override public void close()throws IOException{if(stream!=null)stream.close();}
 }
}
