package ama.minutes.json;
import ama.contract.MinutesContracts.*;
import ama.minutes.contract.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
import static org.junit.jupiter.api.Assertions.*;

class StrictMinutesJsonReaderTest {
 static final String H="0".repeat(64),R="00000000-0000-0000-0000-000000000001",S="00000000-0000-0000-0000-000000000002",F="00000000-0000-0000-0000-000000000003",C="00000000-0000-0000-0000-000000000004",B="00000000-0000-0000-0000-000000000005";
 static final ValidationBudget BUDGET=new ValidationBudget(100000,30,10000,100,10000,10000,10000,100000,100,100,100,100,100,100);
 static final Catalog CAT=new SourceCatalog().build(new CatalogFacts(List.of(new CatalogFact(new TranscriptLocator(R,S),Role.MEETING,new TranscriptVersion(C,R,B,F,H),H,1,new TranscriptBounds(R,S,F,1L))),100000L),new CanonicalBudget(100000,100,10000,10000,10000),()->false);
 static final ValidationContext CTX=new ValidationContext(H,H,Scope.FULL,CAT);
 final StrictMinutesJsonReader reader=new StrictMinutesJsonReader();
 static String json(){return """
 {"schemaVersion":"ama.minutes-output.v1","inputFingerprint":"%s","title":"","body":"A😀B",
 "elements":{"OVERVIEW":[],"DISCUSSION":[],"CONCLUSION":[],"ACTION":[],"RISK":[],"OPEN_QUESTION":[],"DISAGREEMENT":[]},
 "sensitive":[],"background":[],"sourceRefs":[],"evidence":[]}
 """.formatted(H);}
 ValidatedOutput read(String text){return read(text.getBytes(StandardCharsets.UTF_8),BUDGET);}
 ValidatedOutput read(byte[] bytes,ValidationBudget budget){return reader.read(new ByteArrayInputStream(bytes),CTX,budget,()->false);}
 Failure fails(FailureCode code,Runnable r){var e=assertThrows(Failure.class,r::run);assertEquals(code,e.code());assertEquals(code.name(),e.getMessage());assertNull(e.getCause());assertEquals(0,e.getSuppressed().length);return e;}
 @Test void allSevenKindsEmptyDirectoriesTitleAndNonBmpBody(){var v=assertDoesNotThrow(()->read(json()));assertEquals("A😀B",v.output().body());assertEquals(7,v.output().elements().size());assertTrue(v.output().evidence().isEmpty());}
 @Test void strictSchemaUnknownEscapedDuplicateMissingAndEof(){
   fails(UNKNOWN_FIELD,()->read(json().replace("\"title\":\"\"","\"secret-path\":\"\"")));
   fails(INVALID_JSON,()->read(json().replace("\"title\":\"\"","\"title\":\"\",\"t\\u0069tle\":\"\"")));
   fails(INVALID_SCHEMA,()->read(json().replace("\"title\":\"\",","")));
   fails(INVALID_JSON,()->read(json()+" {}"));
   fails(INVALID_JSON,()->read(String.valueOf((char)96).repeat(3)+"json"+json()));
   fails(INVALID_SCHEMA,()->read(json().replace("ama.minutes-output.v1","ama.minutes-output.v2")));
   fails(FINGERPRINT_MISMATCH,()->read(json().replace(H,"1".repeat(64))));
 }
 @Test void utf8DecoderRejectsMalformedSuffixBomOtherEncodingsAndEscapedSurrogates(){
   byte[] valid=json().getBytes(StandardCharsets.UTF_8);byte[] bad=Arrays.copyOf(valid,valid.length+1);bad[valid.length]=(byte)0xff;
   fails(INVALID_UTF8,()->read(bad,BUDGET));
   fails(INVALID_UTF8,()->read(("\ufeff"+json()).getBytes(StandardCharsets.UTF_8),BUDGET));
   for(var charset:List.of(StandardCharsets.UTF_16LE,StandardCharsets.UTF_16BE))assertThrows(Failure.class,()->read(json().getBytes(charset),BUDGET));
   fails(INVALID_SCALAR,()->read(json().replace("A😀B","\\ud800")));
   fails(INVALID_SCALAR,()->read(json().replace("A😀B","\\udc00")));
   assertEquals("😀",read(json().replace("A😀B","\\ud83d\\ude00")).output().body());
 }
 static String referenced(String start,String end){
  return json().replace("\"ACTION\":[]","\"ACTION\":[{\"key\":\"a\",\"text\":\"A😀B\",\"action\":{\"ownerMention\":\"Alice\",\"dueOn\":\"2024-02-29\"}}]")
   .replace("\"sourceRefs\":[]","\"sourceRefs\":[{\"sourceId\":\"s1\",\"locator\":{\"kind\":\"TRANSCRIPT\",\"transcriptRevisionId\":\""+R+"\",\"segmentId\":\""+S+"\"}}]")
   .replace("\"evidence\":[]","\"evidence\":[{\"target\":{\"kind\":\"ELEMENT\",\"elementKey\":\"a\",\"start\":"+start+",\"end\":"+end+"},\"sourceIds\":[\"s1\"]}]");
 }
 @Test void rawActionSuggestionsStrictNumbersDatesAndScalarOffsets(){
   var v=assertDoesNotThrow(()->read(referenced("1","2")));assertEquals("Alice",v.output().elements().get(Category.ACTION).getFirst().action().ownerMention());assertEquals(1,v.anchors().size());
   for(var n:List.of("-0","-1","1.0","1e0","\"1\"","9223372036854775808","true","+1","01"))assertThrows(Failure.class,()->read(referenced(n,"2")),n);
   for(var date:List.of("2023-02-29","2024-2-29","0999-12-31","2024-04-31"))fails(INVALID_DATE,()->read(referenced("0","1").replace("2024-02-29",date)));
   fails(TARGET_INVALID,()->read(referenced("1","4")));
   fails(INVALID_ID,()->read(referenced("0","1").replace("\"s1\"","\"s01\"")));
 }
 @Test void exactWireBoundaryPlusOneSentinelAndNoCloseOnEveryOutcome(){
  byte[] bytes=json().getBytes(StandardCharsets.UTF_8);
  var b=withWire(bytes.length);
  var stream=new Tracking(bytes);assertDoesNotThrow(()->reader.read(stream,CTX,b,()->false));assertFalse(stream.closed);assertEquals(bytes.length,stream.readCount);
  var over=new Tracking(Arrays.copyOf(bytes,bytes.length+50));
  fails(BUDGET_EXCEEDED,()->reader.read(over,CTX,b,()->false));assertFalse(over.closed);assertEquals(bytes.length+1,over.readCount);
  var malformed=new Tracking("private secret".getBytes(StandardCharsets.UTF_8));assertThrows(Failure.class,()->reader.read(malformed,CTX,BUDGET,()->false));assertFalse(malformed.closed);
 }
 static ValidationBudget withWire(long n){return new ValidationBudget(n,30,10000,100,10000,10000,10000,100000,100,100,100,100,100,100);}
 @Test void cancellationReadFailuresAndInterruptedIoAreRedactedAndPreserveOwnership(){
  var cancelled=new Tracking(json().getBytes(StandardCharsets.UTF_8));fails(CANCELLED,()->reader.read(cancelled,CTX,BUDGET,()->true));assertEquals(0,cancelled.readCount);assertFalse(cancelled.closed);
  InputStream broken=new InputStream(){public int read()throws IOException{throw new IOException("secret-path");}};
  fails(READ_FAILED,()->reader.read(broken,CTX,BUDGET,()->false));
  InputStream interrupted=new InputStream(){public int read()throws IOException{throw new InterruptedIOException("secret-path");}};
  try{fails(CANCELLED,()->reader.read(interrupted,CTX,BUDGET,()->false));assertTrue(Thread.currentThread().isInterrupted());}finally{Thread.interrupted();}
  var flag=new AtomicBoolean();var mid=new Tracking(json().getBytes(StandardCharsets.UTF_8)){public int read(byte[] b,int o,int l){int n=super.read(b,o,l);flag.set(true);return n;}};
  fails(CANCELLED,()->reader.read(mid,CTX,BUDGET,flag::get));assertFalse(mid.closed);
 }
 @Test void perFactoryTokenNameStringDepthAndMembershipLimits(){
  for(var b:List.of(new ValidationBudget(100000,1,10000,100,10000,10000,10000,100000,100,100,100,100,100,100),
    new ValidationBudget(100000,30,1,100,10000,10000,10000,100000,100,100,100,100,100,100),
    new ValidationBudget(100000,30,10000,1,10000,10000,10000,100000,100,100,100,100,100,100),
    new ValidationBudget(100000,30,10000,100,1,10000,10000,100000,100,100,100,100,100,100),
    new ValidationBudget(100000,30,10000,100,10000,1,10000,100000,100,100,100,100,100,100),
    new ValidationBudget(100000,30,10000,100,10000,10000,1,100000,100,100,100,100,100,100),
    new ValidationBudget(100000,30,10000,100,10000,10000,10000,1,100,100,100,100,100,100)))
     fails(BUDGET_EXCEEDED,()->read(json().getBytes(StandardCharsets.UTF_8),b));
  String doubled=referenced("0","1").replace("\"sensitive\":[]","\"sensitive\":[{\"key\":\"d\",\"category\":\"POLITICAL_SENSITIVE\",\"sourceIds\":[\"s1\"]}]");
  var one=new ValidationBudget(100000,30,10000,100,10000,10000,10000,100000,100,100,100,100,100,1);
  fails(BUDGET_EXCEEDED,()->read(doubled.getBytes(StandardCharsets.UTF_8),one));
 }
 static class Tracking extends ByteArrayInputStream {boolean closed;long readCount;Tracking(byte[] b){super(b);}
  @Override public int read(byte[] b,int o,int l){int n=super.read(b,o,l);if(n>0)readCount+=n;return n;}
  @Override public int read(){int n=super.read();if(n>=0)readCount++;return n;}
  @Override public void close(){closed=true;}
 }
}
