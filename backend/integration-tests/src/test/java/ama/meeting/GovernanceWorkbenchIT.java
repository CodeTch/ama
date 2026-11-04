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
