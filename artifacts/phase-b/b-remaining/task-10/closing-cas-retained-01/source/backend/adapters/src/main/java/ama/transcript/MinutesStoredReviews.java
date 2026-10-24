package ama.transcript;
import ama.config.*;import ama.config.json.*;import ama.contract.ConfigArchiveContracts.*;import ama.contract.ConfigArchiveContracts.Ref;import ama.contract.MinutesContracts.*;import ama.minutes.contract.*;import java.util.*;
import static ama.contract.MinutesContracts.*;import static ama.contract.MinutesContracts.FailureCode.*;
/** Restores exact persisted scopes and recomputes each review with its original archived profile. */
final class MinutesStoredReviews {
 static List<ReviewScopeFact> scopes(MinutesGraphCapture.Graph graph,TranscriptAcquisitionBudget.Ledger budget){
  var result=new ArrayList<ReviewScopeFact>();var numbers=new HashSet<Long>();var parser=new ama.minutes.json.StoredMinutesAnchorReader();
  for(var row:JdbcMinutesFactsReader.rows(graph,Table.RESULT_REVIEW)){
   budget.check();require(graph.discovery().meeting().equals(row.string("meeting_id"))&&row.number("review_no")>0&&numbers.add(row.number("review_no")),PROVENANCE_UNAVAILABLE);String element=row.nullableString("element_id"),body=row.nullableString("minutes_revision_id");require((element==null)!=(body==null),TARGET_INVALID);var target=JdbcMinutesFactsReader.one(graph,element==null?Table.MINUTES_REVISION:Table.MINUTES_ELEMENT,element==null?body:element);Anchor anchor=null;
   if("SOURCE_SUPPORT".equals(row.string("review_kind"))){require(row.nullableString("target_anchor")!=null&&Set.of("SUPPORTED","PARTIAL","UNSUPPORTED").contains(row.string("outcome")),TARGET_INVALID);anchor=parser.read(row.string("target_anchor"),()->false);require(anchor.equals(OutputValidation.anchor(target.string("body"),anchor.start(),anchor.end(),()->false)),TARGET_INVALID);}
   else require("ACTION_FIELDS".equals(row.string("review_kind"))&&element!=null&&"ACTION".equals(target.string("category"))&&row.nullableString("target_anchor")==null&&"CONFIRMED".equals(row.string("outcome")),TARGET_INVALID);
   result.add(new ReviewScopeFact(row.string("id"),anchor));
  }return List.copyOf(result);
 }
 static List<Ref> validate(StoredProvenanceFacts facts,ConfigArchiveSource.Snapshot snapshot,ConfigBindings bindings,ArchiveReadBudget read,GraphBudget graphBudget,TranscriptAcquisitionBudget.Ledger budget){
  var roots=new LinkedHashSet<Ref>();for(var row:facts.rows())if(row.table()==Table.RESULT_REVIEW){
   budget.check();var scopes=facts.reviewScopes().stream().filter(s->s.reviewId().equals(row.string("id"))).toList();require(scopes.size()==1,PROVENANCE_UNAVAILABLE);var evidence=facts.rows().stream().filter(e->e.table()==Table.RESULT_REVIEW_EVIDENCE&&row.string("id").equals(e.string("review_id"))).map(e->e.string("evidence_id")).toList();var scope=new ReviewFacts(row.string("meeting_id"),ReviewKind.valueOf(row.string("review_kind")),row.nullableString("element_id"),row.nullableString("minutes_revision_id"),scopes.getFirst().anchor(),evidence,facts);
   var profileRefs=snapshot.index().entries().stream().map(ConfigArchiveIndex.Entry::ref).filter(ref->ref.artifactType()==ArtifactType.REVIEW_HASH&&ref.version().equals(row.string("hash_profile"))).toList();require(profileRefs.size()==1,PROFILE_UNAVAILABLE);var targetRaw=load(snapshot,profileRefs,read,budget);var context=bindings.begin(facts,targetRaw,read,()->false);var profile=context.review(row.string("hash_profile"));context.finish();String target=new ResultHashProfiles(bindings,targetRaw,read).target(scope,profile,graphBudget,()->false);require(target.equals(row.string("target_hash")),FINGERPRINT_MISMATCH);
   var sourceRefs=new GenerationArchiveRefs(facts,snapshot.index(),new StrictProcessingManifestReader(),read,graphBudget,()->false).review(scope,profile.version());var sourceRaw=load(snapshot,sourceRefs,read,budget);require(new ResultHashProfiles(bindings,sourceRaw,read).sources(scope,profile,graphBudget,()->false).equals(row.string("source_set_hash")),FINGERPRINT_MISMATCH);roots.addAll(sourceRefs);
  }return List.copyOf(roots);
 }
 static List<RawArchive> load(ConfigArchiveSource.Snapshot snapshot,List<Ref> refs,ArchiveReadBudget read,TranscriptAcquisitionBudget.Ledger budget){var raw=snapshot.loadClosure(refs,read,0,()->false);for(var archive:raw){var bytes=archive.bytes();budget.capture(bytes.length,bytes.length,bytes.length,16);}return raw;}
}
