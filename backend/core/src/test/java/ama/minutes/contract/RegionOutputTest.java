package ama.minutes.contract;

import ama.contract.MinutesContracts.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static ama.contract.MinutesContracts.FailureCode.*;
import static ama.minutes.contract.SourceAndSupportTest.*;
import static org.junit.jupiter.api.Assertions.*;

class RegionOutputTest {
 private Output region(Category category,String title,String body){
  var base=output(category,"x",category==Category.ACTION?new Action(null,null):null,List.of(new Evidence(new ElementTarget("e",0,1),new SourceIds(List.of("s1")))),List.of(new Ref("s1",transcript().locator())));
  return new Output("ama.minutes-output.v2",H,title,body,base.elements(),List.of(),List.of(),base.sourceRefs(),base.evidence());
 }
 private ValidationContext context(Category category){return new ValidationContext(H,H,Scope.REGION,catalog(transcript()),category);}
 @Test void regionUsesOnlyItsCategoryAndRetainsNormalSourceAndAnchorValidation(){
  var out=region(Category.ACTION,"","");assertEquals(1,new OutputValidation().validate(out,context(Category.ACTION),()->false).anchors().size());
  fails(SOURCE_NOT_IN_INPUT,()->new OutputValidation().validate(out,new ValidationContext(H,H,Scope.REGION,catalog(),Category.ACTION),()->false));
 }
 @Test void regionCannotReplaceBodyTitleOrAnotherCategory(){
  fails(INVALID_SCHEMA,()->new OutputValidation().validate(region(Category.ACTION,"new title",""),context(Category.ACTION),()->false));
  fails(INVALID_SCHEMA,()->new OutputValidation().validate(region(Category.ACTION,"","new body"),context(Category.ACTION),()->false));
  fails(INVALID_SCHEMA,()->new OutputValidation().validate(region(Category.CONCLUSION,"",""),context(Category.ACTION),()->false));
 }
 @Test void regionSelectionRequiresBaseSetAndBoundedNonblankInstructions(){
  assertDoesNotThrow(()->new RegionRequest(R,Category.OVERVIEW,"保留关键决定"));
  assertThrows(Failure.class,()->new RegionRequest(null,Category.OVERVIEW,"要求"));
  assertThrows(Failure.class,()->new RegionRequest(R,Category.OVERVIEW,"  "));
  assertThrows(Failure.class,()->new RegionRequest(R,Category.OVERVIEW,"长".repeat(4097)));
 }
}
