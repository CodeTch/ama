package ama.meeting;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Bounded metadata policy only. Real signed cache/ACL integration is tested separately. */
class MaterialAuthorityDependenciesTest {
 static class Rows extends JdbcTemplate {
  final Map<String,List<Map<String,Object>>> values=new HashMap<>();final Map<String,List<Map<String,Object>>> mappings=new HashMap<>();
  @Override public List<Map<String,Object>> queryForList(String sql,Object...args){if(args.length==2)return Objects.requireNonNull(mappings.get(args[1]));assertEquals(1,args.length);assertFalse(sql.contains("r.state='ACTIVE'"),"Removed material history still protects derived copies");return Objects.requireNonNull(values.get(args[0]));}
 }
 Map<String,Object> row(String document,String version,String origin){var r=new HashMap<String,Object>();r.put("provider_id","provider");r.put("library_id","library");r.put("external_id",document);r.put("external_version",version);r.put("publication_id",origin==null?null:"publication");r.put("source_meeting",origin);r.put("lifecycle","ACTIVE");r.put("governance_state","ACTIVE");r.put("mapping_state","ACTIVE");return r;}
 @Test void exactVersionsIncludeRetainedHistoryAndTransitiveSourcesAndAreCanonical(){
  var j=new Rows();j.values.put("consumer",List.of(row("doc-a","a".repeat(64),"source"),row("doc-a","b".repeat(64),"source")));j.values.put("source",List.of(row("doc-b","c".repeat(64),null)));
  var first=MaterialAuthorityDependencies.capture(j,"consumer");assertEquals(3,first.items().size());assertEquals(List.of("source"),first.origins());
  j.values.put("consumer",List.of(row("doc-a","b".repeat(64),"source"),row("doc-a","a".repeat(64),"source"),row("doc-a","a".repeat(64),"source")));
  assertEquals(first,MaterialAuthorityDependencies.capture(j,"consumer"));
  j.values.put("source",List.of(row("doc-b","d".repeat(64),null)));assertNotEquals(first.fingerprint(),MaterialAuthorityDependencies.capture(j,"consumer").fingerprint());
 }
 @Test void independentExternalDocumentsNeedExactLibraryAndVersionAndLocalPendingCannotBeBypassed(){
  var j=new Rows();j.values.put("consumer",List.of(row("outside","a".repeat(64),null)));assertEquals(1,MaterialAuthorityDependencies.capture(j,"consumer").items().size());
  for(String field:List.of("library_id","provider_id","external_id","external_version")){var bad=row("outside","a".repeat(64),null);bad.put(field,null);j.values.put("consumer",List.of(bad));assertThrows(ama.access.MeetingAccess.AuthorityUnavailableException.class,()->MaterialAuthorityDependencies.capture(j,"consumer"));}
  var pending=row("doc-a","a".repeat(64),"source");pending.put("governance_state","WITHDRAW_PENDING");j.values.put("consumer",List.of(pending));assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->MaterialAuthorityDependencies.capture(j,"consumer"));
 }
 @Test void localOnlyNeedsNoExternalProofAndBoundedDistinctHistoryNeverGetsTruncated(){
  var j=new Rows();j.values.put("consumer",List.of());assertTrue(MaterialAuthorityDependencies.capture(j,"consumer").items().isEmpty());
  var rows=new ArrayList<Map<String,Object>>();for(int n=0;n<65;n++)rows.add(row("doc-"+n,"a".repeat(64),null));j.values.put("consumer",rows);assertThrows(ama.access.MeetingAccess.AuthorityUnavailableException.class,()->MaterialAuthorityDependencies.capture(j,"consumer"));
 }
 @Test void aNewReferenceIncludesItsPublishedSourcesBeforeCommitAndCannotBypassPendingGovernance(){
  var j=new Rows();j.values.put("consumer",List.of());j.values.put("published",List.of(row("ancestor","b".repeat(64),null)));var mapped=row("new","a".repeat(64),"published");j.mappings.put("new",List.of(mapped));
  var added=new MaterialAuthorityDependencies.Item("provider","library","new","a".repeat(64));var prepared=MaterialAuthorityDependencies.captureAdditional(j,"consumer",List.of(added));assertEquals(2,prepared.items().size());
  j.values.put("consumer",List.of(mapped));assertEquals(prepared,MaterialAuthorityDependencies.capture(j,"consumer"));
  j.values.put("consumer",List.of());mapped.put("governance_state","WITHDRAW_PENDING");assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->MaterialAuthorityDependencies.captureAdditional(j,"consumer",List.of(added)));
 }

}
