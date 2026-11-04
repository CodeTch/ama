package ama.meeting;

import ama.contract.IdentityContracts.Actor;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** One parameterized permission-first relation for page and every aggregate. No body transfer. */
final class MeetingQueryRelation {
 static final String STAGE="CASE WHEN m.stage<>'ENTRY' THEN m.stage WHEN m.final_confirmation_id IS NOT NULL THEN 'READY_TO_PUBLISH' WHEN (m.minutes_revision_id IS NOT NULL AND m.element_set_id IS NOT NULL) OR m.transcript_confirmation_id IS NOT NULL THEN 'MINUTES_PENDING' WHEN m.transcript_revision_id IS NOT NULL OR EXISTS(SELECT 1 FROM transcript_revision tr WHERE tr.meeting_id=m.id) THEN 'TRANSCRIPT_PENDING' ELSE 'ENTRY' END";
 static final String SPECIAL="(m.ever_published=1 OR EXISTS(SELECT 1 FROM material_reference mr WHERE mr.meeting_id=m.id AND mr.kind='KNOWLEDGE'))";
 private final JdbcTemplate j;private final WorkspaceReadBudget budget;private final String local,from,specialFrom;private final List<Object> localArgs,args,specialArgs;
 record Special(String id,long revision,long access,long epoch){}
 record Counts(long total,Map<String,Long> stages,Map<String,Long> topics){}
 MeetingQueryRelation(JdbcTemplate j,Actor actor,String scope,MeetingListFilter filter,Collection<String> denied,WorkspaceReadBudget budget){
  this.j=j;this.budget=budget;var bound=new ArrayList<Object>();
  String acl;
  if(scope.equals("owned")){acl="m.owner_id=?";bound.add(actor.enterpriseId());}
  else{acl="EXISTS(SELECT 1 FROM meeting_access_grant g JOIN roster_revision r ON r.id=g.roster_id AND r.meeting_id=g.meeting_id AND r.state='CONFIRMED' JOIN roster_member rm ON rm.roster_id=r.id AND rm.enterprise_id=g.enterprise_id AND rm.member_kind='ENTERPRISE' WHERE g.meeting_id=m.id AND g.enterprise_id=? AND g.action='READ' AND g.roster_id=m.roster_revision_id)";bound.add(actor.enterpriseId());}
  local=" FROM meeting m WHERE m.lifecycle='ACTIVE' AND m.governance_state='ACTIVE' AND "+acl;localArgs=List.copyOf(bound);
  var where=new StringBuilder(" FROM meeting m JOIN meeting_search_projection p ON p.meeting_id=m.id WHERE m.lifecycle='ACTIVE' AND m.governance_state='ACTIVE' AND "+acl);
  where.append(" AND p.meeting_revision=m.revision AND p.info_revision_id=m.info_revision_id AND (p.roster_revision_id <=> m.roster_revision_id)");
  if(!filter.q().isEmpty()){where.append(" AND (p.name_search LIKE ? ESCAPE '=' OR p.number_search LIKE ? ESCAPE '=' OR EXISTS(SELECT 1 FROM meeting_person_search n JOIN roster_revision r ON r.id=n.roster_id AND r.meeting_id=n.meeting_id AND r.state='CONFIRMED' WHERE n.meeting_id=m.id AND n.roster_id=m.roster_revision_id AND n.name_search LIKE ? ESCAPE '='))");String q="%"+filter.q().replace("=","==").replace("%","=%").replace("_","=_")+"%";Collections.addAll(bound,q,q,q);}
  if(!filter.stages().isEmpty()){where.append(" AND (").append(STAGE).append(") IN (").append(marks(filter.stages().size())).append(')');bound.addAll(filter.stages());}
  if(!filter.categories().isEmpty()){where.append(" AND p.category_id IN (").append(marks(filter.categories().size())).append(')');bound.addAll(filter.categories());}
  if(filter.from()!=null){where.append(" AND m.actual_meeting_date>=?");bound.add(filter.from());}
  if(filter.to()!=null){where.append(" AND m.actual_meeting_date<=?");bound.add(filter.to());}
  if(filter.topic()!=null){where.append(filter.topic().equals("__none")?" AND NOT EXISTS(SELECT 1 FROM meeting_topic mt JOIN topic t ON t.id=mt.topic_id WHERE mt.meeting_id=m.id AND t.owner_id=?)":" AND EXISTS(SELECT 1 FROM meeting_topic mt JOIN topic t ON t.id=mt.topic_id WHERE mt.meeting_id=m.id AND t.owner_id=? AND t.id=?)");bound.add(actor.enterpriseId());if(!filter.topic().equals("__none"))bound.add(filter.topic());}
  specialFrom=where.toString();specialArgs=List.copyOf(bound);
  if(!denied.isEmpty()){where.append(" AND m.id NOT IN (").append(marks(denied.size())).append(')');bound.addAll(denied);}
  from=where.toString();args=List.copyOf(bound);
 }
 private static String marks(int n){return String.join(",",Collections.nCopies(n,"?"));}
 List<Special> special(){
  budget.reserve(129,516,129*100,129*100,129*100,2,false);
  var rows=j.query("SELECT m.id,m.revision,m.access_revision,m.cancel_epoch"+specialFrom+" AND "+SPECIAL+" ORDER BY m.id LIMIT 129",(r,n)->new Special(r.getString(1),r.getLong(2),r.getLong(3),r.getLong(4)),specialArgs.toArray());
  if(rows.size()>128)throw new WorkspaceReadBudget.Unavailable();return rows;
 }
 void requireCurrentProjection(){
  // Omitted/stale projections must fail, never silently reduce totals.
  budget.reserve(1,8,128,128,128,2,false);
  var invalid=j.queryForList("SELECT m.id"+local+" AND (NOT EXISTS(SELECT 1 FROM meeting_search_projection p JOIN meeting_info_revision i ON i.id=p.info_revision_id AND i.meeting_id=p.meeting_id WHERE p.meeting_id=m.id AND p.meeting_revision=m.revision AND p.info_revision_id=m.info_revision_id AND (p.roster_revision_id <=> m.roster_revision_id) AND (i.actual_meeting_date <=> m.actual_meeting_date)) OR NOT EXISTS(SELECT 1 FROM task_subject f WHERE f.subject_kind='MEETING' AND f.subject_id=m.id AND f.business_version=m.revision AND f.cancel_epoch=m.cancel_epoch AND f.deleted=0)) LIMIT 1",String.class,localArgs.toArray());
  if(!invalid.isEmpty())throw new WorkspaceReadBudget.Unavailable();
 }
 List<MeetingListScan.Candidate> page(int limit,MeetingListScan.Position after){
  var values=new ArrayList<>(args);String continuation="";
  if(after!=null){continuation=" AND (m.updated_at<? OR (m.updated_at=? AND m.id<?))";Collections.addAll(values,after.updated(),after.updated(),after.id());}values.add(limit+1);
  budget.reserve(limit+1,(limit+1)*5L,(limit+1)*256L,(limit+1)*256L,(limit+1)*256L,3,false);
  return j.query("SELECT m.id,m.updated_at,m.revision,m.info_revision_id,m.stage"+from+continuation+" ORDER BY m.updated_at DESC,m.id DESC LIMIT ?",(r,n)->new MeetingListScan.Candidate(new MeetingListScan.Position(r.getObject(2,LocalDateTime.class),r.getString(1)),r.getLong(3),r.getString(4),r.getString(5)),values.toArray());
 }
 Counts counts(Actor actor,String scope){
  boolean owned=scope.equals("owned");int maximum=owned?2048:8;
  var stages=new LinkedHashMap<String,Long>();for(String stage:MeetingListScan.STAGES)stages.put(stage,0L);var topics=new LinkedHashMap<String,Long>();long[] total={0};
  String topic=owned?"COALESCE((SELECT mt.topic_id FROM meeting_topic mt JOIN topic t ON t.id=mt.topic_id WHERE mt.meeting_id=m.id AND t.owner_id=?),'__none')":"'__none'";
  var values=new ArrayList<Object>();if(owned)values.add(actor.enterpriseId());values.addAll(args);
  String grouped="SELECT "+STAGE+" workspace_stage,"+topic+" topic_key,COUNT(*) n"+from+" GROUP BY workspace_stage,topic_key LIMIT "+(maximum+1);
  // Admit numeric cardinality before transferring any grouped string. The same locked
  // transaction and exact relation are used for both passes, with the original hard cap.
  budget.reserve(1,8,128,128,128,2,false);
  long groups=j.queryForObject("SELECT COUNT(*) FROM ("+grouped+") bounded_groups",Long.class,values.toArray());
  if(groups<0||groups>maximum)throw new WorkspaceReadBudget.Unavailable();
  budget.reserve(0,0,(groups+1)*96L,(groups+1)*96L,(groups+1)*96L,3,false);
  j.query(grouped,rs->{int rows=0;while(rs.next()){budget.check();if(++rows>groups)throw new WorkspaceReadBudget.Unavailable();budget.reserve(1,3,96,96,96,1,false);String stage=rs.getString(1),key=rs.getString(2);long n=rs.getLong(3);if(!stages.containsKey(stage)||n<0)throw new WorkspaceReadBudget.Unavailable();stages.compute(stage,(k,v)->Math.addExact(v,n));total[0]=Math.addExact(total[0],n);if(owned)topics.merge(key,n,Math::addExact);if(topics.size()>256)throw new WorkspaceReadBudget.Unavailable();}return null;},values.toArray());
  budget.reserve(8+topics.size(),16+topics.size()*2L,2048+topics.size()*128L,2048+topics.size()*128L,2048+topics.size()*128L,2,true);
  return new Counts(total[0],Map.copyOf(stages),Map.copyOf(topics));
 }
}
