package ama.minutes.contract;
import ama.contract.MinutesContracts.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Reports credit already recorded for this exact scope; cannot attest content or aggregate partial spans. */
public final class ReviewCoverageEvaluator {
 public ReviewCoverage evaluate(ReviewFacts scope,String targetHash,String sourceHash,List<FullRow> reviews,BooleanSupplier cancelled){
  hash(targetHash);hash(sourceHash);check(cancelled);FullRow latest=null;var numbers=new HashSet<Long>();
  String canonicalAnchor=scope.anchor()==null?null:OutputValidation.anchorText(scope.anchor());
  for(var row:reviews){
   check(cancelled);require(row.table()==Table.RESULT_REVIEW,INVALID_ARGUMENT);
   if(!row.string("meeting_id").equals(scope.meetingId()))continue;
   positive(row.number("review_no"));require(numbers.add(row.number("review_no")),SOURCE_MEMBERSHIP_MISMATCH);
   if(!row.string("review_kind").equals(scope.kind().name())||!Objects.equals(row.nullableString("element_id"),scope.elementId())
    ||!Objects.equals(row.nullableString("minutes_revision_id"),scope.minutesRevisionId())||!Objects.equals(row.nullableString("target_anchor"),canonicalAnchor)
    ||!row.string("target_hash").equals(targetHash)||!row.string("source_set_hash").equals(sourceHash))continue;
   require(row.string("hash_profile").equals(ResultHashProfiles.REVIEW_PROFILE),PROFILE_UNAVAILABLE);
   if(latest==null||row.number("review_no")>latest.number("review_no"))latest=row;
  }
  boolean full=false;
  if(latest!=null&&scope.kind()==ReviewKind.SOURCE_SUPPORT&&latest.string("outcome").equals("SUPPORTED")){
   var target=scope.stored().rows().stream().filter(r->r.table()==(scope.elementId()!=null?Table.MINUTES_ELEMENT:Table.MINUTES_REVISION)&&r.string("id").equals(scope.elementId()!=null?scope.elementId():scope.minutesRevisionId())).toList();
   require(target.size()==1,PROVENANCE_UNAVAILABLE);String body=target.getFirst().string("body");
   require(scope.anchor().equals(OutputValidation.anchor(body,scope.anchor().start(),scope.anchor().end(),cancelled)),TARGET_INVALID);
   full=scope.anchor().start()==0&&scope.anchor().end()==OutputValidation.scalarCount(body,cancelled);
  }
  check(cancelled);return new ReviewCoverage(latest==null?null:latest.string("id"),latest==null?null:latest.string("outcome"),full);
 }
}
