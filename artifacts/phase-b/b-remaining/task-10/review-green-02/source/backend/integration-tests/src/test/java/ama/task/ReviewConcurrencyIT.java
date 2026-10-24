package ama.task;

import ama.contract.ExternalContracts.*;
import ama.contract.TaskContracts.*;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** Review regressions: real independent MySQL RR transactions, gated at JDBC boundaries, no sleeps/reset. */
class ReviewConcurrencyIT {
    @Test void allModelLocksPrecedeReservationReadView() throws Exception {
        var fixture = new TaskFixture();
        String suffix = UUID.randomUUID().toString(), a = "FIX1_A_" + suffix, b = "FIX1_B_" + suffix;
        var admissionReady = new CountDownLatch(1);
        var releaseAdmission = new CountDownLatch(1);
        var adminAtB = new CountDownLatch(1);
        var admissionThread = new AtomicReference<Thread>();
        var adminThread = new AtomicReference<Thread>();
        var trace = new ConcurrentLinkedQueue<String>();
        var source = new ProbeSource(fixture.source, new Probe() {
            public void before(String sql, Map<Integer,Object> parameters) {
                if (Thread.currentThread() == adminThread.get()
                        && sql.startsWith("INSERT INTO model_capacity") && b.equals(parameters.get(1))) {
                    trace.add("admin_attempt_B_lock"); adminAtB.countDown();
                }
            }
            public void after(String sql, Map<Integer,Object> parameters) {
                if (Thread.currentThread() == adminThread.get() && sql.startsWith("SELECT COUNT(*) FROM model_reservation"))
                    trace.add("admin_count_" + (a.equals(parameters.get(1)) ? "A" : "B"));
            }
            public void beforeCommit() {
                if (Thread.currentThread() == admissionThread.get()) {
                    trace.add("B_reservation_inserted_uncommitted"); admissionReady.countDown(); await(releaseAdmission);
                }
            }
            public void afterCommit() {
                if (Thread.currentThread() == admissionThread.get()) trace.add("B_admission_committed");
            }
        });
        var external = new JdbcExternalOperations(queue(fixture, source));
        var original = new JdbcModelPermitPool(external, Map.of(a, config(a,2,1), b, config(b,2,1)), (x,y,z)->{});
        original.configure();
        var first = begin(fixture, external); var second = begin(fixture, external);
        assertTrue(original.acquire(first,b,null).isPresent());
        var changed = new JdbcModelPermitPool(external, Map.of(a, config(a,2,2), b, config(b,1,2)), (x,y,z)->{});
        Throwable refusal;
        try (var threads = Executors.newFixedThreadPool(2)) {
            var admitted = threads.submit(() -> { admissionThread.set(Thread.currentThread()); return original.acquire(second,b,null); });
            try {
                assertTrue(admissionReady.await(10,TimeUnit.SECONDS), "real B admission must hold its reservation uncommitted");
                var configured = threads.submit(() -> { adminThread.set(Thread.currentThread()); changed.configure(); return true; });
                assertTrue(adminAtB.await(10,TimeUnit.SECONDS), "configuration must reach the held B model lock");
                releaseAdmission.countDown();
                assertTrue(admitted.get(10,TimeUnit.SECONDS).isPresent());
                try { configured.get(10,TimeUnit.SECONDS); refusal = null; }
                catch (ExecutionException failed) { refusal = failed.getCause(); }
            } finally { releaseAdmission.countDown(); }
        }
        int inflight = fixture.j.queryForObject("SELECT COUNT(*) FROM model_reservation WHERE model=? AND settled_at IS NULL",Integer.class,b);
        var rows = fixture.j.queryForList("SELECT model,capacity,config_revision FROM model_capacity WHERE model IN (?,?) ORDER BY model",a,b);
        System.out.println("FIX1_I1 isolation=REPEATABLE_READ trace="+trace+" inflight="+inflight+" configurations="+rows);
        final Throwable observed = refusal;
        assertAll(() -> assertInstanceOf(IllegalStateException.class,observed),
            () -> assertEquals(2,inflight),
            () -> assertEquals(2,((Number)rows.get(1).get("capacity")).intValue()),
            () -> assertTrue(((Number)rows.get(1).get("capacity")).intValue() >= inflight),
            () -> assertEquals(1L,((Number)rows.get(0).get("config_revision")).longValue()),
            () -> assertEquals(1L,((Number)rows.get(1).get("config_revision")).longValue()));
    }

    @Test void lateSuccessSeesZeroEffectCommittedWhileWaitingForSubject() throws Exception { lateSuccess(false); }
    @Test void lateSuccessRediscoversRetryCommittedWhileWaitingForSubject() throws Exception { lateSuccess(true); }

