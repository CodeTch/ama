package ama.minutes.json;
import ama.contract.MinutesContracts.Anchor;
import ama.minutes.contract.OutputValidation;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;
class Task09StoredAnchorTest {
 static final Anchor A=new Anchor(1,3,"a".repeat(64));
 Anchor read(String raw,BooleanSupplier cancel)throws Exception {
  Class<?> c=assertDoesNotThrow(()->Class.forName("ama.minutes.json.StoredMinutesAnchorReader"),"stored anchors must use strict parsing");
  try{return (Anchor)c.getMethod("read",String.class,BooleanSupplier.class).invoke(c.getConstructor().newInstance(),raw,cancel);}catch(InvocationTargetException e){if(e.getCause() instanceof RuntimeException x)throw x;throw e;}
 }
 @Test void retainsExactScalarAnchorAndCanonicalStoredText()throws Exception {assertEquals(A,read(OutputValidation.anchorText(A),()->false));}
 @Test void rejectsReorderedWhitespaceDuplicateUnknownAndTrailingStoredForms()throws Exception {
  String good=OutputValidation.anchorText(A);
  for(String bad:new String[]{" "+good,good+" ",good.replace("\"start\":1","\"start\":1,\"start\":1"),good.replace("\"start\":1","\"unknown\":1"),good+"{}",good.replace("1,\"end\"","1.0,\"end\""),good.replace("UNICODE_SCALAR","UTF16"),"\ufeff"+good})assertThrows(RuntimeException.class,()->read(bad,()->false),bad);
 }
 @Test void rejectsOversizeAndCancellation()throws Exception {
  assertThrows(RuntimeException.class,()->read(" ".repeat(4097),()->false));assertThrows(RuntimeException.class,()->read(OutputValidation.anchorText(A),()->true));
 }
}
