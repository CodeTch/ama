package ama.transcript;
import ama.contract.IdentityContracts.Actor;import java.util.*;
public final class JdbcMinutesDecisions {
 public JdbcMinutesDecisions(JdbcTranscriptAuthority authority){}
 public Map<String,Object> reject(Actor actor,String meeting,String key,long expected,String candidate,String trace){throw new ama.contract.TranscriptContracts.Failure(503,"DECISION_NOT_INSTALLED");}
 public Map<String,Object> dispose(Actor actor,String meeting,String key,long expected,String candidate,String trace){throw new ama.contract.TranscriptContracts.Failure(503,"DECISION_NOT_INSTALLED");}
}