    @Test void observationRejectsAmbientTransactionBeforeAnyEffects() {
        var fixture = new TaskFixture(); var original = new JdbcExternalOperations(fixture.queue());
        var spec = fixture.spec(); var token = original.beginExternal(fixture.claimed(original.q,spec),request(spec)).orElseThrow();
        var called = new AtomicBoolean(); Throwable rejection = null;
        try { fixture.tx.required(() -> original.observe(token,new ExternalObservation(ExternalOutcome.SUCCEEDED,
            "TEST_RESULT:"+spec.input().id(),spec.input(),null,null),()->called.set(true))); }
        catch (RuntimeException failure) { rejection = failure; }
        final Throwable observed = rejection;
        assertAll(() -> assertInstanceOf(IllegalStateException.class,observed), () -> assertFalse(called.get()),
            () -> assertEquals("MARKED",fixture.j.queryForObject("SELECT state FROM external_operation WHERE id=?",String.class,token.operationId())));
    }

    @Test void exhaustedRediscoveryRequiresResubmittingFactWithoutAnotherExternalSend() throws Exception {
        var fixture = new TaskFixture(); var original = new JdbcExternalOperations(fixture.queue());
        var spec = fixture.spec(); var token = original.beginExternal(fixture.claimed(original.q,spec),request(spec)).orElseThrow();
        var zero = new ExternalObservation(ExternalOutcome.FAILED_ZERO_EFFECT,null,null,"REJECTED",
            new NoEffectProof(NoEffectBasis.DEFINITIVE_REJECTION,"TEST_VERIFIED_ZERO",java.time.Instant.now()));
        assertEquals(ObservationResult.APPLIED,original.observe(token,zero,()->{}));
        var fact = new ExternalObservation(ExternalOutcome.SUCCEEDED,"TEST_RESULT:"+spec.input().id(),spec.input(),null,null);
        var advances = new AtomicInteger(); var active = new AtomicBoolean(true); var called = new AtomicBoolean();
        try (var mutations = Executors.newSingleThreadExecutor()) {
            var source = new ProbeSource(fixture.source,new Probe() {
                public void before(String sql,Map<Integer,Object> parameters) {
                    if (!active.get() || !sql.contains("FROM task_subject") || !sql.contains("FOR UPDATE")) return;
                    int generation = advances.incrementAndGet(); assertTrue(generation<=4,"rediscovery must be bounded");
                    try { mutations.submit(() -> {
                        var next = new TaskSpec(spec.type(),spec.pool(),spec.subject(),TaskFixture.hash("fix1-bounded-"+generation+spec.producerKey()),
                            spec.input(),spec.inputFingerprint(),spec.businessVersion(),spec.priority(),spec.cancelEpoch(),spec.maxAttempts());
                        original.retryAfterZeroEffect(token.operationId(),generation,next,request(spec));
                        var lease = original.q.claim(spec.pool(),"fix1-generation-writer").orElseThrow();
                        var fresh = original.beginExternal(lease,request(spec)).orElseThrow();
                        assertEquals(ObservationResult.APPLIED,original.observe(fresh,zero,()->{}));
                        return true;
                    }).get(10,TimeUnit.SECONDS); }
                    catch (Exception failure) { throw new AssertionError("generation writer failed",failure); }
                }
            });
            var observer = new JdbcExternalOperations(queue(fixture,source));
            var failure = assertThrows(RuntimeException.class,()->observer.observe(token,fact,()->called.set(true)));
            assertEquals("ObservationRetryRequiredException",failure.getClass().getSimpleName());
            assertEquals(4,advances.get()); assertFalse(called.get()); assertFalse(original.renew(token));
            assertEquals(5L,fixture.j.queryForObject("SELECT current_execution_no FROM external_operation WHERE id=?",Long.class,token.operationId()));
            active.set(false);
            assertEquals(ObservationResult.QUARANTINED,observer.observe(token,fact,()->called.set(true)));
            assertFalse(called.get());
            assertEquals("MANUAL_REQUIRED",fixture.j.queryForObject("SELECT state FROM external_operation WHERE id=?",String.class,token.operationId()));
            System.out.println("FIX1_BOUNDED advances="+advances.get()+" retryable="+failure.getClass().getSimpleName()+" same_fact_resubmission=QUARANTINED no_external_network=true");
        }
    }

