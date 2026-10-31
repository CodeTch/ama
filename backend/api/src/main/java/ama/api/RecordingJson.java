package ama.api;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
/** Finite control messages are parsed before any business locks. */
final class RecordingJson {
 static final class TooLarge extends RuntimeException {}
 private static final ObjectMapper JSON=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(4).maxStringLength(128).maxNumberLength(20).build()).build()).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
 static <T>T read(HttpServletRequest request,Class<T> type)throws IOException {
  if(request.getContentLengthLong()>1024)throw new TooLarge();byte[] bytes=request.getInputStream().readNBytes(1025);if(bytes.length>1024)throw new TooLarge();
  try{return JSON.readValue(bytes,type);}catch(com.fasterxml.jackson.core.JsonProcessingException invalid){throw new IllegalArgumentException("Invalid recording control message",invalid);}
 }
 static Map<String,Long> finals(HttpServletRequest request)throws IOException {
  var node=read(request,JsonNode.class);if(node==null||!node.isObject()||node.size()>16)throw new IllegalArgumentException("Invalid final sequences");var values=new LinkedHashMap<String,Long>();
  var fields=node.fields();while(fields.hasNext()){var entry=fields.next();ama.contract.OutboxContracts.uuid(entry.getKey());var value=entry.getValue();if(!value.isIntegralNumber()||!value.canConvertToLong()||value.longValue()<0)throw new IllegalArgumentException("Invalid final sequence");values.put(entry.getKey(),value.longValue());}return Map.copyOf(values);
 }
}
