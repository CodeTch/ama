package ama.transcript;
import java.nio.file.*;import java.nio.file.attribute.PosixFilePermissions;import java.security.*;import java.security.spec.*;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
/** Test-only access to the actual strict permanent-proof verifier; no protocol/SEND or private signing key. */
public final class Task11B2RejectionProofProbe {
 private Task11B2RejectionProofProbe(){}
 public static Map<String,Object> verify(Path publicKey,Map<String,String> identities,String token)throws Exception {
  assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());assertFalse(Files.isSymbolicLink(publicKey));assertEquals(PosixFilePermissions.fromString("rw-------"),Files.getPosixFilePermissions(publicKey));assertTrue(Files.size(publicKey)>0&&Files.size(publicKey)<=8192);byte[] bytes=Files.readAllBytes(publicKey);
  try{return ControlledKnowledgeClient.verifyPermanentRejection(KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(bytes)),identities,token);}finally{Arrays.fill(bytes,(byte)0);}
 }
}
