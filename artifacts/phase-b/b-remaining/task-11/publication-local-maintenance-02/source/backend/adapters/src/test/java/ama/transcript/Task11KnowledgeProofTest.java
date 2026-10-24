package ama.transcript;
import java.util.*;import java.security.*;import java.security.spec.*;import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;
class Task11KnowledgeProofTest {
 Map<String,Object> vectors;Map<String,String> identities;PublicKey key;
 @BeforeEach void publicVectors()throws Exception {try(var in=getClass().getResourceAsStream("/ama/knowledge/permanent-rejection-public-vectors.json")){vectors=ControlledKnowledgeClient.parse(in.readAllBytes(),65536);}identities=(Map<String,String>)vectors.get("identities");key=KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode((String)vectors.get("publicKeySpki"))));}
 @Test void permanentHistoricalSignatureRemainsVerifiable(){var proof=ControlledKnowledgeClient.verifyPermanentRejection(key,identities,(String)vectors.get("validPermanent"));assertEquals(1,((Number)proof.get("verifiedAt")).longValue());assertEquals("DEFINITIVE_REJECTION",proof.get("noEffectBasis"));}
 @Test void forgedSignatureRejected(){String token=(String)vectors.get("validPermanent");int split=token.indexOf('.');char old=token.charAt(split+1);String changed=token.substring(0,split+1)+(old=='A'?'B':'A')+token.substring(split+2);assertThrows(IllegalArgumentException.class,()->ControlledKnowledgeClient.verifyPermanentRejection(key,identities,changed));}
 @Test void crossStoreRejected(){var other=new HashMap<>(identities);other.put("storeInstanceId","00000000-0000-4000-8000-000000000099");assertThrows(IllegalArgumentException.class,()->ControlledKnowledgeClient.verifyPermanentRejection(key,other,(String)vectors.get("validPermanent")));}
 @Test void signedInvalidClaimsAndNoncanonicalBytesRejected(){for(String name:List.of("wrongSchema","wrongBasis","extraField","future","noncanonical"))assertThrows(IllegalArgumentException.class,()->ControlledKnowledgeClient.verifyPermanentRejection(key,identities,(String)vectors.get(name)),name);}
}
