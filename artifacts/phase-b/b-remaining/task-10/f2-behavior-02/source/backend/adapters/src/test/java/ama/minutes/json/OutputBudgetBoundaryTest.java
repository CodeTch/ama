package ama.minutes.json;
import ama.contract.MinutesContracts.*;import java.nio.charset.StandardCharsets;import java.util.stream.*;import org.junit.jupiter.api.*;
import static ama.minutes.json.StrictMinutesJsonReaderTest.*;import static ama.contract.MinutesContracts.FailureCode.*;import static org.junit.jupiter.api.Assertions.*;
class OutputBudgetBoundaryTest {
 // Independently counted from the literal JSON shape: 46 tokens, 234 decoded aggregate scalars, 3 container levels.
 static ValidationBudget budget(long[] a){return new ValidationBudget(a[0],(int)a[1],a[2],(int)a[3],(int)a[4],a[5],a[6],a[7],1,1,1,1,1,1);}
 static long[] exact(){return new long[]{json().getBytes(StandardCharsets.UTF_8).length,3,46,16,64,64,64,234};}
 @Test void exactEveryScalarTokenNameDepthAndWireBoundaryAccepts(){
  var raw=json().getBytes(StandardCharsets.UTF_8);assertDoesNotThrow(()->new StrictMinutesJsonReader().read(new java.io.ByteArrayInputStream(raw),CTX,budget(exact()),()->false));
 }
 @TestFactory Stream<DynamicTest> eachBoundaryPlusOneFailsWithoutPartialResult(){
  return IntStream.range(0,8).mapToObj(i->DynamicTest.dynamicTest("output-boundary-"+i,()->{
   var b=exact();b[i]--;var raw=json().getBytes(StandardCharsets.UTF_8);
   assertEquals(BUDGET_EXCEEDED,assertThrows(Failure.class,()->new StrictMinutesJsonReader().read(new java.io.ByteArrayInputStream(raw),CTX,budget(b),()->false)).code());
  }));
 }

 static String fullDirectories(){
  String source2="{\"sourceId\":\"s2\",\"locator\":{\"kind\":\"TRANSCRIPT\",\"transcriptRevisionId\":\""+R+"\",\"segmentId\":\"00000000-0000-0000-0000-000000000006\"}}";
  return json().replace("\"CONCLUSION\":[]","\"CONCLUSION\":[{\"key\":\"a\",\"text\":\"A\",\"action\":null},{\"key\":\"b\",\"text\":\"B\",\"action\":null}]")
   .replace("\"sourceRefs\":[]","\"sourceRefs\":[{\"sourceId\":\"s1\",\"locator\":{\"kind\":\"TRANSCRIPT\",\"transcriptRevisionId\":\""+R+"\",\"segmentId\":\""+S+"\"}},"+source2+"]")
   .replace("\"sensitive\":[]","\"sensitive\":[{\"key\":\"d1\",\"category\":\"POLITICAL_SENSITIVE\",\"sourceIds\":[\"s1\"]},{\"key\":\"d2\",\"category\":\"INTERNAL_BUSINESS_DATA\",\"sourceIds\":[\"s2\"]}]")
   .replace("\"background\":[]","\"background\":[{\"key\":\"n1\",\"text\":\"N\",\"sourceIds\":[\"s1\"]},{\"key\":\"n2\",\"text\":\"N\",\"sourceIds\":[\"s2\"]}]")
   .replace("\"evidence\":[]","\"evidence\":[{\"target\":{\"kind\":\"ELEMENT\",\"elementKey\":\"a\",\"start\":0,\"end\":1},\"sourceIds\":[\"s1\"]},{\"target\":{\"kind\":\"ELEMENT\",\"elementKey\":\"b\",\"start\":0,\"end\":1},\"sourceIds\":[\"s2\"]}]");
 }
 static ama.contract.MinutesContracts.ValidationContext completeContext(){
  var entries=new java.util.ArrayList<ama.contract.MinutesContracts.CatalogFact>();entries.add(CAT.sources().getFirst().fact());String s2="00000000-0000-0000-0000-000000000006";
  entries.add(new ama.contract.MinutesContracts.CatalogFact(new ama.contract.MinutesContracts.TranscriptLocator(R,s2),ama.contract.MinutesContracts.Role.MEETING,new ama.contract.MinutesContracts.TranscriptVersion(C,R,B,F,H),H,2,new ama.contract.MinutesContracts.TranscriptBounds(R,s2,F,2L)));
  var catalog=new ama.minutes.contract.SourceCatalog().build(new ama.contract.MinutesContracts.CatalogFacts(entries,100),new ama.contract.MinutesContracts.CanonicalBudget(100000,100,10000,10000,10000),()->false);
  return new ama.contract.MinutesContracts.ValidationContext(H,H,ama.contract.MinutesContracts.Scope.FULL,catalog);
 }
 static ValidationBudget containers(long[] a){return new ValidationBudget(100000,30,10000,100,10000,10000,10000,100000,a[0],a[1],a[2],a[3],a[4],a[5]);}
 @Test void exactAllDirectoryAndMembershipCountsAccept(){
  byte[] raw=fullDirectories().getBytes(StandardCharsets.UTF_8);assertDoesNotThrow(()->new StrictMinutesJsonReader().read(new java.io.ByteArrayInputStream(raw),completeContext(),containers(new long[]{2,2,2,2,2,6}),()->false));
 }
 @TestFactory Stream<DynamicTest> eachContainerLimitIsCumulativeAndFailsAtOneOver(){
  return IntStream.range(0,6).mapToObj(i->DynamicTest.dynamicTest("container-boundary-"+i,()->{
   long[] a={2,2,2,2,2,6};a[i]--;byte[] raw=fullDirectories().getBytes(StandardCharsets.UTF_8);
   assertEquals(BUDGET_EXCEEDED,assertThrows(Failure.class,()->new StrictMinutesJsonReader().read(new java.io.ByteArrayInputStream(raw),completeContext(),containers(a),()->false)).code());
  }));
 }
}
