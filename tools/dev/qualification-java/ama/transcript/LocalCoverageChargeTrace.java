package ama.transcript;

import java.lang.instrument.Instrumentation;
import java.util.*;
import java.util.function.Supplier;
import java.util.function.Consumer;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.asm.Advice;
import static net.bytebuddy.matcher.ElementMatchers.*;

/** Qualification-only entry/exit observations; every original method body runs without a mocked return. */
public final class LocalCoverageChargeTrace {
    public record Result(Object prepared,List<Map<String,Object>> charges,long peakCopyByteAdmission){}
    public record Ticket(TranscriptAcquisitionBudget.Ledger ledger,long[] before){}
    static final class State {
        final List<Map<String,Object>> charges=new ArrayList<>();
        final IdentityHashMap<TranscriptAcquisitionBudget.Ledger,Long> peaks=new IdentityHashMap<>();
        final IdentityHashMap<TranscriptAcquisitionBudget.Ledger,Long> deadlines=new IdentityHashMap<>();
    }
    private static final ThreadLocal<State> CURRENT=new ThreadLocal<>();
    public static long deadline(TranscriptAcquisitionBudget.Ledger ledger){try{var field=ledger.getClass().getDeclaredField("deadline");field.setAccessible(true);return field.getLong(ledger);}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}}
    public static long observedDeadline(){var state=CURRENT.get();if(state==null||state.deadlines.size()!=1)throw new IllegalStateException("One actual charged ledger required");return state.deadlines.values().iterator().next();}
    public static Object enter(TranscriptAcquisitionBudget.Ledger ledger){var state=CURRENT.get();if(state==null)return null;state.deadlines.putIfAbsent(ledger,deadline(ledger));return new Ticket(ledger,ledger.usage());}
    public static void exit(Object token,Throwable failure){
        var state=CURRENT.get();if(state==null||token==null)return;var ticket=(Ticket)token;
        state.charges.add(Map.of("ledgerIdentity",System.identityHashCode(ticket.ledger()),"before",ticket.before(),"after",ticket.ledger().usage(),"completed",failure==null,"exitNanos",System.nanoTime(),"deadlineNanos",deadline(ticket.ledger()),"failure",failure==null?"":failure.toString()));
    }
    public static void admission(TranscriptAcquisitionBudget.Ledger ledger,long bytes,int copies){
        var state=CURRENT.get();if(state!=null)state.peaks.merge(ledger,Math.addExact(ledger.usage()[3],Math.multiplyExact(bytes,copies)),Math::max);
    }
    public static final class ChargeAdvice {
        @Advice.OnMethodEnter public static Object enter(@Advice.Argument(1) TranscriptAcquisitionBudget.Ledger ledger){return LocalCoverageChargeTrace.enter(ledger);}
        @Advice.OnMethodExit(onThrowable=Throwable.class) public static void exit(@Advice.Enter Object token,@Advice.Thrown Throwable failure){LocalCoverageChargeTrace.exit(token,failure);}
    }
    public static final class AdmissionAdvice {
        @Advice.OnMethodEnter public static void enter(@Advice.This TranscriptAcquisitionBudget.Ledger ledger,@Advice.Argument(0) long bytes,@Advice.Argument(3) int copies){LocalCoverageChargeTrace.admission(ledger,bytes,copies);}
    }
    public static Result run(Supplier<?> call){return run(call,ignored->{});}
    public static Result run(Supplier<?> call,Consumer<Map<String,Object>> observed){
        Instrumentation instrumentation=ByteBuddyAgent.getInstrumentation();
        ResettableClassFileTransformer transformer=new AgentBuilder.Default().disableClassFormatChanges().with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .type(named("ama.transcript.JdbcMinutesFactsReader").or(named("ama.transcript.TranscriptAcquisitionBudget$Ledger")))
            .transform((builder,type,loader,module,domain)->type.getName().equals("ama.transcript.JdbcMinutesFactsReader")?builder.visit(Advice.to(ChargeAdvice.class).on(named("charge").and(isStatic()))):builder.visit(Advice.to(AdmissionAdvice.class).on(named("admitCapture"))))
            .installOn(instrumentation);
        var state=new State();CURRENT.set(state);
        try{
            Object prepared=call.get();long peak=state.peaks.values().stream().mapToLong(Long::longValue).max().orElse(0);
            return new Result(prepared,List.copyOf(state.charges),peak);
        }finally{observed.accept(Map.of("charges",List.copyOf(state.charges),"distinctChargedLedgerReferences",state.deadlines.size(),"observedDeadlines",List.copyOf(state.deadlines.values())));CURRENT.remove();if(!transformer.reset(instrumentation,AgentBuilder.RedefinitionStrategy.RETRANSFORMATION))throw new IllegalStateException("Qualification instrumentation must reset");}
    }
}
