package ama.transcript;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.Actor;
import ama.contract.MinutesContracts.*;
import ama.storage.FileRegistry;
import ama.task.ControlledSpeechProfile;
import ama.task.SpeechCheckpointCodec;
import java.nio.ByteBuffer;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Fixed private collaborator of the saved-transcript service. Detached rows cannot grant a write. */
final class SpeechReviewRepository {
 record Proposal(String id,String segment,String file,String clip,String engine,String recognitionSha,String enhancementSha,String before,String after) {}
 static final class Plan {
  final SpeechReviewRepository issuer;final SavedTranscriptGraph graph;final List<Proposal> proposals;final boolean available;
  private Plan(SpeechReviewRepository issuer,SavedTranscriptGraph graph,List<Proposal> proposals,boolean available){this.issuer=issuer;this.graph=graph;this.proposals=List.copyOf(proposals);this.available=available;}
 }
 private final JdbcTranscriptAuthority authority;private final JdbcTemplate j;
 SpeechReviewRepository(JdbcTranscriptAuthority authority){this.authority=Objects.requireNonNull(authority);j=new JdbcTemplate(authority.dataSource());j.setQueryTimeout(5);}
 SpeechReviewFacts capture(Object scope,FileRegistry.LockedMeeting gate,Actor actor,JdbcTranscriptAuthority.SavedPurpose purpose,SavedTranscriptGraph graph,TranscriptAcquisitionBudget.Ledger budget,boolean models){
  authority.requireSavedScope(scope,gate,actor,graph.meeting(),purpose);
  authority.savedAccess().require(authority.fileRegistry().access(gate),MeetingAccess.Action.EDIT);
  require(actor.enterpriseId().equals(graph.root().get("owner_id")),CAPABILITY_UNAVAILABLE);
  if(graph.selected()==null)return SpeechReviewFacts.EMPTY;
  var raw=raw(graph);String rawId=raw.string("id"),input=graph.input().string("id");
  // Switching to another RAW retires the former branch's suggestions. Declare
  // those exact pending IDs in S/F without transferring unrelated original text.
  for(int n=0;n<61;n++)budget.row(5);budget.capture(61*256L,61*256L,61*256L,2);
  var otherPending=freeze(j.queryForList("SELECT id,raw_revision_id,segment_id,input_id,state FROM speech_review_candidate WHERE meeting_id=? AND raw_revision_id<>? AND state='PENDING' ORDER BY id LIMIT 61 FOR UPDATE",graph.meeting(),rawId));require(otherPending.size()<=60,BUDGET_EXCEEDED);
  var size=j.queryForObject("SELECT COUNT(*),COALESCE(SUM(OCTET_LENGTH(before_text)+OCTET_LENGTH(after_text)),0),COALESCE(SUM(CHAR_LENGTH(before_text)+CHAR_LENGTH(after_text)),0),COALESCE(SUM(OCTET_LENGTH(CONVERT(before_text USING utf16))+OCTET_LENGTH(CONVERT(after_text USING utf16))),0) DIV 2 FROM (SELECT before_text,after_text FROM speech_review_candidate WHERE meeting_id=? AND raw_revision_id=? ORDER BY id LIMIT 61) r",(r,n)->new long[]{r.getLong(1),r.getLong(2),r.getLong(3),r.getLong(4)},graph.meeting(),rawId);
  require(size!=null&&size[0]<=60,BUDGET_EXCEEDED);for(long n=0;n<size[0];n++){budget.row(30);budget.text(2048,2048,2048,4,false);}
  var candidateMetadata=j.queryForList("SELECT id,meeting_id,raw_revision_id,created_source_revision_id,input_id,segment_id,file_id,clip_key,engine_sha256,recognition_sha256,enhancement_sha256,state,decision_revision_id,undo_revision_id,created_by,created_at,decided_at,OCTET_LENGTH(before_text) before_bytes,OCTET_LENGTH(after_text) after_bytes,CHAR_LENGTH(before_text) before_points,CHAR_LENGTH(after_text) after_points,OCTET_LENGTH(CONVERT(before_text USING utf16)) DIV 2 before_units,OCTET_LENGTH(CONVERT(after_text USING utf16)) DIV 2 after_units FROM speech_review_candidate WHERE meeting_id=? AND raw_revision_id=? ORDER BY id LIMIT 61 FOR UPDATE",graph.meeting(),rawId);
  require(candidateMetadata.size()==size[0],PROVENANCE_UNAVAILABLE);long currentBytes=0,currentPoints=0,currentUnits=0;
  for(var row:candidateMetadata){currentBytes+=number(row,"before_bytes")+number(row,"after_bytes");currentPoints+=number(row,"before_points")+number(row,"after_points");currentUnits+=number(row,"before_units")+number(row,"after_units");}
  require(currentBytes<=size[1]&&currentPoints<=size[2]&&currentUnits<=size[3],PROVENANCE_UNAVAILABLE);
  var mutableCandidates=new ArrayList<Map<String,Object>>();
  for(var row:candidateMetadata){
   budget.text(number(row,"before_bytes"),number(row,"before_points"),number(row,"before_units"),4,true);budget.text(number(row,"after_bytes"),number(row,"after_points"),number(row,"after_units"),4,true);
   var bodies=j.queryForList("SELECT before_text,after_text FROM speech_review_candidate WHERE id=? AND OCTET_LENGTH(before_text)=? AND OCTET_LENGTH(after_text)=? FOR UPDATE",row.get("id"),number(row,"before_bytes"),number(row,"after_bytes"));require(bodies.size()==1,PROVENANCE_UNAVAILABLE);
   var captured=new LinkedHashMap<>(row);captured.putAll(bodies.getFirst());mutableCandidates.add(captured);
  }
  var candidates=freeze(mutableCandidates);
  if(!Set.of(ControlledSpeechProfile.PARSER,RecordingTranscriptProfile.PARSER).contains(graph.input().string("parser_version"))){require(candidates.isEmpty(),PROVENANCE_UNAVAILABLE);return new SpeechReviewFacts(candidates,List.of(),List.of(),models,otherPending);}
  if(!models)return new SpeechReviewFacts(candidates,List.of(),List.of(),false,otherPending);
  for(int n=0;n<61;n++)budget.row(18);budget.capture(61*2048L,61*2048L,61*2048L,2);
  var audio=freeze(j.queryForList("SELECT id,meeting_id,input_id,task_id,file_id,clip_key,source_sha256,pcm_sha256,engine_sha256,source_samples,start_sample,sample_count,wav_sha256,byte_count,state FROM speech_pcm_artifact WHERE input_id=? ORDER BY file_id,start_sample LIMIT 61 FOR UPDATE",input));
  require(audio.size()<=60,BUDGET_EXCEEDED);
  for(int n=0;n<181;n++)budget.row(3);budget.capture(181*96L,181*96L,181*96L,2);
  var lengths=j.queryForList("SELECT clip_key,stage,OCTET_LENGTH(payload) bytes FROM speech_checkpoint WHERE input_id=? AND stage IN ('ASR','ENHANCEMENT','CORRECTION') ORDER BY file_id,start_sample,stage LIMIT 181",input);
  require(lengths.size()<=180&&lengths.size()>=audio.size()*2&&lengths.size()<=audio.size()*3,PROVENANCE_UNAVAILABLE);long payloadBytes=0;
  for(var row:lengths){long bytes=number(row,"bytes");require(bytes>0&&bytes<=1048576,BUDGET_EXCEEDED);payloadBytes=Math.addExact(payloadBytes,bytes);}
  for(int n=0;n<lengths.size();n++)budget.row(13);budget.capture(payloadBytes+lengths.size()*2048L,payloadBytes+lengths.size()*2048L,payloadBytes+lengths.size()*2048L,4);
  var stages=new ArrayList<SpeechReviewFacts.Stage>();
  for(var length:lengths){
   // Recheck each admitted length in SQL before its bytes enter JDBC. A larger
   // concurrent/corrupt payload cannot cross the already reserved copy budget.
   var values=j.queryForList("SELECT input_id,clip_key,stage,file_id,source_sha256,pcm_sha256,engine_sha256,start_sample,sample_count,task_id,payload_sha256,IF(OCTET_LENGTH(payload)=?,payload,NULL) payload FROM speech_checkpoint WHERE input_id=? AND clip_key=? AND stage=? FOR UPDATE",number(length,"bytes"),input,length.get("clip_key"),length.get("stage"));
   require(values.size()==1,PROVENANCE_UNAVAILABLE);var row=new LinkedHashMap<>(values.getFirst());Object blob=row.remove("payload");require(blob instanceof byte[],PROVENANCE_UNAVAILABLE);byte[] bytes=(byte[])blob;
   require(bytes.length==number(length,"bytes")&&Objects.equals(row.get("clip_key"),length.get("clip_key"))&&Objects.equals(row.get("stage"),length.get("stage")),PROVENANCE_UNAVAILABLE);
   stages.add(new SpeechReviewFacts.Stage(row,ByteBuffer.wrap(bytes)));
  }
  return new SpeechReviewFacts(candidates,audio,stages,true,otherPending);
 }
 static FullRow raw(SavedTranscriptGraph graph){var rows=graph.rows().stream().filter(r->r.table()==Table.TRANSCRIPT_REVISION&&r.string("kind").equals("RAW")).toList();require(rows.size()==1,PROVENANCE_UNAVAILABLE);return rows.getFirst();}
 /** Parsing and digesting model originals is outside the owner root transaction. */
 Plan plan(SavedTranscriptGraph graph,String engine){
  if(!new ama.governance.JdbcGovernanceRuntime(j.getDataSource()).corpusCurrent(graph.input().string("id")))throw new ama.contract.TranscriptContracts.Failure(409,"CORRECTION_STALE");
  require(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive(),CAPABILITY_UNAVAILABLE);
  var facts=graph.review();require(facts.modelsCaptured(),PROVENANCE_UNAVAILABLE);var raw=raw(graph);var rawSegments=graph.segments(raw.string("id"));var current=graph.segments(graph.selected());
  var stages=new HashMap<String,SpeechReviewFacts.Stage>();for(var stage:facts.stages())require(stages.put(text(stage.metadata(),"clip_key")+":"+text(stage.metadata(),"stage"),stage)==null,PROVENANCE_UNAVAILABLE);
  var members=new HashMap<String,String>();for(var member:graph.rows())if(member.table()==Table.INPUT_BATCH_FILE)require(members.put(member.string("file_id"),member.string("sha256"))==null,PROVENANCE_UNAVAILABLE);
  var proposals=new ArrayList<Proposal>();var clips=new HashSet<String>();int expectedStages=0;
  for(var audio:facts.audio()){
   String clip=text(audio,"clip_key"),file=text(audio,"file_id");long start=number(audio,"start_sample"),samples=number(audio,"sample_count"),sourceSamples=number(audio,"source_samples");
   require(clips.add(clip)&&graph.meeting().equals(audio.get("meeting_id"))&&graph.input().string("id").equals(audio.get("input_id"))&&Objects.equals(graph.task().get("id"),audio.get("task_id"))&&Objects.equals(engine,audio.get("engine_sha256"))&&Objects.equals(members.get(file),audio.get("source_sha256"))&&"READY".equals(audio.get("state")),PROVENANCE_UNAVAILABLE);
   require(start>=0&&samples>0&&samples<=1920000&&sourceSamples>0&&sourceSamples<=115200000&&start<=sourceSamples-samples&&start%1920000==0&&number(audio,"byte_count")==44+samples*2,PROVENANCE_UNAVAILABLE);
   var recognized=stage(stages,audio,"ASR");var enhanced=stage(stages,audio,"ENHANCEMENT");
   var recognition=(SpeechPipeline.Recognition)SpeechCheckpointCodec.decode(SpeechPipeline.Stage.ASR,recognized.bytes());var enhancement=(SpeechPipeline.Enhancement)SpeechCheckpointCodec.decode(SpeechPipeline.Stage.ENHANCEMENT,enhanced.bytes());
   String before=recognition.result().text(),after=enhancement.text();scalars(before);scalars(after);require(before.length()<=65536&&after.length()<=65536,PROVENANCE_UNAVAILABLE);
   expectedStages+=2;
   if(stages.containsKey(clip+":CORRECTION")){
    var corrected=stage(stages,audio,"CORRECTION");var correction=(SpeechPipeline.Correction)SpeechCheckpointCodec.decode(SpeechPipeline.Stage.CORRECTION,corrected.bytes());
    require(correction.result().sourceText().equals(enhancement.text())&&correction.result().modelVersion().equals(ControlledSpeechProfile.CORRECTION_MODEL),PROVENANCE_UNAVAILABLE);
    after=correction.result().correctedText();enhanced=corrected;expectedStages++;
   }
   var originals=rawSegments.stream().filter(s->file.equals(s.string("source_file_id"))&&s.number("source_paragraph")==start/1920000+1).toList();
   if(before.isBlank()){require(after.isBlank()&&originals.isEmpty()&&facts.candidates().stream().noneMatch(c->clip.equals(c.get("clip_key"))),PROVENANCE_UNAVAILABLE);continue;}
   require(originals.size()==1,PROVENANCE_UNAVAILABLE);var original=originals.getFirst();String segment=original.string("segment_id");
   require(before.equals(original.string("body"))&&Objects.equals(original.nullableNumber("start_ms"),start/16)&&Objects.equals(original.nullableNumber("end_ms"),(start+samples+15)/16),PROVENANCE_UNAVAILABLE);
   var saved=current.stream().filter(s->segment.equals(s.string("segment_id"))).toList();require(saved.size()==1,PROVENANCE_UNAVAILABLE);
   var existing=facts.candidates().stream().filter(c->segment.equals(c.get("segment_id"))).toList();require(existing.size()<=1,PROVENANCE_UNAVAILABLE);
   if(!existing.isEmpty()){
    var candidate=existing.getFirst();require(Objects.equals(candidate.get("engine_sha256"),engine)&&Objects.equals(candidate.get("clip_key"),clip)&&Objects.equals(candidate.get("file_id"),file)&&Objects.equals(candidate.get("before_text"),before)&&Objects.equals(candidate.get("after_text"),after)&&Objects.equals(candidate.get("recognition_sha256"),recognized.metadata().get("payload_sha256"))&&Objects.equals(candidate.get("enhancement_sha256"),enhanced.metadata().get("payload_sha256")),PROVENANCE_UNAVAILABLE);continue;
   }
   if(before.equals(saved.getFirst().string("body"))&&!before.equals(after))proposals.add(new Proposal(UUID.randomUUID().toString(),segment,file,clip,engine,text(recognized.metadata(),"payload_sha256"),text(enhanced.metadata(),"payload_sha256"),before,after));
  }
  require(facts.stages().size()==expectedStages&&(facts.candidates().isEmpty()||!facts.audio().isEmpty()),PROVENANCE_UNAVAILABLE);
  return new Plan(this,graph,proposals,!facts.audio().isEmpty());
 }
 private static SpeechReviewFacts.Stage stage(Map<String,SpeechReviewFacts.Stage> stages,Map<String,Object> audio,String kind){
  var stage=stages.get(text(audio,"clip_key")+":"+kind);require(stage!=null,PROVENANCE_UNAVAILABLE);var row=stage.metadata();
  for(String key:List.of("input_id","clip_key","file_id","source_sha256","pcm_sha256","engine_sha256","start_sample","sample_count","task_id"))require(Objects.equals(row.get(key),audio.get(key)),PROVENANCE_UNAVAILABLE);
  require(digest(stage.bytes()).equals(row.get("payload_sha256")),PROVENANCE_UNAVAILABLE);return stage;
 }
 private static String digest(byte[] bytes){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
 void validateExisting(SavedTranscriptGraph graph,String engine){
  var raw=raw(graph);var segments=graph.segments(raw.string("id"));var seen=new HashSet<String>();
  for(var row:graph.review().candidates()){
   String id=text(row,"id"),segment=text(row,"segment_id");uuid(id);require(seen.add(segment),PROVENANCE_UNAVAILABLE);
   var matches=segments.stream().filter(s->segment.equals(s.string("segment_id"))).toList();require(matches.size()==1,PROVENANCE_UNAVAILABLE);var original=matches.getFirst();
   require(graph.meeting().equals(row.get("meeting_id"))&&raw.string("id").equals(row.get("raw_revision_id"))&&graph.input().string("id").equals(row.get("input_id"))&&original.string("source_file_id").equals(row.get("file_id"))&&Objects.equals(engine,row.get("engine_sha256"))&&original.string("body").equals(row.get("before_text")),PROVENANCE_UNAVAILABLE);
   String before=text(row,"before_text"),after=text(row,"after_text");scalars(before);scalars(after);require(before.length()<=65536&&!after.isBlank()&&after.length()<=65536&&!before.equals(after)&&Set.of("PENDING","ACCEPTED","REJECTED","UNDONE","STALE").contains(row.get("state")),PROVENANCE_UNAVAILABLE);
   for(String key:List.of("clip_key","engine_sha256","recognition_sha256","enhancement_sha256"))hash(text(row,key));
  }
 }
 int pending(SavedTranscriptGraph graph){return Math.toIntExact(graph.review().candidates().stream().filter(c->"PENDING".equals(c.get("state"))).count()+graph.review().otherPending().size());}
 List<Map<String,Object>> decisions(SavedTranscriptGraph graph,ama.contract.TranscriptContracts.Command command){
  if(command.correction()!=null&&command.correction().decision()==ama.contract.TranscriptContracts.CorrectionDecision.ACCEPT&&!new ama.governance.JdbcGovernanceRuntime(j.getDataSource()).corpusCurrent(graph.input().string("id")))throw new ama.contract.TranscriptContracts.Failure(409,"CORRECTION_STALE");
  var correction=command.correction();if(correction==null)throw new IllegalArgumentException("Original correction decision required");
  var result=new ArrayList<Map<String,Object>>();
  for(String candidateId:correction.candidateIds()){
  var rows=graph.review().candidates().stream().filter(c->candidateId.equals(c.get("id"))).toList();if(rows.size()!=1)throw new ama.contract.TranscriptContracts.Failure(404,"CORRECTION_NOT_FOUND");var row=rows.getFirst();
  if(!"PENDING".equals(row.get("state")))throw new ama.contract.TranscriptContracts.Failure(409,"CORRECTION_ALREADY_HANDLED");
  var segments=graph.segments(graph.selected()).stream().filter(s->s.string("segment_id").equals(row.get("segment_id"))).toList();require(segments.size()==1,PROVENANCE_UNAVAILABLE);
  if(correction.decision()==ama.contract.TranscriptContracts.CorrectionDecision.ACCEPT&&!segments.getFirst().string("body").equals(row.get("before_text")))throw new ama.contract.TranscriptContracts.Failure(409,"CORRECTION_STALE");
  result.add(row);
  }
  return List.copyOf(result);
 }
 List<Map<String,Object>> items(SavedTranscriptGraph graph,boolean writable){
  var current=new HashMap<String,FullRow>();for(var s:graph.segments(graph.selected()))current.put(s.string("segment_id"),s);var result=new ArrayList<Map<String,Object>>();
  Boolean corpusCurrent=null;
  for(var row:graph.review().candidates()){
   var segment=current.get(text(row,"segment_id"));require(segment!=null,PROVENANCE_UNAVAILABLE);boolean pending="PENDING".equals(row.get("state"));var item=new LinkedHashMap<String,Object>();
   item.put("id",row.get("id"));item.put("segmentId",row.get("segment_id"));item.put("source",row.get("before_text"));item.put("replacement",row.get("after_text"));item.put("status",row.get("state"));item.put("operationId",row.get("decision_revision_id"));
   item.put("range",TranscriptCorrectionRange.between(text(row,"before_text"),text(row,"after_text")));item.put("createdSourceRevisionId",row.get("created_source_revision_id"));item.put("kind","SPEECH_ENHANCEMENT");
   boolean applicable=writable&&pending&&segment.string("body").equals(row.get("before_text"));
   if(applicable&&corpusCurrent==null)corpusCurrent=new ama.governance.JdbcGovernanceRuntime(j.getDataSource()).corpusCurrent(graph.input().string("id"));
   item.put("canAccept",applicable&&Boolean.TRUE.equals(corpusCurrent));item.put("canReject",writable&&pending);item.put("canUndo",writable&&"ACCEPTED".equals(row.get("state"))&&graph.selected().equals(row.get("decision_revision_id")));result.add(Collections.unmodifiableMap(item));
  }
  return List.copyOf(result);
 }
 String createBatch(JdbcTranscriptAuthority.HeldSavedTranscript held,FileRegistry.LockedMeeting gate,Actor actor,SavedTranscriptGraph graph,Plan plan,JdbcSavedTranscriptReceipts.Identity identity,long expected){
  authority.requireSpeechReviewWrite(held,gate,actor,graph);require(plan.issuer==this&&plan.graph.equals(graph)&&plan.available,PROVENANCE_UNAVAILABLE);
  var raw=raw(graph);var ids=new ArrayList<String>();for(var c:graph.review().candidates())ids.add(text(c,"id"));
  for(var p:plan.proposals){
   require(j.update("INSERT INTO speech_review_candidate(id,meeting_id,raw_revision_id,created_source_revision_id,input_id,segment_id,file_id,clip_key,engine_sha256,recognition_sha256,enhancement_sha256,before_text,after_text,state,created_by,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'PENDING',?,UTC_TIMESTAMP(6))",p.id(),graph.meeting(),raw.string("id"),graph.selected(),graph.input().string("id"),p.segment(),p.file(),p.clip(),p.engine(),p.recognitionSha(),p.enhancementSha(),p.before(),p.after(),actor.enterpriseId())==1,PROVENANCE_UNAVAILABLE);ids.add(p.id());
  }
  require(ids.size()<=60&&new HashSet<>(ids).size()==ids.size(),BUDGET_EXCEEDED);ids.sort(String::compareTo);String batch=UUID.randomUUID().toString();String json=ids.stream().map(id->"\""+id+"\"").collect(java.util.stream.Collectors.joining(",","[","]"));
  require(j.update("INSERT INTO speech_review_batch(id,scope_sha256,request_sha256,meeting_id,source_revision_id,expected_revision,actor_id,candidate_ids,created_count,created_at) VALUES (?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6))",batch,identity.scope(),identity.request(),graph.meeting(),graph.selected(),expected,actor.enterpriseId(),json,plan.proposals.size())==1,PROVENANCE_UNAVAILABLE);return batch;
 }
 void afterRevision(JdbcTranscriptAuthority.HeldSavedTranscript held,FileRegistry.LockedMeeting gate,Actor actor,SavedTranscriptGraph graph,ama.contract.TranscriptContracts.Command command,String result,List<FullRow> written){
  authority.requireSpeechReviewWrite(held,gate,actor,graph);var bodies=new HashMap<String,String>();for(var row:written)if(row.table()==Table.TRANSCRIPT_SEGMENT){require(result.equals(row.string("revision_id"))&&bodies.put(row.string("segment_id"),row.string("body"))==null,PROVENANCE_UNAVAILABLE);}
  require(bodies.size()==graph.segments(graph.selected()).size(),PROVENANCE_UNAVAILABLE);var decided=new HashSet<String>();
  if(command.operation()==ama.contract.TranscriptContracts.Operation.CORRECTION){
   for(var row:decisions(graph,command)){String candidateId=text(row,"id");decided.add(candidateId);String state=command.correction().decision()==ama.contract.TranscriptContracts.CorrectionDecision.ACCEPT?"ACCEPTED":"REJECTED";
   require(j.update("UPDATE speech_review_candidate SET state=?,decision_revision_id=?,decided_at=UTC_TIMESTAMP(6) WHERE id=? AND meeting_id=? AND state='PENDING'",state,result,candidateId,graph.meeting())==1,PROVENANCE_UNAVAILABLE);
   }
  }
  for(var row:graph.review().candidates()){
   if("PENDING".equals(row.get("state"))&&!decided.contains(row.get("id"))&&!Objects.equals(bodies.get(text(row,"segment_id")),row.get("before_text")))stale(graph,text(row,"id"));
   if(command.operation()==ama.contract.TranscriptContracts.Operation.UNDO&&"ACCEPTED".equals(row.get("state"))&&graph.selected().equals(row.get("decision_revision_id")))require(j.update("UPDATE speech_review_candidate SET state='UNDONE',undo_revision_id=?,decided_at=UTC_TIMESTAMP(6) WHERE id=? AND meeting_id=? AND state='ACCEPTED' AND decision_revision_id=?",result,row.get("id"),graph.meeting(),graph.selected())==1,PROVENANCE_UNAVAILABLE);
  }
  for(var row:graph.review().otherPending())stale(graph,text(row,"id"));
 }
 private void stale(SavedTranscriptGraph graph,String id){require(j.update("UPDATE speech_review_candidate SET state='STALE',decided_at=UTC_TIMESTAMP(6) WHERE id=? AND meeting_id=? AND state='PENDING'",id,graph.meeting())==1,PROVENANCE_UNAVAILABLE);}
 static long number(Map<String,Object> row,String key){Object value=row.get(key);require(value instanceof Number,PROVENANCE_UNAVAILABLE);return ((Number)value).longValue();}
 static String text(Map<String,Object> row,String key){Object value=row.get(key);require(value instanceof String,PROVENANCE_UNAVAILABLE);return (String)value;}
 private static List<Map<String,Object>> freeze(List<Map<String,Object>> rows){return rows.stream().map(row->Collections.unmodifiableMap(new LinkedHashMap<>(row))).toList();}
}
