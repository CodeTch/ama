package ama.minutes.contract;
import ama.contract.MinutesContracts.*;
import ama.config.ConfigBindings;
import ama.contract.ConfigArchiveContracts.*;
import ama.canonical.CanonicalValue;
import ama.canonical.CanonicalValueCodec;
import java.util.*;
import java.util.function.BooleanSupplier;
import static ama.canonical.CanonicalValue.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Content/source projections and hashes only. Never creates review credit or human intent. */
public final class ResultHashProfiles {
 public static final String REVIEW_PROFILE="ama.result-review.v2",FINAL_PROFILE="ama.final-sources.v2";
 private final ArchivedProfileResolver resolver;
 public ResultHashProfiles(){this(ArchivedProfileResolver.unavailable());}
 public ResultHashProfiles(ConfigBindings bindings,List<RawArchive> archives,ArchiveReadBudget budget){this(ArchivedProfileResolver.configured(bindings,archives,budget));}
 ResultHashProfiles(ArchivedProfileResolver resolver){require(resolver!=null,INVALID_ARGUMENT);this.resolver=resolver;}
 public CanonicalValue targetData(ReviewFacts facts,GraphBudget budget,BooleanSupplier cancelled){
  return targetData(facts,new StoredFactsIndex(facts.stored(),budget,cancelled));
 }
 static CanonicalValue targetData(ReviewFacts facts,StoredFactsIndex index){
  var target=targetRow(facts,index);String body=target.string("body");boolean element=facts.elementId()!=null;
  CanonicalValue targetValue,category=nil(),title=nil(),action=nil();
  if(element){
   var set=index.one(Table.ELEMENT_SET,target.string("set_id"),PROVENANCE_UNAVAILABLE);index.meeting(set,facts.meetingId());
   targetValue=object(field("kind",string("ELEMENT")),field("id",string(target.string("id"))),field("setId",string(set.string("id"))),field("setRevision",number(set.number("revision"))),field("minutesRevision",nil()));
   category=string(target.string("category"));
  }else {
   index.meeting(target,facts.meetingId());title=string(target.string("title"));
   targetValue=object(field("kind",string("BODY")),field("id",string(target.string("id"))),field("setId",nil()),field("setRevision",nil()),field("minutesRevision",number(target.number("revision"))));
  }
  if(facts.kind()==ReviewKind.ACTION_FIELDS){
   require(element&&target.string("category").equals("ACTION"),TARGET_INVALID);
   var accepted=index.single(index.matching(Table.ACTION_ITEM,"element_id",target.string("id")),PROVENANCE_UNAVAILABLE);
   action=object(field("ownerEnterpriseId",ContractValues.nullable(accepted.nullableString("owner_enterprise_id"))),field("ownerDisplay",ContractValues.nullable(accepted.nullableString("owner_display"))),field("dueOn",ContractValues.nullable(accepted.nullableString("due_on"))));
  }
  CanonicalValue anchor=nil();
  if(facts.anchor()!=null){
   require(facts.anchor().equals(OutputValidation.anchor(body,facts.anchor().start(),facts.anchor().end(),index.cancelled)),TARGET_INVALID);anchor=ContractValues.anchor(facts.anchor());
  }
  check(index.cancelled);return object(field("meetingId",string(facts.meetingId())),field("reviewKind",string(facts.kind().name())),field("target",targetValue),
   field("content",object(field("title",title),field("body",string(body)),field("category",category),field("action",action))),field("anchor",anchor));
 }
 static FullRow targetRow(ReviewFacts facts,StoredFactsIndex index){
  return facts.elementId()!=null?index.one(Table.MINUTES_ELEMENT,facts.elementId(),PROVENANCE_UNAVAILABLE):index.one(Table.MINUTES_REVISION,facts.minutesRevisionId(),PROVENANCE_UNAVAILABLE);
 }
 public String target(ReviewFacts facts,Artifact profile,GraphBudget budget,BooleanSupplier cancelled){
  var operation=resolver.open(facts.stored(),cancelled);String result=new ResultHashProfiles(operation.resolver()).targetBound(facts,profile,budget,cancelled);operation.finish();check(cancelled);return result;
 }
 private String targetBound(ReviewFacts facts,Artifact profile,GraphBudget budget,BooleanSupplier cancelled){
  return targetBound(facts,profile,new StoredFactsIndex(facts.stored(),budget,cancelled));
 }
 private String targetBound(ReviewFacts facts,Artifact profile,StoredFactsIndex index){
  var budget=index.budget;var cancelled=index.cancelled;
  require(profile!=null&&profile.equals(resolver.reviewProfile(profile.version(),cancelled)),PROFILE_UNAVAILABLE);
  return DomainHash.hash(profile,REVIEW_PROFILE,"REVIEW_TARGET",targetData(facts,index),budget.canonical(),cancelled);
 }
 static List<FullRow> evidence(ReviewFacts facts,StoredFactsIndex index){
  var target=targetRow(facts,index);var rows=index.matching(Table.EVIDENCE_LINK,facts.elementId()!=null?"element_id":"minutes_revision_id",target.string("id"));var included=new ArrayList<FullRow>();
  for(var row:rows){check(index.cancelled);var a=index.anchor(row,target.string("body"));
   if(facts.kind()==ReviewKind.ACTION_FIELDS||a.start()<facts.anchor().end()&&facts.anchor().start()<a.end())included.add(row);
  }
  var supplied=new HashSet<String>();for(var id:facts.selectedEvidenceIds()){check(index.cancelled);uuid(id);require(supplied.add(id),SOURCE_MEMBERSHIP_MISMATCH);}
  var expected=new HashSet<String>();for(var row:included)expected.add(row.string("id"));require(supplied.equals(expected),SOURCE_MEMBERSHIP_MISMATCH);return included;
 }
 public CanonicalValue sourcesData(ReviewFacts facts,GraphBudget budget,BooleanSupplier cancelled){
  return sourcesData(facts,new StoredFactsIndex(facts.stored(),budget,cancelled));
 }
 private CanonicalValue sourcesData(ReviewFacts facts,StoredFactsIndex index){
  var budget=index.budget;var cancelled=index.cancelled;var targetData=targetData(facts,index);var rows=evidence(facts,index);
  String targetHash=new CanonicalValueCodec().sha256(ContractValues.envelope(REVIEW_PROFILE,"REVIEW_TARGET",targetData),budget.canonical(),cancelled);
  String set=facts.elementId()==null?null:targetRow(facts,index).string("set_id");
  var session=index.sources;var selected=new HashSet<>(facts.selectedEvidenceIds());
  var graph=session.graph(new GraphSelection(facts.meetingId(),facts.minutesRevisionId(),set,null,null),selected);session.walk(graph.sourceUsages(),facts.meetingId());
  check(cancelled);return object(field("meetingId",string(facts.meetingId())),field("targetHash",string(targetHash)),field("evidenceRows",RowValues.rows(rows,cancelled)),field("locatorRows",RowValues.rows(graph.locatorRows(),cancelled)),field("sourceUsages",RowValues.usages(graph.sourceUsages(),cancelled)));
 }
 public String sources(ReviewFacts facts,Artifact profile,GraphBudget budget,BooleanSupplier cancelled){
  var operation=resolver.open(facts.stored(),cancelled);String result=new ResultHashProfiles(operation.resolver()).sourcesBound(facts,profile,budget,cancelled);operation.finish();check(cancelled);return result;
 }
 private String sourcesBound(ReviewFacts facts,Artifact profile,GraphBudget budget,BooleanSupplier cancelled){
  return sourcesBound(facts,profile,new StoredFactsIndex(facts.stored(),budget,cancelled));
 }
 private String sourcesBound(ReviewFacts facts,Artifact profile,StoredFactsIndex index){
  var budget=index.budget;var cancelled=index.cancelled;
  require(profile!=null&&profile.equals(resolver.reviewProfile(profile.version(),cancelled)),PROFILE_UNAVAILABLE);
  // Source digests require verified owning raw archive chains; the schema interpreter is deliberately an explicit prerequisite.
  var data=sourcesData(facts,index);
  String set=facts.elementId()==null?null:targetRow(facts,index).string("set_id");
  var session=index.sources;var graph=session.graph(new GraphSelection(facts.meetingId(),facts.minutesRevisionId(),set,null,null),new HashSet<>(facts.selectedEvidenceIds()));
  OriginChainVerifier.verify(index,session,graph,resolver);
  return DomainHash.hash(profile,REVIEW_PROFILE,"REVIEW_SOURCES",data,budget.canonical(),cancelled);
 }

