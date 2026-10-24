package ama.transcript;
import ama.contract.MinutesContracts.*;
import ama.contract.TaskContracts.TaskSpec;
import ama.minutes.contract.*;
import ama.canonical.CanonicalValueCodec;
import java.util.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
/** Bounded inert row plan. Only the registered one-use external invocation may write it. */
public final class MinutesCandidateGraphPlan {
 private final String candidate,body,set,time;private final TaskSpec spec;private final String owner;private final List<FullRow> templates,reused;private final TranscriptAcquisitionBudget.Ledger budget;
 private MinutesCandidateGraphPlan(String candidate,String body,String set,String time,TaskSpec spec,String owner,List<FullRow> templates,List<FullRow> reused,TranscriptAcquisitionBudget.Ledger budget){this.candidate=candidate;this.body=body;this.set=set;this.time=time;this.spec=spec;this.owner=owner;this.templates=List.copyOf(templates);this.reused=List.copyOf(reused);this.budget=budget;}
 public String candidateId(){return candidate;}public String bodyId(){return body;}public String setId(){return set;}public String createdAt(){return time;}TaskSpec spec(){return spec;}String owner(){return owner;}List<FullRow> reusedLocators(){return reused;}
 /** History numbers are selected independently under the genuine root, never inferred from task r. */
 public List<FullRow> rows(long bodyRevision,long setRevision){budget.check();require(bodyRevision>0&&bodyRevision<Long.MAX_VALUE&&setRevision>0&&setRevision<Long.MAX_VALUE,INVALID_ARGUMENT);var result=new ArrayList<FullRow>();for(var row:templates){if(row.table()==Table.MINUTES_REVISION||row.table()==Table.ELEMENT_SET){var cells=new ArrayList<>(row.cells());cells.set(row.table().index("revision"),cell(row.table()==Table.MINUTES_REVISION?bodyRevision:setRevision));result.add(new FullRow(row.table(),cells));}else result.add(row);}return List.copyOf(result);}
 public static MinutesCandidateGraphPlan prepare(TaskSpec spec,String owner,String parent,ValidatedOutput output,Catalog catalog,List<FullRow> existing,TranscriptAcquisitionBudget.Ledger budget){
  JdbcMinutesFactsReader.independent();Objects.requireNonNull(budget);budget.check();MinutesTaskDomainPolicy.shape(spec);text(owner,false);if(parent!=null){uuid(parent);require(existing.stream().filter(r->r.table()==Table.MINUTES_REVISION&&r.string("id").equals(parent)&&r.string("meeting_id").equals(spec.subject().id())).count()==1,PROVENANCE_UNAVAILABLE);}
  var validated=new OutputValidation().validate(output.output(),new ValidationContext(spec.inputFingerprint(),spec.inputFingerprint(),Scope.FULL,catalog),()->false);var classification=new CreationSupport().classify(validated,catalog);var value=validated.output();String candidate=id(),body=id(),set=id(),meeting=spec.subject().id();String time=java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'").format(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS));var rows=new ArrayList<FullRow>();var reused=new ArrayList<FullRow>();
  // Internal history slots are not stored facts; rows() replaces both before any persistence.
  rows.add(row(budget,Table.MINUTES_REVISION,body,meeting,1L,parent,value.title(),"AI",value.body(),spec.input().id(),owner,time));rows.add(row(budget,Table.ELEMENT_SET,set,meeting,1L,time));
  var locatorIds=new HashMap<Locator,String>();var aliases=new HashMap<String,String>();for(var ref:value.sourceRefs()){
   budget.check();var locator=catalog.source(ref.sourceId()).fact().locator();require(locator instanceof TranscriptLocator,CAPABILITY_UNAVAILABLE);String selected=locatorIds.get(locator);
   if(selected==null){var transcript=(TranscriptLocator)locator;var candidates=existing.stream().filter(r->r.table()==Table.SOURCE_LOCATOR&&meeting.equals(r.string("meeting_id"))&&"TRANSCRIPT".equals(r.string("kind"))&&transcript.transcriptRevisionId().equals(r.nullableString("transcript_revision_id"))&&transcript.segmentId().equals(r.nullableString("segment_id"))).sorted(Comparator.comparing(r->r.string("id"))).toList();
    if(!candidates.isEmpty()){var old=candidates.getFirst();var exact=row(budget,Table.SOURCE_LOCATOR,old.string("id"),meeting,"TRANSCRIPT",transcript.transcriptRevisionId(),transcript.segmentId(),null,null,null,null,null,null,null);require(old.equals(exact),PROVENANCE_UNAVAILABLE);selected=old.string("id");reused.add(old);}else{selected=id();rows.add(row(budget,Table.SOURCE_LOCATOR,selected,meeting,"TRANSCRIPT",transcript.transcriptRevisionId(),transcript.segmentId(),null,null,null,null,null,null,null));}locatorIds.put(locator,selected);
   }require(aliases.put(ref.sourceId(),selected)==null,SOURCE_MEMBERSHIP_MISMATCH);
  }
  var elementIds=new HashMap<String,String>();var support=new HashMap<String,Support>();for(var entry:classification.elements())support.put(entry.key(),entry.support());
  for(var category:Category.values()){long ordinal=1;for(var element:value.elements().get(category)){
   String elementId=id();require(elementIds.put(element.key(),elementId)==null,INVALID_SCHEMA);rows.add(row(budget,Table.MINUTES_ELEMENT,elementId,set,category.name(),ordinal++,element.text(),support.get(element.key()).name()));
   if(category==Category.ACTION){rows.add(row(budget,Table.ACTION_ITEM,elementId,null,null,null));rows.add(row(budget,Table.ACTION_SUGGESTION,elementId,spec.input().id(),element.action().ownerMention(),element.action().dueOn()));}
  }}
  var detectionOrdinals=new EnumMap<SensitiveKind,Long>(SensitiveKind.class);for(var detection:value.sensitive()){String detectionId=id();long ordinal=detectionOrdinals.merge(detection.category(),1L,Long::sum);rows.add(row(budget,Table.SENSITIVE_DETECTION,detectionId,set,spec.input().id(),detection.category().name(),ordinal));for(String alias:detection.sourceIds().values())rows.add(row(budget,Table.SENSITIVE_DETECTION_SOURCE,detectionId,required(aliases,alias)));}
  long noteOrdinal=1;for(var note:value.background()){String noteId=id();rows.add(row(budget,Table.BACKGROUND_NOTE,noteId,set,spec.input().id(),noteOrdinal++,note.text()));for(String alias:note.sourceIds().values())rows.add(row(budget,Table.BACKGROUND_NOTE_SOURCE,noteId,required(aliases,alias)));}
  for(var anchored:validated.anchors())for(String alias:anchored.evidence().sourceIds().values()){
   var target=anchored.evidence().target();String element=target instanceof ElementTarget t?required(elementIds,t.elementKey()):null;var states=classification.evidence().stream().filter(e->e.target().equals(target)&&e.sourceId().equals(alias)).toList();require(states.size()==1,PROVENANCE_UNAVAILABLE);rows.add(row(budget,Table.EVIDENCE_LINK,id(),required(aliases,alias),element,element==null?body:null,states.getFirst().support().name(),OutputValidation.anchorText(anchored.anchor())));
  }
  budget.check();return new MinutesCandidateGraphPlan(candidate,body,set,time,spec,owner,rows,reused,budget);
 }
 private static String required(Map<String,String> values,String key){String value=values.get(key);require(value!=null,PROVENANCE_UNAVAILABLE);return value;}
 private static String id(){return UUID.randomUUID().toString();}
 private static FullRow row(TranscriptAcquisitionBudget.Ledger budget,Table table,Object...values){budget.row(table.columns().size());var cells=new ArrayList<Cell>();for(var value:values){if(value instanceof String text){budget.text(CanonicalValueCodec.utf8Length(text,()->false),text.codePointCount(0,text.length()),text.length(),8,true);cells.add(cell(text));}else cells.add(value==null?new NullCell():cell((Long)value));}return new FullRow(table,cells);}
}
