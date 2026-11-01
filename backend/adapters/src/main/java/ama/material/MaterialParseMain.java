package ama.material;
import java.io.*;
import java.nio.file.*;
/** Private child entry point. Source name is only a format label; it is never used as a path. */
public final class MaterialParseMain {
 private MaterialParseMain(){}
 @SuppressWarnings("removal") public static void main(String[] args){
  try{
   if(args.length!=2||System.getSecurityManager()==null)throw new IllegalStateException("Confined parser required");MaterialParser.Result result=null;String error=null;
   try(var data=new DataInputStream(Files.newInputStream(Path.of(args[0])))){
    String name=MaterialParseProcess.text(data,16384),hash=MaterialParseProcess.text(data,64);long source=data.readLong(),units=data.readLong();int count=data.readInt(),size=data.readInt();if(source<1||source>MaterialParseProcess.MAX_BYTES||units<1||units>8L*1024*1024||count<1||count>10000||size<0||size>source)throw new IOException("Invalid parse bounds");byte[] bytes=data.readNBytes(size);if(bytes.length!=size||data.read()!=-1)throw new EOFException();
    try{result=new MaterialDocumentParser().parse(new ByteArrayInputStream(bytes),name,hash,new MaterialParser.Budget(source,units,count),()->false);}catch(MaterialParser.ParseFailure failure){error=failure.code();}
   }
   try(var out=new DataOutputStream(Files.newOutputStream(Path.of(args[1]),StandardOpenOption.CREATE_NEW))){out.writeBoolean(error!=null);if(error!=null){MaterialParseProcess.text(out,error);return;}MaterialParseProcess.text(out,result.format());MaterialParseProcess.text(out,result.sourceSha256());out.writeInt(result.units().size());for(var unit:result.units()){out.writeLong(unit.ordinal());MaterialParseProcess.text(out,unit.kind());out.writeLong(unit.pageNo()==null?-1:unit.pageNo());out.writeLong(unit.paragraphNo()==null?-1:unit.paragraphNo());out.writeBoolean(unit.sectionPath()!=null);if(unit.sectionPath()!=null)MaterialParseProcess.text(out,unit.sectionPath());MaterialParseProcess.text(out,unit.body());}byte[] text=result.textBytes();out.writeInt(text.length);out.write(text);}
  }catch(Exception failure){System.err.println("MATERIAL_PARSE_FAILED: "+failure.getClass().getSimpleName());System.exit(1);}
 }
}
