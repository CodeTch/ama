package ama.transcript;

import java.security.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KnowledgeMaterialClaimsTest {
 final KeyPair keys;final long now=Instant.now().getEpochSecond();
 final Map<String,String> pins=Map.of("providerId","p","storeInstanceId","s","clientId","c","delegationKeyId","d","assertionKeyId","a");
 KnowledgeMaterialClaimsTest()throws Exception{keys=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();}
 Map<String,Object> claims(){var v=new TreeMap<String,Object>(pins);v.putAll(Map.of("schema","ama.controlled-material-authority.v1","actorId","read-only-member","itemsSha256","b".repeat(64),"issuedAt",now,"expiresAt",now+5));return v;}
 String token(Map<String,Object> value)throws Exception{byte[] raw=ControlledKnowledgeClient.JSON.writeValueAsBytes(value);var signer=Signature.getInstance("Ed25519");signer.initSign(keys.getPrivate());signer.update(raw);return ControlledKnowledgeClient.base64(raw)+"."+ControlledKnowledgeClient.base64(signer.sign());}
 Map<String,Object> verify(Map<String,Object> v)throws Exception{return KnowledgeMaterialClaims.verify(keys.getPublic(),pins,token(v),"read-only-member","b".repeat(64),now);}
 @Test void genuineSignatureHasExactActorBatchAndAbsoluteDeadline()throws Exception{assertEquals(claims(),verify(claims()));}
 @Test void crossActorBatchStoreKeyAndUnspecifiedFieldsNeverAuthorize()throws Exception{
  for(String field:List.of("actorId","itemsSha256","providerId","storeInstanceId","clientId","delegationKeyId","assertionKeyId","schema")){var v=claims();v.put(field,"other");assertThrows(IllegalArgumentException.class,()->verify(v));}
  var extra=claims();extra.put("payload","private");assertThrows(IllegalArgumentException.class,()->verify(extra));
  var stranger=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();assertThrows(IllegalArgumentException.class,()->KnowledgeMaterialClaims.verify(stranger.getPublic(),pins,token(claims()),"read-only-member","b".repeat(64),now));
 }
 @Test void staleFutureOrOverlongExpiryCannotAuthorizeCachedOrStreamingContent(){
  for(var pair:List.of(new long[]{now-5,now},new long[]{now,now+6},new long[]{now+2,now+7},new long[]{now+3,now+5},new long[]{now+2,now+1})){var v=claims();v.put("issuedAt",pair[0]);v.put("expiresAt",pair[1]);assertThrows(IllegalArgumentException.class,()->verify(v));}
  var v=claims();v.put("expiresAt",now+5.0);assertThrows(IllegalArgumentException.class,()->verify(v));
 }
 @Test void exactBatchHashUsesCanonicalArrayBytesFromTheProtocol()throws Exception{
  var items=List.of(Map.of("libraryId","library_1","documentId","doc_1","version","a".repeat(64)));
  String json="[{\"documentId\":\"doc_1\",\"libraryId\":\"library_1\",\"version\":\""+"a".repeat(64)+"\"}]";
  String expected=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  assertEquals(expected,KnowledgeMaterialClaims.itemsHash(items));var claims=claims();claims.put("itemsSha256",expected);
  assertEquals(expected,KnowledgeMaterialClaims.verify(keys.getPublic(),pins,token(claims),"read-only-member",KnowledgeMaterialClaims.itemsHash(items),now).get("itemsSha256"));
 }

}
