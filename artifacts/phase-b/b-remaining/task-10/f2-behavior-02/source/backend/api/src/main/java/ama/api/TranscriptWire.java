package ama.api;
import ama.contract.TranscriptContracts.*;
import ama.contract.MinutesContracts.Mark;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.util.*;
/** Stable streaming protocol envelope, independent of current operational admission. */
final class TranscriptWire {
 static final int REQUEST_BYTES=8388608;
 private static final JsonFactory FACTORY=JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(8).maxStringLength(REQUEST_BYTES).maxNumberLength(19).build()).build();
 private static final ObjectMapper JSON=new ObjectMapper(FACTORY);
 static Command parse(InputStream input,long length,Operation operation)throws IOException {
  if(length>REQUEST_BYTES)throw new Failure(413,"REQUEST_TOO_LARGE");
  try(var parser=FACTORY.createParser(new Bounded(input))){
   var reader=new Reader(parser);reader.expect(reader.next(),JsonToken.START_OBJECT);var revisions=JSON.createObjectNode();String source=null;List<Edit> edits=null;
   while(reader.next()!=JsonToken.END_OBJECT){reader.expect(parser.currentToken(),JsonToken.FIELD_NAME);String field=parser.currentName();var token=reader.next();
    switch(field){
     case "expectedRevision"->{reader.expect(token,JsonToken.VALUE_NUMBER_INT);String text=parser.getText();if(!text.matches("0|[1-9][0-9]*"))throw invalid();revisions.put(field,parser.getBigIntegerValue());}
     case "expectedRevisionToken"->revisions.put(field,reader.text(token));
     case "sourceRevisionId","savedRevisionId"->{if(!field.equals(operation==Operation.CONFIRM?"savedRevisionId":"sourceRevisionId"))throw invalid();source=reader.text(token);}
     case "segments"->{if(operation!=Operation.SAVE&&operation!=Operation.MARKS)throw invalid();reader.expect(token,JsonToken.START_ARRAY);var values=new ArrayList<Edit>();while(reader.next()!=JsonToken.END_ARRAY){if(values.size()>=100000)throw new Failure(413,"REQUEST_TOO_LARGE");values.add(reader.segment(operation));}edits=List.copyOf(values);}
     default->throw invalid();
    }
   }
   if(reader.next()!=null||source==null||(operation==Operation.SAVE||operation==Operation.MARKS)&&edits==null)throw invalid();
   return new Command(operation,HttpRevisionTokens.expected(revisions),source,edits==null?List.of():edits);
  }catch(com.fasterxml.jackson.core.JsonProcessingException malformed){throw invalid();}
 }
 private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid transcript request");}
 private static final class Reader {
  final JsonParser parser;int tokens;Reader(JsonParser p){parser=p;}
  JsonToken next()throws IOException {if(++tokens>1000000)throw new Failure(413,"REQUEST_TOO_LARGE");return parser.nextToken();}
  void expect(JsonToken actual,JsonToken expected){if(actual!=expected)throw invalid();}
  String text(JsonToken token)throws IOException{expect(token,JsonToken.VALUE_STRING);String value=parser.getText();ama.contract.MinutesContracts.scalars(value);return value;}
  Edit segment(Operation operation)throws IOException {
   expect(parser.currentToken(),JsonToken.START_OBJECT);String id=null,body=null;List<Mark> marks=null;
   while(next()!=JsonToken.END_OBJECT){expect(parser.currentToken(),JsonToken.FIELD_NAME);String field=parser.currentName();var token=next();switch(field){
    case "segmentId"->id=text(token);
    case "body"->{if(operation!=Operation.SAVE)throw invalid();body=text(token);}
    case "marks"->{expect(token,JsonToken.START_ARRAY);var values=new ArrayList<Mark>();while(next()!=JsonToken.END_ARRAY){if(values.size()>=4)throw new Failure(422,"INVALID_MARKS");String value=text(parser.currentToken());try{values.add(Mark.valueOf(value));}catch(IllegalArgumentException unknown){throw new Failure(422,"INVALID_MARKS");}}marks=List.copyOf(values);}
    default->throw invalid();
   }}
   if(id==null||marks==null||operation==Operation.SAVE&&body==null)throw invalid();return new Edit(id,body,marks);
  }
 }
 private static final class Bounded extends FilterInputStream {
  long count;Bounded(InputStream input){super(input);}
  public int read()throws IOException {int b=in.read();if(b>=0&&++count>REQUEST_BYTES)throw new Failure(413,"REQUEST_TOO_LARGE");return b;}
  public int read(byte[] b,int offset,int length)throws IOException {int n=in.read(b,offset,(int)Math.min(length,REQUEST_BYTES-count+1));if(n>0){count+=n;if(count>REQUEST_BYTES)throw new Failure(413,"REQUEST_TOO_LARGE");}return n;}
 }
 static byte[] response(Object value,long maximum)throws IOException {
  class Counter extends OutputStream {long size;public void write(int b){reserve(1);}public void write(byte[] b,int o,int n){reserve(n);}void reserve(int n){if(n>maximum-size)throw new Failure(503,"RESPONSE_BUDGET_EXCEEDED");size+=n;}}
  var count=new Counter();JSON.writeValue(count,value);return JSON.writeValueAsBytes(value);
 }
}
