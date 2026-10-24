package ama.transcript;

import ama.contract.ConfigArchiveContracts.ArchiveReadBudget;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Fixed metadata projections; SQL admits the entire bounded result before any text crosses JDBC. */
final class BoundedTranscriptMetadata {
 static final String ROOT="id owner_id revision# cancel_epoch# stage lifecycle governance_state ever_published# transcript_revision_id";
 static final String REVISION="id meeting_id revision# kind batch_id parent_id input_fingerprint processing_input_id producer_task_id";
 static final String INPUT="id meeting_id batch_id kind source_revision_id material_snapshot_id material_mode processing_config_version parser_version model_config_version prompt_version manifest_schema_version# input_fingerprint base_revision# cancel_epoch#";
 static final String BATCH="id meeting_id kind input_fingerprint base_revision#";
 static final String MEMBER="batch_id ordinal# file_id sha256";
 static final String FILE="id meeting_id sha256 size_bytes#";
 static final String TASK="id task_type pool subject_kind subject_id producer_key input_kind input_id input_fingerprint business_version# priority# cancel_epoch# max_attempts#";
 private final JdbcTemplate j;private final ArchiveReadBudget budget;private final boolean locked,skipLocked;private long rows,edges,bytes,points;
 BoundedTranscriptMetadata(JdbcTemplate j,ArchiveReadBudget budget){this(j,budget,false);}
 BoundedTranscriptMetadata(JdbcTemplate j,ArchiveReadBudget budget,boolean locked){this(j,budget,locked,false);}
 BoundedTranscriptMetadata(JdbcTemplate j,ArchiveReadBudget budget,boolean locked,boolean skipLocked){this.j=j;this.budget=budget;this.locked=locked;this.skipLocked=skipLocked;}
 void check(){ama.contract.MinutesContracts.check(()->false);}
 Map<String,Object> one(String table,String shape,String id){var found=select(table,shape,"id=?","id",2,id);if(found.size()!=1)throw new ama.contract.FileContracts.NotFound();return found.getFirst();}
 List<Map<String,Object>> select(String table,String shape,String predicate,String order,int maximum,Object... args){
  check();require(maximum>0,BUDGET_EXCEEDED);var fields=List.of(shape.split(" "));var texts=fields.stream().filter(f->!f.endsWith("#")).toList();
  long remainingRows=budget.maxFactRows()-rows,remainingBytes=budget.maxTotalBytes()-bytes,remainingPoints=budget.maxAggregateScalars()-points;
  long limit=Math.min((long)maximum+1,remainingRows==Long.MAX_VALUE?remainingRows:remainingRows+1);
  // UUID/text edges are conservatively charged per projected text field.
  long remainingEdges=(budget.maxFactEdges()-edges)/Math.max(1,texts.size());
  String b=sum(texts,"OCTET_LENGTH"),p=sum(texts,"CHAR_LENGTH"),u=greatest(texts,true),single=greatest(texts,false);
  String cols=String.join(",",fields.stream().map(f->f.replace("#","")).toList());
  String admitted="__rows<="+Math.min(maximum,remainingRows)+" AND __rows<="+remainingEdges+" AND __total_bytes<="+remainingBytes+" AND __total_points<="+remainingPoints+" AND __max_bytes<="+budget.maxStringUtf8Bytes()+" AND __max_units<="+budget.maxStringUtf16();
  String projected=String.join(",",fields.stream().map(f->{String c=f.replace("#","");return "CASE WHEN "+admitted+" THEN "+c+" ELSE NULL END AS "+c;}).toList());
  String sql="SELECT "+projected+",("+admitted+") AS __admitted,__row_bytes,__row_points FROM (SELECT a.*,COUNT(*) OVER() AS __rows,SUM(__row_bytes) OVER() AS __total_bytes,SUM(__row_points) OVER() AS __total_points,MAX(__single_bytes) OVER() AS __max_bytes,MAX(__single_units) OVER() AS __max_units FROM (SELECT "+cols+","+b+" AS __row_bytes,"+p+" AS __row_points,"+single+" AS __single_bytes,"+u+" AS __single_units FROM "+table+" WHERE "+predicate+" ORDER BY "+order+" LIMIT ?"+(locked?" FOR UPDATE"+(skipLocked?" SKIP LOCKED":""):"")+") a) admitted ORDER BY "+order;
  Object[] parameters=Arrays.copyOf(args,args.length+1);parameters[args.length]=limit;
  return j.query(sql,rs->{
   var result=new ArrayList<Map<String,Object>>();while(rs.next()){
    check();require(rs.getBoolean("__admitted"),BUDGET_EXCEEDED);rows++;edges+=texts.size();bytes+=rs.getLong("__row_bytes");points+=rs.getLong("__row_points");
    var map=new LinkedHashMap<String,Object>();for(String field:fields){String col=field.replace("#","");if(field.endsWith("#")){var n=rs.getBigDecimal(col);try{map.put(col,n==null?null:n.longValueExact());}catch(ArithmeticException e){throw fail(INPUT_INVALID);}}else map.put(col,rs.getString(col));}
    result.add(Collections.unmodifiableMap(map));
   }check();return List.copyOf(result);
  },parameters);
 }
 private static String sum(List<String> fields,String fn){return fields.isEmpty()?"0":String.join("+",fields.stream().map(f->"COALESCE("+fn+"("+f+"),0)").toList());}
 private static String greatest(List<String> fields,boolean units){return "GREATEST(0,"+String.join(",",fields.stream().map(f->units?"COALESCE(OCTET_LENGTH(CONVERT("+f+" USING utf16)) DIV 2,0)":"COALESCE(OCTET_LENGTH("+f+"),0)").toList())+")";}
}
