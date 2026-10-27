package ama.meeting;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Clock;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Short-lived opaque continuation, bound to actor and query scope; a restart requires a new first page. */
public final class WorkspaceCursor {
 private static final int NONCE_BYTES=12,MAX_POSITION_BYTES=1024,MAX_TOKEN_CHARS=1536;
 private static final long TTL_SECONDS=300;
 private final Clock clock;private final SecretKey key;private final SecureRandom random=new SecureRandom();
 public WorkspaceCursor(){this(Clock.systemUTC());}
 WorkspaceCursor(Clock clock){this.clock=Objects.requireNonNull(clock);byte[] bytes=new byte[32];random.nextBytes(bytes);key=new SecretKeySpec(bytes,"AES");}
 public String encode(String actor,String scope,String position) {
  byte[] value=WorkspaceText.encode(position,MAX_POSITION_BYTES,false);
  byte[] nonce=new byte[NONCE_BYTES];random.nextBytes(nonce);
  try {
   var cipher=cipher(Cipher.ENCRYPT_MODE,nonce,actor,scope);
   byte[] payload=ByteBuffer.allocate(9+value.length).put((byte)1).putLong(clock.instant().getEpochSecond()).put(value).array();
   byte[] encrypted=cipher.doFinal(payload);
   return Base64.getUrlEncoder().withoutPadding().encodeToString(ByteBuffer.allocate(nonce.length+encrypted.length).put(nonce).put(encrypted).array());
  }catch(GeneralSecurityException e){throw new IllegalStateException("Cursor unavailable",e);}
 }
 public String decode(String actor,String scope,String token) {
  if(token==null||token.isEmpty()||token.length()>MAX_TOKEN_CHARS||!token.matches("[A-Za-z0-9_-]+"))throw invalid();
  try {
   byte[] bytes=Base64.getUrlDecoder().decode(token);
   if(!Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(token)||bytes.length<NONCE_BYTES+25)throw invalid();
   byte[] nonce=Arrays.copyOf(bytes,NONCE_BYTES);byte[] plain=cipher(Cipher.DECRYPT_MODE,nonce,actor,scope).doFinal(bytes,NONCE_BYTES,bytes.length-NONCE_BYTES);
   if(plain.length<9||plain.length>9+MAX_POSITION_BYTES)throw invalid();var payload=ByteBuffer.wrap(plain);if(payload.get()!=1)throw invalid();
   long issued=payload.getLong(),now=clock.instant().getEpochSecond();if(issued>now||issued<=now-TTL_SECONDS)throw invalid();
   byte[] value=new byte[payload.remaining()];payload.get(value);return new String(value,StandardCharsets.UTF_8);
  }catch(GeneralSecurityException e){throw invalid();}
 }
 private Cipher cipher(int mode,byte[] nonce,String actor,String scope)throws GeneralSecurityException {
  byte[] a=WorkspaceText.encode(actor,1020,true),s=WorkspaceText.encode(scope,256,true);
  ama.contract.IdentityContracts.stableId(actor);
  var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(mode,key,new GCMParameterSpec(128,nonce));
  cipher.updateAAD(ByteBuffer.allocate(8+a.length+s.length).putInt(a.length).put(a).putInt(s.length).put(s).array());return cipher;
 }
 private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid or expired cursor; restart pagination");}
}
