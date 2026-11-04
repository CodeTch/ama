package ama.transcript;

import java.lang.reflect.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PublicationConfigurationDataTest {
 final Map<String,Object> rules=Map.of("maxCount",2,"maxLength",4,"allowEmpty",true,"trim",true,"caseSensitive",true);
 Object tags(Object input,Map<String,Object> policy)throws Exception {
  var type=assertDoesNotThrow(()->Class.forName("ama.transcript.PublicationConfigurationData"));
  var method=type.getDeclaredMethod("tags",Object.class,Map.class);method.setAccessible(true);
  try{return method.invoke(null,input,policy);}catch(InvocationTargetException e){if(e.getCause() instanceof RuntimeException failure)throw failure;throw e;}
 }
 @Test void rulesComeFromServerAndNormalizationIsDeterministic()throws Exception {
  assertEquals(List.of("项目","评审"),tags(List.of(" 项目 ","项目","评审"),rules));assertEquals(List.of(),tags(List.of(),rules));
  assertThrows(IllegalArgumentException.class,()->tags(List.of("五个中文字"),rules));
  assertThrows(IllegalArgumentException.class,()->tags(List.of("a","b","c"),rules));
  assertThrows(IllegalArgumentException.class,()->tags(List.of("a\nb"),rules));
 }
 @Test void unicodeLengthCountsCharactersAndCaseRulesAreExplicit()throws Exception {
  assertEquals(List.of("😀😀😀😀"),tags(List.of("😀😀😀😀"),rules));
  assertEquals(List.of("A","a"),tags(List.of("A","a"),rules));
  var policy=new HashMap<>(rules);policy.put("caseSensitive",false);assertEquals(List.of("A"),tags(List.of("A","a"),policy));
  policy.put("allowEmpty",false);assertThrows(IllegalArgumentException.class,()->tags(List.of(),policy));
 }
}
