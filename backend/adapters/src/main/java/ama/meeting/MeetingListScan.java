package ama.meeting;
import ama.contract.IdentityContracts.Actor;
import java.time.LocalDateTime;import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Bounded indexed fixed-metadata discovery only; no result is an authorization grant. */
final class MeetingListScan {
 static final List<String> STAGES=List.of("ENTRY","TRANSCRIPT_PENDING","TRANSCRIPT_FAILED","MINUTES_PENDING","MINUTES_FAILED","READY_TO_PUBLISH","PUBLISH_FAILED","PUBLISHED");
 record Position(LocalDateTime updated,String id) {
  Position {Objects.requireNonNull(updated);ama.contract.OutboxContracts.uuid(id);if(updated.getNano()%1000!=0)throw new IllegalArgumentException("Invalid timestamp precision");}
  String encode(){return updated+"|"+id;}
  static Position parse(String text){try{String[] fields=text.split("\\|",-1);if(fields.length!=2)throw new IllegalArgumentException();var key=new Position(LocalDateTime.parse(fields[0]),fields[1]);if(!key.encode().equals(text))throw new IllegalArgumentException();return key;}catch(RuntimeException e){throw new IllegalArgumentException("Invalid list cursor position",e);}}
 }
 record Candidate(Position position,long revision,String info,String stage){}
 private final JdbcTemplate jdbc;
 MeetingListScan(JdbcTemplate jdbc){this.jdbc=jdbc;this.jdbc.setQueryTimeout(5);}
 List<Candidate> discover(Actor actor,String scope,int limit,Position after,WorkspaceReadBudget budget) {
  if(!Set.of("owned","joined").contains(scope)||limit<1||limit>50)throw new IllegalArgumentException("Invalid list request");
  var result=new ArrayList<Candidate>();
  for(String partition:scope.equals("owned")?List.of("owned"):STAGES) {
   int maximum=limit+1;budget.reserve(maximum,maximum*5L,maximum*256L,maximum*256L,maximum*256L,4,false);
   String where=scope.equals("owned")?"owner_id=? AND lifecycle='ACTIVE'":"lifecycle='ACTIVE' AND stage=?";
   var args=new ArrayList<Object>();args.add(scope.equals("owned")?actor.enterpriseId():partition);
   if(after!=null){where+=" AND (updated_at<? OR (updated_at=? AND id<?))";args.add(after.updated());args.add(after.updated());args.add(after.id());}
   args.add(maximum);
   String index=scope.equals("owned")?"meeting_owner_updated":"meeting_stage_updated";
   var rows=jdbc.query("SELECT id,updated_at,revision,info_revision_id,stage FROM meeting FORCE INDEX ("+index+") WHERE "+where+" ORDER BY updated_at DESC,id DESC LIMIT ?",rs->{
    var page=new ArrayList<Candidate>();while(rs.next()){
     budget.check();if(page.size()>=maximum)throw new WorkspaceReadBudget.Unavailable();
     var position=new Position(rs.getObject("updated_at",LocalDateTime.class),rs.getString("id"));long revision=rs.getLong("revision");String info=rs.getString("info_revision_id"),stage=rs.getString("stage");
     if(revision<0||revision==Long.MAX_VALUE||!STAGES.contains(stage))throw new WorkspaceReadBudget.Unavailable();if(info!=null)ama.contract.OutboxContracts.uuid(info);
     page.add(new Candidate(position,revision,info,stage));
    }return List.copyOf(page);
   },args.toArray());result.addAll(rows);
  }
  if(result.stream().map(c->c.position.id()).distinct().count()!=result.size())throw new WorkspaceReadBudget.Unavailable();
  result.sort(Comparator.comparing((Candidate c)->c.position.updated()).thenComparing(c->c.position.id()).reversed());budget.check();return List.copyOf(result);
 }
}
