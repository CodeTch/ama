package ama.meeting;

import ama.access.MeetingAccess.AccessDeniedException;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Deny-only metadata traversal over retained knowledge material copies, including history.
 * Does not lock another meeting after the caller's root or transfer any source body. The minimum
 * original signature expiry also caps a long stream; a stale repeatable-read snapshot cannot
 * keep a copy readable beyond that deadline. Unknown external provenance is unavailable. */
final class PublishedDependencyValidity {
 private final JdbcTemplate j;
 PublishedDependencyValidity(JdbcTemplate j){this.j=j;}
 private record Node(String meeting,int depth){}
 Optional<Instant> deadline(String meeting){
  var todo=new ArrayDeque<Node>();todo.add(new Node(meeting,0));var seen=new HashSet<String>();Instant earliest=null;int edges=0;
  while(!todo.isEmpty()){
   var next=todo.removeFirst();if(!seen.add(next.meeting()))continue;if(seen.size()>256||next.depth()>16)throw new AccessDeniedException();
   var rows=j.queryForList("SELECT r.external_id,d.publication_id,p.meeting_id source_meeting,m.lifecycle,m.governance_state,d.authority_state mapping_state,a.state authority_state,a.document_id,a.verified_until FROM material_reference r LEFT JOIN external_document_mapping d ON d.provider_id=r.provider_id AND d.external_document_id=r.external_id LEFT JOIN publication p ON p.id=d.publication_id AND p.state='SUCCEEDED' LEFT JOIN meeting m ON m.id=p.meeting_id AND m.final_confirmation_id=p.confirmation_id LEFT JOIN publication_governance_authority a ON a.publication_id=p.id WHERE r.meeting_id=? AND r.kind='KNOWLEDGE' ORDER BY r.id LIMIT 65",next.meeting());
   if(rows.size()>64||(edges+=rows.size())>256)throw new AccessDeniedException();
   for(var row:rows){
    if(!(row.get("source_meeting") instanceof String origin)||!"ACTIVE".equals(row.get("lifecycle"))||!"ACTIVE".equals(row.get("governance_state"))||!"ACTIVE".equals(row.get("mapping_state"))||!"ACTIVE".equals(row.get("authority_state"))||row.get("document_id")==null||!row.get("document_id").equals(row.get("external_id")))throw new AccessDeniedException();
    Object value=row.get("verified_until");Instant until=value instanceof LocalDateTime time?time.toInstant(ZoneOffset.UTC):value instanceof java.sql.Timestamp timestamp?timestamp.toLocalDateTime().toInstant(ZoneOffset.UTC):null;
    if(until==null||!until.isAfter(Instant.now()))throw new AccessDeniedException();if(earliest==null||until.isBefore(earliest))earliest=until;todo.addLast(new Node(origin,next.depth()+1));
   }
  }
  return Optional.ofNullable(earliest);
 }
}
