package ama.transcript;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TranscriptReplacementPlannerTest {
 static final String REV="00000000-0000-4000-8000-000000000001",OTHER="00000000-0000-4000-8000-000000000002";
 static final String ONE="00000000-0000-4000-8000-000000000003",TWO="00000000-0000-4000-8000-000000000004";
 @Test void completeSearchAndSelectedScalarOccurrencesPreserveIdenticalHomonymsOutsideTheSelection(){
  var planner=new TranscriptReplacementPlanner();var rows=List.of(new TranscriptReplacementPlanner.Segment(ONE,"😀最大回车，最大回车"),new TranscriptReplacementPlanner.Segment(TWO,"尾段最大回车"));
  var matches=planner.search(REV,rows,"最大回车");assertEquals(3,matches.size());assertEquals(1,matches.getFirst().startScalar());assertEquals(5,matches.getFirst().endScalar());assertEquals(TWO,matches.getLast().segmentId());
  var result=planner.replace(REV,rows,"最大回车","最大回撤",List.of(matches.get(1).occurrenceId(),matches.get(2).occurrenceId()));
  assertEquals("😀最大回车，最大回撤",result.getFirst().body());assertEquals("尾段最大回撤",result.getLast().body());assertEquals(ONE,result.getFirst().segmentId());assertEquals(rows, List.of(new TranscriptReplacementPlanner.Segment(ONE,"😀最大回车，最大回车"),new TranscriptReplacementPlanner.Segment(TWO,"尾段最大回车")));
 }
 @Test void occurrenceIdentityCannotBeReusedAcrossRevisionQueryOrChangedBodyAndDuplicatesAreRejected(){
  var planner=new TranscriptReplacementPlanner();var rows=List.of(new TranscriptReplacementPlanner.Segment(ONE,"甲甲"));var matches=planner.search(REV,rows,"甲");
  assertEquals(matches,planner.search(REV,rows,"甲"));assertNotEquals(matches,planner.search(OTHER,rows,"甲"));
  assertThrows(IllegalArgumentException.class,()->planner.replace(OTHER,rows,"甲","乙",List.of(matches.getFirst().occurrenceId())));
  assertThrows(IllegalArgumentException.class,()->planner.replace(REV,rows,"甲甲","乙",List.of(matches.getFirst().occurrenceId())));
  assertThrows(IllegalArgumentException.class,()->planner.replace(REV,List.of(new TranscriptReplacementPlanner.Segment(ONE,"乙甲")),"甲","乙",List.of(matches.getFirst().occurrenceId())));
  assertThrows(IllegalArgumentException.class,()->planner.replace(REV,rows,"甲","乙",List.of(matches.getFirst().occurrenceId(),matches.getFirst().occurrenceId())));
 }
 @Test void adjacentMatchesAreAppliedAgainstTheOriginalAndEmptyReplacementCanRemoveSelectedText(){
  var planner=new TranscriptReplacementPlanner();var rows=List.of(new TranscriptReplacementPlanner.Segment(ONE,"非常非常重要"));var matches=planner.search(REV,rows,"非常");
  assertEquals(2,matches.size());assertEquals("十分十分重要",planner.replace(REV,rows,"非常","十分",matches.stream().map(TranscriptReplacementPlanner.Occurrence::occurrenceId).toList()).getFirst().body());
  assertEquals("非常重要",planner.replace(REV,rows,"非常","",List.of(matches.getFirst().occurrenceId())).getFirst().body());
 }
 @Test void completeSearchAndExpansionHaveFiniteBudgetsAndMalformedUnicodeIsRejected(){
  var planner=new TranscriptReplacementPlanner();
  assertThrows(IllegalArgumentException.class,()->planner.search(REV,List.of(new TranscriptReplacementPlanner.Segment(ONE,"甲".repeat(10001))),"甲"));
  var rows=List.of(new TranscriptReplacementPlanner.Segment(ONE,"甲".repeat(1000)));var matches=planner.search(REV,rows,"甲");
  assertThrows(IllegalArgumentException.class,()->planner.replace(REV,rows,"甲","乙".repeat(10000),matches.stream().map(TranscriptReplacementPlanner.Occurrence::occurrenceId).toList()));
  assertThrows(IllegalArgumentException.class,()->planner.search(REV,rows,""));
  assertThrows(IllegalArgumentException.class,()->planner.search(REV,List.of(new TranscriptReplacementPlanner.Segment(ONE,"\uD800")),"甲"));
 }
}
