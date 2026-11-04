package ama.transcript;
import ama.access.MeetingAccess;import ama.contract.IdentityContracts.Actor;import ama.contract.MinutesContracts.*;import java.util.*;import org.springframework.jdbc.core.JdbcTemplate;
import static ama.contract.MinutesContracts.*;import static ama.contract.MinutesContracts.FailureCode.*;
/** Owner-only proposals indexed by a genuine asynchronous candidate. No automatic information write. */
public final class JdbcMinutesInfoSuggestions {
 private final JdbcTranscriptAuthority authority;private final JdbcMinutesFactsReader reader;private final JdbcTemplate j;
 JdbcMinutesInfoSuggestions(JdbcTranscriptAuthority authority,JdbcMinutesFactsReader reader){this.authority=authority;this.reader=reader;j=new JdbcTemplate(authority.dataSource());j.setQueryTimeout(3);}
 public static final class Prepared {
  private final String actor,meeting,id,candidate,field,value,confirmation;private final long revision;
  private Prepared(Actor actor,String meeting,String id,String candidate,String field,String value,String confirmation,long revision){this.actor=actor.enterpriseId();this.meeting=meeting;this.id=id;this.candidate=candidate;this.field=field;this.value=value;this.confirmation=confirmation;this.revision=revision;}
  public void requireFor(Actor actor,String meeting,String suggestion,long revision,String confirmation){if(!this.actor.equals(actor.enterpriseId())||!this.meeting.equals(meeting)||!id.equals(suggestion)||this.revision!=revision||!this.confirmation.equals(confirmation))throw new ama.contract.MeetingContracts.Conflict();}
  public String candidateId(){return candidate;}public String field(){return field;}public String value(){return value;}
 }
 private Map<String,Object> root(Actor actor,String meeting){authority.savedAccess().prepareExternalRead(actor,meeting);return authority.transactionRunner().required(()->{authority.fileRegistry().lock(actor,meeting,MeetingAccess.Action.EDIT);return j.queryForMap("SELECT revision,transcript_confirmation_id FROM meeting WHERE id=?",meeting);});}
 public Map<String,Object> list(Actor actor,String meeting){return list(actor,meeting,null,null);}
 public Map<String,Object> list(Actor actor,String meeting,ama.model.HttpMeetingCategorySuggestion model,ama.meeting.CategoryDictionary categories){
  uuid(meeting);var before=root(actor,meeting);long revision=((Number)before.get("revision")).longValue();
  var candidates=j.queryForList("SELECT c.id FROM generation_candidate c JOIN generation_input i ON i.id=c.input_id AND i.meeting_id=c.meeting_id WHERE c.meeting_id=? AND c.state IN ('PENDING','ADOPTED','STALE') AND i.scope='FULL' AND i.transcript_confirmation_id=? ORDER BY c.created_at DESC,c.id DESC LIMIT 1",meeting,before.get("transcript_confirmation_id"));
  var items=new ArrayList<Map<String,Object>>();String categoryStatus="SELECT_REQUIRED";
  if(!candidates.isEmpty()){
   String candidate=(String)candidates.getFirst().get("id");var snapshot=reader.readOwner(actor,meeting,"CANDIDATE",candidate);
   if(((Number)snapshot.observation().get("revision")).longValue()!=revision)throw new ama.contract.MeetingContracts.Conflict();
   var row=candidate(snapshot,candidate);var input=JdbcMinutesFactsReader.one(snapshot.graph(),Table.GENERATION_INPUT,(String)row.get("input_id"));
   if(currentSources(snapshot,input)){
    var original=values(snapshot,row);
    if(model!=null&&categories!=null&&j.queryForObject("SELECT COUNT(*) FROM meeting_info_suggestion WHERE candidate_id=? AND field='CATEGORY'",Long.class,candidate)==0){
     try{
      var budget=new ama.meeting.WorkspaceReadBudget();var catalog=categories.catalog(budget);var dictionary=new ArrayList<Map<String,String>>();
      for(var metadata:catalog.metadata(budget)){var category=catalog.read(metadata,budget);dictionary.add(Map.of("id",category.id(),"label",category.label()));}
      var request=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(Map.of("profile",ama.model.HttpMeetingCategorySuggestion.PROFILE,"candidateId",candidate,"inputFingerprint",input.string("input_fingerprint"),"name",original.getOrDefault("NAME",""),"summary",original.getOrDefault("SUMMARY",""),"dictionaryVersion",catalog.version(budget),"categories",dictionary));
      String value=model.execute(request);
      if(value!=null){categories.requireAvailableCategory(value);authority.transactionRunner().required(()->{
       authority.fileRegistry().lock(actor,meeting,MeetingAccess.Action.EDIT);
       if(!before.equals(j.queryForMap("SELECT revision,transcript_confirmation_id FROM meeting WHERE id=?",meeting)))throw new ama.contract.MeetingContracts.Conflict();
       j.update("INSERT INTO meeting_info_suggestion(id,meeting_id,candidate_id,field,state,created_at,category_value,category_input_fingerprint,category_request_sha256,category_profile) VALUES (?,?,?,'CATEGORY','PENDING',?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id",UUID.randomUUID().toString(),meeting,candidate,java.time.LocalDateTime.ofInstant(java.time.Instant.parse((String)row.get("created_at")),java.time.ZoneOffset.UTC),value,input.string("input_fingerprint"),ama.model.HttpMeetingCategorySuggestion.sha(request),ama.model.HttpMeetingCategorySuggestion.PROFILE);return null;
      });}
     }catch(java.io.IOException|ama.contract.MeetingContracts.CategoryUnavailable|IllegalArgumentException unavailable){categoryStatus="UNAVAILABLE";}
    }
    var indexes=j.queryForList("SELECT id,field,state,category_value,category_input_fingerprint,category_profile FROM meeting_info_suggestion WHERE meeting_id=? AND candidate_id=? ORDER BY field LIMIT 3",meeting,candidate);
    for(var index:indexes){String field=(String)index.get("field"),value=suggestionValue(original,index,input);if(value==null)continue;
     if("CATEGORY".equals(field))categoryStatus="AVAILABLE";
     var item=new LinkedHashMap<String,Object>();item.putAll(Map.of("id",index.get("id"),"field",field,"state",index.get("state"),"value",value,"createdAt",row.get("created_at"),"candidateId",candidate,"inputId",input.string("id"),"inputFingerprint",input.string("input_fingerprint"),"transcriptConfirmationId",input.string("transcript_confirmation_id")));items.add(Collections.unmodifiableMap(item));
    }
   }
  }
  var after=root(actor,meeting);if(!before.equals(after))throw new ama.contract.MeetingContracts.Conflict();return Map.of("meetingId",meeting,"revisionToken",Long.toString(revision),"items",List.copyOf(items),"categoryStatus",categoryStatus);
 }
 private static String suggestionValue(Map<String,String> original,Map<String,Object> index,FullRow input){
  String field=(String)index.get("field");if(!"CATEGORY".equals(field))return original.get(field);
  require(ama.model.HttpMeetingCategorySuggestion.PROFILE.equals(index.get("category_profile"))&&input.string("input_fingerprint").equals(index.get("category_input_fingerprint")),PROVENANCE_UNAVAILABLE);
  return (String)index.get("category_value");
 }
 public Prepared prepare(Actor actor,String meeting,String id){
  uuid(meeting);uuid(id);root(actor,meeting);var rows=j.queryForList("SELECT candidate_id,field,category_value,category_input_fingerprint,category_profile FROM meeting_info_suggestion WHERE id=? AND meeting_id=?",id,meeting);require(rows.size()==1,PROVENANCE_UNAVAILABLE);var index=rows.getFirst();String candidate=(String)index.get("candidate_id");var snapshot=reader.readOwner(actor,meeting,"CANDIDATE",candidate);var row=candidate(snapshot,candidate);require(Set.of("PENDING","ADOPTED","STALE").contains(row.get("state")),PROVENANCE_UNAVAILABLE);var input=JdbcMinutesFactsReader.one(snapshot.graph(),Table.GENERATION_INPUT,(String)row.get("input_id"));String confirmation=input.string("transcript_confirmation_id"),field=(String)index.get("field"),value=suggestionValue(values(snapshot,row),index,input);if(!currentSources(snapshot,input))throw new ama.contract.MeetingContracts.Conflict();require(value!=null,PROVENANCE_UNAVAILABLE);return new Prepared(actor,meeting,id,candidate,field,value,confirmation,((Number)snapshot.observation().get("revision")).longValue());
 }
 private static boolean currentSources(JdbcMinutesFactsReader.Snapshot snapshot,FullRow input){for(String field:List.of("transcript_confirmation_id","material_snapshot_id","template_revision_id","exclusion_revision_id"))if(!Objects.equals(input.nullableString(field),snapshot.observation().get(field)))return false;return true;}
 private static Map<String,Object> candidate(JdbcMinutesFactsReader.Snapshot snapshot,String id){var rows=snapshot.graph().auxiliary().getOrDefault("generation_candidate",List.of()).stream().filter(r->id.equals(r.get("id"))).toList();require(rows.size()==1,PROVENANCE_UNAVAILABLE);return rows.getFirst();}
 private static Map<String,String> values(JdbcMinutesFactsReader.Snapshot snapshot,Map<String,Object> row){return MinutesInfoSuggestionValues.from(snapshot.facts().rows(),(String)snapshot.observation().get("id"),(String)row.get("minutes_revision_id"),(String)row.get("element_set_id"),(String)row.get("input_id"));}
 static void index(JdbcTemplate j,String meeting,String candidate,List<FullRow> rows,String body,String set,String input,String time){if(!"FULL".equals(j.queryForObject("SELECT scope FROM generation_input WHERE id=? AND meeting_id=?",String.class,input,meeting)))return;for(String field:MinutesInfoSuggestionValues.from(rows,meeting,body,set,input).keySet())j.update("INSERT INTO meeting_info_suggestion(id,meeting_id,candidate_id,field,state,created_at) VALUES (?,?,?,?,'PENDING',?)",UUID.randomUUID().toString(),meeting,candidate,field,java.time.LocalDateTime.ofInstant(java.time.Instant.parse(time),java.time.ZoneOffset.UTC));}
}
