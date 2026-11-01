package ama.api;
import com.fasterxml.jackson.core.*;import com.fasterxml.jackson.databind.*;import jakarta.servlet.http.HttpServletRequest;import java.io.*;import java.util.*;import ama.contract.MinutesContracts.*;
final class MinutesWire {
 private static final ObjectMapper JSON=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(4).maxStringLength(8192).maxNumberLength(19).build()).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
 record Command(long expected,String template,RegionRequest region){}
 static Command command(HttpServletRequest request,boolean initial)throws IOException {return parse(request,initial?"INITIAL":"GENERATION");}
 static long intent(HttpServletRequest request)throws IOException{return parse(request,"INTENT").expected();}
 private static Command parse(HttpServletRequest request,String kind)throws IOException {
  if(request.getContentLengthLong()>65536)throw new ama.contract.TranscriptContracts.Failure(413,"REQUEST_TOO_LARGE");byte[] bytes;try(var stream=request.getInputStream()){bytes=stream.readNBytes(65537);}if(bytes.length>65536)throw new ama.contract.TranscriptContracts.Failure(413,"REQUEST_TOO_LARGE");
  JsonNode node;try(var parser=JSON.getFactory().createParser(bytes)){int count=0;while(parser.nextToken()!=null)if(++count>32)throw new IllegalArgumentException("Too many command fields");node=JSON.readTree(bytes);}catch(com.fasterxml.jackson.core.JsonProcessingException malformed){throw new IllegalArgumentException("Malformed minutes request");}if(node==null||!node.isObject())throw new IllegalArgumentException("Object required");
  var allowed=new HashSet<>(Set.of("expectedRevision","expectedRevisionToken"));String template=null;RegionRequest region=null;
  if(kind.equals("INITIAL")){allowed.add("templateRevisionId");template=string(node,"templateRevisionId");ama.contract.MinutesContracts.uuid(template);}
  if(kind.equals("GENERATION")){allowed.add("scope");String scope=string(node,"scope");if(scope.equals("REGION")){allowed.addAll(Set.of("baseElementSetId","category","instructions"));region=new RegionRequest(string(node,"baseElementSetId"),Category.valueOf(string(node,"category")),string(node,"instructions"));}else if(!scope.equals("FULL"))throw new IllegalArgumentException("Unsupported generation scope");}
  node.fieldNames().forEachRemaining(name->{if(!allowed.contains(name))throw new IllegalArgumentException("Unknown field");});return new Command(HttpRevisionTokens.expected(node),template,region);
 }
 private static String string(JsonNode node,String name){var value=node.get(name);if(value==null||!value.isTextual())throw new IllegalArgumentException("Text field required");return value.textValue();}
}