 static ReviewFacts storedReview(FullRow review,StoredFactsIndex index){
  var kind=ReviewKind.valueOf(review.string("review_kind"));Anchor anchor=null;
  if(kind==ReviewKind.SOURCE_SUPPORT){
   var scopes=new ArrayList<ReviewScopeFact>();for(var fact:index.facts.reviewScopes()){check(index.cancelled);if(fact.reviewId().equals(review.string("id")))scopes.add(fact);}
   require(scopes.size()==1&&scopes.getFirst().anchor()!=null,TARGET_INVALID);anchor=scopes.getFirst().anchor();
   require(OutputValidation.anchorText(anchor).equals(review.nullableString("target_anchor")),TARGET_INVALID);
  }else require(review.nullableString("target_anchor")==null,TARGET_INVALID);
  var ids=new ArrayList<String>();for(var membership:index.matching(Table.RESULT_REVIEW_EVIDENCE,"review_id",review.string("id")))ids.add(membership.string("evidence_id"));
  return new ReviewFacts(review.string("meeting_id"),kind,review.nullableString("element_id"),review.nullableString("minutes_revision_id"),anchor,ids,index.facts);
 }
 public CanonicalValue finalData(FinalFacts facts,GraphBudget budget,BooleanSupplier cancelled){
  return finalData(facts,new StoredFactsIndex(facts.stored(),budget,cancelled));
 }
 private CanonicalValue finalData(FinalFacts facts,StoredFactsIndex index){return finalData(selection(facts,index),index);}
 public CanonicalValue finalData(FinalDraft draft,GraphBudget budget,BooleanSupplier cancelled){return finalData(draft,new StoredFactsIndex(draft.stored(),budget,cancelled));}
 private static FinalDraft selection(FinalFacts facts,StoredFactsIndex index){
  var row=index.one(Table.FINAL_CONFIRMATION,facts.confirmationId(),PROVENANCE_UNAVAILABLE);
  return new FinalDraft(row.string("id"),row.string("meeting_id"),row.number("meeting_revision"),row.string("info_revision_id"),row.string("roster_revision_id"),row.string("transcript_confirmation_id"),row.string("minutes_revision_id"),row.nullableString("material_snapshot_id"),row.string("element_set_id"),row.string("template_revision_id"),row.string("exclusion_revision_id"),row.string("input_fingerprint"),row.string("confirmed_by"),row.string("confirmed_at"),index.matching(Table.FINAL_RESULT_REVIEW,"confirmation_id",row.string("id")).stream().map(r->r.string("review_id")).toList(),facts.stored());
 }
 private static String selected(FinalDraft draft,String field){return switch(field){
  case "id"->draft.confirmationId();case "meeting_id"->draft.meetingId();case "minutes_revision_id"->draft.minutesRevisionId();case "element_set_id"->draft.elementSetId();case "transcript_confirmation_id"->draft.transcriptConfirmationId();case "material_snapshot_id"->draft.materialSnapshotId();case "template_revision_id"->draft.templateRevisionId();case "exclusion_revision_id"->draft.exclusionRevisionId();case "input_fingerprint"->draft.inputFingerprint();default->throw fail(INVALID_ARGUMENT);
 };}
 private static CanonicalValue finalMembership(FinalDraft draft,BooleanSupplier cancelled){
  var ids=new ArrayList<>(draft.reviewIds());ids.sort((a,b)->CanonicalValueCodec.compareUtf8(a,b,cancelled));var rows=new ArrayList<CanonicalValue>();
  for(String id:ids){check(cancelled);rows.add(object(field("confirmation_id",string(draft.confirmationId())),field("review_id",string(id))));}return new CanonicalValue.Sequence(rows);
 }
 private CanonicalValue finalData(FinalDraft draft,StoredFactsIndex index){
  var budget=index.budget;var cancelled=index.cancelled;String meeting=draft.meetingId();
  var body=index.one(Table.MINUTES_REVISION,selected(draft,"minutes_revision_id"),PROVENANCE_UNAVAILABLE);index.meeting(body,meeting);
  String inputId=body.nullableString("generation_input_id");require(inputId!=null,PROFILE_UNAVAILABLE);var input=index.one(Table.GENERATION_INPUT,inputId,PROFILE_UNAVAILABLE);index.meeting(input,meeting);
  for(var column:List.of("transcript_confirmation_id","material_snapshot_id","template_revision_id","exclusion_revision_id"))require(Objects.equals(input.nullableString(column),selected(draft,column)),SOURCE_MEMBERSHIP_MISMATCH);
  require(input.string("input_fingerprint").equals(selected(draft,"input_fingerprint")),FINGERPRINT_MISMATCH);
  var session=index.sources;var graph=session.graph(new GraphSelection(meeting,body.string("id"),selected(draft,"element_set_id"),selected(draft,"exclusion_revision_id"),inputId),null);session.walk(graph.sourceUsages(),meeting);
  var evidenceIds=new HashSet<String>();for(var usage:graph.sourceUsages())if(usage.usage() instanceof EvidenceUsage e)evidenceIds.add(e.evidenceId());
  var evidenceRows=new ArrayList<FullRow>();for(var row:index.rows(Table.EVIDENCE_LINK))if(evidenceIds.contains(row.string("id")))evidenceRows.add(row);
  var setRows=RowsetProjection.elements(index,meeting,selected(draft,"element_set_id"));
  var directoryRows=new ArrayList<FullRow>();var elementIds=new HashSet<String>();
  for(var row:setRows){check(cancelled);if(RowsetProjection.DIRECTORY_TABLES.contains(row.table()))directoryRows.add(row);if(row.table()==Table.MINUTES_ELEMENT)elementIds.add(row.string("id"));}
  var exclusionRows=RowsetProjection.exclusions(index,meeting,selected(draft,"exclusion_revision_id"));
  var reviewRows=new ArrayList<FullRow>();var reviewEvidence=new ArrayList<FullRow>();var finalReviews=draft.reviewIds();
  var reviewNumbers=new HashSet<Long>();
  for(var member:finalReviews){
   check(cancelled);var review=index.one(Table.RESULT_REVIEW,member,PROVENANCE_UNAVAILABLE);index.meeting(review,meeting);positive(review.number("review_no"));require(reviewNumbers.add(review.number("review_no")),SOURCE_MEMBERSHIP_MISMATCH);
   require(review.nullableString("element_id")!=null&&elementIds.contains(review.string("element_id"))||body.string("id").equals(review.nullableString("minutes_revision_id")),SOURCE_MEMBERSHIP_MISMATCH);
   require(review.string("hash_profile").equals(REVIEW_PROFILE),PROFILE_UNAVAILABLE);
   var reviewFacts=storedReview(review,index);var target=targetData(reviewFacts,index);
   String targetHash=new CanonicalValueCodec().sha256(ContractValues.envelope(review.string("hash_profile"),"REVIEW_TARGET",target),budget.canonical(),cancelled);
   String sourcesHash=new CanonicalValueCodec().sha256(ContractValues.envelope(review.string("hash_profile"),"REVIEW_SOURCES",sourcesData(reviewFacts,index)),budget.canonical(),cancelled);
   require(targetHash.equals(review.string("target_hash"))&&sourcesHash.equals(review.string("source_set_hash")),FINGERPRINT_MISMATCH);
   reviewRows.add(review);reviewEvidence.addAll(index.matching(Table.RESULT_REVIEW_EVIDENCE,"review_id",review.string("id")));
  }
  check(cancelled);
  return object(field("meetingId",string(meeting)),field("confirmationId",string(selected(draft,"id"))),field("minutesRevisionId",string(body.string("id"))),field("elementSetId",string(selected(draft,"element_set_id"))),
   field("transcriptConfirmationId",string(selected(draft,"transcript_confirmation_id"))),field("materialSnapshotId",ContractValues.nullable(selected(draft,"material_snapshot_id"))),field("templateRevisionId",string(selected(draft,"template_revision_id"))),field("exclusionRevisionId",string(selected(draft,"exclusion_revision_id"))),field("inputFingerprint",string(input.string("input_fingerprint"))),
   field("locatorRows",RowValues.rows(graph.locatorRows(),cancelled)),field("sourceUsages",RowValues.usages(graph.sourceUsages(),cancelled)),field("evidenceRows",RowValues.rows(evidenceRows,cancelled)),
   field("directoryRows",RowsetProjection.tableArray(directoryRows,RowsetProjection.DIRECTORY_TABLES,index)),field("exclusionRows",RowsetProjection.tableArray(exclusionRows,EnumSet.of(Table.EXCLUSION_REVISION,Table.SENSITIVE_EXCLUSION),index)),
   field("reviewRows",RowValues.rows(reviewRows,cancelled)),field("reviewEvidenceRows",RowValues.rows(reviewEvidence,cancelled)),field("finalReviewRows",finalMembership(draft,cancelled)));
 }
 public String finalSources(FinalFacts facts,Artifact profile,GraphBudget budget,BooleanSupplier cancelled){
  var operation=resolver.open(facts.stored(),cancelled);String result=new ResultHashProfiles(operation.resolver()).finalSourcesBound(facts,profile,budget,cancelled);operation.finish();check(cancelled);return result;
 }
 public String finalSources(FinalDraft draft,Artifact profile,GraphBudget budget,BooleanSupplier cancelled){
  var operation=resolver.open(draft.stored(),cancelled);String result=new ResultHashProfiles(operation.resolver()).finalSourcesBound(draft,profile,budget,cancelled);operation.finish();check(cancelled);return result;
 }
 private String finalSourcesBound(FinalFacts facts,Artifact profile,GraphBudget budget,BooleanSupplier cancelled){return finalSourcesBound(selection(facts,new StoredFactsIndex(facts.stored(),budget,cancelled)),profile,budget,cancelled);}
 private String finalSourcesBound(FinalDraft draft,Artifact profile,GraphBudget budget,BooleanSupplier cancelled){
  var index=new StoredFactsIndex(draft.stored(),budget,cancelled);var body=index.one(Table.MINUTES_REVISION,draft.minutesRevisionId(),PROVENANCE_UNAVAILABLE);
  String inputId=body.nullableString("generation_input_id");require(inputId!=null,PROFILE_UNAVAILABLE);var input=index.one(Table.GENERATION_INPUT,inputId,PROFILE_UNAVAILABLE);
  var bound=resolver.generation(input,draft.stored(),budget,cancelled);require(bound.equals(index.config(input))&&bound.profiles().definition(ProfileKind.FINAL_SOURCES).equals(profile),PROFILE_UNAVAILABLE);
  GenerationFingerprint.historicalFence(input,draft.stored(),cancelled);
  for(var member:draft.reviewIds()){
   check(cancelled);var review=index.one(Table.RESULT_REVIEW,member,PROVENANCE_UNAVAILABLE);var reviewFacts=storedReview(review,index);
   require(review.string("hash_profile").equals(REVIEW_PROFILE),PROFILE_UNAVAILABLE);var reviewProfile=resolver.review(review,draft.stored(),budget,cancelled);
   require(targetBound(reviewFacts,reviewProfile,index).equals(review.string("target_hash"))&&sourcesBound(reviewFacts,reviewProfile,index).equals(review.string("source_set_hash")),FINGERPRINT_MISMATCH);
  }
  var session=index.sources;var graph=session.graph(new GraphSelection(selected(draft,"meeting_id"),body.string("id"),selected(draft,"element_set_id"),selected(draft,"exclusion_revision_id"),inputId),null);
  session.walkInputs(List.of(inputId),selected(draft,"meeting_id"));OriginChainVerifier.verify(index,session,graph,resolver);
  return DomainHash.hash(profile,FINAL_PROFILE,"FINAL_SOURCES",finalData(draft,index),TranscriptConfirmationHash.minimum(budget.canonical(),resolver.generationBudget(input,cancelled)),cancelled);
 }
}
