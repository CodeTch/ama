package ama.config.json;
import ama.config.*;
import ama.canonical.CanonicalValue;
import ama.canonical.CanonicalValueCodec;
import ama.contract.ConfigArchiveContracts.*;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import java.io.*;
import java.nio.charset.*;
import java.util.*;
import static ama.canonical.CanonicalValue.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
/** Bounded streaming value algebra for the finite config/manifest schemas; no ObjectMapper or unknown fields. */
final class StrictConfigJson {
 private static final Set<String> FIELDS=Set.of(
 "archiveSchema","artifactType","version","definitionSchema","definition","dependencies","slot","ref","sha256",
 "asr","modelManifestSha256","decoder","subtitles","formats","engines","pdfbox","poi","xml","contract","manifestSchemaVersion","parseBudget","canonicalBudget","maxSourceBytes","maxUtf16CodeUnits","maxSegments","maxEncodedBytes","maxDepth","maxNodes","maxStringBytes","maxContainerEntries",
 "instructionText","protocolVersion","modelIdentity","credentialRef","parameters","temperatureMicros","maxOutputTokens","seed",
 "output","canonical","maxWireBytes","maxTokens","maxNameUtf16","maxStringUtf16","maxStringUtf8Bytes","maxStringScalars","maxAggregateScalars","maxElements","maxDetections","maxNotes","maxRefs","maxEvidence","maxMemberships",
 "rendererVersion","defaultTemplateRevisionId","schemaVersion","profile","meetingId","inputId","kind","batch","sourceRevisionId","materials","configs","base","steps","files","id","ordinal","fileId","sizeBytes","mode","items","processing","parser","model","prompt","profiles",
 "meetingRevision","subjectBusinessVersion","cancelEpoch","parentInput","priorOutput","source","config");
 static ObjectValue parse(ArchiveBytes bytes,ArchiveReadState state){
  var b=state.budget;check(state.cancelled);
  var builder=JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(b.maxJsonDepth()).maxDocumentLength(Math.max(b.maxArtifactBytes(),b.maxManifestBytes())).maxTokenCount(b.maxTokens()).maxNumberLength(19).maxNameLength(b.maxNameUtf16()).maxStringLength(b.maxStringUtf16()).build())
   .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).disable(StreamReadFeature.AUTO_CLOSE_SOURCE);
  for(var feature:JsonReadFeature.values())builder.disable(feature);
  var factory=builder.build();factory.disable(JsonParser.Feature.IGNORE_UNDEFINED);
  var decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
  var input=new PushbackReader(new InputStreamReader(bytes.openStream(),decoder),1);
  try{
   check(state.cancelled);int first=input.read();check(state.cancelled);require(first!=0xfeff,INVALID_UTF8);if(first>=0)input.unread(first);
   try(var parser=factory.createParser(input)){
    var reader=new Reader(parser,state);reader.next();var value=reader.value(0);require(value instanceof ObjectValue,INVALID_SCHEMA);require(reader.next()==null,INVALID_JSON);check(state.cancelled);return (ObjectValue)value;
   }
  }catch(ama.contract.MinutesContracts.Failure safe){throw safe;}
  catch(CharacterCodingException e){throw fail(INVALID_UTF8);}
  catch(StreamConstraintsException e){throw fail(BUDGET_EXCEEDED);}
  catch(InterruptedIOException e){Thread.currentThread().interrupt();throw fail(CANCELLED);}
  catch(IOException e){check(state.cancelled);Throwable cursor=e;for(int i=0;cursor!=null&&i<16;i++,cursor=cursor.getCause())if(cursor instanceof CharacterCodingException)throw fail(INVALID_UTF8);throw fail(e instanceof JsonProcessingException?INVALID_JSON:READ_FAILED);}
 }
 private static final class Reader {
  final JsonParser p;final ArchiveReadState state;Reader(JsonParser p,ArchiveReadState state){this.p=p;this.state=state;}
  JsonToken next()throws IOException{
   check(state.cancelled);var t=p.nextToken();check(state.cancelled);if(t==null)return null;state.token();
   if(t==JsonToken.FIELD_NAME||t.isScalarValue()){
    String value=p.getText();scalars(value);long count=value.codePointCount(0,value.length());state.scalars(count);
    if(t==JsonToken.FIELD_NAME||t==JsonToken.VALUE_STRING)require(value.length()<=(t==JsonToken.FIELD_NAME?state.budget.maxNameUtf16():state.budget.maxStringUtf16())&&CanonicalValueCodec.utf8Length(value,state.cancelled)<=state.budget.maxStringUtf8Bytes(),BUDGET_EXCEEDED);
   }return t;
  }
  CanonicalValue value(int parentDepth)throws IOException{
   check(state.cancelled);var t=p.currentToken();require(t!=null,INVALID_JSON);int depth=parentDepth+((t==JsonToken.START_OBJECT||t==JsonToken.START_ARRAY)?1:0);require(depth<=state.budget.maxJsonDepth(),BUDGET_EXCEEDED);require(depth<=32,INVALID_SCHEMA);
   return switch(t){
    case VALUE_NULL->nil();case VALUE_TRUE->new Bool(true);case VALUE_FALSE->new Bool(false);case VALUE_STRING->string(p.getText());
    case VALUE_NUMBER_INT->{String raw=p.getText();require(raw.matches("0|[1-9][0-9]{0,18}"),INVALID_NUMBER);try{yield number(Long.parseLong(raw));}catch(NumberFormatException e){throw fail(INVALID_NUMBER);}}
    case START_OBJECT->{
     var entries=new ArrayList<Entry>();while(next()!=JsonToken.END_OBJECT){
      require(p.currentToken()==JsonToken.FIELD_NAME,INVALID_SCHEMA);String key=p.currentName();require(FIELDS.contains(key),UNKNOWN_FIELD);require(entries.size()<32,INVALID_SCHEMA);
      next();entries.add(field(key,value(depth)));
     }yield new ObjectValue(entries);
    }
    case START_ARRAY->{var values=new ArrayList<CanonicalValue>();while(next()!=JsonToken.END_ARRAY){require(values.size()<Integer.MAX_VALUE,BUDGET_EXCEEDED);values.add(value(depth));}yield new Sequence(values);}
    case VALUE_NUMBER_FLOAT->throw fail(INVALID_NUMBER);default->throw fail(INVALID_SCHEMA);
   };
  }
 }
}