    private void lateSuccess(boolean retry) throws Exception {
        var fixture = new TaskFixture(); var q = fixture.queue(); var original = new JdbcExternalOperations(q);
        var spec = fixture.spec(); var token = original.beginExternal(fixture.claimed(q,spec),request(spec)).orElseThrow();
        var zero = new ExternalObservation(ExternalOutcome.FAILED_ZERO_EFFECT,null,null,"REJECTED",
            new NoEffectProof(NoEffectBasis.DEFINITIVE_REJECTION,"TEST_VERIFIED_ZERO",java.time.Instant.now()));
        if (retry) assertEquals(ObservationResult.APPLIED,original.observe(token,zero,()->{}));
        var heldSubject = new CountDownLatch(1); var observerAtSubject = new CountDownLatch(1);
        var callback = new AtomicBoolean(); var trace = new ConcurrentLinkedQueue<String>();
        var source = new ProbeSource(fixture.source,new Probe() {
            public void before(String sql,Map<Integer,Object> parameters) {
                if (sql.contains("FROM task_subject") && sql.contains("FOR UPDATE")) {
                    trace.add("observer_attempt_subject"); observerAtSubject.countDown();
                } else if (sql.contains("FOR UPDATE")) trace.add("observer_lock:"+sql);
            }
            public void afterRollback() { trace.add("observer_transaction_rollback"); }
            public void afterCommit() { trace.add("observer_transaction_commit"); }
        });
        var observer = new JdbcExternalOperations(queue(fixture,source));
        var firstWriterLock = new AtomicBoolean();
        var writerSource = new ProbeSource(fixture.source,new Probe() {
            public void after(String sql,Map<Integer,Object> parameters) {
                if (sql.contains("FROM task_subject") && sql.contains("FOR UPDATE") && firstWriterLock.compareAndSet(false,true)) {
                    heldSubject.countDown(); await(observerAtSubject);
                }
            }
        });
        var writerExternal = new JdbcExternalOperations(queue(fixture,writerSource));
        var next = new TaskSpec(spec.type(),spec.pool(),spec.subject(),TaskFixture.hash("fix1-retry"+spec.producerKey()),
            spec.input(),spec.inputFingerprint(),spec.businessVersion(),spec.priority(),spec.cancelEpoch(),spec.maxAttempts());
        ObservationResult result;
        try (var threads = Executors.newFixedThreadPool(2)) {
            var writer = threads.submit(() -> {
                if (retry) writerExternal.retryAfterZeroEffect(token.operationId(),1,next,request(spec));
                else assertEquals(ObservationResult.APPLIED,writerExternal.observe(token,zero,()->{}));
                trace.add(retry ? "writer_committed_generation2" : "writer_committed_zero_effect");
                return true;
            });
            assertTrue(heldSubject.await(10,TimeUnit.SECONDS));
            var success = threads.submit(() -> observer.observe(token,new ExternalObservation(ExternalOutcome.SUCCEEDED,
                "TEST_RESULT:"+spec.input().id(),spec.input(),null,null),()->callback.set(true)));
            assertTrue(writer.get(10,TimeUnit.SECONDS)); result = success.get(10,TimeUnit.SECONDS);
        }
        var state = fixture.j.queryForMap("SELECT state,current_execution_no,reconcile_epoch,external_result_id FROM external_operation WHERE id=?",token.operationId());
        System.out.println("FIX1_I2 retry="+retry+" result="+result+" trace="+trace+" operation="+state);
        assertAll(() -> assertEquals(ObservationResult.QUARANTINED,result),
            () -> assertEquals("MANUAL_REQUIRED",state.get("state")),
            () -> assertEquals("TEST_RESULT:"+spec.input().id(),state.get("external_result_id")),
            () -> assertFalse(callback.get()),
            () -> assertEquals(retry?2L:1L,((Number)state.get("current_execution_no")).longValue()),
            () -> assertEquals(0,fixture.j.queryForObject("SELECT needs_generation FROM meeting WHERE id=?",Integer.class,spec.subject().id())));
        assertFalse(original.renew(token));
        if (retry) {
            var lease = q.claim(spec.pool(),"fix1-after-quarantine").orElseThrow();
            assertTrue(original.beginExternal(lease,request(spec)).isEmpty(), "quarantine cannot restore new-send authority");
        }
    }

