package ama.governance;
import ama.contract.SpeechContracts.VoiceEmbedding;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
/** Per-row AES-256-GCM. AAD binds the ciphertext to identity, version and resource. */
public final class EncryptedEmbeddingStore {
 private final SecretKeySpec key;private final SecureRandom random=new SecureRandom();
 public EncryptedEmbeddingStore(byte[] key){if(key==null||key.length!=32)throw new IllegalArgumentException("256-bit operations key required");this.key=new SecretKeySpec(key.clone(),"AES");}
 public byte[] encrypt(String aad,VoiceEmbedding value){
  byte[] nonce=new byte[12];random.nextBytes(nonce);var plain=ByteBuffer.allocate(value.dimension()*8);for(double v:value.values())plain.putDouble(v);
  try{var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce));cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));byte[] encrypted=cipher.doFinal(plain.array());return ByteBuffer.allocate(nonce.length+encrypted.length).put(nonce).put(encrypted).array();}catch(GeneralSecurityException e){throw new IllegalStateException("Embedding encryption unavailable");}finally{Arrays.fill(plain.array(),(byte)0);}
 }
 public VoiceEmbedding decrypt(String aad,String model,int dimension,byte[] bytes){
  if(bytes==null||dimension<1||dimension>16384||bytes.length!=12+16+dimension*8)throw new IllegalArgumentException("Encrypted embedding unavailable");byte[] plain=null;
  try{var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,bytes,0,12));cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));plain=cipher.doFinal(bytes,12,bytes.length-12);var buffer=ByteBuffer.wrap(plain);var values=new ArrayList<Double>();for(int i=0;i<dimension;i++)values.add(buffer.getDouble());return new VoiceEmbedding(model,dimension,values);}catch(GeneralSecurityException e){throw new IllegalArgumentException("Encrypted embedding unavailable");}finally{if(plain!=null)Arrays.fill(plain,(byte)0);}
 }
}
