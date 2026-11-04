package ama.minutes;
import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class MinutesGenerationFreshnessTest {
 final MinutesGenerationFreshness.Input current=new MinutesGenerationFreshness.Input("tx","materials","template","excluded","set",9);
 @Test void elementEditsRequireGenerationButHumanBodyEditsDoNot(){assertTrue(MinutesGenerationFreshness.afterEdit(true,false));assertFalse(MinutesGenerationFreshness.afterEdit(false,false));assertTrue(MinutesGenerationFreshness.afterEdit(false,true));}
 @Test void exactSuccessfulFullCandidateClearsPromptBeforeAdoption(){assertFalse(MinutesGenerationFreshness.needsPrompt(true,current,List.of(new MinutesGenerationFreshness.Completed(current,true,true,true))));}
 @Test void failedStalePartialAndSubsequentEditsDoNotClearPrompt(){
  for(var c:List.of(new MinutesGenerationFreshness.Completed(current,true,true,false),new MinutesGenerationFreshness.Completed(current,false,true,true),new MinutesGenerationFreshness.Completed(current,true,false,true),new MinutesGenerationFreshness.Completed(new MinutesGenerationFreshness.Input("tx","materials","new","excluded","set",10),true,true,true)))assertTrue(MinutesGenerationFreshness.needsPrompt(true,current,List.of(c)));
 }
}
