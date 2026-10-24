package ama.transcript;
import ama.config.ConfigArchiveSource;import ama.contract.IdentityContracts.Actor;import java.util.*;import java.util.function.Supplier;
public final class JdbcMinutesReviews {
 public JdbcMinutesReviews(JdbcTranscriptAuthority authority,Supplier<ConfigArchiveSource.Snapshot> archives,String pin,TranscriptAcquisitionBudget limits){}
 public Map<String,Object> review(Actor actor,String meeting,String key,long expected,String targetKind,String targetId,String reviewKind,String scope,String outcome,Long start,Long end,String trace){throw new ama.contract.TranscriptContracts.Failure(503,"REVIEW_NOT_INSTALLED");}
}
