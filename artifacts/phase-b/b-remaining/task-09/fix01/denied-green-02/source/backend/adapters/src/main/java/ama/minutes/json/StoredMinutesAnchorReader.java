package ama.minutes.json;

import ama.contract.MinutesContracts.Anchor;
import ama.minutes.contract.OutputValidation;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Stored anchors are canonical schema-owned bytes, not caller-supplied attestations. */
public final class StoredMinutesAnchorReader {
 public Anchor read(String text,BooleanSupplier cancelled) {
  require(text!=null&&cancelled!=null,INVALID_ARGUMENT);check(cancelled);
  require(text.length()<=4096,BUDGET_EXCEEDED);scalars(text);
  var builder=JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder()
    .maxNestingDepth(2).maxDocumentLength(4096).maxTokenCount(32).maxNumberLength(19).maxNameLength(32).maxStringLength(128).build())
    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION);
  for(var feature:JsonReadFeature.values())builder.disable(feature);
  try(var parser=builder.build().createParser(text)) {
   require(parser.nextToken()==JsonToken.START_OBJECT,INVALID_SCHEMA);var seen=new HashSet<String>();Long start=null,end=null;String hash=null;
   while(parser.nextToken()!=JsonToken.END_OBJECT) {
    check(cancelled);require(parser.currentToken()==JsonToken.FIELD_NAME,INVALID_SCHEMA);String key=parser.currentName();require(seen.add(key),INVALID_JSON);
    var token=parser.nextToken();
    switch(key){
     case "schemaVersion"->{require(token==JsonToken.VALUE_STRING&&parser.getText().equals("ama.anchor.v1"),INVALID_SCHEMA);}
     case "unit"->{require(token==JsonToken.VALUE_STRING&&parser.getText().equals("UNICODE_SCALAR"),INVALID_SCHEMA);}
     case "start"->{start=number(parser);}
     case "end"->{end=number(parser);}
     case "targetTextSha256"->{require(token==JsonToken.VALUE_STRING,INVALID_SCHEMA);hash=parser.getText();}
     default->throw fail(UNKNOWN_FIELD);
    }
   }
   require(seen.equals(Set.of("schemaVersion","unit","start","end","targetTextSha256"))&&parser.nextToken()==null,INVALID_SCHEMA);
   var anchor=new Anchor(start,end,hash);require(OutputValidation.anchorText(anchor).equals(text),INVALID_SCHEMA);check(cancelled);return anchor;
  }catch(Failure safe){throw safe;}catch(IOException error){check(cancelled);throw fail(INVALID_JSON);}
 }
 private static long number(JsonParser parser)throws IOException {
  require(parser.currentToken()==JsonToken.VALUE_NUMBER_INT,INVALID_NUMBER);String raw=parser.getText();require(raw.matches("0|[1-9][0-9]{0,18}"),INVALID_NUMBER);
  try{return Long.parseLong(raw);}catch(NumberFormatException error){throw fail(INVALID_NUMBER);}
 }
}
