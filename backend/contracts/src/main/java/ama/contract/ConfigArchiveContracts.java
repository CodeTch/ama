package ama.contract;
import java.io.*;
import java.security.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
public final class ConfigArchiveContracts {
 private ConfigArchiveContracts(){}
 public enum ArtifactType { PROCESSING_CONFIG,PARSER_CONFIG,PROMPT_CONFIG,MODEL_CONFIG,CANONICAL_CODEC,CONFIRMATION_HASH,GENERATION_HASH,ROWSET_HASH,ANCHOR,REVIEW_HASH,FINAL_SOURCES,OUTPUT_SCHEMA,VALIDATION_BUDGET,CATALOG,RENDER,EXCLUSION,SUPPORT,TITLE,CHUNK_PLAN }
 public static String version(String value){scalars(value);long count=value.codePointCount(0,value.length());require(count>0&&count<=255,PROFILE_UNAVAILABLE);for(int i=0;i<value.length();i++)require(value.charAt(i)>=32&&value.charAt(i)!=127,PROFILE_UNAVAILABLE);return value;}
 public record Identity(ArtifactType artifactType,String version){public Identity {require(artifactType!=null,INVALID_ARGUMENT);ConfigArchiveContracts.version(version);}}
 public record Ref(ArtifactType artifactType,String version,String sha256){public Ref {require(artifactType!=null,INVALID_ARGUMENT);ConfigArchiveContracts.version(version);hash(sha256);}public Identity identity(){return new Identity(artifactType,version);}}
 /** Untrusted raw carrier; the verifier checks cumulative bounds before copying. */
 public record RawArchive(ArtifactType claimedArtifactType,String claimedVersion,byte[] bytes){public RawArchive {require(claimedArtifactType!=null&&bytes!=null,INVALID_ARGUMENT);version(claimedVersion);}public Identity claimedIdentity(){return new Identity(claimedArtifactType,claimedVersion);}}
 /** Isolated immutable bytes, captured once only after length and cumulative limits are reserved. */
 public static final class ArchiveBytes {
  private final byte[] bytes;private ArchiveBytes(byte[] raw){bytes=raw.clone();}
  public static ArchiveBytes capture(byte[] raw,long maximum,long remaining,BooleanSupplier cancelled){require(raw!=null&&maximum>0&&remaining>=0,INVALID_ARGUMENT);check(cancelled);require(raw.length<=maximum&&raw.length<=remaining,BUDGET_EXCEEDED);var result=new ArchiveBytes(raw);check(cancelled);return result;}
  public int size(){return bytes.length;}public InputStream openStream(){return new ByteArrayInputStream(bytes);}public boolean sameBytes(byte[] candidate){return Arrays.equals(bytes,candidate);}
  public boolean sameBytes(ArchiveBytes candidate){return candidate!=null&&Arrays.equals(bytes,candidate.bytes);}
  public int contentHashCode(){return Arrays.hashCode(bytes);}
  public String sha256(BooleanSupplier cancelled){try{var digest=MessageDigest.getInstance("SHA-256");for(int i=0;i<bytes.length;){check(cancelled);int n=Math.min(1024,bytes.length-i);digest.update(bytes,i,n);i+=n;}check(cancelled);return HexFormat.of().formatHex(digest.digest());}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException("SHA-256");}}
 }
 public record ArchiveReadBudget(long maxArtifactBytes,long maxTotalBytes,int maxArtifacts,long maxDependencies,int maxGraphDepth,long maxTokens,int maxJsonDepth,int maxNameUtf16,int maxStringUtf16,long maxStringUtf8Bytes,long maxAggregateScalars,long maxManifestBytes,int maxBatchFiles,long maxFactRows,long maxFactEdges){
  public ArchiveReadBudget {require(maxArtifactBytes>0&&maxArtifactBytes<Long.MAX_VALUE&&maxTotalBytes>0&&maxTotalBytes<Long.MAX_VALUE&&maxArtifacts>0&&maxDependencies>0&&maxGraphDepth>0&&maxTokens>0&&maxJsonDepth>0&&maxNameUtf16>0&&maxStringUtf16>0&&maxStringUtf8Bytes>0&&maxAggregateScalars>0&&maxManifestBytes>0&&maxManifestBytes<Long.MAX_VALUE&&maxBatchFiles>0&&maxFactRows>0&&maxFactEdges>0,INVALID_ARGUMENT);}
 }
}
