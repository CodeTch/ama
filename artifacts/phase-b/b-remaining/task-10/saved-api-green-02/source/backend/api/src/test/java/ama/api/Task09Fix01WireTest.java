package ama.api;
import org.junit.jupiter.api.Test;import org.springframework.mock.web.MockHttpServletRequest;import static org.junit.jupiter.api.Assertions.*;
class Task09Fix01WireTest {
 MinutesWire.Command read(String body,boolean initial)throws Exception{var r=new MockHttpServletRequest();r.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));return MinutesWire.command(r,initial);}
 @Test void fullScopeAcceptsTokenLegacyAndEqualDualForms(){for(String revision:new String[]{"\"expectedRevisionToken\":\"4\"","\"expectedRevision\":4","\"expectedRevisionToken\":\"4\",\"expectedRevision\":4"})assertEquals(4,assertDoesNotThrow(()->read("{"+revision+",\"scope\":\"FULL\"}",false)).expected());}
 @Test void missingOrWrongScopeIsNeverImplicitFull(){for(String body:new String[]{"{\"expectedRevision\":4}","{\"expectedRevision\":4,\"scope\":null}","{\"expectedRevision\":4,\"scope\":4}","{\"expectedRevision\":4,\"scope\":\"PARTIAL\"}"})assertThrows(IllegalArgumentException.class,()->read(body,false));}
 @Test void fullStillRejectsDuplicateAndUnknownFields(){for(String tail:new String[]{",\"scope\":\"FULL\"",",\"unknown\":true"})assertThrows(IllegalArgumentException.class,()->read("{\"expectedRevision\":4,\"scope\":\"FULL\""+tail+"}",false));}
 @Test void initialSettingsRemainsSeparate(){String body="{\"expectedRevision\":3,\"templateRevisionId\":\"00000000-0000-0000-0000-000000000001\"}";assertEquals(3,assertDoesNotThrow(()->read(body,true)).expected());assertThrows(IllegalArgumentException.class,()->read(body.substring(0,body.length()-1)+",\"scope\":\"FULL\"}",true));}
}
