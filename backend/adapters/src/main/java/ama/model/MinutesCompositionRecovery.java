package ama.model;
import java.io.IOException;import java.util.*;
/** Resumes accepted immutable computations independently of read-only lookup. */
final class MinutesCompositionRecovery {
 interface Pending{List<String> read()throws IOException;}interface Complete{void run(String id)throws IOException;}
 private final Pending pending;private final Complete complete;
 MinutesCompositionRecovery(Pending pending,Complete complete){this.pending=pending;this.complete=complete;}
 void runOnce(){try{for(String id:pending.read().stream().limit(4).toList()){if(Thread.currentThread().isInterrupted())return;try{complete.run(id);}catch(IOException unavailable){/* Preserve the reserved request; the next pass retries exactly that execution. */}}}catch(IOException unavailable){/* Durable request lookup remains the authority; never synthesize an execution. */}}
}
