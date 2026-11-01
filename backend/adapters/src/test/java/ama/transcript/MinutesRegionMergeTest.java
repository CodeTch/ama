package ama.transcript;
import ama.contract.MinutesContracts.*;import java.util.*;import org.junit.jupiter.api.Test;import static ama.contract.MinutesContracts.*;import static org.junit.jupiter.api.Assertions.*;
class MinutesRegionMergeTest {
 static String id(int n){return "00000000-0000-4000-8000-%012d".formatted(n);}static final String TIME="2026-11-01T00:00:00.000000Z";
 static FullRow row(Table table,Object...values){assertEquals(table.columns().size(),values.length,table.name());return assertDoesNotThrow(()->new FullRow(table,Arrays.stream(values).map(v->v==null?(Cell)new NullCell():v instanceof Number n?cell(n.longValue()):cell((String)v)).toList()),table.name());}
 static List<FullRow> rows(){return List.of(
  row(Table.ELEMENT_SET,id(2),id(1),1L,TIME),row(Table.ELEMENT_SET,id(3),id(1),2L,TIME),
  row(Table.MINUTES_ELEMENT,id(4),id(2),"OVERVIEW",1L,"旧概述","PARTIAL"),row(Table.MINUTES_ELEMENT,id(5),id(2),"ACTION",1L,"人工行动项","UNSUPPORTED"),row(Table.MINUTES_ELEMENT,id(6),id(3),"OVERVIEW",1L,"新概述","PARTIAL"),
  row(Table.ACTION_ITEM,id(5),"owner","负责人",null),row(Table.ACTION_SUGGESTION,id(5),id(7),"原提及",null),
  row(Table.SOURCE_LOCATOR,id(8),id(1),"TRANSCRIPT",id(9),id(10),null,null,null,null,null,null,null),
  row(Table.EVIDENCE_LINK,id(11),id(8),id(5),null,"UNSUPPORTED","anchor retained"),row(Table.ELEMENT_SOURCE_CONTEXT,id(5),id(7)),row(Table.EVIDENCE_SOURCE_CONTEXT,id(11),id(7)),
  row(Table.BACKGROUND_NOTE,id(12),id(2),id(7),1L,"原背景"),row(Table.BACKGROUND_NOTE_SOURCE,id(12),id(8)),
  row(Table.SENSITIVE_DETECTION,id(13),id(2),id(7),"POLITICAL_SENSITIVE",1L),row(Table.SENSITIVE_DETECTION_SOURCE,id(13),id(8)));
 }
 @Test void mergingReplacesOnlySelectedRegionAndRetainsUnrelatedHumanFieldsAndSources(){
  var plan=MinutesSavedGraphPlan.region(id(2),id(3),Category.OVERVIEW,rows(),"owner",Task11PublicationManifestTest.ledger());assertNull(plan.body());assertNotEquals(id(2),plan.set());var copied=plan.rows(1,3);
  assertEquals(List.of("人工行动项","新概述"),copied.stream().filter(r->r.table()==Table.MINUTES_ELEMENT).map(r->r.string("body")).sorted().toList());
  var action=copied.stream().filter(r->r.table()==Table.ACTION_ITEM).findFirst().orElseThrow();assertEquals("owner",action.string("owner_enterprise_id"));assertNull(action.nullableString("due_on"));
  assertEquals(1,copied.stream().filter(r->r.table()==Table.EVIDENCE_SOURCE_CONTEXT).count());assertEquals(1,copied.stream().filter(r->r.table()==Table.ELEMENT_SOURCE_CONTEXT).count());
  assertEquals("原背景",copied.stream().filter(r->r.table()==Table.BACKGROUND_NOTE).findFirst().orElseThrow().string("body"));assertEquals(1,copied.stream().filter(r->r.table()==Table.SENSITIVE_DETECTION).count());assertTrue(copied.stream().noneMatch(r->r.table()==Table.MINUTES_REVISION||r.table()==Table.RESULT_REVIEW));
 }
 @Test void wrongCategoryCandidateCannotBeMerged(){assertThrows(Failure.class,()->MinutesSavedGraphPlan.region(id(2),id(3),Category.ACTION,rows(),"owner",Task11PublicationManifestTest.ledger()));}
 @Test void emptySelectedRegionDoesNotEraseAnotherRegion(){var empty=rows().stream().filter(r->r.table()!=Table.MINUTES_ELEMENT||!id(6).equals(r.string("id"))).toList();var copied=MinutesSavedGraphPlan.region(id(2),id(3),Category.OVERVIEW,empty,"owner",Task11PublicationManifestTest.ledger()).rows(1,3);assertEquals(List.of("人工行动项"),copied.stream().filter(r->r.table()==Table.MINUTES_ELEMENT).map(r->r.string("body")).toList());}
}
