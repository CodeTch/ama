package ama.api;
import ama.contract.TranscriptContracts.*;import ama.contract.MinutesContracts.Mark;
import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class TranscriptWireTest {
 static final String R="11111111-1111-4111-8111-111111111111",S="22222222-2222-4222-8222-222222222222";
 static String save(){return "{\"expectedRevisionToken\":\"9007199254740993\",\"sourceRevisionId\":\""+R+"\",\"segments\":[{\"segmentId\":\""+S+"\",\"body\":\"A😀\\n B\",\"marks\":[\"DISPUTE\",\"KEY\"]}]}";}
 static Command parse(String text)throws IOException{return TranscriptWire.parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)),-1,Operation.SAVE);}
 @Test void exactUnicodeAndLongTokenWithNormalizedMarks()throws Exception {var value=parse(save());assertEquals(9007199254740993L,value.expectedRevision());assertEquals("A😀\n B",value.segments().getFirst().body());assertEquals(List.of(Mark.KEY,Mark.DISPUTE),value.segments().getFirst().marks());}
 @Test void duplicateUnknownTrailingScalarAndInvalidTokenRejected(){for(String text:List.of(save()+"{}",save().replace("\"body\":","\"body\":\"x\",\"body\":"),save().replace("\"marks\":","\"hash\":"),save().replace("9007199254740993","09007199254740993"),save().replace("A😀","\\ud800")))assertThrows(RuntimeException.class,()->parse(text));}
 @Test void unknownLengthStopsAtSingleOverflowSentinelAndDeclaredExcessDoesNotRead(){
  class Spaces extends InputStream {long read;public int read(){read++;return ' ';}public int read(byte[] b,int off,int len){Arrays.fill(b,off,off+len,(byte)' ');read+=len;return len;}}
  var stream=new Spaces();var failure=assertThrows(Failure.class,()->TranscriptWire.parse(stream,-1,Operation.SAVE));assertEquals(413,failure.status());assertTrue(stream.read<=TranscriptWire.REQUEST_BYTES+1L);
  var untouched=new Spaces();assertThrows(Failure.class,()->TranscriptWire.parse(untouched,TranscriptWire.REQUEST_BYTES+1L,Operation.SAVE));assertEquals(0,untouched.read);
 }
 @Test void escapedResponseAcceptsExactBytesAndRejectsOneLess()throws Exception {
  var body=Map.of("body","\u0000".repeat(10));int exact=("{\"body\":\""+"\\u0000".repeat(10)+"\"}").getBytes(StandardCharsets.UTF_8).length;
  assertEquals(exact,TranscriptWire.response(body,exact).length);assertEquals(503,assertThrows(Failure.class,()->TranscriptWire.response(body,exact-1)).status());
 }
}
