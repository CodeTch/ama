package ama.transcript;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MeasuredTextTranscriptParserTest {
 @Test void reportsAllDecodedUnitsAndBytesWithoutChangingLegacyResult() throws Exception {
  String text="\ufeff😀\r\n \t\r\nx\n";
  byte[] bytes=text.getBytes(StandardCharsets.UTF_8);
  String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  var parser=new TextTranscriptParser();
  var budget=new TranscriptDocumentParser.ParseBudget(bytes.length,text.length(),2);
  var file=UUID.fromString("00000000-0000-4000-8000-000000000001");
  var legacy=parser.parse(new ByteArrayInputStream(bytes),file,sha,budget,()->false);
  var method=Arrays.stream(parser.getClass().getMethods()).filter(m->m.getName().equals("parseMeasured")).findFirst();
  assertTrue(method.isPresent(),"Actual parser usage must be available to aggregate admission");
  InputStream split=new ByteArrayInputStream(bytes) {
   @Override public synchronized int read(byte[] b,int off,int len) {return super.read(b,off,Math.min(1,len));}
  };
  Object measured=method.orElseThrow().invoke(parser,split,file,sha,budget,(java.util.function.BooleanSupplier)()->false);
  assertEquals(legacy,measured.getClass().getMethod("result").invoke(measured));
  Object usage=measured.getClass().getMethod("usage").invoke(measured);
  assertEquals((long)bytes.length,usage.getClass().getMethod("sourceBytes").invoke(usage));
  assertEquals((long)text.length(),usage.getClass().getMethod("decodedUtf16CodeUnits").invoke(usage));
  assertEquals(2,usage.getClass().getMethod("segments").invoke(usage));
 }
}
