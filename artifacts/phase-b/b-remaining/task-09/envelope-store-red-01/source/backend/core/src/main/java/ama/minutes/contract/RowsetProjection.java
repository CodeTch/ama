package ama.minutes.contract;
import ama.contract.MinutesContracts.*;
import ama.canonical.CanonicalValue;
import ama.canonical.CanonicalValueCodec;
import java.util.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

final class RowsetProjection {
 static final Set<Table> ELEMENT_TABLES=EnumSet.of(Table.ELEMENT_SET,Table.MINUTES_ELEMENT,Table.ACTION_ITEM,Table.ACTION_SUGGESTION,Table.SENSITIVE_DETECTION,Table.SENSITIVE_DETECTION_SOURCE,Table.BACKGROUND_NOTE,Table.BACKGROUND_NOTE_SOURCE,Table.EVIDENCE_LINK,Table.SOURCE_LOCATOR);
 static final Set<Table> DIRECTORY_TABLES=EnumSet.of(Table.SENSITIVE_DETECTION,Table.SENSITIVE_DETECTION_SOURCE,Table.BACKGROUND_NOTE,Table.BACKGROUND_NOTE_SOURCE,Table.ACTION_SUGGESTION);
 static final Set<Table> EXCLUSION_TABLES=EnumSet.of(Table.EXCLUSION_REVISION,Table.SENSITIVE_EXCLUSION,Table.SOURCE_LOCATOR);
 static List<FullRow> elements(StoredFactsIndex index,String meeting,String setId){
  var rows=new ArrayList<FullRow>();var set=index.one(Table.ELEMENT_SET,setId,PROVENANCE_UNAVAILABLE);index.meeting(set,meeting);rows.add(set);
  var elements=index.matching(Table.MINUTES_ELEMENT,"set_id",setId);rows.addAll(elements);var ids=new HashSet<String>();var orders=new HashSet<String>();
  for(var element:elements){check(index.cancelled);ids.add(element.string("id"));positive(element.number("ordinal"));require(orders.add(element.string("category")+":"+element.number("ordinal")),SOURCE_MEMBERSHIP_MISMATCH);}
  for(var table:List.of(Table.ACTION_ITEM,Table.ACTION_SUGGESTION)){
   for(var row:index.rows(table))if(ids.contains(row.string("element_id"))){
    check(index.cancelled);require(index.one(Table.MINUTES_ELEMENT,row.string("element_id"),PROVENANCE_UNAVAILABLE).string("category").equals("ACTION"),SOURCE_MEMBERSHIP_MISMATCH);rows.add(row);
   }
  }
  for(var table:List.of(Table.SENSITIVE_DETECTION,Table.BACKGROUND_NOTE)){
   var directory=index.matching(table,"set_id",setId);rows.addAll(directory);var seen=new HashSet<String>();
   for(var row:directory){
    check(index.cancelled);positive(row.number("ordinal"));String order=(table==Table.SENSITIVE_DETECTION?row.string("category"):"")+":"+row.number("ordinal");require(seen.add(order),SOURCE_MEMBERSHIP_MISMATCH);
    rows.addAll(index.matching(table==Table.SENSITIVE_DETECTION?Table.SENSITIVE_DETECTION_SOURCE:Table.BACKGROUND_NOTE_SOURCE,table==Table.SENSITIVE_DETECTION?"detection_id":"note_id",row.string("id")));
   }
  }
  var locators=new HashSet<String>();
  for(var row:index.rows(Table.EVIDENCE_LINK))if(row.nullableString("element_id")!=null&&ids.contains(row.string("element_id"))){check(index.cancelled);rows.add(row);locators.add(row.string("locator_id"));}
  for(var row:rows)if(row.table()==Table.SENSITIVE_DETECTION_SOURCE||row.table()==Table.BACKGROUND_NOTE_SOURCE)locators.add(row.string("locator_id"));
  for(var id:locators){check(index.cancelled);var locator=index.one(Table.SOURCE_LOCATOR,id,PROVENANCE_UNAVAILABLE);index.meeting(locator,meeting);rows.add(locator);}
  return rows;
 }
 static List<FullRow> exclusions(StoredFactsIndex index,String meeting,String revisionId){
  var rows=new ArrayList<FullRow>();var revision=index.one(Table.EXCLUSION_REVISION,revisionId,PROVENANCE_UNAVAILABLE);index.meeting(revision,meeting);rows.add(revision);
  var exclusions=index.matching(Table.SENSITIVE_EXCLUSION,"revision_id",revisionId);rows.addAll(exclusions);var ids=new HashSet<String>();
  for(var row:exclusions){check(index.cancelled);ids.add(row.string("locator_id"));}
  for(var id:ids){check(index.cancelled);var row=index.one(Table.SOURCE_LOCATOR,id,PROVENANCE_UNAVAILABLE);index.meeting(row,meeting);RowValues.locator(row);rows.add(row);}return rows;
 }
 static String hash(List<FullRow> rows,Set<Table> tables,StoredFactsIndex index){
  return new CanonicalValueCodec().sha256(ContractValues.envelope("ama.rowset.v1","ROWSET",RowValues.tables(rows,tables,index.cancelled)),index.hashBudget,index.cancelled);
 }
 static CanonicalValue tableArray(List<FullRow> rows,Set<Table> tables,StoredFactsIndex index){
  var object=(CanonicalValue.ObjectValue)RowValues.tables(rows,tables,index.cancelled);return object.entries().getFirst().value();
 }
}
