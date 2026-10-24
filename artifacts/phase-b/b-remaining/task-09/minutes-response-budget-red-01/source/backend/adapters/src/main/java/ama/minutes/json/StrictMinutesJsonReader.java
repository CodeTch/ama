package ama.minutes.json;

import ama.minutes.contract.ModelOutputReader;
import ama.minutes.contract.OutputValidation;
import ama.contract.MinutesContracts.*;
import ama.canonical.CanonicalValueCodec;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
import static com.fasterxml.jackson.core.JsonToken.*;

/** Dedicated Jackson 2 streaming schema, no binding, repair, global defaults or caller stream ownership. */
public final class StrictMinutesJsonReader implements ModelOutputReader {
 @Override public ValidatedOutput read(InputStream source,ValidationContext context,ValidationBudget budget,BooleanSupplier cancelled) {
  require(source!=null&&context!=null&&budget!=null&&cancelled!=null,INVALID_ARGUMENT);check(cancelled);
  var builder=JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder()
    .maxNestingDepth(budget.maxDepth()).maxDocumentLength(budget.maxWireBytes()).maxTokenCount(budget.maxTokens())
    .maxNumberLength(19).maxStringLength(budget.maxStringUtf16()).maxNameLength(budget.maxNameUtf16()).build())
    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
    .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).disable(StreamReadFeature.AUTO_CLOSE_SOURCE);
  for(var feature:JsonReadFeature.values())builder.disable(feature);
  var factory=builder.build();factory.disable(JsonParser.Feature.IGNORE_UNDEFINED);
  var decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
  var input=new InputStreamReader(new BoundedSource(source,budget.maxWireBytes(),cancelled),decoder);
  var bomChecked=new FilterReader(input) {
   boolean first=true;
   @Override public int read(char[] buffer,int offset,int length)throws IOException {
    check(cancelled);int n=super.read(buffer,offset,length);check(cancelled);
    if(n>0&&first){first=false;if(buffer[offset]=='\ufeff')throw new SafeIo(INVALID_UTF8);}return n;
   }
   @Override public void close(){} // Caller owns source even when parser creation fails.
  };
  try(var parser=factory.createParser(bomChecked)) {
   var schema=new Schema(parser,budget,cancelled);schema.next();var output=schema.output();
   require(schema.next()==null,INVALID_JSON);check(cancelled);
   return new OutputValidation().validate(output,context,cancelled);
  } catch(Failure safe) {throw safe;}
  catch(IOException error) {throw translated(error,cancelled);}
 }
 private static Failure translated(IOException error,BooleanSupplier cancelled) {
  // Inspect only exception types, never exception text/location/payload.
  Throwable cursor=error;
  for(int n=0;cursor!=null&&n<32;n++,cursor=cursor.getCause()) {
   if(cursor instanceof SafeIo e)return fail(e.code);
   if(cursor instanceof CharacterCodingException)return fail(INVALID_UTF8);
   if(cursor instanceof InterruptedIOException){Thread.currentThread().interrupt();return fail(CANCELLED);}
   if(cursor instanceof StreamConstraintsException)return fail(BUDGET_EXCEEDED);
  }
  check(cancelled);return fail(error instanceof JsonProcessingException?INVALID_JSON:READ_FAILED);
 }
 private static final class SafeIo extends IOException {final FailureCode code;SafeIo(FailureCode code){super(code.name());this.code=code;}}
 private static final class BoundedSource extends InputStream {
  final InputStream source;final long limit;final BooleanSupplier cancelled;long used;
  BoundedSource(InputStream source,long limit,BooleanSupplier cancelled){this.source=source;this.limit=limit;this.cancelled=cancelled;}
  @Override public int read(byte[] bytes,int offset,int length)throws IOException {
   if(length==0)return 0;check(cancelled);long remaining=limit-used;
   int count=source.read(bytes,offset,(int)Math.min(length,remaining+1));check(cancelled);
   if(count>remaining)throw new SafeIo(BUDGET_EXCEEDED);if(count>0)used+=count;return count;
  }
  @Override public int read()throws IOException {byte[] one=new byte[1];int n=read(one,0,1);return n<0?-1:Byte.toUnsignedInt(one[0]);}
 }
 @FunctionalInterface private interface ReadValue<T>{T read()throws IOException;}
 @FunctionalInterface private interface ReadField{Object read(String field)throws IOException;}
 private enum Count { ELEMENT,DETECTION,NOTE,REF,EVIDENCE,MEMBERSHIP }
 private static final class Schema {
  final JsonParser p;final ValidationBudget b;final BooleanSupplier cancelled;
  long tokens,aggregate;int depth;final long[] counts=new long[Count.values().length];
  Schema(JsonParser p,ValidationBudget b,BooleanSupplier cancelled){this.p=p;this.b=b;this.cancelled=cancelled;}
  JsonToken next()throws IOException {
   check(cancelled);var token=p.nextToken();check(cancelled);if(token==null)return null;
   require(tokens<b.maxTokens(),BUDGET_EXCEEDED);tokens++;
   if(token==START_OBJECT||token==START_ARRAY){require(depth<b.maxDepth(),BUDGET_EXCEEDED);depth++;}
   if(token==END_OBJECT||token==END_ARRAY)depth--;
   if(token==FIELD_NAME||token.isScalarValue()){
    String value=p.getText();scalars(value);long scalars=OutputValidation.scalarCount(value,cancelled);
    require(scalars<=b.maxAggregateScalars()-aggregate,BUDGET_EXCEEDED);aggregate+=scalars;
    if(token==FIELD_NAME||token==VALUE_STRING){
     require(value.length()<=(token==FIELD_NAME?b.maxNameUtf16():b.maxStringUtf16()),BUDGET_EXCEEDED);
     require(scalars<=b.maxStringScalars()&&CanonicalValueCodec.utf8Length(value,cancelled)<=b.maxStringUtf8Bytes(),BUDGET_EXCEEDED);
    }
   }return token;
  }
  void expect(JsonToken token){require(p.currentToken()==token,INVALID_SCHEMA);}
  String string()throws IOException {expect(VALUE_STRING);return p.getText();}
  String nullableString()throws IOException {return p.currentToken()==VALUE_NULL?null:string();}
  long number()throws IOException {
   require(p.currentToken()==VALUE_NUMBER_INT,INVALID_NUMBER);String raw=p.getText();
   require(raw.matches("0|[1-9][0-9]{0,18}"),INVALID_NUMBER);
   try{return Long.parseLong(raw);}catch(NumberFormatException e){throw fail(INVALID_NUMBER);}
  }
  void count(Count kind){
   long max=switch(kind){case ELEMENT->b.maxElements();case DETECTION->b.maxDetections();case NOTE->b.maxNotes();case REF->b.maxRefs();case EVIDENCE->b.maxEvidence();case MEMBERSHIP->b.maxMemberships();};
   require(counts[kind.ordinal()]<max&&counts[kind.ordinal()]<Integer.MAX_VALUE,BUDGET_EXCEEDED);counts[kind.ordinal()]++;
  }
  <T> List<T> array(Count count,ReadValue<T> read)throws IOException {
   expect(START_ARRAY);var list=new ArrayList<T>();
   while(next()!=END_ARRAY){require(p.currentToken()!=null,INVALID_JSON);count(count);list.add(read.read());}
   return List.copyOf(list);
  }
  // This map is bounded by a fixed schema field allowlist, never an arbitrary JSON tree.
  Map<String,Object> object(Set<String> allowed,ReadField reader)throws IOException {
   expect(START_OBJECT);var fields=new HashMap<String,Object>();
   while(next()!=END_OBJECT){
    expect(FIELD_NAME);String name=p.currentName();require(allowed.contains(name),UNKNOWN_FIELD);
    require(!fields.containsKey(name),INVALID_JSON);require(next()!=null,INVALID_JSON);
    fields.put(name,reader.read(name));
   }return fields;
  }
  static Set<String> keys(String... names){return Set.of(names);}
  static void exact(Map<String,Object> fields,String... names){require(fields.keySet().equals(keys(names)),INVALID_SCHEMA);}
  @SuppressWarnings("unchecked") static <T>T get(Map<String,Object> fields,String key){return (T)fields.get(key);}
  SourceIds ids()throws IOException{return new SourceIds(array(Count.MEMBERSHIP,this::string));}
  Output output()throws IOException {
   var f=object(keys("schemaVersion","inputFingerprint","title","body","elements","sensitive","background","sourceRefs","evidence"),name->switch(name){
    case "schemaVersion","inputFingerprint","title","body"->string();case "elements"->elements();
    case "sensitive"->array(Count.DETECTION,this::detection);case "background"->array(Count.NOTE,this::note);
    case "sourceRefs"->array(Count.REF,this::ref);case "evidence"->array(Count.EVIDENCE,this::evidence);default->throw fail(UNKNOWN_FIELD);});
   exact(f,"schemaVersion","inputFingerprint","title","body","elements","sensitive","background","sourceRefs","evidence");
   return new Output(get(f,"schemaVersion"),get(f,"inputFingerprint"),get(f,"title"),get(f,"body"),get(f,"elements"),get(f,"sensitive"),get(f,"background"),get(f,"sourceRefs"),get(f,"evidence"));
  }
  Map<Category,List<Element>> elements()throws IOException {
   var allowed=new HashSet<String>();for(var kind:Category.values())allowed.add(kind.name());
   var f=object(allowed,name->array(Count.ELEMENT,this::element));require(f.keySet().equals(allowed),INVALID_SCHEMA);
   var out=new EnumMap<Category,List<Element>>(Category.class);for(var kind:Category.values())out.put(kind,get(f,kind.name()));return out;
  }
  Element element()throws IOException {
   var f=object(keys("key","text","action"),name->switch(name){case "key","text"->string();case "action"->p.currentToken()==VALUE_NULL?null:action();default->throw fail(UNKNOWN_FIELD);});
   exact(f,"key","text","action");return new Element(get(f,"key"),get(f,"text"),get(f,"action"));
  }
  Action action()throws IOException {
   var f=object(keys("ownerMention","dueOn"),name->nullableString());exact(f,"ownerMention","dueOn");return new Action(get(f,"ownerMention"),get(f,"dueOn"));
  }
  Detection detection()throws IOException {
   var f=object(keys("key","category","sourceIds"),name->name.equals("sourceIds")?ids():string());exact(f,"key","category","sourceIds");
   SensitiveKind kind;try{kind=SensitiveKind.valueOf(get(f,"category"));}catch(IllegalArgumentException e){throw fail(INVALID_SCHEMA);}
   return new Detection(get(f,"key"),kind,get(f,"sourceIds"));
  }
  Note note()throws IOException {
   var f=object(keys("key","text","sourceIds"),name->name.equals("sourceIds")?ids():string());exact(f,"key","text","sourceIds");
   return new Note(get(f,"key"),get(f,"text"),get(f,"sourceIds"));
  }
  Ref ref()throws IOException {
   var f=object(keys("sourceId","locator"),name->name.equals("locator")?locator():string());exact(f,"sourceId","locator");return new Ref(get(f,"sourceId"),get(f,"locator"));
  }
  Evidence evidence()throws IOException {
   var f=object(keys("target","sourceIds"),name->name.equals("target")?target():ids());exact(f,"target","sourceIds");return new Evidence(get(f,"target"),get(f,"sourceIds"));
  }
  Target target()throws IOException {
   var f=object(keys("kind","elementKey","start","end"),name->name.equals("start")||name.equals("end")?number():string());
   require(f.containsKey("kind"),INVALID_SCHEMA);String kind=get(f,"kind");
   if(kind.equals("BODY")){exact(f,"kind","start","end");return new BodyTarget(get(f,"start"),get(f,"end"));}
   if(kind.equals("ELEMENT")){exact(f,"kind","elementKey","start","end");return new ElementTarget(get(f,"elementKey"),get(f,"start"),get(f,"end"));}
   throw fail(INVALID_SCHEMA);
  }
  Locator locator()throws IOException {
   var f=object(keys("kind","transcriptRevisionId","segmentId","fileId","startMs","endMs","pageNo","paragraphNo","sectionPath","materialRevisionId"),name->switch(name){
    case "startMs","endMs","pageNo","paragraphNo"->number();case "sectionPath"->{String section=nullableString();if(section!=null)text(section,false);yield section;}default->string();});
   require(f.containsKey("kind"),INVALID_SCHEMA);String kind=get(f,"kind");
   return switch(kind){
    case "TRANSCRIPT"->{exact(f,"kind","transcriptRevisionId","segmentId");yield new TranscriptLocator(get(f,"transcriptRevisionId"),get(f,"segmentId"));}
    case "AUDIO"->{exact(f,"kind","fileId","startMs","endMs");yield new AudioLocator(get(f,"fileId"),get(f,"startMs"),get(f,"endMs"));}
    case "PDF_PAGE","PPT_PAGE"->{exact(f,"kind","fileId","pageNo");yield new PageLocator(PageKind.valueOf(kind),get(f,"fileId"),get(f,"pageNo"));}
    case "WORD_PARAGRAPH","TEXT_PARAGRAPH"->{exact(f,"kind","fileId","paragraphNo","sectionPath");yield new ParagraphLocator(ParagraphKind.valueOf(kind),get(f,"fileId"),get(f,"paragraphNo"),get(f,"sectionPath"));}
    case "KNOWLEDGE_VERSION"->{exact(f,"kind","materialRevisionId");yield new KnowledgeLocator(get(f,"materialRevisionId"));}
    default->throw fail(INVALID_SCHEMA);
   };
  }
 }
}
