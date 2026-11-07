package ama.transcript;
import java.util.*;

/** Counts and history from the effective final's immutable versions; never transfers source content. */
final class PublicationDocumentDetails {
 static Map<String,Object> capture(JdbcPublicationService service,PublicationDocumentRead.Snapshot snapshot,TranscriptAcquisitionBudget.Ledger budget){
  budget.check();budget.admitCapture(65536,32768,32768,3);var j=service.j;var f=snapshot.confirmation();var result=new LinkedHashMap<String,Object>();
  result.put("meetingId",snapshot.root().get("id"));result.put("publishedAt",snapshot.publication().get("published_at"));result.put("publishedBy",snapshot.submission().get("requester_id"));
  budget.row(4);var information=j.queryForMap("SELECT category_id,OCTET_LENGTH(summary) summary_bytes,CHAR_LENGTH(summary) summary_scalars,OCTET_LENGTH(CONVERT(summary USING utf16))/2 summary_utf16 FROM meeting_info_revision WHERE id=? AND meeting_id=?",f.get("info_revision_id"),snapshot.root().get("id"));String category=(String)information.get("category_id");if(category!=null&&service.categories!=null){var labels=new ama.meeting.WorkspaceReadBudget(ama.meeting.WorkspaceReadBudget.DEFAULT,()->{budget.check();return false;});try{var catalog=service.categories.catalog(labels);for(var metadata:catalog.metadata(labels))if(metadata.id().equals(category)){String label=catalog.read(metadata,labels).label();budget.admitCapture(6L*label.length(),6L*label.length(),6L*label.length(),3);result.put("categoryName",label);break;}}catch(ama.contract.MeetingContracts.CategoryUnavailable unavailable){/* Preserve the immutable category ID when the current label catalog is unavailable. */}}if(information.get("summary_bytes") instanceof Number bytes){
   long points=((Number)information.get("summary_scalars")).longValue(),units=((Number)information.get("summary_utf16")).longValue();
   budget.row(1);budget.text(bytes.longValue(),points,units,3,false);
   String summary=j.queryForObject("SELECT summary FROM meeting_info_revision WHERE id=? AND meeting_id=? AND OCTET_LENGTH(summary)=? AND CHAR_LENGTH(summary)=? AND OCTET_LENGTH(CONVERT(summary USING utf16))/2=?",String.class,f.get("info_revision_id"),snapshot.root().get("id"),bytes.longValue(),points,units);
   result.put("summary",Objects.requireNonNull(summary));
  }
  result.put("path",snapshot.publication().get("library_id")+" / "+snapshot.publication().get("folder_id"));
  var counts=j.queryForMap("SELECT COUNT(*) evidenceCount,COALESCE(SUM(e.support_state='SUPPORTED'),0) supportedCount FROM evidence_link e LEFT JOIN minutes_element m ON m.id=e.element_id WHERE e.minutes_revision_id=? OR m.set_id=?",f.get("minutes_revision_id"),f.get("element_set_id"));result.putAll(counts);
  result.put("backgroundCount",j.queryForObject("SELECT COUNT(*) FROM background_note WHERE set_id=?",Long.class,f.get("element_set_id")));
  result.put("materialCount",j.queryForObject("SELECT COUNT(DISTINCT COALESCE(s.material_revision_id,s.file_id)) FROM source_locator s WHERE s.meeting_id=? AND (EXISTS(SELECT 1 FROM evidence_link e LEFT JOIN minutes_element m ON m.id=e.element_id WHERE e.locator_id=s.id AND (e.minutes_revision_id=? OR m.set_id=?)) OR EXISTS(SELECT 1 FROM background_note_source b JOIN background_note n ON n.id=b.note_id WHERE b.locator_id=s.id AND n.set_id=?))",Long.class,snapshot.root().get("id"),f.get("minutes_revision_id"),f.get("element_set_id"),f.get("element_set_id")));
  var attempts=new ArrayList<Map<String,Object>>();for(var row:j.queryForList("SELECT outcome,kind,created_at,finished_at FROM publication_attempt WHERE publication_id=? ORDER BY created_at DESC,id DESC LIMIT 20",snapshot.publication().get("id"))){budget.row(4);var entry=new LinkedHashMap<String,Object>();entry.put("outcome",row.get("outcome"));entry.put("detail","RECONCILE".equals(row.get("kind"))?"核对原执行结果":"提交正式发布");entry.put("at",JdbcPublicationQueries.normalizeMetadata(row.get("finished_at")!=null?row.get("finished_at"):row.get("created_at")));attempts.add(Collections.unmodifiableMap(entry));}result.put("attempts",List.copyOf(attempts));budget.check();return Collections.unmodifiableMap(result);
 }
}
