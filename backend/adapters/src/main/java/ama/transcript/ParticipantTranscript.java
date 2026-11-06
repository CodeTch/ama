package ama.transcript;

import org.springframework.jdbc.core.JdbcTemplate;

/** The participant's visible immutable revision, shared by navigation, body and source downloads. */
public record ParticipantTranscript(String revision,String confirmation) {
 public static ParticipantTranscript select(JdbcTemplate jdbc,String meeting){
  var confirmed=jdbc.query("""
   SELECT r.id,c.id FROM transcript_confirmation c JOIN transcript_revision r ON r.id=c.revision_id AND r.meeting_id=c.meeting_id
   WHERE c.meeting_id=? ORDER BY r.revision DESC LIMIT 1
   """,(row,n)->new ParticipantTranscript(row.getString(1),row.getString(2)),meeting);
  if(!confirmed.isEmpty())return confirmed.getFirst();
  var enhanced=jdbc.query("""
   SELECT r.id FROM material_enhancement_candidate e JOIN transcript_revision raw ON raw.id=e.source_revision_id AND raw.meeting_id=e.meeting_id AND raw.kind='RAW'
   JOIN transcript_revision r ON r.id=e.adopted_revision_id AND r.meeting_id=e.meeting_id AND r.parent_id=raw.id
   WHERE e.meeting_id=? ORDER BY r.revision DESC LIMIT 1
   """,(row,n)->new ParticipantTranscript(row.getString(1),null),meeting);
  return enhanced.isEmpty()?new ParticipantTranscript(null,null):enhanced.getFirst();
 }
}
