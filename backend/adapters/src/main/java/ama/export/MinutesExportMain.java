package ama.export;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;

/** Internal child entry point; no server, HTTP, source URL or user-selected output path. */
public final class MinutesExportMain {
 private MinutesExportMain(){}
 @SuppressWarnings("removal") public static void main(String[] args){
  try{
   if(args.length!=5||System.getSecurityManager()==null)throw new IllegalStateException("Confined export child required");
   MinutesExportDocument document;
   try(var data=new DataInputStream(Files.newInputStream(Path.of(args[0])))){document=new MinutesExportDocument(read(data),read(data));if(data.read()!=-1)throw new IOException("Trailing export input");}
   var fonts=new MinutesExportRenderer.Fonts(args[3].isEmpty()?null:Path.of(args[3]),args[4].isEmpty()?null:Path.of(args[4]));
   byte[] result=MinutesExportRenderer.render(document,MinutesExportRenderer.Format.valueOf(args[2]),fonts,()->false);
   Files.write(Path.of(args[1]),result,StandardOpenOption.CREATE_NEW);
  }catch(Exception failure){System.err.println("EXPORT_CONVERSION_FAILED: "+failure.getClass().getSimpleName());System.exit(1);}
 }
 private static String read(DataInputStream data)throws IOException{
  int length=data.readInt();if(length<0||length>1024*1024)throw new IOException("Export input budget");byte[] bytes=data.readNBytes(length);if(bytes.length!=length)throw new EOFException();
  return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
 }
}
