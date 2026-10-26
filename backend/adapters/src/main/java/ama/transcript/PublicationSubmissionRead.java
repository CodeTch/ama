package ama.transcript;
import java.util.*;import org.springframework.jdbc.core.JdbcTemplate;
import static ama.contract.MinutesContracts.*;import static ama.contract.MinutesContracts.FailureCode.*;
/** Worker submission transfer after the complete publication root and rows are held. */
final class PublicationSubmissionRead {
 static String read(JdbcTemplate j,TranscriptAcquisitionBudget.Ledger budget,long max,String task,Map<String,Object> row){
  long bytes=((Number)row.get("request_manifest_bytes")).longValue(),scalars=((Number)row.get("request_manifest_scalars")).longValue(),units=((Number)row.get("request_manifest_utf16")).longValue();require(bytes>0&&scalars>0&&units>0&&bytes<=Math.min(65536,max),BUDGET_EXCEEDED);budget.text(bytes,scalars,units,4,false);String original=j.queryForObject("SELECT request_manifest FROM publication_submission WHERE task_id=? AND OCTET_LENGTH(request_manifest)=? AND CHAR_LENGTH(request_manifest)=? AND OCTET_LENGTH(CONVERT(request_manifest USING utf16))/2=?",String.class,task,bytes,scalars,units);require(original!=null,PROVENANCE_UNAVAILABLE);return original;
 }
}
