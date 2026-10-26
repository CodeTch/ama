package ama.transcript;

import ama.canonical.CanonicalValue;
import com.fasterxml.jackson.core.*;
import java.io.*;
import java.nio.charset.*;
import java.util.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Request-cancellable bounded JSON, used only by the interactive discovery/document reads. */
final class ControlledKnowledgeReadJson {
 static Map<String,Object> parse(byte[] bytes,int limit,TranscriptAcquisitionBudget.Ledger budget) {
  budget.check();require(bytes!=null&&bytes.length<=limit&&limit<=8454144,BUDGET_EXCEEDED);
  var decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
  try(var input=new FilterInputStream(new ByteArrayInputStream(bytes)){
   @Override public int read()throws IOException{budget.check();return super.read();}
   @Override public int read(byte[] b,int off,int len)throws IOException{budget.check();return in.read(b,off,Math.min(len,4096));}
  };var reader=new PushbackReader(new InputStreamReader(input,decoder),1)) {
   int first=reader.read();require(first!=0xfeff,INVALID_UTF8);if(first>=0)reader.unread(first);
   try(var parser=ControlledKnowledgeClient.JSON.getFactory().createParser(reader)) {
    var state=new State(parser,budget,limit);parser.nextToken();Object value=state.value(0);require(value instanceof Map<?,?>&&parser.nextToken()==null,INVALID_SCHEMA);budget.check();
    @SuppressWarnings("unchecked") var object=(Map<String,Object>)value;return object;
   }
  }catch(IOException malformed){throw fail(INVALID_JSON);}
 }
 private static final class State {
  final JsonParser parser;final TranscriptAcquisitionBudget.Ledger budget;final long limit;long nodes,textBytes;
  State(JsonParser parser,TranscriptAcquisitionBudget.Ledger budget,long limit){this.parser=parser;this.budget=budget;this.limit=limit;}
  String string(String value){scalars(value);long bytes=ama.canonical.CanonicalValueCodec.utf8Length(value,()->{budget.check();return false;});require(bytes<=limit-textBytes,BUDGET_EXCEEDED);textBytes+=bytes;return value;}
  Object value(int depth)throws IOException {
   budget.check();require(depth<16&&++nodes<=200000&&parser.currentToken()!=null,BUDGET_EXCEEDED);
   return switch(parser.currentToken()) {
    case VALUE_NULL->null;case VALUE_TRUE->true;case VALUE_FALSE->false;case VALUE_STRING->string(parser.getText());
    case VALUE_NUMBER_INT->{String number=parser.getText();require(number.matches("0|[1-9][0-9]{0,18}"),INVALID_NUMBER);try{yield Long.parseLong(number);}catch(NumberFormatException invalid){throw fail(INVALID_NUMBER);}}
    case START_OBJECT->{var result=new LinkedHashMap<String,Object>();budget.row(0);while(parser.nextToken()!=JsonToken.END_OBJECT){budget.check();require(parser.currentToken()==JsonToken.FIELD_NAME&&result.size()<64,INVALID_SCHEMA);String key=string(parser.currentName());require(!result.containsKey(key),INVALID_SCHEMA);budget.row(1);parser.nextToken();result.put(key,value(depth+1));}yield Collections.unmodifiableMap(result);}
    case START_ARRAY->{var result=new ArrayList<Object>();budget.row(0);while(parser.nextToken()!=JsonToken.END_ARRAY){budget.check();require(result.size()<100000,BUDGET_EXCEEDED);budget.row(1);result.add(value(depth+1));}yield Collections.unmodifiableList(result);}
    default->throw fail(INVALID_SCHEMA);
   };
  }
 }
 static byte[] canonical(Object value,TranscriptAcquisitionBudget.Ledger budget) {
  long bound=bound(value,budget);budget.capture(bound,bound,bound,5);
  return ama.config.CanonicalJson.encode(convert(value,budget),8388608,()->{budget.check();return false;});
 }
 private static long bound(Object value,TranscriptAcquisitionBudget.Ledger budget){budget.check();if(value==null)return 4;if(value instanceof String s)return Math.addExact(2,Math.multiplyExact(s.length(),6L));if(value instanceof Number||value instanceof Boolean)return 32;long size=2;if(value instanceof Map<?,?> m){budget.row(m.size());for(var e:m.entrySet())size=Math.addExact(size,2+bound(e.getKey(),budget)+bound(e.getValue(),budget));return size;}if(value instanceof List<?> l){budget.row(l.size());for(Object v:l)size=Math.addExact(size,1+bound(v,budget));return size;}throw fail(INVALID_SCHEMA);}
 private static CanonicalValue convert(Object value,TranscriptAcquisitionBudget.Ledger budget){
  budget.check();if(value==null)return CanonicalValue.nil();if(value instanceof String s){scalars(s);return CanonicalValue.string(s);}if(value instanceof Boolean b)return new CanonicalValue.Bool(b);if(value instanceof Integer||value instanceof Long){long n=((Number)value).longValue();require(n>=0,INVALID_NUMBER);return CanonicalValue.number(n);}
  if(value instanceof Map<?,?> m){var entries=new ArrayList<CanonicalValue.Entry>();for(var e:m.entrySet()){require(e.getKey() instanceof String,INVALID_SCHEMA);entries.add(CanonicalValue.field((String)e.getKey(),convert(e.getValue(),budget)));}entries.sort(Comparator.comparing(CanonicalValue.Entry::key));return new CanonicalValue.ObjectValue(entries);}
  if(value instanceof List<?> l){var items=new ArrayList<CanonicalValue>();for(Object v:l)items.add(convert(v,budget));return new CanonicalValue.Sequence(items);}throw fail(INVALID_SCHEMA);
 }
}
