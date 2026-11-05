package ama.transcript;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
/** Compare the entire locked metadata union without copying it across JDBC again. */
final class PublicationMetadataComparison {
 private PublicationMetadataComparison(){}
 static void requireSame(JdbcTemplate jdbc,MinutesPendingDiscovery.Query query,List<Map<String,Object>> expected,TranscriptAcquisitionBudget.Ledger budget){
  require(matches(jdbc,query,expected,budget),PROVENANCE_UNAVAILABLE);
 }
 static boolean matches(JdbcTemplate jdbc,MinutesPendingDiscovery.Query query,List<Map<String,Object>> expected,TranscriptAcquisitionBudget.Ledger budget){
  require(expected.size()<=Math.min(256,budget.taskLimit()),BUDGET_EXCEEDED);
  String projection="SELECT "+String.join(",",query.columns())+" FROM "+query.table()+" WHERE ("+query.where()+") ORDER BY "+String.join(",",query.keys())+" LIMIT ? FOR UPDATE";
  var args=new ArrayList<>(query.args());args.add(expected.size()+1);
  budget.admitCapture(64,64,64,2);budget.row(1);
  long count=jdbc.queryForObject("SELECT COUNT(*) FROM ("+projection+") locked_metadata",Long.class,args.toArray());budget.capture(32,32,32,2);if(count!=expected.size())return false;
  var columns=query.columns().stream().map(c->c.contains(" AS ")?c.substring(c.lastIndexOf(" AS ")+4):c).toList();
  var keys=new HashSet<List<Object>>();for(var row:expected){require(row.keySet().equals(new HashSet<>(columns)),PROVENANCE_UNAVAILABLE);require(keys.add(query.keys().stream().map(row::get).toList()),PROVENANCE_UNAVAILABLE);}
  for(int offset=0;offset<expected.size();offset+=8){
   budget.check();var page=expected.subList(offset,Math.min(expected.size(),offset+8));var values=new ArrayList<Object>(args);var matches=new ArrayList<String>();
   for(var row:page){var cells=new ArrayList<String>();for(String column:columns){
    Object value=row.get(column);
    if(value==null)cells.add(column+" IS NULL");
    else if(MinutesGraphDiscovery.timestampColumn(query.table(),column)||Set.of("created_at","verified_at","finished_at","local_decided_at").contains(column)){cells.add(column+" <=> CAST(? AS DATETIME(6))");values.add(value.toString().replace('T',' '));}
    else if(value instanceof Number){cells.add(column+" <=> ?");values.add(value);}
    else {cells.add("CAST("+column+" AS BINARY) <=> CAST(? AS BINARY)");values.add(value.toString());}
   }matches.add("("+String.join(" AND ",cells)+")");}
   budget.admitCapture(64,64,64,2);budget.row(1);long matched=jdbc.queryForObject("SELECT COUNT(*) FROM ("+projection+") locked_metadata WHERE "+String.join(" OR ",matches),Long.class,values.toArray());budget.capture(32,32,32,2);if(matched!=page.size())return false;
  }
  return true;
 }
}
