package ama.transcript;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.Actor;
import ama.contract.TaskContracts.SubjectRef;
import ama.contract.TranscriptContracts.Failure;
import ama.task.JdbcSubjectFences;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static ama.contract.MinutesContracts.uuid;

/** Durable governance commands. Technical publication metadata never grants ordinary content access. */
public final class JdbcPublicationGovernance implements AutoCloseable {
 private final JdbcTranscriptAuthority authority;
 private final ControlledKnowledgeClient client;
 private final PublicationGovernanceBinding binding;
 private final JdbcTemplate j;
 private final JdbcSubjectFences fences;
 private final java.util.concurrent.ScheduledExecutorService scheduler=java.util.concurrent.Executors.newScheduledThreadPool(3,r->{var t=new Thread(r,"publication-governance");t.setDaemon(true);return t;});
 private final java.util.concurrent.atomic.AtomicBoolean started=new java.util.concurrent.atomic.AtomicBoolean();
 private volatile boolean closed;
 private final ama.task.PreparationCancellation cancellation=new ama.task.PreparationCancellation(){public boolean cancelled(){return closed||Thread.currentThread().isInterrupted();}public AutoCloseable register(Runnable abort){return ()->{};}};
 public JdbcPublicationGovernance(JdbcTranscriptAuthority authority,ControlledKnowledgeClient client,TranscriptAcquisitionBudget limits){
  this.authority=Objects.requireNonNull(authority);this.client=Objects.requireNonNull(client);this.binding=new PublicationGovernanceBinding(authority,client,limits);
  j=new JdbcTemplate(authority.dataSource());j.setQueryTimeout(2);fences=new JdbcSubjectFences(authority.dataSource(),Set.of("MEETING"));
 }
 public Map<String,Object> accept(Actor actor,String meeting,String publication,String key,long expected,String action,String reason){
  JdbcMinutesFactsReader.independent();uuid(meeting);uuid(publication);JdbcProcessingIntake.key(key);
  if(expected<0||!Set.of("WITHDRAW","OBSOLETE").contains(action)||reason==null||reason.isBlank()||reason.codePointCount(0,reason.length())>2048||reason.getBytes(StandardCharsets.UTF_8).length>8192)
   throw new IllegalArgumentException("Bounded governance action, revision and reason required");
  ama.contract.MinutesContracts.scalars(reason);
  String scope=JdbcProcessingIntake.digest(actor.enterpriseId(),meeting,publication,"GOVERNANCE",key);
  String request=JdbcProcessingIntake.digest(Long.toString(expected),action,reason);
  var prepared=binding.prepare(meeting,publication);
  return binding.withinCurrent(prepared,()->{
   owner(actor,prepared);
   var prior=j.queryForList("SELECT id,request_hash FROM publication_governance_command WHERE scope_hash=? FOR UPDATE",scope);
   if(!prior.isEmpty()){if(!request.equals(prior.getFirst().get("request_hash")))throw new Failure(409,"IDEMPOTENCY_CONFLICT");return projection((String)prior.getFirst().get("id"));}
   if(expected!=prepared.revision())throw new Failure(409,"REVISION_CONFLICT");
   if(!Set.of("ACTIVE","UNKNOWN").contains(prepared.governanceState())||j.queryForObject("SELECT COUNT(*) FROM publication_governance_command WHERE publication_id=?",Long.class,publication)!=0)
    throw new Failure(409,"GOVERNANCE_ALREADY_REQUESTED");
   String id=UUID.randomUUID().toString();var body=new TreeMap<String,Object>();
   body.put("protocol","ama.controlled-knowledge-governance.v1");body.put("actorId",prepared.actorId());body.put("documentId",prepared.documentId());body.put("publicationId",publication);
   body.put("commandKey",id);body.put("expectedRevisionToken","0");body.put("action",action);body.put("reason",reason);
   byte[] wire=ControlledKnowledgeClient.canonical(body);if(wire.length>32768)throw new IllegalArgumentException("Governance wire budget");
   j.update("INSERT INTO publication_governance_command(id,meeting_id,publication_id,actor_id,scope_hash,request_hash,wire_hash,wire_body,action,reason,state) VALUES(?,?,?,?,?,?,?,?,?,?,'PENDING')",
       id,meeting,publication,actor.enterpriseId(),scope,request,ControlledKnowledgeClient.digest(wire),new String(wire,StandardCharsets.UTF_8),action,reason);
   changeRoot(prepared,action.equals("WITHDRAW")?"WITHDRAW_PENDING":"OBSOLETE_PENDING");return projection(id);
  });
 }
 public Map<String,Object> status(Actor actor,String meeting,String publication,String id){
  JdbcMinutesFactsReader.independent();uuid(id);var prepared=binding.prepare(meeting,publication);
  return binding.withinCurrent(prepared,()->{owner(actor,prepared);if(j.queryForObject("SELECT COUNT(*) FROM publication_governance_command WHERE id=? AND meeting_id=? AND publication_id=? AND actor_id=?",Long.class,id,meeting,publication,actor.enterpriseId())!=1)throw new Failure(404,"GOVERNANCE_NOT_FOUND");return projection(id);});
 }
 public List<Map<String,Object>> history(Actor actor,String meeting,String publication){
  JdbcMinutesFactsReader.independent();var prepared=binding.prepare(meeting,publication);
  return binding.withinCurrent(prepared,()->{owner(actor,prepared);return j.queryForList("SELECT id FROM publication_governance_command WHERE publication_id=? ORDER BY created_at DESC,id DESC LIMIT 20",String.class,publication).stream().map(this::projection).toList();});
 }
 private void owner(Actor actor,PublicationGovernanceBinding.Prepared prepared){
  // The exact original owner identity is already locked by the technical capture.
  if(actor==null||!prepared.actorId().equals(actor.enterpriseId())||!"ACTIVE".equals(j.queryForObject("SELECT status FROM enterprise_identity WHERE enterprise_id=?",String.class,prepared.actorId())))throw new MeetingAccess.AccessDeniedException();
 }
 private Map<String,Object> projection(String id){
  var rows=j.queryForList("SELECT id,meeting_id,publication_id,action,state,last_error,created_at,finished_at,reason FROM publication_governance_command WHERE id=?",id);
  if(rows.size()!=1)throw new Failure(404,"GOVERNANCE_NOT_FOUND");var row=rows.getFirst();var result=new LinkedHashMap<String,Object>();
  for(var field:Map.of("governanceJobId","id","meetingId","meeting_id","publicationId","publication_id","action","action","state","state","lastError","last_error","createdAt","created_at","finishedAt","finished_at","reason","reason").entrySet())result.put(field.getKey(),JdbcPublicationQueries.normalizeMetadata(row.get(field.getValue())));
  return Collections.unmodifiableMap(result);
 }
 private void changeRoot(PublicationGovernanceBinding.Prepared prepared,String next){
  if(next.equals(prepared.governanceState()))return;
  if(prepared.accessRevision()==Long.MAX_VALUE||prepared.cancelEpoch()==Long.MAX_VALUE)throw new Failure(409,"REVISION_EXHAUSTED");
  var subject=fences.lock(new SubjectRef("MEETING",prepared.meetingId()),false).orElseThrow();
  if(subject.businessVersion()!=prepared.revision()||subject.cancelEpoch()!=prepared.cancelEpoch())throw new IllegalStateException("Governance fence changed");
  j.update("UPDATE meeting SET governance_state=?,access_revision=access_revision+1,cancel_epoch=cancel_epoch+1 WHERE id=?",next,prepared.meetingId());
  fences.advance(subject,prepared.revision(),prepared.cancelEpoch()+1,false);
 }
 /** An accepted, leased original command only; neither a caller map nor a read proof can mint it. */
 final class Send {
  final PublicationGovernanceBinding.Prepared binding;
  private final String id,lease,hash;private final byte[] wire;
  private Send(PublicationGovernanceBinding.Prepared binding,String id,String lease,String hash,byte[] wire){this.binding=binding;this.id=id;this.lease=lease;this.hash=hash;this.wire=wire.clone();}
  byte[] bytes(){return wire.clone();}
  void require(ControlledKnowledgeClient expected){
   if(client!=expected)throw new IllegalArgumentException("Foreign governance sender");binding.require(expected);
   if(j.queryForObject("SELECT COUNT(*) FROM publication_governance_command WHERE id=? AND lease_owner=? AND wire_hash=? AND state IN ('PENDING','RECONCILING') AND lease_until>UTC_TIMESTAMP(6)",Long.class,id,lease,hash)!=1)throw new IllegalArgumentException("Expired governance command lease");
  }
 }
 private record Work(String id,String meeting,String publication,String lease){}
 public void start(){if(started.compareAndSet(false,true)){scheduler.scheduleWithFixedDelay(()->safe(this::commandOnce),0,1,java.util.concurrent.TimeUnit.SECONDS);for(int n=0;n<2;n++)scheduler.scheduleWithFixedDelay(()->safe(this::pollOnce),n,1,java.util.concurrent.TimeUnit.SECONDS);}}
 private void safe(Runnable action){if(closed)return;try{action.run();}catch(RuntimeException unavailable){/* Durable due rows remain recoverable; no response body or credential logging. */}}
 @Override public void close(){closed=true;scheduler.shutdownNow();try{scheduler.awaitTermination(20,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}}
 /** Bounded one-at-a-time command claim. Locks are released before provenance work or network I/O. */
 public void commandOnce(){
  JdbcMinutesFactsReader.independent();if(closed)return;
  Work work=authority.transactionRunner().required(()->{
   var rows=j.queryForList("SELECT id,meeting_id,publication_id FROM publication_governance_command WHERE state IN ('PENDING','RECONCILING') AND next_attempt_at<=UTC_TIMESTAMP(6) AND (lease_until IS NULL OR lease_until<UTC_TIMESTAMP(6)) ORDER BY next_attempt_at,id LIMIT 1 FOR UPDATE SKIP LOCKED");
   if(rows.isEmpty())return null;var row=rows.getFirst();String lease=UUID.randomUUID().toString();
   j.update("UPDATE publication_governance_command SET lease_owner=?,lease_until=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 60 SECOND),attempts=attempts+1 WHERE id=?",lease,row.get("id"));
   return new Work((String)row.get("id"),(String)row.get("meeting_id"),(String)row.get("publication_id"),lease);
  });if(work==null)return;
  try{
   var prepared=binding.prepare(work.meeting(),work.publication());Send send=binding.withinCurrent(prepared,()->{
    var rows=j.queryForList("SELECT wire_hash,IF(OCTET_LENGTH(wire_body)<=32768,wire_body,NULL) wire_body FROM publication_governance_command WHERE id=? AND lease_owner=? AND lease_until>UTC_TIMESTAMP(6) AND state IN ('PENDING','RECONCILING') FOR UPDATE",work.id(),work.lease());
    if(rows.size()!=1||!(rows.getFirst().get("wire_body") instanceof String text))throw new IllegalArgumentException("Governance original unavailable");byte[] bytes=text.getBytes(StandardCharsets.UTF_8);String hash=(String)rows.getFirst().get("wire_hash");var body=ControlledKnowledgeClient.parse(bytes,32768);
    if(!hash.equals(ControlledKnowledgeClient.digest(bytes))||!Arrays.equals(bytes,ControlledKnowledgeClient.canonical(body))||!prepared.documentId().equals(body.get("documentId"))||!prepared.publicationId().equals(body.get("publicationId"))||!prepared.actorId().equals(body.get("actorId"))||!work.id().equals(body.get("commandKey")))throw new IllegalArgumentException("Governance original binding mismatch");
    return new Send(prepared,work.id(),work.lease(),hash,bytes);
   });
   Map<String,Object> result;
   try{result=client.exchange(client.governanceRead(prepared,work.id()),12288,cancellation);}
   catch(ControlledKnowledgeClient.ProviderFailure missing){if(missing.status!=404)throw missing;result=client.exchange(client.governanceWrite(send),12288,cancellation);}
   var proof=client.governanceProof(ControlledKnowledgeClient.string(result,"assertion"),false);proof.require(prepared);
   if(!work.id().equals(proof.claims.get("commandKey"))||!send.hash.equals(proof.claims.get("requestSha256")))throw new IllegalArgumentException("Foreign governance receipt");
   // A response may outlive its original preparation; reacquire the genuine current metadata.
   apply(binding.prepare(work.meeting(),work.publication()),proof,work);
  }catch(Exception unavailable){
   if(unavailable instanceof InterruptedException)Thread.currentThread().interrupt();
   j.update("UPDATE publication_governance_command SET state='RECONCILING',last_error='GOVERNANCE_OUTCOME_UNKNOWN',lease_owner=NULL,lease_until=NULL,next_attempt_at=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 5 SECOND) WHERE id=? AND lease_owner=? AND state IN ('PENDING','RECONCILING')",work.id(),work.lease());
  }
 }
 /** Demand refresh after the caller has independently checked its local ACL. No business locks span I/O. */
 public void ensureFresh(String meeting){
  JdbcMinutesFactsReader.independent();uuid(meeting);
  var rows=j.queryForList("SELECT p.id,a.verified_until>DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 2 SECOND) fresh FROM meeting m JOIN publication p ON p.meeting_id=m.id AND p.confirmation_id=m.final_confirmation_id AND p.state='SUCCEEDED' LEFT JOIN publication_governance_authority a ON a.publication_id=p.id AND a.state='ACTIVE' WHERE m.id=? AND m.lifecycle='ACTIVE' AND m.governance_state='ACTIVE' AND m.ever_published=1 LIMIT 2",meeting);
  if(rows.isEmpty())return;if(rows.size()!=1)throw new MeetingAccess.AuthorityUnavailableException();var row=rows.getFirst();if(row.get("fresh") instanceof Number n&&n.intValue()==1)return;
  try{var prepared=binding.prepare(meeting,(String)row.get("id"));var response=client.exchange(client.governanceRead(prepared,null),12288,cancellation);var proof=client.governanceProof(ControlledKnowledgeClient.string(response,"assertion"),true);apply(prepared,proof,null);}
  catch(Exception unavailable){throw new MeetingAccess.AuthorityUnavailableException(unavailable);}
 }
 /** Polling recovers lost callbacks; failures never extend a cached authority deadline. */
 public void pollOnce(){
  JdbcMinutesFactsReader.independent();if(closed)return;
  j.update("INSERT IGNORE INTO publication_governance_poll(publication_id) SELECT p.id FROM publication p JOIN external_document_mapping d ON d.publication_id=p.id WHERE p.state='SUCCEEDED' AND NOT EXISTS(SELECT 1 FROM publication_governance_poll g WHERE g.publication_id=p.id) ORDER BY p.id LIMIT 16");
  Work work=authority.transactionRunner().required(()->{
   var rows=j.queryForList("SELECT g.publication_id,p.meeting_id FROM publication_governance_poll g JOIN publication p ON p.id=g.publication_id WHERE g.next_check_at<=UTC_TIMESTAMP(6) AND (g.lease_until IS NULL OR g.lease_until<UTC_TIMESTAMP(6)) ORDER BY g.next_check_at,g.publication_id LIMIT 1 FOR UPDATE SKIP LOCKED");
   if(rows.isEmpty())return null;var row=rows.getFirst();String lease=UUID.randomUUID().toString();j.update("UPDATE publication_governance_poll SET lease_owner=?,lease_until=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 60 SECOND) WHERE publication_id=?",lease,row.get("publication_id"));return new Work(null,(String)row.get("meeting_id"),(String)row.get("publication_id"),lease);
  });if(work==null)return;
  boolean success=false;
  try{
   var prepared=binding.prepare(work.meeting(),work.publication());var response=client.exchange(client.governanceRead(prepared,null),12288,cancellation);var proof=client.governanceProof(ControlledKnowledgeClient.string(response,"assertion"),true);
   proof.require(prepared);apply(prepared,proof,null);success=true;
  }catch(Exception unavailable){if(unavailable instanceof InterruptedException)Thread.currentThread().interrupt();}
  finally{j.update("UPDATE publication_governance_poll SET lease_owner=NULL,lease_until=NULL,last_error=?,next_check_at=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 SECOND) WHERE publication_id=? AND lease_owner=?",success?null:"GOVERNANCE_AUTHORITY_UNKNOWN",work.publication(),work.lease());}
 }
 /** Unauthenticated HTTP callbacks supply only a bounded signature. It must verify before DB lookup. */
 public void callback(String token){
  JdbcMinutesFactsReader.independent();var proof=client.governanceProof(token,false);String publication=(String)proof.claims.get("publicationId");
  var meetings=j.queryForList("SELECT meeting_id FROM publication WHERE id=?",String.class,publication);if(meetings.size()!=1)throw new IllegalArgumentException("Unknown governance publication");
  apply(binding.prepare(meetings.getFirst(),publication),proof,null);
 }
 private void apply(PublicationGovernanceBinding.Prepared prepared,ControlledKnowledgeClient.GovernanceProof proof,Work work){
  proof.require(prepared);var claims=proof.claims;String observed=(String)claims.get("state"),event=(String)claims.get("eventId");long version=Long.parseLong((String)claims.get("revisionToken"));
  binding.withinCurrent(prepared,()->{
   proof.require(prepared);
   if(work!=null&&j.queryForObject("SELECT COUNT(*) FROM publication_governance_command WHERE id=? AND lease_owner=? AND lease_until>UTC_TIMESTAMP(6) AND state IN ('PENDING','RECONCILING') FOR UPDATE",Long.class,work.id(),work.lease())!=1)throw new IllegalArgumentException("Stale governance lease");
   var previous=j.queryForList("SELECT document_id,state,remote_revision,event_id FROM publication_governance_authority WHERE publication_id=? FOR UPDATE",prepared.publicationId());
   if(!previous.isEmpty()&&!prepared.documentId().equals(previous.getFirst().get("document_id")))throw new IllegalArgumentException("Governance document changed");
   String prior=previous.isEmpty()?null:(String)previous.getFirst().get("state");long priorVersion=previous.isEmpty()?-1:((Number)previous.getFirst().get("remote_revision")).longValue();
   String next=KnowledgeGovernanceTransition.next(prepared.governanceState(),prior,priorVersion,observed,version);
   if(!previous.isEmpty()&&priorVersion==version&&!event.equals(previous.getFirst().get("event_id")))throw new IllegalArgumentException("Governance version event mismatch");
   if(!"ACTIVE".equals(observed)){
    var old=j.queryForList("SELECT state,remote_revision FROM publication_governance_event WHERE publication_id=? AND event_id=? FOR UPDATE",prepared.publicationId(),event);
    if(old.isEmpty())j.update("INSERT INTO publication_governance_event(publication_id,event_id,state,remote_revision,proof_hash) VALUES(?,?,?,?,?)",prepared.publicationId(),event,observed,version,proof.digest);
    else if(!observed.equals(old.getFirst().get("state"))||version!=((Number)old.getFirst().get("remote_revision")).longValue())throw new IllegalArgumentException("Conflicting governance event");
   }
   var until=java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(proof.fresh?((Number)claims.get("expiresAt")).longValue():0),java.time.ZoneOffset.UTC);
   j.update("INSERT INTO publication_governance_authority(publication_id,document_id,state,remote_revision,event_id,proof_hash,verified_until,next_check_at) VALUES(?,?,?,?,?,?,?,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE state=VALUES(state),remote_revision=VALUES(remote_revision),event_id=VALUES(event_id),proof_hash=VALUES(proof_hash),verified_until=VALUES(verified_until),next_check_at=VALUES(next_check_at)",prepared.publicationId(),prepared.documentId(),observed,version,event,proof.digest,until);
   // Heartbeat freshness belongs to the authority cache. Do not invalidate an in-flight
   // immutable publication/source capture when the authority state itself has not changed.
   j.update("UPDATE external_document_mapping SET authority_state=?,verified_at=UTC_TIMESTAMP(6) WHERE publication_id=? AND external_document_id=? AND authority_state<>?",observed,prepared.publicationId(),prepared.documentId(),observed);
   changeRoot(prepared,next);
   if(!proof.fresh){
    var matching=j.queryForList("SELECT id,wire_hash,action,reason FROM publication_governance_command WHERE id=? AND publication_id=? AND actor_id=? FOR UPDATE",claims.get("commandKey"),prepared.publicationId(),prepared.actorId());
    if(!matching.isEmpty()){
     var row=matching.getFirst();if(!row.get("wire_hash").equals(claims.get("requestSha256"))||!row.get("action").equals(claims.get("action"))||!ControlledKnowledgeClient.digest(((String)row.get("reason")).getBytes(StandardCharsets.UTF_8)).equals(claims.get("reasonSha256")))throw new IllegalArgumentException("Governance command receipt mismatch");
     j.update("UPDATE publication_governance_command SET state='SUCCEEDED',last_error=NULL,lease_owner=NULL,lease_until=NULL,finished_at=COALESCE(finished_at,UTC_TIMESTAMP(6)) WHERE id=?",row.get("id"));
    }
   }
   if(!"ACTIVE".equals(observed))j.update("UPDATE publication_governance_command SET state='CONFLICT',last_error='OTHER_TERMINAL_GOVERNANCE',lease_owner=NULL,lease_until=NULL,finished_at=UTC_TIMESTAMP(6) WHERE publication_id=? AND state IN ('PENDING','RECONCILING') AND action<>?",prepared.publicationId(),observed.equals("WITHDRAWN")?"WITHDRAW":"OBSOLETE");
   return null;
  });
 }
}
