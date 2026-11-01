package ama.api;
import org.junit.jupiter.api.Test;import org.springframework.mock.web.MockHttpServletRequest;import java.nio.charset.StandardCharsets;import static org.junit.jupiter.api.Assertions.*;
class MinutesRegionWireTest {
 private static final String ID="11111111-1111-4111-8111-111111111111";
 private MinutesWire.Command parse(String body)throws Exception{var request=new MockHttpServletRequest();request.setContent(body.getBytes(StandardCharsets.UTF_8));return MinutesWire.command(request,false);}
 @Test void acceptsExplicitRegionAndKeepsFullCompatible()throws Exception{
  var region=parse("{\"expectedRevisionToken\":\"7\",\"scope\":\"REGION\",\"baseElementSetId\":\""+ID+"\",\"category\":\"ACTION\",\"instructions\":\"突出负责人待确认\"}");assertEquals("ACTION",region.region().category().name());assertEquals(7,region.expected());assertNull(parse("{\"expectedRevisionToken\":\"7\",\"scope\":\"FULL\"}").region());
 }
 @Test void rejectsUnknownScopeMissingSelectionAndFullWithHiddenRegionFields(){
  for(String body:new String[]{"{\"expectedRevisionToken\":\"7\",\"scope\":\"ELEMENT\"}","{\"expectedRevisionToken\":\"7\",\"scope\":\"REGION\"}","{\"expectedRevisionToken\":\"7\",\"scope\":\"FULL\",\"instructions\":\"hidden\"}","{\"expectedRevisionToken\":\"7\",\"scope\":\"FULL\",\"scope\":\"FULL\"}"})assertThrows(RuntimeException.class,()->parse(body));
 }
}
