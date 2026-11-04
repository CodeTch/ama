package ama.storage;

import ama.contract.FileContracts.*;
import ama.model.HttpMaterialEnhancement;
import ama.transcript.*;
import java.io.*;
import java.net.URI;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static ama.storage.ControlledSpeechImportIT.*;

/** Actual saved RAW/working -> authorized material bytes -> sibling model -> reviewed SAVE. */
class MaterialEnhancementIT {
 @TempDir Path root;final SpeechReviewIT support=new SpeechReviewIT();
 @BeforeAll static void schema(){ControlledSpeechImportIT.schema();}
 @AfterEach void cleanup()throws Exception{support.clearScenarios();}
 JdbcMaterials materials(ControlledSpeechImportIT.Fixture f){return new JdbcMaterials(f.components.authority(),uploads(f),null,new ama.material.MaterialParser.Budget(1048576,1048576,100));}
 FileUploads uploads(ControlledSpeechImportIT.Fixture f){return new JdbcFileUploads(source,f.tx,f.access,new TranscriptFilePurposePolicy(),f.catalog,new ama.audit.JdbcAuditAdapter(source),f.outbox,1048576,f.components.authority().publicationWriteGuard());}
 void addMaterial(ControlledSpeechImportIT.Fixture f,JdbcMaterials materials,String text)throws Exception{
  byte[] body=text.getBytes(StandardCharsets.UTF_8);String hash=sha(body);var uploads=uploads(f);var accepted=uploads.begin(f.actor,f.meeting,id(),new Begin("MATERIAL_SOURCE","背景术语.txt",body.length,hash),id());uploads.part(f.actor,accepted.uploadId(),1,new ByteArrayInputStream(body),body.length,hash,id());String file=uploads.complete(f.actor,accepted.uploadId(),new Completion(List.of(new ClientPart(1,hash)),hash),id()).fileId();
  assertEquals("READY",materials.add(f.actor,f.meeting,id(),new JdbcMaterials.Selection(file,null,null,null),id()).state());
 }
 @Test void actualMaterialChangesOnlyAReviewCandidateAndExplicitAdoptionPreservesRawAndReplays()throws Exception{
  support.root=root;String original="招上基金讨论本次会议。";
  try(var f=support.fixture(1,original,original);var model=new HttpMaterialEnhancement(URI.create("http://127.0.0.1:8792"))){
   var saved=support.service(f);String working=support.working(f,saved);var materials=materials(f);addMaterial(f,materials,"术语对照：招上基金=招商基金");var listing=materials.list(f.actor,f.meeting);
   var service=new JdbcMaterialEnhancements(f.components.authority(),saved,model);String key=id();long revision=support.revision(f);
   var result=service.generate(f.actor,f.meeting,key,revision,working,listing.materialSnapshotId());String candidate=(String)result.get("candidateId");assertEquals("READY",result.get("state"));
   assertEquals(original,support.segments(saved.readCurrent(f.actor,f.meeting)).getFirst().get("body"));assertEquals(result,service.generate(f.actor,f.meeting,key,revision,working,listing.materialSnapshotId()));
   String decision=id();var adopted=service.adopt(f.actor,f.meeting,candidate,decision);assertEquals(adopted.get("result"),service.adopt(f.actor,f.meeting,candidate,decision).get("result"));
   assertEquals("招商基金讨论本次会议。",support.segments(saved.readCurrent(f.actor,f.meeting)).getFirst().get("body"));assertEquals(original,support.segments(saved.readOwner(f.actor,f.meeting,support.raw(f))).getFirst().get("body"));assertEquals(original,support.segments(saved.readOwner(f.actor,f.meeting,working)).getFirst().get("body"));
   assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM material_enhancement_candidate WHERE meeting_id=?",Integer.class,f.meeting));
  }
 }
 @Test void changedMaterialSnapshotMakesOldCandidateUnusableWithoutOverwritingTheWorkingDraft()throws Exception{
  support.root=root;String original="招上基金讨论本次会议。";
  try(var f=support.fixture(1,original,original);var model=new HttpMaterialEnhancement(URI.create("http://127.0.0.1:8792"))){
   var saved=support.service(f);String working=support.working(f,saved);var materials=materials(f);addMaterial(f,materials,"术语对照：招上基金=招商基金");
   var service=new JdbcMaterialEnhancements(f.components.authority(),saved,model);var candidate=service.generate(f.actor,f.meeting,id(),support.revision(f),working,materials.list(f.actor,f.meeting).materialSnapshotId());
   addMaterial(f,materials,"补充的会议背景。");
   assertThrows(ama.contract.TranscriptContracts.Failure.class,()->service.adopt(f.actor,f.meeting,(String)candidate.get("candidateId"),id()));
   assertEquals("STALE",service.read(f.actor,f.meeting,(String)candidate.get("candidateId")).get("state"));
   assertEquals(original,support.segments(saved.readCurrent(f.actor,f.meeting)).getFirst().get("body"));
  }
 }
 @Test void emptyContextManualChangesAndCurrentAuthorityNeverRewriteOriginals()throws Exception{
  support.root=root;String original="招上基金讨论本次会议。";
  try(var f=support.fixture(1,original,original);var model=new HttpMaterialEnhancement(URI.create("http://127.0.0.1:8792"))){
   var saved=support.service(f);String working=support.working(f,saved);var service=new JdbcMaterialEnhancements(f.components.authority(),saved,model);
   var empty=service.generate(f.actor,f.meeting,id(),support.revision(f),working,null);assertEquals(false,empty.get("canApply"));assertEquals(0,((com.fasterxml.jackson.databind.JsonNode)empty.get("changes")).size());assertThrows(ama.contract.TranscriptContracts.Failure.class,()->service.adopt(f.actor,f.meeting,(String)empty.get("candidateId"),id()));assertEquals("READY",service.read(f.actor,f.meeting,(String)empty.get("candidateId")).get("state"));
   var materials=materials(f);addMaterial(f,materials,"术语对照：招上基金=招商基金");String key=id();long revision=support.revision(f);String snapshot=materials.list(f.actor,f.meeting).materialSnapshotId();var proposal=service.generate(f.actor,f.meeting,key,revision,working,snapshot);String candidate=(String)proposal.get("candidateId");
   assertEquals(proposal,new JdbcMaterialEnhancements(f.components.authority(),saved,model).read(f.actor,f.meeting,candidate));assertThrows(ama.contract.TranscriptContracts.Failure.class,()->service.generate(f.actor,f.meeting,key,revision,working,null));
   var segments=support.segments(saved.readCurrent(f.actor,f.meeting));var edits=segments.stream().map(s->new ama.contract.TranscriptContracts.Edit((String)s.get("segmentId"),"人工修订保留。",List.<ama.contract.MinutesContracts.Mark>of())).toList();saved.mutate(f.actor,f.meeting,id(),new ama.contract.TranscriptContracts.Command(ama.contract.TranscriptContracts.Operation.SAVE,revision,working,edits),id());
   assertThrows(ama.contract.TranscriptContracts.Failure.class,()->service.adopt(f.actor,f.meeting,candidate,id()));assertEquals("STALE",service.read(f.actor,f.meeting,candidate).get("state"));assertEquals("人工修订保留。",support.segments(saved.readCurrent(f.actor,f.meeting)).getFirst().get("body"));
   f.access.installExternalPreparation((actor,meeting)->{throw new ama.access.MeetingAccess.AccessDeniedException();});assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->service.read(f.actor,f.meeting,candidate));assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->service.generate(f.actor,f.meeting,key,revision,working,snapshot));
  }
 }
 @Test void aBoundedProviderRejectionIsDefiniteAndDoesNotPublishAnOversizedCandidate()throws Exception{
  var segments=new ArrayList<Map<String,Object>>();for(int n=0;n<32;n++)segments.add(Map.of("id",id(),"text","A".repeat(32768)));
  var input=Map.of("profile",HttpMaterialEnhancement.PROFILE,"meetingId",id(),"sourceRevisionId",id(),"materialSnapshotId",id(),"expectedRevisionToken","1","segments",segments,"materials",List.of(Map.of("revisionId",id(),"sha256","a".repeat(64),"units",List.of(Map.of("ordinal",1,"text","术语对照：A=AA")))));
  try(var model=new HttpMaterialEnhancement(URI.create("http://127.0.0.1:8792"))){assertThrows(HttpMaterialEnhancement.RejectedInput.class,()->model.execute(id(),SpeechReviewIT.JSON.writeValueAsBytes(input)));}
 }

}
