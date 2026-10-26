package ama.storage;
import java.util.*;import com.fasterxml.jackson.databind.node.*;import static org.junit.jupiter.api.Assertions.*;
final class Task11Fix02WireVariants {
 static final com.fasterxml.jackson.databind.ObjectMapper json=new com.fasterxml.jackson.databind.ObjectMapper();
 static Map<String,String> of(String original)throws Exception{
  var input=(ObjectNode)json.readTree(original);assertTrue(input.get("bindingTransition").isObject());var result=new LinkedHashMap<String,String>();var reverse=json.createObjectNode();var keys=new ArrayList<String>();input.fieldNames().forEachRemaining(keys::add);Collections.reverse(keys);for(String key:keys)reverse.set(key,input.get(key));result.put("noncanonical-order",json.writeValueAsString(reverse));
  for(String variant:List.of("unsupported-schema","legacy-incomplete","transition-absent","transition-null","transition-type","transition-extra","transition-predecessors","transition-new-key","transition-new-hash","transition-generation")){
   var bad=input.deepCopy();var transition=(ObjectNode)bad.get("bindingTransition");switch(variant){
    case "unsupported-schema"->bad.put("schema","ama.knowledge-publication-manifest.v9");case "legacy-incomplete"->bad.remove("archiveIndexSha256");case "transition-absent"->bad.remove("bindingTransition");case "transition-null"->bad.putNull("bindingTransition");case "transition-type"->bad.put("bindingTransition",0);case "transition-extra"->transition.put("extra",0);case "transition-predecessors"->transition.set("priorProofs",json.createArrayNode());case "transition-new-key"->transition.put("newBusinessKey","wrong");case "transition-new-hash"->transition.put("newLogicalRequestHash","0".repeat(64));case "transition-generation"->transition.put("expectedGeneration",2);default->throw new AssertionError(variant);
   }result.put(variant,fit(bad,original));
  }return result;
 }
 static String fit(ObjectNode value,String original)throws Exception{
  String text=ama.transcript.Task11Fix02WireSemanticsProbe.canonical(json.writeValueAsString(value));int delta=original.length()-text.length();
  for(String key:List.of("requesterId","providerId","storeInstanceId","clientId","delegationKeyId","assertionKeyId","libraryId","folderId")){
   if(delta==0)break;String field=value.get(key).asText();int change=delta>0?Math.min(delta,255-field.length()):-Math.min(-delta,field.length()-1);if(change==0)continue;value.put(key,change>0?field+"x".repeat(change):field.substring(-change));delta-=change;
  }assertEquals(0,delta);text=ama.transcript.Task11Fix02WireSemanticsProbe.canonical(json.writeValueAsString(value));assertEquals(original.length(),text.length());return text;
 }
}
