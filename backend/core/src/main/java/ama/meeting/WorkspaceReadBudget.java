package ama.meeting;

import java.time.Duration;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** One cumulative acquisition/response ledger, shared across every phase and copy of a request. */
public final class WorkspaceReadBudget {
 public record Limits(long bytes,long responseBytes,long scalars,long units,long rows,long edges,Duration timeout) {
  public Limits {if(bytes<1||responseBytes<1||scalars<1||units<1||rows<1||edges<1||timeout==null||timeout.isNegative()||timeout.isZero()||timeout.compareTo(Duration.ofSeconds(30))>0)throw new IllegalArgumentException("Invalid read budget");}
 }
 public static final Limits DEFAULT=new Limits(8L*1024*1024,4L*1024*1024,2L*1024*1024,4L*1024*1024,4096,16384,Duration.ofSeconds(5));
 private final Limits limits;private final BooleanSupplier cancelled;private final long started=System.nanoTime();
 private long bytes,response,scalars,units,rows,edges;
 public WorkspaceReadBudget(){this(DEFAULT,()->Thread.currentThread().isInterrupted());}
 public WorkspaceReadBudget(Limits limits,BooleanSupplier cancelled){this.limits=Objects.requireNonNull(limits);this.cancelled=Objects.requireNonNull(cancelled);}
 public void check(){if(cancelled.getAsBoolean()||System.nanoTime()-started>=limits.timeout().toNanos())throw unavailable();}
 public void reserve(long rowCount,long edgeCount,long utf8,long points,long utf16,int copies,boolean json) {
  check();if(rowCount<0||edgeCount<0||utf8<0||points<0||utf16<0||copies<1)throw unavailable();
  try {
   long b=Math.addExact(bytes,Math.addExact(Math.multiplyExact(utf8,copies),Math.multiplyExact(rowCount,128)));
   long s=Math.addExact(scalars,Math.multiplyExact(points,copies)),u=Math.addExact(units,Math.multiplyExact(utf16,copies));
   long r=Math.addExact(rows,rowCount),e=Math.addExact(edges,edgeCount);
   long out=json?Math.addExact(response,Math.addExact(Math.multiplyExact(utf16,6),Math.addExact(Math.multiplyExact(rowCount,256),64))):response;
   if(b>limits.bytes()||s>limits.scalars()||u>limits.units()||r>limits.rows()||e>limits.edges()||out>limits.responseBytes())throw unavailable();
   bytes=b;scalars=s;units=u;rows=r;edges=e;response=out;
  }catch(ArithmeticException e){throw unavailable();}
 }
 public record Usage(long bytes,long responseBytes,long scalars,long units,long rows,long edges){}
 public Usage usage(){return new Usage(bytes,response,scalars,units,rows,edges);}
 public static final class Unavailable extends IllegalStateException {
  public Unavailable(){super("Protected workspace read unavailable");}
  public Unavailable(Throwable cause){super("Protected workspace read unavailable",cause);}
 }
 private static Unavailable unavailable(){return new Unavailable();}
}
