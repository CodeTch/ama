package ama.governance;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class QualityMetricsTest {
 @Test void unicodeEditsKeepInsertionsDeletionsAndSubstitutions() {
  var result=QualityMetrics.characters("甲乙丙", "甲丁丙戊");
  assertEquals(1d,result.get("substitutions"));assertEquals(1d,result.get("insertions"));
  assertEquals(0d,result.get("deletions"));assertEquals(2d/3,result.get("cer"),1e-9);
  assertEquals(1d,QualityMetrics.characters("𠮷", "吉").get("cer"));
 }
 @Test void diarizationMeasuresTimeAndPermitsArbitrarySpeakerNames() {
  var ref=List.of(new QualityMetrics.Turn(0,100,"a"),new QualityMetrics.Turn(100,200,"b"));
  var renamed=List.of(new QualityMetrics.Turn(0,100,"x"),new QualityMetrics.Turn(100,200,"y"));
  assertEquals(0d,QualityMetrics.diarization(ref,renamed).get("der"));
  var misplaced=List.of(new QualityMetrics.Turn(0,150,"x"),new QualityMetrics.Turn(150,200,"y"));
  assertEquals(.25,QualityMetrics.diarization(ref,misplaced).get("der"),1e-9);
  assertEquals(.5,QualityMetrics.diarization(ref,List.of(new QualityMetrics.Turn(0,100,"x"))).get("der"),1e-9);
 }
 @Test void overlapAndFalseAlarmStayInDiarizationError() {
  var ref=List.of(new QualityMetrics.Turn(0,100,"a"),new QualityMetrics.Turn(0,100,"b"));
  var hyp=List.of(new QualityMetrics.Turn(0,150,"x"));
  var result=QualityMetrics.diarization(ref,hyp);
  assertEquals(100d,result.get("missed"));assertEquals(50d,result.get("falseAlarm"));assertEquals(.75,result.get("der"));
 }
 @Test void entitySetMetricsCountMissingAndInventedEntities() {
  var result=QualityMetrics.entities(Set.of("PERSON:张三","PRODUCT:甲"),Set.of("PERSON:张三","PERSON:李四"));
  assertEquals(.5,result.get("precision"));assertEquals(.5,result.get("recall"));assertEquals(.5,result.get("f1"));
 }
 @Test void entityTypesRetainTheirOwnMissingAndInventedCounts() {
  var result=QualityMetrics.entities(Set.of("PERSON:张三","PRODUCT:甲"),Set.of("PERSON:张三","DEPARTMENT:研发"));
  assertEquals(1d,result.get("PERSON.f1"));assertEquals(1d,result.get("PRODUCT.falseNegative"));
  assertEquals(1d,result.get("DEPARTMENT.falsePositive"));assertEquals(0d,result.get("PRODUCT.f1"));
  assertFalse(result.containsKey("FINANCIAL_TERM.f1"),"No evaluated sample must not be presented as a perfect type");
  assertThrows(IllegalArgumentException.class,()->QualityMetrics.entities(Set.of("PERSON:张三"),Set.of("UNKNOWN:张三")));
 }
}
