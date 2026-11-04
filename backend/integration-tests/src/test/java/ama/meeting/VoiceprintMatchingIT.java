package ama.meeting;
import ama.governance.*;
import ama.contract.GovernanceContracts.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
class VoiceprintMatchingIT extends GovernanceLifecycleIT {
 @Test void actualEmbedCalibratedReleaseRevocationExpiryAndIdentityNeverChangeAcl(){
  var admin=reviewer(true);String audio=Base64.getEncoder().encodeToString(wav(220)),other=Base64.getEncoder().encodeToString(wav(880));var candidate=new ModelConfig("harness-fixture-embedding-v1","summary-v1",0.99);
  String dataset=(String)governance.dataset(admin,UUID.randomUUID().toString(),"voice-"+UUID.randomUUID(),List.of(new Sample("positive",Kind.VOICE_SAME,audio,audio,"true"),new Sample("negative",Kind.VOICE_DIFFERENT,audio,other,"false"))).get("id");
  String run=publish(admin,dataset,candidate);assertEquals(1d,((Number)((Map<?,?>)governance.evaluation(admin,run).get("metrics")).get("score")).doubleValue());
  String voice=(String)governance.enroll(actor,UUID.randomUUID().toString(),"仅本人同意候选用途",Instant.now().plusSeconds(86400).toString(),wav(220)).get("id");governance.reviewVoice(admin,voice,UUID.randomUUID().toString(),0,"APPROVED","确认用途");
  var acl=j.queryForList("SELECT * FROM meeting_access_grant WHERE meeting_id=?",meeting);assertTrue(governance.match(admin,wav(220)).stream().anyMatch(c->c.voiceprintId().equals(voice)));assertEquals(acl,j.queryForList("SELECT * FROM meeting_access_grant WHERE meeting_id=?",meeting));
  j.update("UPDATE governed_voiceprint SET expires_at=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(6)) WHERE id=?",voice);assertTrue(governance.match(admin,wav(220)).stream().noneMatch(c->c.voiceprintId().equals(voice)));governance.cleanExpired();assertNull(j.queryForObject("SELECT encrypted_embedding FROM governed_voiceprint WHERE id=?",byte[].class,voice));
  String second=(String)governance.enroll(actor,UUID.randomUUID().toString(),"新的本人授权",Instant.now().plusSeconds(86400).toString(),wav(220)).get("id");governance.reviewVoice(admin,second,UUID.randomUUID().toString(),0,"APPROVED","确认用途");governance.revoke(actor,second,UUID.randomUUID().toString(),1,"本人撤销");assertTrue(governance.match(admin,wav(220)).stream().noneMatch(c->c.voiceprintId().equals(second)));

 }
}
