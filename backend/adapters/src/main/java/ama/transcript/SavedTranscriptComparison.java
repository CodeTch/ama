package ama.transcript;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
/** Final full-cell equality for already acquired immutable rows; metadata union locks precede this call. */
final class SavedTranscriptComparison {
 private SavedTranscriptComparison(){}
 static Object value(FullRow row,String name){var cell=row.value(name);return cell instanceof NullCell?null:cell instanceof UIntCell number?number.value():((TextCell)cell).value();}
 static List<FullRow> rows(JdbcTemplate jdbc,TranscriptAcquisitionBudget.Ledger budget,Table table,List<Map<String,Object>> declared,List<FullRow> original){
  var keys=new HashSet<List<Object>>();for(var row:declared)require(keys.add(table.primaryKey().stream().map(row::get).toList()),PROVENANCE_UNAVAILABLE);
  var selected=original.stream().filter(row->row.table()==table&&keys.contains(table.primaryKey().stream().map(k->value(row,k)).toList())).toList();require(selected.size()==declared.size(),PROVENANCE_UNAVAILABLE);
  for(int offset=0;offset<selected.size();offset+=8){
   budget.check();var page=selected.subList(offset,Math.min(selected.size(),offset+8));var matches=new ArrayList<String>();var args=new ArrayList<Object>();
   for(var row:page){var cells=new ArrayList<String>();for(String key:table.primaryKey()){cells.add(key+"=?");args.add(value(row,key));}for(var column:table.columns()){
    String name=column.name();Object value=value(row,name);
    if(value==null){cells.add(name+" IS NULL");continue;}
    if(value instanceof String text)budget.copies(ama.canonical.CanonicalValueCodec.utf8Length(text,()->false),text.length(),6);
    if(column.kind()==CellKind.TIMESTAMP){cells.add(name+" <=> CAST(? AS DATETIME(6))");args.add(value.toString().replace('T',' ').replaceFirst("Z$",""));}
    else if(column.kind()==CellKind.UINT){cells.add(name+" <=> ?");args.add(value);}
    else {cells.add("CAST("+name+" AS BINARY) <=> CAST(? AS BINARY)");args.add(value);}
   }matches.add("("+String.join(" AND ",cells)+")");}
   budget.admitCapture(64,64,64,2);budget.row(1);args.add(page.size()+1);
   long matched=jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT 1 FROM "+table.sqlName()+" WHERE "+String.join(" OR ",matches)+" ORDER BY "+String.join(",",table.primaryKey())+" LIMIT ? FOR UPDATE) exact_saved_content",Long.class,args.toArray());budget.capture(32,32,32,2);require(matched==page.size(),PROVENANCE_UNAVAILABLE);
  }
  return selected;
 }
 static List<Map<String,Object>> auxiliary(JdbcTemplate jdbc,TranscriptAcquisitionBudget.Ledger budget,SavedTranscriptRows.Shape shape,List<Map<String,Object>> declared,List<Map<String,Object>> original){
  var keys=new HashSet<List<Object>>();for(var row:declared)require(keys.add(shape.keys().stream().map(row::get).toList()),PROVENANCE_UNAVAILABLE);
  var selected=original.stream().filter(row->keys.contains(shape.keys().stream().map(row::get).toList())).toList();require(selected.size()==declared.size(),PROVENANCE_UNAVAILABLE);
  for(int offset=0;offset<selected.size();offset+=8){
   budget.check();var page=selected.subList(offset,Math.min(selected.size(),offset+8));var matches=new ArrayList<String>();var args=new ArrayList<Object>();
   for(var row:page){require(row.keySet().equals(new HashSet<>(shape.columns())),PROVENANCE_UNAVAILABLE);var cells=new ArrayList<String>();for(String key:shape.keys()){cells.add(key+"=?");args.add(row.get(key));}
    for(String column:shape.columns()){Object value=row.get(column);if(value==null){cells.add(column+" IS NULL");continue;}
     if(value instanceof String text)budget.copies(ama.canonical.CanonicalValueCodec.utf8Length(text,()->false),text.length(),6);
     if(shape.timestamps().contains(column)){cells.add(column+" <=> CAST(? AS DATETIME(6))");args.add(value.toString().replace('T',' ').replaceFirst("Z$",""));}
     else if(shape.numbers().contains(column)){cells.add(column+" <=> ?");args.add(value);}
     else {cells.add("CAST("+column+" AS BINARY) <=> CAST(? AS BINARY)");args.add(value);}
    }matches.add("("+String.join(" AND ",cells)+")");
   }
   budget.admitCapture(64,64,64,2);budget.row(1);args.add(page.size()+1);
   long matched=jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT 1 FROM "+shape.table()+" WHERE "+String.join(" OR ",matches)+" ORDER BY "+String.join(",",shape.keys())+" LIMIT ? FOR UPDATE) exact_saved_auxiliary",Long.class,args.toArray());budget.capture(32,32,32,2);require(matched==page.size(),PROVENANCE_UNAVAILABLE);
  }
  return selected;
 }
}
