package ama.meeting;

import org.springframework.jdbc.core.JdbcTemplate;

/** Owner-visible fallback only. Never persists a suggested title as confirmed meeting information. */
public final class MeetingDisplayTitle {
 private MeetingDisplayTitle(){}
 static final String OWNER_FALLBACK="""
      COALESCE(
       (SELECT CONCAT('在线会议 ',DATE_FORMAT(created_at + INTERVAL 8 HOUR,'%Y-%m-%d %H:%i')) FROM recording_session
         WHERE meeting_id=m.id ORDER BY created_at,session_id LIMIT 1),
       (SELECT LEFT(u.display_name,256) FROM transcription_input t JOIN input_batch_file f ON f.batch_id=t.batch_id
         JOIN upload_session u ON u.completed_file_id=f.file_id AND u.meeting_id=t.meeting_id
         WHERE t.meeting_id=m.id AND t.kind='IMPORT' ORDER BY t.base_revision DESC,f.ordinal LIMIT 1),
       CASE WHEN EXISTS(SELECT 1 FROM meeting_import_draft WHERE meeting_id=m.id)
        THEN (SELECT LEFT(u.display_name,256) FROM meeting_import_draft_file f JOIN upload_session u
              ON u.completed_file_id=f.file_id AND u.meeting_id=f.meeting_id
              WHERE f.meeting_id=m.id AND u.state='COMPLETED' ORDER BY f.ordinal LIMIT 1)
        ELSE (SELECT LEFT(display_name,256) FROM upload_session WHERE meeting_id=m.id AND purpose='TRANSCRIPT_INPUT'
              AND state='COMPLETED' ORDER BY created_at,id LIMIT 1) END)
      """;
 public static String temporary(JdbcTemplate jdbc,String meeting){
  return jdbc.queryForObject("SELECT "+OWNER_FALLBACK+" FROM meeting m WHERE m.id=?",String.class,meeting);
 }
}
