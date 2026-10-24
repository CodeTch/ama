package ama.transcript;
import ama.contract.ConfigArchiveContracts.ArchiveReadBudget;
import ama.contract.MinutesContracts.*;
import java.util.function.BooleanSupplier;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
/** Operation-wide admission, shared by S, F and readback; limits count each owned acquisition. */
public record TranscriptAcquisitionBudget(long utf8,long scalars,long utf16,long copyBytes,long copyUtf16,long responseBytes,int revisionDepth,int revisionNodes,int tasks) {
 public TranscriptAcquisitionBudget {require(utf8>0&&scalars>0&&utf16>0&&copyBytes>0&&copyUtf16>0&&responseBytes>0&&revisionDepth>0&&revisionNodes>0&&tasks>0,INVALID_ARGUMENT);}
 public Ledger ledger(ArchiveReadBudget archive,CanonicalBudget canonical,BooleanSupplier cancelled){return new Ledger(this,archive,canonical,cancelled);}
 public static final class Ledger {
  private final TranscriptAcquisitionBudget limits;private final ArchiveReadBudget archive;private final CanonicalBudget canonical;private final BooleanSupplier cancel;
  private long bytes,points,units,copies,copyUnits,rows,edges,response,encoded,nodes;private final long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
  private Ledger(TranscriptAcquisitionBudget l,ArchiveReadBudget a,CanonicalBudget c,BooleanSupplier stop){limits=l;archive=a;canonical=c;cancel=stop;check();}
  public void text(long b,long p,long u,int ownedCopies,boolean projected){
   check();require(b>=0&&p>=0&&u>=0&&ownedCopies>0,INVALID_ARGUMENT);
   require(b<=Math.min(archive.maxStringUtf8Bytes(),canonical.maxStringBytes())&&u<=archive.maxStringUtf16(),BUDGET_EXCEEDED);
   require(9<=canonical.maxEncodedBytes()-encoded&&b<=canonical.maxEncodedBytes()-encoded-9,BUDGET_EXCEEDED);
   reserve(b,p,u,ownedCopies,projected);encoded+=b+9;
  }
  public void capture(long b,long p,long u,int ownedCopies){check();require(b>=0&&p>=0&&u>=0&&ownedCopies>0,INVALID_ARGUMENT);reserve(b,p,u,ownedCopies,false);}
  /** Transient JDBC result admission before execution; actual retained captures are charged separately. */
  public void admitCapture(long b,long p,long u,int ownedCopies){
   check();require(b>=0&&p>=0&&u>=0&&ownedCopies>0,INVALID_ARGUMENT);
   require(b<=Math.min(limits.utf8,archive.maxTotalBytes())-bytes&&p<=Math.min(limits.scalars,archive.maxAggregateScalars())-points&&u<=limits.utf16-units,BUDGET_EXCEEDED);
   require(b<=(limits.copyBytes-copies)/ownedCopies&&u<=(limits.copyUtf16-copyUnits)/ownedCopies,BUDGET_EXCEEDED);
  }
  private void reserve(long b,long p,long u,int ownedCopies,boolean projected){
   require(b<=Math.min(limits.utf8,archive.maxTotalBytes())-bytes&&p<=Math.min(limits.scalars,archive.maxAggregateScalars())-points&&u<=limits.utf16-units,BUDGET_EXCEEDED);
   require(b<=(limits.copyBytes-copies)/ownedCopies&&u<=(limits.copyUtf16-copyUnits)/ownedCopies,BUDGET_EXCEEDED);
   if(projected)require(u<=(limits.responseBytes-response)/6,BUDGET_EXCEEDED);
   bytes+=b;points+=p;units+=u;copies+=b*ownedCopies;copyUnits+=u*ownedCopies;if(projected)response+=u*6;
  }
  public void row(long links){
   check();require(links>=0&&rows<archive.maxFactRows()&&links<=archive.maxFactEdges()-edges,BUDGET_EXCEEDED);
   require(rows<canonical.maxContainerEntries()&&links<=canonical.maxContainerEntries(),BUDGET_EXCEEDED);
   require(1<=canonical.maxNodes()-nodes&&links<=(canonical.maxNodes()-nodes-1)/2,BUDGET_EXCEEDED);
   require(32<=canonical.maxEncodedBytes()-encoded&&links<=(canonical.maxEncodedBytes()-encoded-32)/32,BUDGET_EXCEEDED);
   // Conservative row names, numeric/null cells, type tags and owned structural captures.
   require(512<=limits.copyBytes-copies&&256<=limits.copyUtf16-copyUnits,BUDGET_EXCEEDED);
   rows++;edges+=links;copies+=512;copyUnits+=256;nodes+=1+2*links;encoded+=32+32*links;
  }
  public void responseOverhead(long size){check();require(size>=0&&size<=limits.responseBytes-response,BUDGET_EXCEEDED);response+=size;}
  public long[] usage(){return new long[]{bytes,points,units,copies,copyUnits,rows,edges,response};}
  public int taskLimit(){return limits.tasks;}
  public long remainingRows(){return archive.maxFactRows()-rows;}
  public void check(){ama.contract.MinutesContracts.check(cancel);require(System.nanoTime()-deadline<0,BUDGET_EXCEEDED);}
 }
}
