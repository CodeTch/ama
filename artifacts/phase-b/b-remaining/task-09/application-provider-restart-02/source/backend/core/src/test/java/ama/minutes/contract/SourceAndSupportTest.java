package ama.minutes.contract;
import ama.contract.MinutesContracts.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
import static org.junit.jupiter.api.Assertions.*;

class SourceAndSupportTest {
 static final String H="0".repeat(64),R="00000000-0000-0000-0000-000000000001",S="00000000-0000-0000-0000-000000000002",F="00000000-0000-0000-0000-000000000003",C="00000000-0000-0000-0000-000000000004",B="00000000-0000-0000-0000-000000000005",M="00000000-0000-0000-0000-000000000006",D="00000000-0000-0000-0000-000000000007";
 static final CanonicalBudget BUDGET=new CanonicalBudget(100000,100,10000,10000,10000);
 static CatalogFact transcript(){return new CatalogFact(new TranscriptLocator(R,S),Role.MEETING,new TranscriptVersion(C,R,B,F,H),H,1,new TranscriptBounds(R,S,F,4L));}
 static CatalogFact paragraph(long n){return new CatalogFact(new ParagraphLocator(ParagraphKind.TEXT_PARAGRAPH,F,n,null),Role.BACKGROUND,new FileVersion(F,M,D,"p1",H),H,0,new DocumentBounds(F,M,D,"p1",H,List.of(new SourceUnit(1,null,1L,null),new SourceUnit(2,null,4L,null)),100000L));}
 static Catalog catalog(CatalogFact... facts){return new SourceCatalog().build(new CatalogFacts(List.of(facts),100000L),BUDGET,()->false);}
 static Output output(Category category,String text,Action action,List<Evidence> evidence,List<Ref> refs) {
    var kinds=new EnumMap<Category,List<Element>>(Category.class);for(var c:Category.values())kinds.put(c,List.of());
    kinds.put(category,List.of(new Element("e",text,action)));
    return new Output("ama.minutes-output.v1",H,"","A😀B",kinds,List.of(),List.of(),refs,evidence);
 }
 static void fails(FailureCode c,Runnable r){assertEquals(c,assertThrows(Failure.class,r::run).code());}
 @Test void deterministicAliasesTranscriptFirstAndExactDedup(){
    var a=catalog(paragraph(4),transcript(),paragraph(1),transcript());
    var b=catalog(transcript(),paragraph(1),paragraph(4));
    assertEquals(a,b);assertEquals(3,a.sources().size());assertEquals(new TranscriptLocator(R,S),a.source("s1").fact().locator());
    assertEquals(new ParagraphLocator(ParagraphKind.TEXT_PARAGRAPH,F,1,null),a.source("s2").fact().locator());
    var bad=new CatalogFact(transcript().locator(),Role.BACKGROUND,transcript().sourceVersion(),H,1,transcript().bounds());
    fails(INPUT_INVALID,()->catalog(transcript(),bad));
 }
 @Test void unitMembershipUsesSavedPhysicalParagraphGapsAndVersion(){
    fails(LOCATOR_INVALID,()->catalog(paragraph(2)));
    var p=paragraph(1);var wrong=new CatalogFact(p.locator(),p.role(),new FileVersion(F,M,D,"p2",H),H,0,p.bounds());
    fails(LOCATOR_INVALID,()->catalog(wrong));
 }
 @Test void scalarAnchorHashesWholeTextAndFormatsCanonicalAscii(){
    var a=OutputValidation.anchor("A😀B",1,2,()->false);
    assertEquals("aacfb6637bd0df2238641c5b15898d1a61df3157b1d0b4099f590fc0b0b6fbbd",a.targetTextSha256());
    assertEquals("{\"schemaVersion\":\"ama.anchor.v1\",\"unit\":\"UNICODE_SCALAR\",\"start\":1,\"end\":2,\"targetTextSha256\":\""+a.targetTextSha256()+"\"}",OutputValidation.anchorText(a));
    fails(TARGET_INVALID,()->OutputValidation.anchor("A😀B",2,4,()->false));
 }
 @Test void aliasesAndFullTargetsValidatedWithoutInferringTruth(){
    var cat=catalog(transcript());var e=new Evidence(new ElementTarget("e",0,3),new SourceIds(List.of("s1")));
    var o=output(Category.CONCLUSION,"999",null,List.of(e),List.of(new Ref("s1",transcript().locator())));
    var v=new OutputValidation().validate(o,new ValidationContext(H,H,Scope.FULL,cat),()->false);
    var c=new CreationSupport().classify(v,cat);assertEquals(1,c.elements().size());assertEquals(1,c.evidence().size());
    assertEquals(Support.PARTIAL,c.elements().getFirst().support());assertEquals(Support.PARTIAL,c.evidence().getFirst().support());
    fails(SOURCE_NOT_IN_INPUT,()->new OutputValidation().validate(o,new ValidationContext(H,H,Scope.FULL,catalog()),()->false));
    var extra=output(Category.CONCLUSION,"999",null,List.of(),o.sourceRefs());
    fails(SOURCE_MEMBERSHIP_MISMATCH,()->new OutputValidation().validate(extra,new ValidationContext(H,H,Scope.FULL,cat),()->false));
 }
 @Test void backgroundOnlyActionsStayUnsupportedAndSuggestionsNeverAcceptIdentity(){
    var cat=catalog(paragraph(1));var e=new Evidence(new ElementTarget("e",0,1),new SourceIds(List.of("s1")));
    var o=output(Category.ACTION,"x",new Action("Alice","2024-02-29"),List.of(e),List.of(new Ref("s1",paragraph(1).locator())));
    var c=new CreationSupport().classify(new OutputValidation().validate(o,new ValidationContext(H,H,Scope.FULL,cat),()->false),cat);
    assertEquals(1,c.elements().size());assertEquals(1,c.evidence().size());assertEquals(Support.UNSUPPORTED,c.elements().getFirst().support());assertEquals(new AcceptedAction(null,null,null),c.elements().getFirst().acceptedAction());
    assertEquals(Support.UNSUPPORTED,c.evidence().getFirst().support());
 }
 @Test void danglingDuplicateRangeAndPartialScopeNeverSucceed(){
    var cat=catalog(transcript());var e=new Evidence(new ElementTarget("e",0,1),new SourceIds(List.of("s1")));
    var o=output(Category.DISCUSSION,"x",null,List.of(e,e),List.of(new Ref("s1",transcript().locator())));
    fails(SOURCE_MEMBERSHIP_MISMATCH,()->new OutputValidation().validate(o,new ValidationContext(H,H,Scope.FULL,cat),()->false));
    fails(CAPABILITY_UNAVAILABLE,()->new OutputValidation().validate(o,new ValidationContext(H,H,Scope.ELEMENT,cat),()->false));
 }

 @Test void syntheticAudioVersionHasNullMaterialAndParserFields(){
  var audio=assertDoesNotThrow(()->new CatalogFact(new AudioLocator(F,0,9),Role.BACKGROUND,new FileVersion(F,null,null,null,null),H,0,new AudioBounds(F,9)));
  assertEquals(1,catalog(audio).sources().size());
  fails(LOCATOR_INVALID,()->catalog(new CatalogFact(new AudioLocator(F,0,10),Role.BACKGROUND,audio.sourceVersion(),H,0,audio.bounds())));
 }
}
