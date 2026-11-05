package ama.meeting;
import ama.governance.*;
import ama.contract.GovernanceContracts.*;
import ama.contract.IdentityContracts.Actor;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class GovernanceWorkbenchIT extends FeedbackReviewIT {
 JdbcGovernance governance;JdbcGovernanceWorkbench workbench;
 final QualificationRules rules=new QualificationRules(1,1,.8,.8,.8,.8,0,0,1);
 @BeforeEach void setupWorkbench(){var tx=new ama.db.TransactionRunner(source);var access=new JdbcMeetingAccess(source,tx,new ama.task.JdbcSubjectFences(source,Set.of("MEETING")));governance=new JdbcGovernance(source,tx,access,new GovernanceHttpProvider(java.net.URI.create("http://127.0.0.1:8792"),"harness-fixture-embedding-v1",256),new EncryptedEmbeddingStore(new byte[32]));workbench=new JdbcGovernanceWorkbench(source,tx,access,governance);}
 String key(){return UUID.randomUUID().toString();}
 @Test void needsMoreRetainsAuthorizationAndRequiresANewSampleRevision(){
  var admin=reviewer(true);var created=governance.enroll(actor,key(),"本人明确授权",java.time.Instant.now().plusSeconds(86400).toString(),GovernanceLifecycleIT.wav(220));String id=(String)created.get("id");
  var reviewed=governance.reviewVoice(admin,id,key(),0,"NEEDS_MORE","请使用更清晰的独立声音");assertEquals("NEEDS_MORE",reviewed.get("state"));
  assertEquals("NOT_APPLIED",workbench.voiceDetail(actor,id).get("usageState"));assertEquals(2,((List<?>)workbench.voiceDetail(actor,id).get("history")).size());
  assertThrows(ama.contract.MeetingContracts.Conflict.class,()->governance.reviewVoice(admin,id,key(),1,"APPROVED","不能直接沿用旧样本"));
  String replaceKey=key(),expires=java.time.Instant.now().plusSeconds(86400).toString();var replaced=governance.replaceSample(actor,id,replaceKey,1,"本人再次授权",expires,GovernanceLifecycleIT.wav(330),"重新提供清晰声音");assertEquals("2",replaced.get("revisionToken"));assertEquals("PENDING",replaced.get("state"));assertEquals(replaced,governance.replaceSample(actor,id,replaceKey,1,"本人再次授权",expires,GovernanceLifecycleIT.wav(330),"重新提供清晰声音"));assertEquals(2,((List<?>)workbench.voiceDetail(actor,id).get("sampleHistory")).size());governance.reviewVoice(admin,id,key(),2,"APPROVED","复核新样本");
 }
 @Test void corpusSnapshotsReachCorrectionContextAndWithdrawalInvalidatesOldAndNewUse(){
  var admin=reviewer(true);var created=feedback.createStructured(actor,key(),meeting,"FINANCIAL_TERM","净值估计","单位净值","已核验");String id=(String)created.get("id");
  j.update("INSERT INTO corpus_feedback_binding(feedback_id,source_revision,segment_ids,adoption_state,adoption_version) VALUES (?,0,'[]','ADOPTED','harness-corpus-runtime')",id);
  feedback.review(admin,id,key(),0,"ACCEPTED","依据充分");var runtime=new JdbcGovernanceRuntime(source);String input=key();runtime.freeze(input);assertEquals(List.of("单位净值"),runtime.entities(input));
  j.update("UPDATE corpus_feedback_binding SET withdrawn=TRUE WHERE feedback_id=?",id);assertFalse(runtime.corpusCurrent(input));assertEquals(List.of(),runtime.entities(input));String next=key();runtime.freeze(next);assertEquals(List.of(),runtime.entities(next));
 }
 @Test void keysetPagesCoverTiedRowsAndBindEveryFilterAndActor(){
  var admin=reviewer(true);String prefix="paging-"+key();for(int n=0;n<23;n++)governance.dataset(admin,key(),prefix+"-"+n,List.of(new Sample("s",Kind.MINUTES,"会议",null,"参考")));
  j.update("UPDATE governance_dataset SET created_at='2026-11-03 00:00:00' WHERE version LIKE ?",prefix+"%");var first=workbench.page(admin,"datasets",null,prefix,null,20);String cursor=(String)first.get("nextCursor");var second=workbench.page(admin,"datasets",cursor,prefix,null,20);var ids=new HashSet<Object>();for(var page:List.of(first,second))for(var row:(List<?>)page.get("items"))assertTrue(ids.add(((Map<?,?>)row).get("id")));assertEquals(23,ids.size());assertEquals(23L,((Number)first.get("total")).longValue());
  assertThrows(IllegalArgumentException.class,()->workbench.page(admin,"datasets",cursor,prefix+"x",null,20));assertThrows(IllegalArgumentException.class,()->workbench.page(reviewer(true),"datasets",cursor,prefix,null,20));assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->workbench.page(actor,"datasets",null,null,null,20));assertEquals(0L,((Number)workbench.page(actor,"my-voiceprints",null,null,null,20).get("total")).longValue());
 }
 @Test void controlledAssetsKeepAudioOutOfDatasetAndMetadataPages(){
  var admin=reviewer(true);byte[] wav=GovernanceLifecycleIT.wav(220);String key=key();var asset=workbench.upload(admin,key,"sample.wav",wav);
  assertEquals(asset,workbench.upload(admin,key,"sample.wav",wav));assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->workbench.upload(actor,key(),"sample.wav",wav));
  String id=(String)governance.dataset(admin,key(),"asset-"+key(),List.of(new Sample("a",Kind.ASR,"asset:"+asset.get("id"),null,"测试"))).get("id");
  assertFalse(j.queryForObject("SELECT CAST(samples_json AS CHAR) FROM governance_dataset WHERE id=?",String.class,id).contains(Base64.getEncoder().encodeToString(wav)));
  assertEquals(1,((List<?>)workbench.datasetDetail(admin,id).get("samples")).size());
  var page=workbench.page(admin,"datasets",null,"asset-",null,20);assertTrue(((Number)page.get("total")).longValue()>0);assertFalse(page.toString().contains("wav"));
 }
 @Test void independentApprovalIsBoundToExactCandidateAndEditInvalidatesIt(){
  var author=reviewer(true);var approver=reviewer(true);var config=new ModelConfig("harness-fixture-embedding-v1","summary-v1",null);
  var candidate=workbench.saveCandidate(author,null,key(),0,"候选配置",config,rules,"新建");String id=(String)candidate.get("id");
  String dataset=(String)governance.dataset(author,key(),"review-"+key(),List.of(new Sample("a",Kind.MINUTES,"测试",null,"测试"))).get("id");
  String run=(String)workbench.evaluateCandidate(author,id,key(),0,dataset).get("id");for(int i=0;i<5&&!"COMPLETED".equals(governance.evaluation(author,run).get("state"));i++)governance.processEvaluation();
  assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->workbench.approveCandidate(author,id,key(),0,run,"自审"));
  workbench.reviewSample(approver,run,"a",key(),Map.of("factuality",1d,"actions",1d,"evidence",1d),"逐项核对");
  workbench.approveCandidate(approver,id,key(),0,run,"复核实际输出及冻结规则");
  var changed=workbench.saveCandidate(author,id,key(),1,"改动后的配置",new ModelConfig("harness-fixture-embedding-v1","summary-v2",null),rules,"调整提示词");assertEquals("DRAFT",changed.get("state"));assertNull(changed.get("evaluationId"));
  assertThrows(ama.contract.MeetingContracts.Conflict.class,()->workbench.publishCandidate(author,id,key(),2,Long.parseLong((String)governance.configuration(author).get("revisionToken")),"不能沿用旧审批"));
 }
 @Test void cancellationIsDurableAndRetryCreatesAnotherRunWithoutRewritingHistory(){
  var admin=reviewer(true);var config=new ModelConfig("harness-fixture-embedding-v1","summary-v1",null);
  String dataset=(String)governance.dataset(admin,key(),"cancel-"+key(),List.of(new Sample("bad",Kind.ASR,"bad",null,"参考"))).get("id");
  String run=(String)governance.evaluate(admin,key(),dataset,config).get("id");workbench.cancel(admin,run,key(),"停止本次实验");governance.processEvaluation();assertEquals("CANCELLED",governance.evaluation(admin,run).get("state"));
  var retry=workbench.retry(admin,run,key());assertNotEquals(run,retry.get("id"));assertEquals("CANCELLED",governance.evaluation(admin,run).get("state"));
 }
}
