package ama.storage;

import ama.task.PreparationCancellation;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Deny-only transport lifetime. Carries no source, lease, plan, holder or write authority. */
public final class SourceReadLifetime implements AutoCloseable {
    private static final ThreadLocal<SourceReadLifetime> CURRENT=new ThreadLocal<>();
    private static final Semaphore CAPACITY=new Semaphore(1024);
    private static final ScheduledThreadPoolExecutor TIMER;
    static {
        TIMER=new ScheduledThreadPoolExecutor(2,r->{var t=new Thread(r,"ama-source-deadline");t.setDaemon(true);return t;});
        TIMER.setRemoveOnCancelPolicy(true);
    }
    private final Set<Registration> requests=new HashSet<>();
    private final AutoCloseable cancellation;
    private final ScheduledFuture<?> timer;
    private final AtomicBoolean closed=new AtomicBoolean();
    private boolean aborted;

    public SourceReadLifetime(PreparationCancellation signal,long deadline) throws Exception {
        Objects.requireNonNull(signal);
        if(CURRENT.get()!=null)throw new IllegalStateException("Nested source lifetime");
        if(!CAPACITY.tryAcquire())throw new IllegalStateException("Source lifetime capacity exhausted");
        AutoCloseable registered=null;
        try {
            registered=signal.register(this::abort);
            timer=TIMER.schedule(this::abort,Math.max(0,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);
            cancellation=registered;
            CURRENT.set(this);
        }catch(Exception|Error failure){if(registered!=null)try{registered.close();}catch(Exception cleanup){failure.addSuppressed(cleanup);}CAPACITY.release();throw failure;}
    }

    /** Register on the calling preparation thread before the actual blocking transport call. */
    public static Registration track(Runnable abort) {
        var current=CURRENT.get();var registration=new Registration(current,Objects.requireNonNull(abort));
        if(current==null)return registration;
        boolean rejected;
        synchronized(current){rejected=current.aborted||current.closed.get();if(!rejected)current.requests.add(registration);}
        if(rejected){registration.abort();throw new IllegalStateException("Source lifetime cancelled");}
        return registration;
    }

    private void abort() {
        List<Registration> active;
        synchronized(this){if(aborted)return;aborted=true;active=List.copyOf(requests);requests.clear();}
        Throwable failure=null;
        for(var request:active)try{request.abort();}catch(RuntimeException|Error error){if(failure==null)failure=error;else if(failure!=error&&failure.getSuppressed().length<8)failure.addSuppressed(error);}
        if(failure instanceof RuntimeException error)throw error;
        if(failure instanceof Error error)throw error;
    }

    @Override public void close() throws Exception {
        if(!closed.compareAndSet(false,true))return;
        try {abort();} finally {
            try{cancellation.close();}finally{timer.cancel(false);CAPACITY.release();CURRENT.remove();}
        }
    }

    public static final class Registration implements AutoCloseable {
        private final SourceReadLifetime owner;private final Runnable action;private final AtomicBoolean done=new AtomicBoolean();
        private Registration(SourceReadLifetime owner,Runnable action){this.owner=owner;this.action=action;}
        private void abort(){if(done.compareAndSet(false,true))action.run();}
        @Override public void close(){if(done.compareAndSet(false,true)&&owner!=null)synchronized(owner){owner.requests.remove(this);}}
    }
}
