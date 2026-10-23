package ama.minutes.contract;
import ama.contract.MinutesContracts.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Derives represented stored edges. Supplied storage authenticity and raw profile interpretation remain separate. */
public final class StoredSourceGraph {
 public SourceGraph derive(StoredProvenanceFacts facts,GraphSelection selection,GraphBudget budget,BooleanSupplier cancelled){
  var session=new StoredFactsIndex(facts,budget,cancelled).sources;var graph=session.graph(selection,null);
  session.walk(graph.sourceUsages(),selection.meetingId());check(cancelled);return graph;
 }
 public void verify(SourceGraph supplied,StoredProvenanceFacts facts,GraphSelection selection,GraphBudget budget,BooleanSupplier cancelled){
  var actual=derive(facts,selection,budget,cancelled);check(cancelled);
  var locators=new ArrayList<>(supplied.locatorRows());var usages=new ArrayList<>(supplied.sourceUsages());
  locators.sort((a,b)->RowValues.compare(a,b,cancelled));usages.sort((a,b)->RowValues.compareUsage(a,b,cancelled));
  require(actual.equals(new SourceGraph(locators,usages)),SOURCE_MEMBERSHIP_MISMATCH);
 }
 static final class Session {
  final StoredFactsIndex index;final BooleanSupplier cancelled;final Map<String,Catalog> catalogs=new HashMap<>();
  final Map<String,SourceGraph> inputGraphs=new LinkedHashMap<>();
  private final Map<String,Integer> heights=new HashMap<>();private final Set<String> visited=new HashSet<>();
  Session(StoredFactsIndex index){this.index=index;this.cancelled=index.cancelled;}
  SourceGraph graph(GraphSelection s,Set<String> evidenceFilter){
   check(cancelled);FullRow body=s.minutesRevisionId()==null?null:index.one(Table.MINUTES_REVISION,s.minutesRevisionId(),PROVENANCE_UNAVAILABLE);
   if(body!=null)index.meeting(body,s.meetingId());
   if(s.elementSetId()!=null)index.meeting(index.one(Table.ELEMENT_SET,s.elementSetId(),PROVENANCE_UNAVAILABLE),s.meetingId());
   var usages=new ArrayList<SourceUsage>();var locatorRows=new HashMap<String,FullRow>();var elementIds=new HashSet<String>();
   if(s.elementSetId()!=null)for(var element:index.matching(Table.MINUTES_ELEMENT,"set_id",s.elementSetId())){check(cancelled);positive(element.number("ordinal"));elementIds.add(element.string("id"));}
   for(var evidence:index.rows(Table.EVIDENCE_LINK)){
    check(cancelled);String element=evidence.nullableString("element_id"),minutes=evidence.nullableString("minutes_revision_id");
    require((element==null)!=(minutes==null),SOURCE_MEMBERSHIP_MISMATCH);
    boolean included=minutes!=null&&minutes.equals(s.minutesRevisionId())||element!=null&&elementIds.contains(element);
    if(!included||evidenceFilter!=null&&!evidenceFilter.contains(evidence.string("id")))continue;
    FullRow locator=locator(evidence.string("locator_id"),s.meetingId());Origin origin;CatalogFact source;
    if(minutes!=null){
     index.anchor(evidence,body.string("body"));var input=input(body.nullableString("generation_input_id"),s.meetingId());
     source=source(input,RowValues.locator(locator));origin=new GenerationOrigin(input.string("id"),input.string("input_fingerprint"));
    }else {
     var target=index.one(Table.MINUTES_ELEMENT,element,PROVENANCE_UNAVAILABLE);index.anchor(evidence,target.string("body"));
     var suggestion=index.matching(Table.ACTION_SUGGESTION,"element_id",element);
     if(target.string("category").equals("ACTION")&&!suggestion.isEmpty()){
      var row=index.single(suggestion,PROVENANCE_UNAVAILABLE);var input=input(row.string("input_id"),s.meetingId());
      source=source(input,RowValues.locator(locator));origin=new GenerationOrigin(input.string("id"),input.string("input_fingerprint"));
     }else {
      require(RowValues.locator(locator) instanceof TranscriptLocator,PROVENANCE_UNAVAILABLE);
      var trans=(TranscriptLocator)RowValues.locator(locator);
      var confirmation=index.single(index.matching(Table.TRANSCRIPT_CONFIRMATION,"revision_id",trans.transcriptRevisionId()),PROVENANCE_UNAVAILABLE);index.meeting(confirmation,s.meetingId());
      source=transcript(confirmation,trans.segmentId(),s.meetingId());origin=new ConfirmationOrigin(confirmation.string("id"),trans.transcriptRevisionId(),confirmation.string("content_hash"));
     }
    }
    add(usages,locatorRows,new SourceUsage(new EvidenceUsage(evidence.string("id")),locator.string("id"),origin,source.role(),source.sourceVersion(),source.sourceContentHash()),locator);
   }
   if(evidenceFilter==null&&s.elementSetId()!=null){
    directories(s,Table.SENSITIVE_DETECTION,Table.SENSITIVE_DETECTION_SOURCE,"detection_id",usages,locatorRows);
    directories(s,Table.BACKGROUND_NOTE,Table.BACKGROUND_NOTE_SOURCE,"note_id",usages,locatorRows);
    for(var suggestion:index.rows(Table.ACTION_SUGGESTION))if(elementIds.contains(suggestion.string("element_id"))){
     var element=index.one(Table.MINUTES_ELEMENT,suggestion.string("element_id"),PROVENANCE_UNAVAILABLE);
     require(element.string("category").equals("ACTION"),SOURCE_MEMBERSHIP_MISMATCH);input(suggestion.string("input_id"),s.meetingId());
    }
   }
   if(s.exclusionRevisionId()!=null){
    var exclusion=index.one(Table.EXCLUSION_REVISION,s.exclusionRevisionId(),PROVENANCE_UNAVAILABLE);index.meeting(exclusion,s.meetingId());
    require(body!=null&&s.appliedInputId()!=null&&s.appliedInputId().equals(body.nullableString("generation_input_id")),SOURCE_MEMBERSHIP_MISMATCH);
    var input=input(s.appliedInputId(),s.meetingId());require(input.string("exclusion_revision_id").equals(s.exclusionRevisionId()),SOURCE_MEMBERSHIP_MISMATCH);
    for(var row:index.matching(Table.SENSITIVE_EXCLUSION,"revision_id",s.exclusionRevisionId())){
     var locator=locator(row.string("locator_id"),s.meetingId());var source=source(input,RowValues.locator(locator));
     add(usages,locatorRows,new SourceUsage(new ExclusionUsage(s.exclusionRevisionId(),locator.string("id"),SensitiveKind.valueOf(row.string("category"))),locator.string("id"),new AppliedOrigin(input.string("id"),input.string("input_fingerprint")),source.role(),source.sourceVersion(),source.sourceContentHash()),locator);
    }
   }
   var locators=new ArrayList<>(locatorRows.values());locators.sort((a,b)->RowValues.compare(a,b,cancelled));usages.sort((a,b)->RowValues.compareUsage(a,b,cancelled));
   var keys=new HashSet<UsageKey>();for(var usage:usages){check(cancelled);require(keys.add(usage.usage()),SOURCE_MEMBERSHIP_MISMATCH);}
   check(cancelled);return new SourceGraph(locators,usages);
  }
  void directories(GraphSelection s,Table directory,Table join,String fk,List<SourceUsage> usages,Map<String,FullRow> locators){
   for(var row:index.matching(directory,"set_id",s.elementSetId())){
    check(cancelled);positive(row.number("ordinal"));var input=input(row.string("input_id"),s.meetingId());var members=index.matching(join,fk,row.string("id"));require(!members.isEmpty(),SOURCE_MEMBERSHIP_MISMATCH);
    for(var member:members){
     check(cancelled);var locator=locator(member.string("locator_id"),s.meetingId());var source=source(input,RowValues.locator(locator));
     UsageKey key=directory==Table.SENSITIVE_DETECTION?new DetectionUsage(row.string("id"),locator.string("id")):new BackgroundUsage(row.string("id"),locator.string("id"));
     add(usages,locators,new SourceUsage(key,locator.string("id"),new GenerationOrigin(input.string("id"),input.string("input_fingerprint")),source.role(),source.sourceVersion(),source.sourceContentHash()),locator);
    }
   }
  }
  void add(List<SourceUsage> usages,Map<String,FullRow> rows,SourceUsage usage,FullRow locator){check(cancelled);rows.putIfAbsent(locator.string("id"),locator);usages.add(usage);}
  FullRow locator(String id,String meeting){var row=index.one(Table.SOURCE_LOCATOR,id,PROVENANCE_UNAVAILABLE);index.meeting(row,meeting);RowValues.locator(row);return row;}
  FullRow input(String id,String meeting){require(id!=null,PROVENANCE_UNAVAILABLE);var row=index.one(Table.GENERATION_INPUT,id,PROVENANCE_UNAVAILABLE);index.meeting(row,meeting);return row;}
  CatalogFact source(FullRow input,Locator locator){
   CatalogFact result=null;for(var entry:catalog(input).sources()){check(cancelled);if(entry.fact().locator().equals(locator))result=entry.fact();}
   require(result!=null,SOURCE_NOT_IN_INPUT);return result;
  }
  Catalog catalog(FullRow input){
   String id=input.string("id");if(catalogs.containsKey(id))return catalogs.get(id);
   String meeting=input.string("meeting_id");var config=index.config(input);var facts=new ArrayList<CatalogFact>();
   var confirmation=index.one(Table.TRANSCRIPT_CONFIRMATION,input.string("transcript_confirmation_id"),PROVENANCE_UNAVAILABLE);index.meeting(confirmation,meeting);
   for(var segment:index.matching(Table.TRANSCRIPT_SEGMENT,"revision_id",confirmation.string("revision_id")))facts.add(transcript(confirmation,segment.string("segment_id"),meeting));
   require(!facts.isEmpty(),PROVENANCE_UNAVAILABLE);String snapshotId=input.nullableString("material_snapshot_id");
   if(snapshotId!=null){
    index.meeting(index.one(Table.MATERIAL_SNAPSHOT,snapshotId,PROVENANCE_UNAVAILABLE),meeting);var materialIds=new HashSet<String>();var ordinals=new HashSet<Long>();
    for(var member:index.matching(Table.MATERIAL_SNAPSHOT_ITEM,"snapshot_id",snapshotId)){
     check(cancelled);positive(member.number("ordinal"));require(ordinals.add(member.number("ordinal")),SOURCE_MEMBERSHIP_MISMATCH);String revisionId=member.string("material_revision_id");materialIds.add(revisionId);
     var revision=index.one(Table.MATERIAL_REVISION,revisionId,PROVENANCE_UNAVAILABLE);var reference=index.one(Table.MATERIAL_REFERENCE,revision.string("reference_id"),PROVENANCE_UNAVAILABLE);index.meeting(reference,meeting);
     var selections=config.parserSelections().stream().filter(p->p.materialRevisionId().equals(revisionId)).toList();
     require(selections.size()==1,selections.size()>1?SOURCE_CONTEXT_AMBIGUOUS:CAPABILITY_UNAVAILABLE);String parser=selections.getFirst().parserVersion();
     var documents=index.matching(Table.PARSED_DOCUMENT,"material_revision_id",revisionId).stream().filter(d->d.string("parser_version").equals(parser)).toList();
     var document=index.single(documents,CAPABILITY_UNAVAILABLE);String fileId=revision.nullableString("content_file_id");require(fileId!=null,CAPABILITY_UNAVAILABLE);
     var content=index.file(fileId);var parsed=index.file(document.nullableString("text_file_id"));var units=index.matching(Table.PARSED_UNIT,"document_id",document.string("id"));
     require(units.size()==document.number("unit_count")&&!units.isEmpty(),SOURCE_MEMBERSHIP_MISMATCH);
     var sourceUnits=new ArrayList<SourceUnit>();for(var unit:units){check(cancelled);sourceUnits.add(new SourceUnit(unit.number("ordinal"),unit.nullableNumber("page_no"),unit.nullableNumber("paragraph_no"),unit.nullableString("section_path")));}
     var bounds=new DocumentBounds(fileId,revisionId,document.string("id"),parser,parsed.sha256(),sourceUnits,index.budget.canonical().maxContainerEntries());SourceVersion version;String hash;
     if(reference.string("kind").equals("LOCAL")){
      require(fileId.equals(reference.nullableString("file_id"))&&revision.nullableString("external_version")==null&&reference.nullableString("provider_id")==null&&reference.nullableString("external_id")==null,SOURCE_MEMBERSHIP_MISMATCH);
      require(content.sha256().equals(revision.string("content_hash")),SOURCE_MEMBERSHIP_MISMATCH);
      version=new FileVersion(fileId,revisionId,document.string("id"),parser,parsed.sha256());hash=content.sha256();
     }else {
      version=new KnowledgeVersion(revisionId,reference.string("provider_id"),reference.string("external_id"),revision.string("external_version"),fileId,document.string("id"),parser,parsed.sha256());hash=revision.string("content_hash");
      facts.add(new CatalogFact(new KnowledgeLocator(revisionId),Role.BACKGROUND,version,hash,0,new KnowledgeBounds(revisionId,reference.string("provider_id"),reference.string("external_id"),revision.string("external_version"),fileId,hash)));
     }
     for(var locator:index.rows(Table.SOURCE_LOCATOR)){
      check(cancelled);if(!locator.string("meeting_id").equals(meeting))continue;var typed=RowValues.locator(locator);
      if(typed instanceof PageLocator page&&page.fileId().equals(fileId)||typed instanceof ParagraphLocator paragraph&&paragraph.fileId().equals(fileId))facts.add(new CatalogFact(typed,Role.BACKGROUND,version,hash,0,bounds));
      if(typed instanceof AudioLocator audio&&audio.fileId().equals(fileId))throw fail(CAPABILITY_UNAVAILABLE);
     }
    }
    for(var selection:config.parserSelections()){check(cancelled);require(materialIds.contains(selection.materialRevisionId()),SOURCE_MEMBERSHIP_MISMATCH);}
   }else require(config.parserSelections().isEmpty(),SOURCE_MEMBERSHIP_MISMATCH);
   var catalog=new SourceCatalog().build(new CatalogFacts(facts,index.budget.canonical().maxContainerEntries()),index.budget.canonical(),cancelled);catalogs.put(id,catalog);return catalog;
  }
  record SavedTranscript(FullRow revision,FullRow batch,List<FullRow> segments,List<FullRow> files) {}
  SavedTranscript validatedTranscriptRevision(String meeting,String revisionId){
   var revision=index.one(Table.TRANSCRIPT_REVISION,revisionId,PROVENANCE_UNAVAILABLE);index.meeting(revision,meeting);
   var processing=index.one(Table.TRANSCRIPTION_INPUT,revision.string("processing_input_id"),PROVENANCE_UNAVAILABLE);index.meeting(processing,meeting);require(processing.string("batch_id").equals(revision.string("batch_id")),SOURCE_MEMBERSHIP_MISMATCH);
   var batch=index.one(Table.INPUT_BATCH,revision.string("batch_id"),PROVENANCE_UNAVAILABLE);index.meeting(batch,meeting);
   var segments=index.matching(Table.TRANSCRIPT_SEGMENT,"revision_id",revisionId);var orders=new HashSet<Long>();boolean content=false;
   var segmentIds=new HashSet<String>();
   for(var row:segments){check(cancelled);positive(row.number("ordinal"));require(orders.add(row.number("ordinal")),SOURCE_MEMBERSHIP_MISMATCH);content|=!row.string("body").isBlank();segmentIds.add(row.string("segment_id"));
    var times=row.nullableNumber("start_ms");var end=row.nullableNumber("end_ms");require((times==null)==(end==null)&&(times==null||end>=times),INPUT_INVALID);
    if(row.nullableNumber("source_paragraph")!=null)positive(row.number("source_paragraph"));}
   require(content,PROVENANCE_UNAVAILABLE);
   for(var mark:index.matching(Table.TRANSCRIPT_SEGMENT_MARK,"revision_id",revisionId)){check(cancelled);require(segmentIds.contains(mark.string("segment_id")),SOURCE_MEMBERSHIP_MISMATCH);}
   var files=index.matching(Table.INPUT_BATCH_FILE,"batch_id",batch.string("id"));var fileOrders=new HashSet<Long>();var fileIds=new HashSet<String>();
   for(var row:files){check(cancelled);positive(row.number("ordinal"));require(fileOrders.add(row.number("ordinal"))&&fileIds.add(row.string("file_id")),SOURCE_MEMBERSHIP_MISMATCH);
    require(index.file(row.string("file_id")).sha256().equals(row.string("sha256")),SOURCE_MEMBERSHIP_MISMATCH);}
   return new SavedTranscript(revision,batch,segments,files);
  }
  FullRow savedMember(SavedTranscript saved,FullRow segment){
   return index.single(saved.files().stream().filter(f->f.string("file_id").equals(segment.string("source_file_id"))).toList(),PROVENANCE_UNAVAILABLE);
  }
  CatalogFact transcript(FullRow confirmation,String segmentId,String meeting){
   index.meeting(confirmation,meeting);String revisionId=confirmation.string("revision_id");var saved=validatedTranscriptRevision(meeting,revisionId);
   var selected=index.single(saved.segments().stream().filter(s->s.string("segment_id").equals(segmentId)).toList(),PROVENANCE_UNAVAILABLE);
   var file=savedMember(saved,selected);
   return new CatalogFact(new TranscriptLocator(revisionId,segmentId),Role.MEETING,new TranscriptVersion(confirmation.string("id"),revisionId,saved.batch().string("id"),file.string("file_id"),file.string("sha256")),confirmation.string("content_hash"),selected.number("ordinal"),new TranscriptBounds(revisionId,segmentId,file.string("file_id"),selected.nullableNumber("source_paragraph")));
  }
  private static final class Walk {
   final String id;final Iterator<String> dependencies;int height=1;
   Walk(String id,Collection<String> dependencies){this.id=id;this.dependencies=dependencies.iterator();}
  }
  void walk(List<SourceUsage> usages,String meeting){
   var initial=new LinkedHashSet<String>();for(var usage:usages){check(cancelled);if(usage.origin() instanceof GenerationOrigin g)initial.add(g.inputId());if(usage.origin() instanceof AppliedOrigin g)initial.add(g.inputId());}walkInputs(initial,meeting);
  }
  void walkInputs(Collection<String> initial,String meeting){
   var active=new HashSet<String>();var stack=new ArrayDeque<Walk>();
   for(String root:initial){
    check(cancelled);stack.push(new Walk(null,List.of(root)));
    while(!stack.isEmpty()){
     check(cancelled);var frame=stack.peek();if(!frame.dependencies.hasNext()){
      stack.pop();if(frame.id!=null){active.remove(frame.id);heights.put(frame.id,frame.height);extend(stack.peek(),frame.height);}continue;
     }
     String id=frame.dependencies.next();require(!active.contains(id),INPUT_INVALID);
     int depth=stack.size();require(depth<=index.budget.maxOriginDepth(),BUDGET_EXCEEDED);
     var height=heights.get(id);
     if(height!=null){require(height<=index.budget.maxOriginDepth()-depth+1,BUDGET_EXCEEDED);extend(frame,height);continue;}
     require(visited.size()<index.budget.maxOriginNodes(),BUDGET_EXCEEDED);visited.add(id);active.add(id);
     var input=input(id,meeting);catalog(input);var dependencies=new LinkedHashSet<String>();String set=input.nullableString("element_set_id");
     var graph=set==null?new SourceGraph(List.of(),List.of()):graph(new GraphSelection(meeting,null,set,null,null),null);inputGraphs.put(id,graph);
     for(var usage:graph.sourceUsages()){check(cancelled);if(usage.origin() instanceof GenerationOrigin origin)dependencies.add(origin.inputId());if(usage.origin() instanceof AppliedOrigin origin)dependencies.add(origin.inputId());}
     stack.push(new Walk(id,dependencies));
    }
   }
  }
  private void extend(Walk parent,int childHeight){
   if(parent==null||parent.id==null)return;long height=(long)childHeight+1;require(height<=index.budget.maxOriginDepth(),BUDGET_EXCEEDED);parent.height=Math.max(parent.height,(int)height);
  }
 }
}
