package ama.transcript;
import ama.contract.ExternalContracts.OperationToken;
import java.time.*;
import java.util.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Read-only lookup preparation may retain the earlier expiry of its own renewed lease.
 * No state, epoch, owner, execution, source or membership change is accepted. */
final class MinutesLookupRenewal {
 static List<Map<String,Object>> conservative(OperationToken token,List<Map<String,Object>> before,List<Map<String,Object>> after){
  require(token!=null&&before.size()==after.size(),PROVENANCE_UNAVAILABLE);boolean found=false;
  for(int n=0;n<before.size();n++){
   var old=before.get(n);var fresh=after.get(n);
   if(!token.operationId().equals(old.get("id"))){require(old.equals(fresh),PROVENANCE_UNAVAILABLE);continue;}
   require(!found&&token.owner().equals(old.get("reconcile_owner"))&&Long.valueOf(token.reconcileEpoch()).equals(old.get("reconcile_epoch"))&&Long.valueOf(token.executionNo()).equals(old.get("current_execution_no"))&&token.subject().kind().equals(old.get("subject_kind"))&&token.subject().id().equals(old.get("subject_id"))&&Set.of("MARKED","UNKNOWN").contains(old.get("state")),PROVENANCE_UNAVAILABLE);found=true;
   var a=new LinkedHashMap<>(old);var b=new LinkedHashMap<>(fresh);
   var first=time(a.remove("reconcile_expires_at"));var next=time(b.remove("reconcile_expires_at"));
   var firstCheck=time(a.remove("next_check_at"));var nextCheck=time(b.remove("next_check_at"));
   require(a.equals(b)&&first!=null&&next!=null&&first.equals(firstCheck)&&next.equals(nextCheck)&&!next.isBefore(first),PROVENANCE_UNAVAILABLE);
  }
  require(found,PROVENANCE_UNAVAILABLE);return before;
 }
 static List<Map<String,Object>> captured(OperationToken token,List<Map<String,Object>> declared,List<Map<String,Object>> captured){
  var format=java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'");var normalized=new ArrayList<Map<String,Object>>();
  for(var row:declared){var copy=new LinkedHashMap<>(row);for(String field:MinutesSourceRows.SHAPES.get("external_operation").timestamps())if(copy.get(field)!=null)copy.put(field,format.format(time(copy.get(field))));normalized.add(Collections.unmodifiableMap(copy));}
  // Full capture additionally contains external_result_id. It is validated as content
  // separately and must not be projected away or replaced by a metadata-only row.
  var metadata=new ArrayList<Map<String,Object>>();for(var row:captured){var copy=new LinkedHashMap<>(row);copy.remove("external_result_id");metadata.add(Collections.unmodifiableMap(copy));}
  conservative(token,List.copyOf(normalized),List.copyOf(metadata));
  var result=new ArrayList<Map<String,Object>>();for(int n=0;n<captured.size();n++){var copy=new LinkedHashMap<>(captured.get(n));for(String field:List.of("reconcile_expires_at","next_check_at"))copy.put(field,normalized.get(n).get(field));result.add(Collections.unmodifiableMap(copy));}return List.copyOf(result);
 }
 private static LocalDateTime time(Object value){try{return value instanceof LocalDateTime t?t:value instanceof String s?(s.endsWith("Z")?Instant.parse(s).atOffset(ZoneOffset.UTC).toLocalDateTime():LocalDateTime.parse(s)):null;}catch(java.time.DateTimeException invalid){throw fail(PROVENANCE_UNAVAILABLE);}}
}
