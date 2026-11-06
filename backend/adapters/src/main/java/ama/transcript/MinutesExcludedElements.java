package ama.transcript;

import ama.contract.MinutesContracts.Cell;
import ama.contract.MinutesContracts.FullRow;
import ama.contract.MinutesContracts.Table;
import java.util.*;
import static ama.contract.MinutesContracts.cell;

/** Match immutable physical locators even when saving a revision allocated new row IDs. */
final class MinutesExcludedElements {
 private MinutesExcludedElements() {}

 static Set<String> matching(List<FullRow> rows,String exclusion) {
  if(exclusion==null)return Set.of();
  var locations=new HashMap<String,List<Cell>>();
  var selected=new HashSet<String>();
  for(var row:rows){
   if(row.table()==Table.SOURCE_LOCATOR)locations.put(row.string("id"),row.cells().subList(2,row.cells().size()));
   if(row.table()==Table.SENSITIVE_EXCLUSION&&exclusion.equals(row.string("revision_id")))selected.add(row.string("locator_id"));
  }
  if(selected.isEmpty())return Set.of();
  var physical=new HashSet<List<Cell>>();
  for(String id:selected)physical.add(Objects.requireNonNull(locations.get(id)));
  var elements=new HashSet<String>();
  for(var row:rows)if(row.table()==Table.EVIDENCE_LINK&&row.nullableString("element_id")!=null&&physical.contains(locations.get(row.string("locator_id"))))elements.add(row.string("element_id"));
  return elements;
 }

 static List<FullRow> retain(List<FullRow> rows,String base,String candidate,String exclusion,String region) {
  if(base==null)return rows;
  var excluded=matching(rows,exclusion);
  if(excluded.isEmpty())return rows;
  var ordinals=new HashMap<String,Long>();
  for(var row:rows)if(row.table()==Table.MINUTES_ELEMENT&&candidate.equals(row.string("set_id")))
   ordinals.merge(row.string("category"),row.number("ordinal"),Math::max);
  var result=new ArrayList<FullRow>(rows.size());
  for(var row:rows){
   if(row.table()==Table.MINUTES_ELEMENT&&base.equals(row.string("set_id"))&&excluded.contains(row.string("id"))&&(region==null||region.equals(row.string("category")))){
    var cells=new ArrayList<>(row.cells());
    cells.set(Table.MINUTES_ELEMENT.index("set_id"),cell(candidate));
    cells.set(Table.MINUTES_ELEMENT.index("ordinal"),cell(ordinals.merge(row.string("category"),1L,Long::sum)));
    row=new FullRow(row.table(),cells);
   }
   result.add(row);
  }
  return result;
 }
}
