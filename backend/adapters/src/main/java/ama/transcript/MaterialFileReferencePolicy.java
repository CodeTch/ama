package ama.transcript;

import ama.storage.*;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.util.*;

/** Immutable material versions retain source and parsed bytes, including removed historical versions. */
public final class MaterialFileReferencePolicy implements FileReferencePolicy {
 public static final String CONTENT="MATERIAL_CONTENT",TEXT="MATERIAL_TEXT",PURPOSE="MATERIAL_SOURCE";
 private final JdbcTemplate jdbc;
 public MaterialFileReferencePolicy(DataSource source){jdbc=new JdbcTemplate(source);jdbc.setQueryTimeout(2);}
 private record Held(String meeting,FileRegistry.Reference reference,String file,String hash){}
 public Object prelock(FileRegistry.LockedMeeting gate,String meeting,FileRegistry.Reference ref,String file){
  if(!Set.of(CONTENT,TEXT).contains(ref.kind())||!PURPOSE.equals(ref.purpose()))throw new ama.contract.FileContracts.NotFound();
  var rows=CONTENT.equals(ref.kind())?jdbc.queryForList("SELECT r.content_file_id file_id,r.content_hash sha256 FROM material_revision r JOIN material_reference m ON m.id=r.reference_id WHERE r.id=? AND m.meeting_id=? FOR UPDATE",ref.ownerId(),meeting):jdbc.queryForList("SELECT d.text_file_id file_id,f.sha256 FROM parsed_document d JOIN material_revision r ON r.id=d.material_revision_id JOIN material_reference m ON m.id=r.reference_id JOIN file_object f ON f.id=d.text_file_id WHERE d.id=? AND m.meeting_id=? AND d.state='READY' FOR UPDATE",ref.ownerId(),meeting);
  if(rows.size()!=1||!file.equals(rows.getFirst().get("file_id")))throw new ama.contract.FileContracts.NotFound();
  return new Held(meeting,ref,file,(String)rows.getFirst().get("sha256"));
 }
 public void validate(Object proof,String meeting,FileRegistry.Reference ref,FileRegistry.FileRecord file,String originalPurpose){
  if(!(proof instanceof Held held)||!held.meeting.equals(meeting)||!held.reference.equals(ref)||!held.file.equals(file.fileId())||!meeting.equals(file.meetingId())||!held.hash.equals(file.sha256())||!"AVAILABLE".equals(file.state())||!PURPOSE.equals(originalPurpose))throw new ama.contract.FileContracts.NotFound();
 }
}
