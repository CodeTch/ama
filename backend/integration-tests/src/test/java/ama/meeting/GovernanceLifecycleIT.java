package ama.meeting;
import ama.governance.*;
import ama.contract.GovernanceContracts.*;
import ama.contract.IdentityContracts.*;
import java.nio.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
class GovernanceLifecycleIT extends FeedbackReviewIT {
 JdbcGovernance governance;
 @BeforeEach void operations(){var tx=new ama.db.TransactionRunner(source);var access=new JdbcMeetingAccess(source,tx,new ama.task.JdbcSubjectFences(source,Set.of("MEETING")));governance=new JdbcGovernance(source,tx,access,new GovernanceHttpProvider(java.net.URI.create("http://127.0.0.1:8792"),"harness-fixture-embedding-v1",256),new EncryptedEmbeddingStore(new byte[32]));}
 void drain(Actor who,String id){for(int n=0;n<100&&!"COMPLETED".equals(governance.evaluation(who,id).get("state"));n++)governance.processEvaluation();assertEquals("COMPLETED",governance.evaluation(who,id).get("state"));}
 static byte[] wav(int hz){int count=48000;var b=ByteBuffer.allocate(44+count*2).order(ByteOrder.LITTLE_ENDIAN);b.put("RIFF".getBytes()).putInt(36+count*2).put("WAVEfmt ".getBytes()).putInt(16).putShort((short)1).putShort((short)1).putInt(16000).putInt(32000).putShort((short)2).putShort((short)16).put("data".getBytes()).putInt(count*2);for(int i=0;i<count;i++)b.putShort((short)(12000*Math.sin(2*Math.PI*hz*i/16000)));return b.array();}
 @Test void voiceConsentReviewRevokeAndCurrentRoleAreDurable(){
  String key=UUID.randomUUID().toString(),expiry=Instant.now().plusSeconds(86400).toString();var created=governance.enroll(actor,key,"明确同意仅用于候选识别",expiry,wav(220));String id=(String)created.get("id");
  assertNotNull(id);assertEquals(created,governance.enroll(actor,key,"明确同意仅用于候选识别",expiry,wav(220)));
  assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM governed_voiceprint WHERE subject_id=?",Integer.class,actor.enterpriseId()));
  assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->governance.reviewVoice(actor,id,UUID.randomUUID().toString(),0,"APPROVED","本人不得审核"));
  var admin=reviewer(true);governance.reviewVoice(admin,id,UUID.randomUUID().toString(),0,"APPROVED","候选用途审核通过");
  assertFalse(governance.voices(admin).toString().contains("encrypted_embedding"));assertFalse(governance.voices(actor).toString().contains("values"));
  governance.revoke(actor,id,UUID.randomUUID().toString(),1,"本人撤回授权");assertNull(j.queryForObject("SELECT encrypted_embedding FROM governed_voiceprint WHERE id=?",byte[].class,id));
  assertThrows(ama.contract.MeetingContracts.Conflict.class,()->governance.reviewVoice(admin,id,UUID.randomUUID().toString(),2,"APPROVED","不能复活"));
  j.update("DELETE FROM role_assignment WHERE enterprise_id=?",admin.enterpriseId());assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->governance.configuration(admin));
 }
 @Test void failedSamplesRemainDurableAndUnpublishable(){
  var admin=reviewer(true);long baseline=Long.parseLong((String)governance.configuration(admin).get("revisionToken"));var dataset=governance.dataset(admin,UUID.randomUUID().toString(),"bad-"+UUID.randomUUID(),List.of(new Sample("invalid",Kind.ASR,"invalid-base64",null,"参照")));
  var run=governance.evaluate(admin,UUID.randomUUID().toString(),(String)dataset.get("id"),new ModelConfig("harness-fixture-embedding-v1","summary-v1",null));String id=(String)run.get("id");
  drain(admin,id);var report=governance.evaluation(admin,id);var metrics=(Map<?,?>)report.get("metrics");assertEquals(1,((Number)metrics.get("sampleCount")).intValue());assertEquals(1,((Number)metrics.get("failed")).intValue());
  governance.reviewEvaluation(reviewer(true),id,UUID.randomUUID().toString(),"失败样本保留在分母");assertThrows(IllegalArgumentException.class,()->governance.release(admin,UUID.randomUUID().toString(),baseline,id,new ModelConfig("harness-fixture-embedding-v1","summary-v1",null),"不能发布失败评测"));
 }
 JdbcGovernanceWorkbench workbench(){var tx=new ama.db.TransactionRunner(source);return new JdbcGovernanceWorkbench(source,tx,new JdbcMeetingAccess(source,tx,new ama.task.JdbcSubjectFences(source,Set.of("MEETING"))),governance);}
 String publish(Actor admin,String dataset,ModelConfig config){
  var wb=workbench();var rules=new QualificationRules(1,1,.8,.8,.8,.8,0,0,1);var c=wb.saveCandidate(admin,null,UUID.randomUUID().toString(),0,"受控配置",config,rules,"创建验证配置");String candidate=(String)c.get("id"),run=(String)wb.evaluateCandidate(admin,candidate,UUID.randomUUID().toString(),0,dataset).get("id");drain(admin,run);var reviewer=reviewer(true);
  for(var result:(java.util.List<?>)wb.report(admin,run).get("samples")){var sample=(java.util.Map<?,?>)result;if("MINUTES".equals(sample.get("kind")))wb.reviewSample(reviewer,run,(String)sample.get("id"),UUID.randomUUID().toString(),Map.of("factuality",1d,"actions",1d,"evidence",1d),"受控协议样本逐项核对");}
  wb.approveCandidate(reviewer,candidate,UUID.randomUUID().toString(),0,run,"独立核对冻结的输入输出与规则");long revision=Long.parseLong((String)governance.configuration(admin).get("revisionToken"));wb.publishCandidate(admin,candidate,UUID.randomUUID().toString(),1,revision,"受控配置发布");return run;
 }
 @Test void configurationRollbacksCreateNewRevisionAndKeepRunningSnapshot(){
  var admin=reviewer(true);long baseline=Long.parseLong((String)governance.configuration(admin).get("revisionToken"));var config=new ModelConfig("harness-fixture-embedding-v1","summary-v1",null);
  String dataset=(String)governance.dataset(admin,UUID.randomUUID().toString(),"minutes-"+UUID.randomUUID(),List.of(new Sample("minutes",Kind.MINUTES,"测试会议",null,"测试会议"))).get("id");publish(admin,dataset,config);
  var running=governance.evaluate(admin,UUID.randomUUID().toString(),dataset,config);String original=j.queryForObject("SELECT CAST(config_json AS CHAR) FROM governance_evaluation WHERE id=?",String.class,running.get("id"));
  assertThrows(IllegalArgumentException.class,()->governance.rollback(admin,UUID.randomUUID().toString(),baseline+1,baseline,"直接回滚缺少独立复核"));
  var wb=workbench();var candidate=wb.rollbackCandidate(admin,UUID.randomUUID().toString(),baseline,"回滚申请","恢复匹配关闭",new QualificationRules(1,1,.8,.8,.8,.8,0,0,1));String id=(String)candidate.get("id"),run=(String)wb.evaluateCandidate(admin,id,UUID.randomUUID().toString(),0,dataset).get("id");drain(admin,run);var approver=reviewer(true);wb.reviewSample(approver,run,"minutes",UUID.randomUUID().toString(),Map.of("factuality",1d,"actions",1d,"evidence",1d),"回滚样本核对");wb.approveCandidate(approver,id,UUID.randomUUID().toString(),0,run,"独立审核回滚");assertEquals(Long.toString(baseline+2),wb.publishCandidate(admin,id,UUID.randomUUID().toString(),1,baseline+1,"回滚生效").get("revisionToken"));
  assertEquals(original,j.queryForObject("SELECT CAST(config_json AS CHAR) FROM governance_evaluation WHERE id=?",String.class,running.get("id")));
  j.update("UPDATE enterprise_identity SET status='DISABLED' WHERE enterprise_id=?",admin.enterpriseId());assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->governance.configuration(admin));
 }
 @Test void largeDatasetListsReadOnlyMetadataWithoutSortingAudioJson(){
  var admin=reviewer(true);String audio=Base64.getEncoder().encodeToString(wav(220));String id=(String)governance.dataset(admin,UUID.randomUUID().toString(),"large-"+UUID.randomUUID(),List.of(new Sample("pair-one",Kind.VOICE_SAME,audio,audio,"true"),new Sample("pair-two",Kind.VOICE_SAME,audio,audio,"true"))).get("id");
  var listed=governance.datasets(admin);var item=listed.stream().filter(row->row.get("id").equals(id)).findFirst().orElseThrow();assertEquals(2,((Number)item.get("sampleCount")).intValue());assertFalse(item.containsKey("samples_json"));assertEquals(Set.of("id","version","contentSha","sampleCount"),item.keySet());
 }
 @Test void minutesEvaluationSendsTheActualJsonPayloadThroughFixedHarness(){
  var port=new GovernanceHttpProvider(java.net.URI.create("http://127.0.0.1:8792"),"harness-fixture-embedding-v1",256);String content="E阶段仅验证JSON链路的受控输入";var result=port.evaluate(new Sample("minutes-wire",Kind.MINUTES,content,null,content),new ModelConfig("harness-fixture-embedding-v1","summary-v1",null));assertTrue(result.succeeded());assertEquals(1d,result.score());
 }
}
