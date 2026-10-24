package ama.config.json;
import ama.config.*;
import ama.config.ConfigArchiveIndex.IndexBudget;
import java.util.function.BooleanSupplier;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.ConfigArchiveContracts.Ref;
import ama.canonical.CanonicalValueCodec;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import java.io.*;
import java.nio.charset.*;
import java.util.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
public final class StrictConfigIndexReader {
 /** Independent closed grammar, with limits before parsing or entry allocation. */
 public ConfigArchiveIndex read(byte[] raw,IndexBudget b,BooleanSupplier c){
  require(raw!=null&&b!=null&&c!=null,INVALID_ARGUMENT);check(c);require(raw.length<=b.maxIndexBytes(),BUDGET_EXCEEDED);
  var factory=JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
   .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(4).maxDocumentLength(b.maxIndexBytes()).maxTokenCount(20L*b.maxEntries()+32).maxNameLength(32).maxStringLength(1024).maxNumberLength(19).build()).build();
  var decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
  try(var input=new InputStreamReader(new ByteArrayInputStream(raw),decoder);var p=factory.createParser(input)){
   token(p,JsonToken.START_OBJECT,c);field(p,"entries",c);token(p,JsonToken.START_ARRAY,c);var entries=new ArrayList<ConfigArchiveIndex.Entry>();
   String previousType=null,previousVersion=null;long total=0;
   while(next(p,c)!=JsonToken.END_ARRAY){
    require(p.currentToken()==JsonToken.START_OBJECT,INVALID_SCHEMA);require(entries.size()<b.maxEntries(),BUDGET_EXCEEDED);
    field(p,"artifactType",c);String type=string(p,c);ArtifactType artifactType;try{artifactType=ArtifactType.valueOf(type);}catch(IllegalArgumentException e){throw fail(PROFILE_UNAVAILABLE);}
    field(p,"sha256",c);String sha=hash(string(p,c));field(p,"sizeBytes",c);long size=uint(p,c);require(size>0&&size<=65536&&size<=b.maxReleaseBytes()-total,BUDGET_EXCEEDED);total+=size;
    field(p,"version",c);String version=ama.contract.ConfigArchiveContracts.version(string(p,c));token(p,JsonToken.END_OBJECT,c);
    if(previousType!=null){int order=previousType.compareTo(type);require(order<0||(order==0&&CanonicalValueCodec.compareUtf8(previousVersion,version,c)<0),INVALID_SCHEMA);}
    previousType=type;previousVersion=version;entries.add(new ConfigArchiveIndex.Entry(new Ref(artifactType,version,sha),size));
   }
   require(!entries.isEmpty(),INVALID_SCHEMA);field(p,"generation",c);long generation=uint(p,c);require(generation>0,INVALID_SCHEMA);
   field(p,"indexSchema",c);require(string(p,c).equals("ama.config-index.v1"),PROFILE_UNAVAILABLE);
   field(p,"previousIndexSha256",c);var t=next(p,c);String previous=t==JsonToken.VALUE_NULL?null:t==JsonToken.VALUE_STRING?hash(p.getText()):invalid();
   require((generation==1)==(previous==null),INVALID_SCHEMA);token(p,JsonToken.END_OBJECT,c);require(next(p,c)==null,INVALID_JSON);
   var index=new ConfigArchiveIndex(generation,previous,entries);var owned=ArchiveBytes.capture(raw,b.maxIndexBytes(),b.maxIndexBytes(),c);
   require(CanonicalJson.matches(index.wire(),owned,c),INVALID_SCHEMA);check(c);return index;
  }catch(Failure safe){throw safe;}catch(StreamConstraintsException e){throw fail(BUDGET_EXCEEDED);}
  catch(IOException e){check(c);for(Throwable at=e;at!=null;at=at.getCause())if(at instanceof CharacterCodingException)throw fail(INVALID_UTF8);throw fail(INVALID_JSON);}
 }
 private static String invalid(){throw fail(INVALID_SCHEMA);}
 private static JsonToken next(JsonParser p,BooleanSupplier c)throws IOException{check(c);var t=p.nextToken();check(c);return t;}
 private static void token(JsonParser p,JsonToken t,BooleanSupplier c)throws IOException{require(next(p,c)==t,INVALID_SCHEMA);}
 private static void field(JsonParser p,String name,BooleanSupplier c)throws IOException{token(p,JsonToken.FIELD_NAME,c);require(name.equals(p.currentName()),UNKNOWN_FIELD);}
 private static String string(JsonParser p,BooleanSupplier c)throws IOException{token(p,JsonToken.VALUE_STRING,c);String value=p.getText();scalars(value);return value;}
 private static long uint(JsonParser p,BooleanSupplier c)throws IOException{token(p,JsonToken.VALUE_NUMBER_INT,c);String raw=p.getText();require(raw.matches("0|[1-9][0-9]{0,18}"),INVALID_NUMBER);try{return Long.parseLong(raw);}catch(NumberFormatException e){throw fail(INVALID_NUMBER);}}
}