    private static ModelConfiguration config(String model,int capacity,long revision) {
        return new ModelConfiguration(model,capacity,revision,true,ModelConfiguration.CutoffPolicy.EXPLICIT_SETTLEMENT);
    }
    private static OperationToken begin(TaskFixture fixture,JdbcExternalOperations external) {
        var spec = fixture.spec(); return external.beginExternal(fixture.claimed(external.q,spec),request(spec)).orElseThrow();
    }
    private static ExternalRequest request(TaskSpec spec) {
        return new ExternalRequest("TEST_CALL",spec.producerKey(),TaskFixture.hash(spec.producerKey()),
            TaskFixture.hash(spec.input().id()),"test-v1:"+spec.input().id());
    }
    private static JdbcTaskQueue queue(TaskFixture fixture,DataSource source) {
        var jdbc = new JdbcTemplate(source);
        TaskDomainPolicy policy = new TaskDomainPolicy() {
  final JdbcTaskPreparations preparations=new JdbcTaskPreparations(source);
  public java.util.Optional<PreparedDomain> prepare(java.util.List<TaskSpec> specs,Acquisition purpose,boolean skip){return java.util.Optional.of(preparations.issue(specs,purpose,null,true));}
  public void requirePrepared(java.util.List<TaskSpec> specs,Acquisition purpose,PreparedDomain held){preparations.require(specs,purpose,held);}
  public boolean permitsDomainEffects(PreparedDomain held){return preparations.permitsEffects(held);}

            public boolean lockRoot(TaskSpec spec,boolean skip) {
                return !jdbc.queryForList("SELECT id FROM meeting WHERE id=? FOR UPDATE"+(skip?" SKIP LOCKED":""),String.class,spec.subject().id()).isEmpty();
            }
            public void validateInput(TaskSpec spec) {
                if (!"MEETING".equals(spec.subject().kind()) || !"TEST_INFO".equals(spec.input().kind())
                    || !spec.inputFingerprint().equals(TaskFixture.hash(spec.input().id()))
                    || jdbc.queryForObject("SELECT COUNT(*) FROM meeting_info_revision WHERE id=? AND meeting_id=? AND revision=?",
                        Integer.class,spec.input().id(),spec.subject().id(),spec.businessVersion())!=1)
                    throw new IllegalArgumentException("Wrong fixed input provenance");
            }
            public void validateOutput(TaskSpec spec,TaskResult result) { fixture.policy.validateOutput(spec,result); }
            public void validateExternalRequest(TaskSpec spec,ExternalRequest request) { fixture.policy.validateExternalRequest(spec,request); }
            public void validateExternalObservation(TaskSpec spec,ExternalObservation observation) { fixture.policy.validateExternalObservation(spec,observation); }
        };
        var fences = new JdbcSubjectFences(source,Set.of("MEETING"));
        var outbox = new JdbcOutbox(source,Map.of("TEST_INFO",event -> {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM meeting_info_revision WHERE id=? AND meeting_id=? AND revision=?",
                Integer.class,event.reference().id(),event.subject().id(),event.subjectRevision())!=1)
                throw new IllegalArgumentException("Wrong event provenance");
        }));
        return new JdbcTaskQueue(source,fences,outbox,Map.of("TEST_TASK",policy));
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(10,TimeUnit.SECONDS)) throw new AssertionError("JDBC interleaving gate timed out"); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
    }
    private interface Probe {
        default void before(String sql,Map<Integer,Object> parameters) {}
        default void after(String sql,Map<Integer,Object> parameters) {}
        default void beforeCommit() {}
        default void afterCommit() {}
        default void afterRollback() {}
    }
    /** Delegates every SQL call to real MySQL and only pauses the selected thread at explicit boundaries. */
    private static final class ProbeSource extends AbstractDataSource {
        private final DataSource source; private final Probe probe;
        ProbeSource(DataSource source,Probe probe) { this.source=source; this.probe=probe; }
        public Connection getConnection() throws SQLException {
            var connection=source.getConnection(); connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            assertEquals(Connection.TRANSACTION_REPEATABLE_READ,connection.getTransactionIsolation());
            return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)-> {
                if (method.getName().equals("commit")) probe.beforeCommit();
                Object result=invoke(connection,method,args);
                if (method.getName().equals("commit")) probe.afterCommit();
                if (method.getName().equals("rollback")) probe.afterRollback();
                if (method.getName().equals("prepareStatement")) {
                    String sql=(String)args[0]; var parameters=new HashMap<Integer,Object>(); var statement=result;
                    return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PreparedStatement.class},(p,m,a)-> {
                        if (m.getName().startsWith("set") && a!=null && a.length>=2 && a[0] instanceof Integer index) parameters.put(index,a[1]);
                        boolean execute=m.getName().startsWith("execute");
                        if (execute) probe.before(sql,parameters);
                        var value=invoke(statement,m,a);
                        if (execute) probe.after(sql,parameters);
                        return value;
                    });
                }
                return result;
            });
        }
        public Connection getConnection(String user,String password) throws SQLException { return getConnection(); }
        private static Object invoke(Object target,Method method,Object[] args) throws Throwable {
            try { return method.invoke(target,args); }
            catch (InvocationTargetException wrapped) { throw wrapped.getCause(); }
        }
    }
}
