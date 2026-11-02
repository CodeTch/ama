package ama.governance;
import ama.contract.SpeechContracts.VoiceEmbedding;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class EncryptedEmbeddingStoreTest {
 @Test void ciphertextIsRandomAndBoundToSubjectAndVersion(){
  var store=new EncryptedEmbeddingStore(new byte[32]);var value=new VoiceEmbedding("embed-v1",2,List.of(0.1,0.2));
  byte[] one=store.encrypt("id:person:embed-v1:2",value),two=store.encrypt("id:person:embed-v1:2",value);
  assertFalse(Arrays.equals(one,two));assertEquals(value,store.decrypt("id:person:embed-v1:2","embed-v1",2,one));
  assertThrows(IllegalArgumentException.class,()->store.decrypt("other:person:embed-v1:2","embed-v1",2,one));
  one[one.length-1]^=1;assertThrows(IllegalArgumentException.class,()->store.decrypt("id:person:embed-v1:2","embed-v1",2,one));
 }
 @Test void sampleDurationAndSilenceAreRejected(){assertThrows(IllegalArgumentException.class,()->GovernanceAudio.require(new byte[44]));}
}
