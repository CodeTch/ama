package ama.model;
import ama.canonical.*;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import java.io.*;
import java.nio.charset.*;
import java.util.*;
import static ama.canonical.CanonicalValue.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
/** Finite streaming value parser shared only by the explicit controlled protocol schemas. */
final class BoundedModelJson {
 static CanonicalValue.ObjectValue parse(byte[] bytes,int limit){
  require(bytes!=null&&bytes.length<=limit,BUDGET_EXCEEDED);
  var builder=JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder().maxDocumentLength(limit).maxNestingDepth(32).maxTokenCount(400000).maxNumberLength(19).maxNameLength(128).maxStringLength(Math.min(limit,4*1024*1024)).build()).enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION);
  for(var f:JsonReadFeature.values())builder.disable(f);
  var decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
  try(var reader=new PushbackReader(new InputStreamReader(new ByteArrayInputStream(bytes),decoder),1)){
   int first=reader.read();require(first!=0xfeff,INVALID_UTF8);if(first>=0)reader.unread(first);
   try(var parser=builder.build().createParser(reader)){
    var state=new State(parser,limit);state.next();var value=state.value(0);require(value instanceof ObjectValue&&state.next()==null,INVALID_SCHEMA);return (ObjectValue)value;
   }
  }catch(Failure e){throw e;}catch(IOException e){throw fail(INVALID_JSON);}
 }
 private static final class State {
  final JsonParser p;final long limit;long text,nodes;State(JsonParser p,long limit){this.p=p;this.limit=limit;}
  JsonToken next()throws IOException {check(()->false);var t=p.nextToken();if(t!=null){require(++nodes<=400000,BUDGET_EXCEEDED);if(t==JsonToken.FIELD_NAME||t==JsonToken.VALUE_STRING){String s=p.getText();scalars(s);long bytes=CanonicalValueCodec.utf8Length(s,()->false);require(bytes<=Math.min(limit,4*1024*1024)&&bytes<=limit-text,BUDGET_EXCEEDED);text+=bytes;}}return t;}
  CanonicalValue value(int depth)throws IOException {
   require(depth<32&&p.currentToken()!=null,BUDGET_EXCEEDED);
   return switch(p.currentToken()){
    case VALUE_NULL->nil();case VALUE_TRUE->new Bool(true);case VALUE_FALSE->new Bool(false);case VALUE_STRING->string(p.getText());
    case VALUE_NUMBER_INT->{String s=p.getText();require(s.matches("0|[1-9][0-9]{0,18}"),INVALID_NUMBER);try{yield number(Long.parseLong(s));}catch(NumberFormatException e){throw fail(INVALID_NUMBER);}}
    case START_ARRAY->{var values=new ArrayList<CanonicalValue>();while(next()!=JsonToken.END_ARRAY){require(values.size()<100000,BUDGET_EXCEEDED);values.add(value(depth+1));}yield new Sequence(values);}
    case START_OBJECT->{var fields=new ArrayList<Entry>();while(next()!=JsonToken.END_OBJECT){require(p.currentToken()==JsonToken.FIELD_NAME&&fields.size()<64,INVALID_SCHEMA);String key=p.currentName();next();fields.add(field(key,value(depth+1)));}yield new ObjectValue(fields);}
    default->throw fail(INVALID_SCHEMA);
   };
  }
 }
}
