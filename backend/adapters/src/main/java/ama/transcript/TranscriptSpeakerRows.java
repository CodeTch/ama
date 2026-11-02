package ama.transcript;
import ama.contract.MinutesContracts.*;import java.util.*;import org.springframework.jdbc.core.JdbcTemplate;import static ama.contract.MinutesContracts.*;import static ama.contract.MinutesContracts.FailureCode.*;
final class TranscriptSpeakerRows {
 static void capture(SavedTranscriptRows read,List<FullRow> rows,Set<String> revisions,int maximum){
  for(String revision:revisions)rows.addAll(read.rows(Table.TRANSCRIPT_SPEAKER_CHANGE,"revision_id=?","segment_id",maximum,true,revision));
  var ids=new TreeSet<String>();for(var row:rows){if(row.table()==Table.TRANSCRIPT_SEGMENT&&row.nullableString("speaker_id")!=null)ids.add(row.string("speaker_id"));if(row.table()==Table.TRANSCRIPT_SPEAKER_CHANGE)for(String key:List.of("before_speaker_id","after_speaker_id"))if(row.nullableString(key)!=null)ids.add(row.string(key));}
  for(String id:ids){var found=read.rows(Table.TRANSCRIPT_SPEAKER_SNAPSHOT,"id=?","id",1,true,id);require(found.size()==1,PROVENANCE_UNAVAILABLE);rows.addAll(found);}
 }
 static void mirrors(JdbcTemplate j,List<FullRow> rows){
  for(var row:rows.stream().filter(r->r.table()==Table.TRANSCRIPT_SPEAKER_SNAPSHOT).sorted(Comparator.comparing(r->r.string("id"))).toList()){
   var match=j.queryForList("SELECT id FROM speaker WHERE id=? AND meeting_id=? AND BINARY display_name=BINARY ? AND enterprise_id <=> ? AND identity_source='HUMAN' AND merged_into_id IS NULL AND revision=1 FOR UPDATE",String.class,row.string("id"),row.string("meeting_id"),row.string("label"),row.nullableString("enterprise_id"));require(match.equals(List.of(row.string("id"))),PROVENANCE_UNAVAILABLE);
  }
 }
}
