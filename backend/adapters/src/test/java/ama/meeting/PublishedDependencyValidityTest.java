package ama.meeting;
import java.time.*;import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Policy fixtures only; actual signed observations are tested separately. */
class PublishedDependencyValidityTest {
 static class Rows extends JdbcTemplate {final Map<String,List<Map<String,Object>>> values=new HashMap<>();@Override public List<Map<String,Object>> queryForList(String sql,Object...args){assertEquals(1,args.length);return Objects.requireNonNull(values.get(args[0]));}}
 Map<String,Object> row(String meeting,Instant until){var row=new HashMap<String,Object>();row.put("source_meeting",meeting);row.put("lifecycle","ACTIVE");row.put("governance_state","ACTIVE");row.put("mapping_state","ACTIVE");row.put("authority_state","ACTIVE");row.put("document_id","doc-"+"a".repeat(32));row.put("external_id","doc-"+"a".repeat(32));row.put("verified_until",LocalDateTime.ofInstant(until,ZoneOffset.UTC));return row;}
 @Test void usesEarliestSignedDeadlineAcrossTheTransitiveImportedDocumentGraph(){
  var j=new Rows();var first=Instant.now().plusSeconds(4);var second=Instant.now().plusSeconds(2);
  j.values.put("consumer",List.of(row("source",first)));j.values.put("source",List.of(row("ancestor",second)));j.values.put("ancestor",List.of());
  assertEquals(Optional.of(second),new PublishedDependencyValidity(j).deadline("consumer"));
 }
 @Test void unknownRevokedStaleAndSubstitutedSourcesDenyAllCachedContent(){
  for(String field:List.of("source_meeting","lifecycle","governance_state","mapping_state","authority_state","document_id","verified_until")){
   var j=new Rows();var row=row("source",Instant.now().plusSeconds(4));row.put(field,field.equals("verified_until")?LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1):null);j.values.put("consumer",List.of(row));assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->new PublishedDependencyValidity(j).deadline("consumer"),field);
  }
 }
 @Test void metadataDiscoveryIsBoundedAndLocalOnlyMaterialsNeedNoKnowledgeGrant(){
  var j=new Rows();j.values.put("consumer",List.of());assertTrue(new PublishedDependencyValidity(j).deadline("consumer").isEmpty());
  j.values.put("consumer",Collections.nCopies(65,row("source",Instant.now().plusSeconds(4))));assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->new PublishedDependencyValidity(j).deadline("consumer"));
 }
}
