package ama.minutes.contract;
import ama.contract.MinutesContracts.*;
import ama.canonical.CanonicalValue;
import ama.canonical.CanonicalValueCodec;
import java.util.*;
import java.util.function.BooleanSupplier;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
import static ama.canonical.CanonicalValue.*;

final class StoredFactsIndex {
 final StoredProvenanceFacts facts;final GraphBudget budget;final BooleanSupplier cancelled;CanonicalBudget hashBudget;
 private final Map<Table,List<FullRow>> tables=new EnumMap<>(Table.class);
 private final Map<String,ContentFile> files=new HashMap<>();private final Map<String,GenerationConfiguration> configs=new HashMap<>();private final Map<String,Anchor> anchors=new HashMap<>();
 StoredFactsIndex(StoredProvenanceFacts facts,GraphBudget budget,BooleanSupplier cancelled){
  require(facts!=null&&budget!=null,INVALID_ARGUMENT);check(cancelled);this.facts=facts;this.budget=budget;this.cancelled=cancelled;this.hashBudget=budget.canonical();
  require((long)facts.rows().size()+facts.files().size()+facts.configurations().size()+facts.evidenceAnchors().size()+facts.taskSpecs().size()+facts.taskFacts().size()+facts.reviewScopes().size()<=budget.maxAggregateFacts(),BUDGET_EXCEEDED);
  var all=new ArrayList<CanonicalValue>();
  for(var row:facts.rows()){check(cancelled);tables.computeIfAbsent(row.table(),t->new ArrayList<>()).add(row);all.add(RowValues.row(row));}
  new CanonicalValueCodec().preflight(new Sequence(all),budget.canonical(),cancelled);
  for(var table:tables.values()){table.sort((a,b)->RowValues.compare(a,b,cancelled));for(int i=1;i<table.size();i++)require(RowValues.compare(table.get(i-1),table.get(i),cancelled)!=0,SOURCE_MEMBERSHIP_MISMATCH);}
  for(var file:facts.files()){check(cancelled);require(files.putIfAbsent(file.id(),file)==null,SOURCE_MEMBERSHIP_MISMATCH);}
  long archiveBytes=0;
  for(var config:facts.configurations()){
   check(cancelled);require(configs.putIfAbsent(config.inputId(),config)==null,SOURCE_CONTEXT_AMBIGUOUS);
   var artifacts=new ArrayList<>(config.profiles().definitions().values());artifacts.add(config.prompt());artifacts.add(config.model());
   for(var artifact:artifacts){check(cancelled);byte[] bytes=artifact.bytes();require(bytes.length<=budget.canonical().maxEncodedBytes()-archiveBytes,BUDGET_EXCEEDED);archiveBytes+=bytes.length;
    var digest=CanonicalValueCodec.digest();for(int i=0;i<bytes.length;i+=Math.min(1024,bytes.length-i)){check(cancelled);digest.update(bytes,i,Math.min(1024,bytes.length-i));}
    require(HexFormat.of().formatHex(digest.digest()).equals(artifact.sha256()),PROFILE_UNAVAILABLE);
   }
  }
  for(var anchor:facts.evidenceAnchors()){check(cancelled);require(anchors.putIfAbsent(anchor.evidenceId(),anchor.anchor())==null,SOURCE_MEMBERSHIP_MISMATCH);}
  validateMembershipRows();check(cancelled);
 }
 List<FullRow> rows(Table table){check(cancelled);return tables.getOrDefault(table,List.of());}
 List<FullRow> matching(Table table,String column,String value){
  var matches=new ArrayList<FullRow>();for(var row:rows(table)){check(cancelled);if(Objects.equals(row.nullableString(column),value))matches.add(row);}return matches;
 }
 FullRow one(Table table,String id,FailureCode code){var rows=matching(table,"id",id);require(rows.size()==1,code);return rows.getFirst();}
 FullRow single(List<FullRow> rows,FailureCode missing){require(rows.size()==1,rows.size()>1?SOURCE_CONTEXT_AMBIGUOUS:missing);return rows.getFirst();}
 ContentFile file(String id){check(cancelled);require(files.containsKey(id),PROVENANCE_UNAVAILABLE);return files.get(id);}
 GenerationConfiguration config(FullRow input){
  var config=configs.get(input.string("id"));require(config!=null,PROFILE_UNAVAILABLE);
  require(config.prompt().version().equals(input.string("prompt_version"))&&config.model().version().equals(input.string("model_config_version")),PROFILE_UNAVAILABLE);return config;
 }
 void meeting(FullRow row,String meeting){require(row.string("meeting_id").equals(meeting),SOURCE_MEMBERSHIP_MISMATCH);}
 Anchor anchor(FullRow evidence,String body){
  var a=anchors.get(evidence.string("id"));require(a!=null,TARGET_INVALID);
  require(OutputValidation.anchorText(a).equals(evidence.string("target_anchor"))&&a.equals(OutputValidation.anchor(body,a.start(),a.end(),cancelled)),TARGET_INVALID);return a;
 }

