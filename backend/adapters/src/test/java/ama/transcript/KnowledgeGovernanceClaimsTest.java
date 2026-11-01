package ama.transcript;

import java.security.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real Ed25519 signatures; this pure wire verifier does not grant meeting authorization. */
class KnowledgeGovernanceClaimsTest {
 final KeyPair keys;final long now=Instant.now().getEpochSecond();
 final Map<String,String> pins=Map.of("providerId","p","storeInstanceId","s","clientId","c","delegationKeyId","d","assertionKeyId","a");
 KnowledgeGovernanceClaimsTest()throws Exception{keys=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();}
 Map<String,Object> state(){var value=new TreeMap<String,Object>(pins);value.putAll(Map.of("schema","ama.controlled-kb-governance-state.v1","documentId","doc-"+"a".repeat(32),"publicationId","00000000-0000-4000-8000-000000000030","actorId","真实用户","state","ACTIVE","revisionToken","0","eventId","b".repeat(32),"issuedAt",now,"expiresAt",now+5));return value;}
 Map<String,Object> receipt(){var v=state();v.remove("issuedAt");v.remove("expiresAt");v.put("schema","ama.controlled-kb-governance-receipt.v1");v.put("state","WITHDRAWN");v.put("revisionToken","1");v.putAll(Map.of("verifiedAt",now-31536000,"commandKey","original-key","requestSha256","c".repeat(64),"reasonSha256","d".repeat(64),"action","WITHDRAW"));return v;}
 String token(Map<String,Object> value)throws Exception{byte[] raw=ControlledKnowledgeClient.canonical(value);var signer=Signature.getInstance("Ed25519");signer.initSign(keys.getPrivate());signer.update(raw);var e=Base64.getUrlEncoder().withoutPadding();return e.encodeToString(raw)+"."+e.encodeToString(signer.sign());}
 Map<String,Object> verify(Map<String,Object> v,boolean fresh)throws Exception{return KnowledgeGovernanceClaims.verify(keys.getPublic(),pins,token(v),fresh,now);}
 @Test void freshAuthorityAndDurableTerminalReceiptAreDistinctProofs()throws Exception{
  assertEquals(state(),verify(state(),true));assertEquals(receipt(),verify(receipt(),false));
  assertThrows(IllegalArgumentException.class,()->verify(state(),false));assertThrows(IllegalArgumentException.class,()->verify(receipt(),true));
 }
 @Test void signaturePinsAndExactFieldSetAreRequired()throws Exception{
  String original=token(state());var stranger=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();assertThrows(IllegalArgumentException.class,()->KnowledgeGovernanceClaims.verify(stranger.getPublic(),pins,original,true,now));
  for(String pin:pins.keySet()){var changed=state();changed.put(pin,"foreign");assertThrows(IllegalArgumentException.class,()->verify(changed,true));}
  var extra=state();extra.put("payload","not allowed");assertThrows(IllegalArgumentException.class,()->verify(extra,true));var missing=state();missing.remove("eventId");assertThrows(IllegalArgumentException.class,()->verify(missing,true));
 }
 @Test void staleFutureAndUnboundedValidityCannotExtendReadAuthority(){
  for(var pair:List.of(new long[]{now-10,now-1},new long[]{now+3,now+5},new long[]{now,now+6},new long[]{now+2,now+7},new long[]{now,now-1})){var v=state();v.put("issuedAt",pair[0]);v.put("expiresAt",pair[1]);assertThrows(IllegalArgumentException.class,()->verify(v,true));}
 }
 @Test void malformedVersionsStatesAndResourceIdsAreRejected(){
  for(String version:List.of("-1","01","9223372036854775807","1e2","1.0")){var v=state();v.put("revisionToken",version);assertThrows(IllegalArgumentException.class,()->verify(v,true));}
  for(String field:List.of("publicationId","documentId","eventId","state","actorId")){var v=state();v.put(field,"");assertThrows(IllegalArgumentException.class,()->verify(v,true));}
  var v=state();v.put("issuedAt",true);assertThrows(IllegalArgumentException.class,()->verify(v,true));
 }
 @Test void terminalReceiptCannotAuthorizeAnActiveOrMismatchedAction(){
  for(String state:List.of("ACTIVE","UNKNOWN","OBSOLETE")){var v=receipt();v.put("state",state);assertThrows(IllegalArgumentException.class,()->verify(v,false));}
  var v=receipt();v.put("revisionToken","0");assertThrows(IllegalArgumentException.class,()->verify(v,false));
 }
}
