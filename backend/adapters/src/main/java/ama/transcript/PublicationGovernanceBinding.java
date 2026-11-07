package ama.transcript;

import ama.contract.IdentityContracts.*;
import ama.meeting.JdbcMeetingAccess;
import java.util.*;
import java.util.function.Supplier;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Original successful publication metadata only, available for technical reconciliation even
 * after ordinary READ is revoked. Never transfers minutes, material, document URL or payload. */
final class PublicationGovernanceBinding {
 private final JdbcTranscriptAuthority authority;private final ControlledKnowledgeClient client;private final TranscriptAcquisitionBudget limits;
 PublicationGovernanceBinding(JdbcTranscriptAuthority authority,ControlledKnowledgeClient client,TranscriptAcquisitionBudget limits){this.authority=Objects.requireNonNull(authority);this.client=Objects.requireNonNull(client);this.limits=Objects.requireNonNull(limits);}
 final class Prepared {
  private final String meeting,publication,owner;private final PublicationGraph.Plan plan;private final PublicationDocumentRead.Snapshot snapshot;private final long expires=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
  private Prepared(String meeting,String publication,String owner,PublicationGraph.Plan plan,PublicationDocumentRead.Snapshot snapshot){this.meeting=meeting;this.publication=publication;this.owner=owner;this.plan=plan;this.snapshot=snapshot;}
  String meetingId(){return meeting;}String publicationId(){return publication;}String documentId(){return (String)snapshot.mapping().get("external_document_id");}String actorId(){return owner;}
  long revision(){return n(snapshot.root(),"revision");}long accessRevision(){return n(snapshot.root(),"access_revision");}long cancelEpoch(){return n(snapshot.root(),"cancel_epoch");}
  String governanceState(){return (String)snapshot.root().get("governance_state");}
  String libraryId(){return (String)snapshot.publication().get("library_id");}
  TranscriptAcquisitionBudget.Ledger budget(){return plan.budget();}
  private PublicationGovernanceBinding issuer(){return PublicationGovernanceBinding.this;}
  void require(ControlledKnowledgeClient expected){if(client!=expected||System.nanoTime()-expires>=0)throw new IllegalArgumentException("Foreign or expired publication governance binding");}
 }
 Prepared prepare(String meeting,String publication){
  JdbcMinutesFactsReader.independent();uuid(meeting);uuid(publication);var budget=limits.ledger(authority.readBudget(),authority.canonicalBudget(),()->Thread.currentThread().isInterrupted());
  var owners=j().queryForList("SELECT owner_id FROM meeting WHERE id=?",String.class,meeting);require(owners.size()==1,PROVENANCE_UNAVAILABLE);String owner=owners.getFirst();
  return PublicationGraph.retryObservation(()->{var prepared=acquire(meeting,publication,owner,budget);held(prepared,prepared,()->null);return prepared;});
 }
 private Prepared acquire(String meeting,String publication,String owner,TranscriptAcquisitionBudget.Ledger budget){
  var plan=PublicationGraph.discover(authority.dataSource(),meeting,budget);var captured=capture(meeting,publication,owner,plan);var manifest=PublicationDocumentRead.verify(client,captured,plan);require(owner.equals(manifest.get("requesterId")),PROVENANCE_UNAVAILABLE);
  return new Prepared(meeting,publication,owner,plan,captured);
 }
 /** Supplies only current metadata locks. Caller must separately establish command ownership
  * or a verified provider observation before changing governance; no content grant is returned. */
 <T>T withinCurrent(Prepared prepared,Supplier<T> work){
  if(prepared==null||prepared.issuer()!=this)throw new IllegalArgumentException("Foreign governance binding");prepared.require(client);
  var current=new Prepared[]{prepared};
  return PublicationGraph.retryObservation(()->{
   // The original lifetime, budget and success association remain binding, even
   // when an unrelated export heartbeat requires a new complete metadata plan.
   prepared.require(client);
   if(current[0]==null)current[0]=acquire(prepared.meeting,prepared.publication,prepared.owner,prepared.plan.budget());
   try{return held(prepared,current[0],work);}catch(PublicationGraph.ObservationChanged changed){current[0]=null;throw changed;}
  });
 }
 private <T>T held(Prepared original,Prepared current,Supplier<T> work){
  return authority.transactionRunner().required(()->{
   var fresh=capture(original.meeting,original.publication,original.owner,current.plan);
   require(fresh.equals(current.snapshot)&&fresh.equals(original.snapshot),PROVENANCE_UNAVAILABLE);
   // Once the caller starts, never replay its command or its effects.
   return invokeWork(work);
  });
 }
 static <T>T invokeWork(Supplier<T> work){try{return work.get();}catch(PublicationGraph.ObservationChanged changed){throw fail(PROVENANCE_UNAVAILABLE);}}
 private org.springframework.jdbc.core.JdbcTemplate j(){var value=new org.springframework.jdbc.core.JdbcTemplate(authority.dataSource());value.setQueryTimeout(2);return value;}
  /** Only a rolled-back metadata acquisition may restart. Caller work is outside this boundary. */
  static <T>T captureMetadata(Supplier<T> capture){try{return capture.get();}catch(org.springframework.dao.CannotAcquireLockException contention){throw new PublicationGraph.ObservationChanged();}}
 private PublicationDocumentRead.Snapshot capture(String meeting,String publication,String owner,PublicationGraph.Plan plan){
   return captureMetadata(()->authority.transactionRunner().required(()->{
   var access=(JdbcMeetingAccess)authority.meetingAccess();var actor=new Actor(owner,Status.ACTIVE,Set.of(ama.contract.IdentityContracts.Role.USER));
   // Actor is an identifier here: actual ACTIVE/DISABLED status comes from locked fact rows.
   // No authorizeFactRoot/READ/EDIT conversion is performed for technical reconciliation.
   var ids=access.tryLockFactIdentities(actor,List.of(),false).orElseThrow();access.lockFactRoot(ids,meeting,false).orElseThrow();var budget=plan.budget();budget.check();
   var root=one(plan,"SELECT id,owner_id,revision,access_revision,cancel_epoch,lifecycle,governance_state,stage,ever_published,final_confirmation_id,info_revision_id,roster_revision_id,transcript_confirmation_id,minutes_revision_id,material_snapshot_id,element_set_id,template_revision_id,exclusion_revision_id FROM meeting WHERE id=?",meeting);require(owner.equals(root.get("owner_id")),PROVENANCE_UNAVAILABLE);
   var pub=one(plan,"SELECT id,meeting_id,confirmation_id,business_key,library_id,folder_id,state,revision,published_at FROM publication WHERE id=? AND meeting_id=? FOR UPDATE",publication,meeting);
   var confirmation=one(plan,"SELECT id,meeting_id,meeting_revision,info_revision_id,roster_revision_id,transcript_confirmation_id,minutes_revision_id,material_snapshot_id,element_set_id,template_revision_id,exclusion_revision_id,source_manifest_hash,input_fingerprint,confirmed_by,confirmed_at FROM final_confirmation WHERE id=? AND meeting_id=? FOR UPDATE",pub.get("confirmation_id"),meeting);
   PublicationGraph.lockObservation(authority.dataSource(),plan,true);PublicationGraph.lockObservation(authority.dataSource(),plan,false);var selected=PublicationDocumentRead.recordedGovernance(root,pub,confirmation,plan.rows());var submission=selected.submission();
   long bytes=n(submission,"request_manifest_bytes"),points=n(submission,"request_manifest_scalars"),units=n(submission,"request_manifest_utf16");require(bytes>0&&bytes<=Math.min(65536,authority.readBudget().maxManifestBytes())&&points>0&&points<=bytes&&units>=points&&units<=bytes,BUDGET_EXCEEDED);budget.text(bytes,points,units,2,false);
   var originals=j().queryForList("SELECT request_manifest FROM publication_submission WHERE task_id=? AND publication_id=? AND OCTET_LENGTH(request_manifest)=? AND CHAR_LENGTH(request_manifest)=? AND OCTET_LENGTH(CONVERT(request_manifest USING utf16))/2=?",submission.get("task_id"),publication,bytes,points,units);require(originals.size()==1&&originals.getFirst().get("request_manifest") instanceof String,PROVENANCE_UNAVAILABLE);String manifest=(String)originals.getFirst().get("request_manifest");
   var stored=PublicationExecutionRead.read(j(),budget,authority.readBudget().maxManifestBytes(),(String)submission.get("task_id"),(String)selected.operation().get("id"),(String)selected.execution().get("id"),n(selected.execution(),"execution_no"));require(manifest.equals(stored.manifest()),PROVENANCE_UNAVAILABLE);
   return new PublicationDocumentRead.Snapshot(root,pub,confirmation,selected.mapping(),submission,selected.task(),selected.operation(),selected.execution(),selected.attempt(),manifest,null);
   }));
 }
 private Map<String,Object> one(PublicationGraph.Plan plan,String sql,Object...args){
  var budget=plan.budget();budget.check();budget.admitCapture(65536,16384,32768,2);var rows=j().queryForList(sql,args);require(rows.size()==1,PROVENANCE_UNAVAILABLE);budget.row(rows.getFirst().size());var result=new LinkedHashMap<String,Object>();
  for(var entry:rows.getFirst().entrySet()){Object value=JdbcPublicationQueries.normalizeMetadata(entry.getValue());if(value instanceof String text){require(text.codePointCount(0,text.length())<=512,BUDGET_EXCEEDED);budget.capture(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,text.codePointCount(0,text.length()),text.length(),3);}result.put(entry.getKey(),value);}return Collections.unmodifiableMap(result);
 }
 private static long n(Map<String,Object> value,String field){require(value.get(field) instanceof Number,PROVENANCE_UNAVAILABLE);return ((Number)value.get(field)).longValue();}
}