 private void unique(Table table,String... columns){
  var seen=new HashSet<List<Cell>>();for(var row:rows(table)){check(cancelled);var key=new ArrayList<Cell>();for(var column:columns)key.add(row.value(column));require(seen.add(key),SOURCE_MEMBERSHIP_MISMATCH);}
 }
 private FullRow parent(Table table,String id){return one(table,id,SOURCE_MEMBERSHIP_MISMATCH);}
 private String elementMeeting(String id){var element=parent(Table.MINUTES_ELEMENT,id);return parent(Table.ELEMENT_SET,element.string("set_id")).string("meeting_id");}
 private void validateMembershipRows(){
  unique(Table.TRANSCRIPT_CONFIRMATION,"meeting_id","revision_id");unique(Table.TRANSCRIPT_SEGMENT,"revision_id","ordinal");unique(Table.INPUT_BATCH_FILE,"batch_id","file_id");
  unique(Table.MINUTES_ELEMENT,"set_id","category","ordinal");unique(Table.SENSITIVE_DETECTION,"set_id","category","ordinal");unique(Table.BACKGROUND_NOTE,"set_id","ordinal");unique(Table.RESULT_REVIEW,"meeting_id","review_no");
  for(var row:rows(Table.MINUTES_ELEMENT)){check(cancelled);positive(row.number("ordinal"));parent(Table.ELEMENT_SET,row.string("set_id"));}
  for(var table:List.of(Table.SENSITIVE_DETECTION,Table.BACKGROUND_NOTE))for(var row:rows(table)){
   check(cancelled);positive(row.number("ordinal"));String meeting=parent(Table.ELEMENT_SET,row.string("set_id")).string("meeting_id");meeting(parent(Table.GENERATION_INPUT,row.string("input_id")),meeting);
  }
  for(var table:List.of(Table.SENSITIVE_DETECTION_SOURCE,Table.BACKGROUND_NOTE_SOURCE))for(var row:rows(table)){
   check(cancelled);boolean detection=table==Table.SENSITIVE_DETECTION_SOURCE;var owner=parent(detection?Table.SENSITIVE_DETECTION:Table.BACKGROUND_NOTE,row.string(detection?"detection_id":"note_id"));
   String meeting=parent(Table.ELEMENT_SET,owner.string("set_id")).string("meeting_id");meeting(parent(Table.SOURCE_LOCATOR,row.string("locator_id")),meeting);
  }
  for(var table:List.of(Table.ACTION_ITEM,Table.ACTION_SUGGESTION))for(var row:rows(table)){
   check(cancelled);var element=parent(Table.MINUTES_ELEMENT,row.string("element_id"));require(element.string("category").equals("ACTION"),SOURCE_MEMBERSHIP_MISMATCH);
   if(table==Table.ACTION_SUGGESTION)meeting(parent(Table.GENERATION_INPUT,row.string("input_id")),elementMeeting(element.string("id")));
  }
  for(var row:rows(Table.EVIDENCE_LINK)){
   check(cancelled);String element=row.nullableString("element_id"),body=row.nullableString("minutes_revision_id");require((element==null)!=(body==null),SOURCE_MEMBERSHIP_MISMATCH);
   String meeting=element!=null?elementMeeting(element):parent(Table.MINUTES_REVISION,body).string("meeting_id");meeting(parent(Table.SOURCE_LOCATOR,row.string("locator_id")),meeting);
  }
  for(var row:rows(Table.SENSITIVE_EXCLUSION)){check(cancelled);String meeting=parent(Table.EXCLUSION_REVISION,row.string("revision_id")).string("meeting_id");meeting(parent(Table.SOURCE_LOCATOR,row.string("locator_id")),meeting);}
 }
}
