package ama.transcript;

import ama.access.MeetingAccess.*;
import ama.contract.IdentityContracts.*;
import ama.meeting.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Exact-version proofs fetched outside business transactions. The DB lease coordinates I/O only;
 * content deadlines always come from the verified signed assertion's original absolute expiry. */
public final class JdbcMaterialAuthority implements AutoCloseable {
 private final JdbcTranscriptAuthority authority;private final JdbcMeetingAccess access;private final ControlledKnowledgeClient client;private final JdbcPublicationGovernance governance;private final JdbcTemplate j;private final String pins;
 private final ScheduledExecutorService scheduler=Executors.newScheduledThreadPool(4,r->{var t=new Thread(r,"material-authority");t.setDaemon(true);return t;});
 private final java.util.concurrent.atomic.AtomicBoolean started=new java.util.concurrent.atomic.AtomicBoolean();private volatile boolean closed;
 public JdbcMaterialAuthority(JdbcTranscriptAuthority authority,ControlledKnowledgeClient client,JdbcPublicationGovernance governance){
  this.authority=Objects.requireNonNull(authority);this.client=Objects.requireNonNull(client);this.governance=governance;
  if(!(authority.savedAccess() instanceof JdbcMeetingAccess real))throw new IllegalArgumentException("Genuine local ACL required");access=real;
  j=new JdbcTemplate(authority.dataSource());j.setQueryTimeout(3);pins=ControlledKnowledgeClient.digest(ControlledKnowledgeClient.canonical(client.identities()));
  authority.installMaterialAuthority(this);access.installMaterialValidity(pins);
  access.installExternalPreparation((actor,meeting)->afterLocalRead(actor,meeting,true));
 }
 static final class Request {
  private final JdbcMaterialAuthority issuer;final Actor actor;final String meeting,lease;final MaterialAuthorityDependencies.Snapshot snapshot;final List<Map<String,String>> items;final String hash;final TranscriptAcquisitionBudget.Ledger budget;
  private Request(JdbcMaterialAuthority issuer,Actor actor,String meeting,String lease,MaterialAuthorityDependencies.Snapshot snapshot){
   this.issuer=issuer;this.actor=actor;this.meeting=meeting;this.lease=lease;this.snapshot=snapshot;budget=issuer.authority.publicationBudget();
   var values=new ArrayList<Map<String,String>>();for(var item:snapshot.items()){if(!issuer.client.identities().get("providerId").equals(item.provider()))throw new AuthorityUnavailableException();values.add(Map.of("libraryId",item.library(),"documentId",item.document(),"version",item.version()));}items=List.copyOf(values);hash=KnowledgeMaterialClaims.itemsHash(items);
  }
  void require(ControlledKnowledgeClient client){if(issuer.client!=client||issuer.closed||items.isEmpty()||items.size()>64)throw new IllegalArgumentException("Foreign material authority request");budget.check();}
 }
 private MaterialAuthorityDependencies.Snapshot local(Actor actor,String meeting){access.requireLocalForPreparation(actor,meeting);return MaterialAuthorityDependencies.capture(j,meeting);}
 public void prepare(Actor actor,String meeting){refresh(actor,meeting,true);}
 private void refresh(Actor actor,String meeting,boolean demand){
  JdbcMinutesFactsReader.independent();if(closed)throw new AuthorityUnavailableException();ama.contract.MinutesContracts.uuid(meeting);
  // Check the ACL before any network traffic, including publication observation.
  authority.transactionRunner().required(()->{access.requireLocalForPreparation(actor,meeting);return null;});
  afterLocalRead(actor,meeting,demand);
 }
 private record Dependencies(boolean published,boolean knowledge){}
 private void afterLocalRead(Actor actor,String meeting,boolean demand){
  JdbcMinutesFactsReader.independent();if(closed)throw new AuthorityUnavailableException();
  // This hint only schedules external work. The content read still verifies the current
  // root and all of its permission dependencies inside its own transaction.
  var rows=j.query("SELECT m.ever_published,EXISTS(SELECT 1 FROM material_reference r WHERE r.meeting_id=m.id AND r.kind='KNOWLEDGE') FROM meeting m WHERE m.id=?",
    (row,n)->new Dependencies(row.getBoolean(1),row.getBoolean(2)),meeting);
  if(rows.isEmpty())throw new AccessDeniedException();
  var dependencies=rows.getFirst();
  if(governance!=null&&dependencies.published())governance.ensureFresh(meeting);
  if(!dependencies.knowledge())return;
  Request request=claim(actor,meeting,demand);
  if(request==null)return;
  try{
   var proof=client.materialAuthority(request);
   authority.transactionRunner().required(()->{
    var current=local(actor,meeting);proof.require(request);if(!current.fingerprint().equals(request.snapshot.fingerprint()))throw new AuthorityUnavailableException();
    if(j.update("UPDATE material_authority_cache SET state='ACTIVE',proof_hash=?,verified_until=?,next_check_at=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 SECOND),last_error=NULL WHERE meeting_id=? AND actor_id=? AND dependency_hash=? AND pins_hash=? AND lease_owner=?",proof.digest,java.sql.Timestamp.from(proof.until),meeting,actor.enterpriseId(),current.fingerprint(),pins,request.lease)!=1)throw new AuthorityUnavailableException();return null;
   });
  }catch(ControlledKnowledgeClient.ProviderFailure refused){
   if(Set.of(401,403,404,409).contains(refused.status)){
    j.update("UPDATE material_authority_cache SET state='DENIED',proof_hash=NULL,verified_until=NULL,last_error='MATERIAL_AUTHORITY_DENIED' WHERE meeting_id=? AND actor_id=? AND dependency_hash=? AND pins_hash=? AND lease_owner=?",meeting,actor.enterpriseId(),request.snapshot.fingerprint(),pins,request.lease);throw new AccessDeniedException();
   }throw new AuthorityUnavailableException(refused);
  }catch(AccessDeniedException|AuthorityUnavailableException denied){throw denied;}
  catch(Exception unavailable){if(unavailable instanceof InterruptedException)Thread.currentThread().interrupt();throw new AuthorityUnavailableException(unavailable);}
  finally{j.update("UPDATE material_authority_cache SET lease_owner=NULL,lease_until=NULL,next_check_at=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 SECOND) WHERE meeting_id=? AND actor_id=? AND lease_owner=?",meeting,actor.enterpriseId(),request.lease);}
 }
 private static final class RefreshInProgress extends RuntimeException {}
 private Request claim(Actor actor,String meeting,boolean demand){
  long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
  while(true){
   if(closed||Thread.currentThread().isInterrupted())throw new AuthorityUnavailableException();
   try{
    return authority.transactionRunner().required(()->{
   var snapshot=local(actor,meeting);if(snapshot.items().isEmpty())return null;
   for(var item:snapshot.items())if(!client.identities().get("providerId").equals(item.provider()))throw new AuthorityUnavailableException();
   var rows=j.queryForList("SELECT dependency_hash,pins_hash,state,verified_until>DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 3 SECOND) reusable FROM material_authority_cache WHERE meeting_id=? AND actor_id=? FOR UPDATE",meeting,actor.enterpriseId());
   if(rows.isEmpty())j.update("INSERT INTO material_authority_cache(meeting_id,actor_id,dependency_hash,pins_hash,state,demand_until,next_check_at) VALUES(?,?,?,?,'UNKNOWN',DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 120 SECOND),UTC_TIMESTAMP(6))",meeting,actor.enterpriseId(),snapshot.fingerprint(),pins);
   else {var old=rows.getFirst();if(!snapshot.fingerprint().equals(old.get("dependency_hash"))||!pins.equals(old.get("pins_hash")))j.update("UPDATE material_authority_cache SET dependency_hash=?,pins_hash=?,state='UNKNOWN',proof_hash=NULL,verified_until=NULL,lease_owner=NULL,lease_until=NULL WHERE meeting_id=? AND actor_id=?",snapshot.fingerprint(),pins,meeting,actor.enterpriseId());}
   if(demand)j.update("UPDATE material_authority_cache SET demand_until=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 120 SECOND) WHERE meeting_id=? AND actor_id=?",meeting,actor.enterpriseId());
   if(!rows.isEmpty()){var old=rows.getFirst();if(snapshot.fingerprint().equals(old.get("dependency_hash"))&&pins.equals(old.get("pins_hash"))&&"ACTIVE".equals(old.get("state"))&&old.get("reusable") instanceof Number n&&n.intValue()==1)return null;}
   String lease=UUID.randomUUID().toString();int acquired=j.update("UPDATE material_authority_cache SET lease_owner=?,lease_until=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 30 SECOND) WHERE meeting_id=? AND actor_id=? AND (lease_until IS NULL OR lease_until<UTC_TIMESTAMP(6))",lease,meeting,actor.enterpriseId());
   if(acquired!=1)throw new RefreshInProgress();return new Request(this,actor,meeting,lease,snapshot);
  });
   }catch(RefreshInProgress busy){
    // Never retain the identity/root/cache locks while the I/O lease holder installs its proof.
    // This wait grants no access and never extends the signed assertion's absolute deadline.
    if(System.nanoTime()>=deadline)throw new AuthorityUnavailableException();
    try{Thread.sleep(50);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new AuthorityUnavailableException(interrupted);}
   }
  }
 }
 static final class PreparedChange {
  private final JdbcMaterialAuthority issuer;private final Request request;private final ControlledKnowledgeClient.MaterialProof proof;
  private PreparedChange(JdbcMaterialAuthority issuer,Request request,ControlledKnowledgeClient.MaterialProof proof){this.issuer=issuer;this.request=request;this.proof=proof;}
 }
 PreparedChange prepareChange(Actor actor,String meeting,Collection<MaterialAuthorityDependencies.Item> added)throws java.io.IOException {
  JdbcMinutesFactsReader.independent();var snapshot=authority.transactionRunner().required(()->{access.requireLocalForPreparation(actor,meeting);return MaterialAuthorityDependencies.captureAdditional(j,meeting,added);});
  if(snapshot.items().isEmpty())return null;var request=new Request(this,actor,meeting,null,snapshot);return new PreparedChange(this,request,client.materialAuthority(request));
 }
 void installChange(PreparedChange change,Actor actor,String meeting,ama.access.MeetingAccess.Locked gate){
  if(change==null)return;if(change.issuer!=this||!change.request.actor.equals(actor)||!change.request.meeting.equals(meeting))throw new IllegalArgumentException("Foreign material change proof");
  access.requireLocalRead(gate);change.proof.require(change.request);var current=MaterialAuthorityDependencies.capture(j,meeting);if(!current.fingerprint().equals(change.request.snapshot.fingerprint()))throw new AuthorityUnavailableException();
  j.update("INSERT INTO material_authority_cache(meeting_id,actor_id,dependency_hash,pins_hash,proof_hash,state,verified_until,demand_until,next_check_at) VALUES(?,?,?,?,?,'ACTIVE',?,DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 120 SECOND),UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE dependency_hash=VALUES(dependency_hash),pins_hash=VALUES(pins_hash),proof_hash=VALUES(proof_hash),state=VALUES(state),verified_until=VALUES(verified_until),demand_until=VALUES(demand_until),next_check_at=VALUES(next_check_at),lease_owner=NULL,lease_until=NULL,last_error=NULL",meeting,actor.enterpriseId(),current.fingerprint(),pins,change.proof.digest,java.sql.Timestamp.from(change.proof.until));
 }
 public void start(){if(started.compareAndSet(false,true))for(int n=0;n<4;n++)scheduler.scheduleWithFixedDelay(this::pollSafely,n*100L,250,TimeUnit.MILLISECONDS);}
 private void pollSafely(){try{pollOnce();}catch(RuntimeException ignored){/* Gates remain closed after the original proof expires. */}}
 public void pollOnce(){
  JdbcMinutesFactsReader.independent();if(closed)return;
  var rows=j.queryForList("SELECT c.meeting_id,c.actor_id FROM material_authority_cache c JOIN meeting m ON m.id=c.meeting_id AND m.lifecycle='ACTIVE' AND m.governance_state='ACTIVE' WHERE c.next_check_at<=UTC_TIMESTAMP(6) AND (c.lease_until IS NULL OR c.lease_until<UTC_TIMESTAMP(6)) AND (c.state<>'ACTIVE' OR c.verified_until IS NULL OR c.verified_until<=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 3 SECOND)) AND (c.demand_until>UTC_TIMESTAMP(6) OR EXISTS(SELECT 1 FROM durable_task t WHERE t.subject_kind='MEETING' AND t.subject_id=c.meeting_id AND t.state IN ('READY','RUNNING','RECONCILING'))) ORDER BY c.next_check_at,c.meeting_id,c.actor_id LIMIT 1");
  if(rows.isEmpty())return;var row=rows.getFirst();String meeting=(String)row.get("meeting_id"),actor=(String)row.get("actor_id");
  // One fast atomic scheduling claim; the separate I/O lease is acquired with the local ACL/root.
  if(j.update("UPDATE material_authority_cache SET next_check_at=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 2 SECOND) WHERE meeting_id=? AND actor_id=? AND next_check_at<=UTC_TIMESTAMP(6)",meeting,actor)!=1)return;
  try{refresh(new Actor(actor,Status.ACTIVE,Set.of(Role.USER)),meeting,false);}catch(RuntimeException unavailable){j.update("UPDATE material_authority_cache SET last_error='MATERIAL_AUTHORITY_UNAVAILABLE' WHERE meeting_id=? AND actor_id=?",meeting,actor);}
 }
 public void close(){closed=true;scheduler.shutdownNow();try{scheduler.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}}
}
