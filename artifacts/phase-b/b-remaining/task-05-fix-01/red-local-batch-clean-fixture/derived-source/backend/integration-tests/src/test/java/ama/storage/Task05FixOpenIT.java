package ama.storage;

import ama.contract.TaskContracts.*;
import ama.task.*;
import ama.worker.WorkerLoop;
import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Task05FixOpenIT extends Task05FixFixture {
    @Test void genuineLocalPreparationExitsAtBatchDeadlineBeforeErrorBodyRelease() throws Exception { localOpen(false); }
    @Test void genuineLocalPreparationExitsOnRealRenewalLossBeforeErrorBodyRelease() throws Exception { localOpen(true); }

    void localOpen(boolean renewalLoss) throws Exception {
        String file = upload("Actual original source\nSecond");
        String task = accept(List.of(file));
        assertEquals("READY", j.queryForObject("SELECT state FROM durable_task WHERE id=?", String.class, task));
        // Same owned managed identity and TLS origin, after genuine upload/intake; never redirect authorization.
        nativeFixture.process.destroy(); assertTrue(nativeFixture.process.waitFor(5, TimeUnit.SECONDS));
        nativeFixture.receipt("stopped-before-error-route");
        var reached = new CountDownLatch(1); var release = new CountDownLatch(1); var serverExit = new CountDownLatch(1);
        var serverPool = Executors.newSingleThreadExecutor();
        var server = HttpsServer.create(new InetSocketAddress("127.0.0.1", nativeFixture.port), 2);
        var tls = Task05TransportIT.tls("server");
        server.setHttpsConfigurator(new HttpsConfigurator(tls) { public void configure(HttpsParameters p) {
            var parameters=tls.getDefaultSSLParameters(); parameters.setNeedClientAuth(true); p.setSSLParameters(parameters);
        }});
        server.setExecutor(serverPool);
        server.createContext("/internal/v1/", exchange -> {
            try {
                assertTrue(exchange instanceof HttpsExchange);
                assertEquals("/internal/v1/read", exchange.getRequestURI().getPath());
                exchange.getRequestBody().readAllBytes();
                exchange.sendResponseHeaders(503, 64); exchange.getResponseBody().write('{'); exchange.getResponseBody().flush();
                reached.countDown(); release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); serverExit.countDown(); }
        });
        server.start();
        var exited = new CountDownLatch(1); var aborts = new AtomicInteger();
        var observed = new AtomicReference<TaskLease>();
        var queue = app.getBean(JdbcTaskQueue.class); var normal = settings();
        var bounded = new TranscriptImportRuntime.Settings(normal.archives(), normal.archiveBudget(), normal.aggregate(),
                Duration.ofSeconds(renewalLoss ? 30 : 1));
        var loop = WorkerLoop.contextual(queue, Map.of("TRANSCRIPT_IMPORT",1), Map.of("TRANSCRIPT_IMPORT_V1", (lease,cancel) -> {
            assertEquals(task,lease.taskId()); observed.set(lease);
            PreparationCancellation counted = new PreparationCancellation() {
                public boolean cancelled(){return cancel.cancelled();}
                public AutoCloseable register(Runnable abort){return cancel.register(()->{aborts.incrementAndGet();abort.run();});}
            };
            try { return TranscriptImportRuntime.prepare(queue,lease,bounded,counted); }
            finally { exited.countDown(); }
        }), "TASK05_FIX_OPEN");
        long start=System.nanoTime();
        Throwable primary=null;
        try {
            loop.start(); assertTrue(reached.await(5,TimeUnit.SECONDS),"Genuine source open must enter the partial error body");
            if(renewalLoss) {
                var lease=observed.get();
                assertEquals(1,j.update("UPDATE durable_task SET expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=? AND state='RUNNING' AND attempt=? AND lease_owner=?",task,lease.attempt(),lease.leaseOwner()));
            }
            boolean stopped=exited.await(renewalLoss?8:3,TimeUnit.SECONDS);
            Files.writeString(receipt.resolve("open-observation.json"),json.writeValueAsString(Map.of(
                    "task",task,"renewalLoss",renewalLoss,"preparationExitedBeforeFixtureRelease",stopped,
                    "releaseCount",release.getCount(),"registeredAbortCount",aborts.get(),
                    "elapsedMillis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start),
                    "batchMillis",bounded.batchDeadline().toMillis(),"transportMillis",300000)),StandardOpenOption.CREATE_NEW);
            assertTrue(stopped,"Actual preparation must exit before fixture release, independently of five-minute transport timeout");
            assertEquals(1,release.getCount());
            if(renewalLoss)assertEquals(1,aborts.get(),"The real failed renewal must signal the pre-open registration once");
            assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM transcript_revision WHERE producer_task_id=?",Integer.class,task));
            assertNull(j.queryForObject("SELECT output_id FROM durable_task WHERE id=?",String.class,task));
        } catch(Exception|AssertionError failure){primary=failure;throw failure;} finally {
          try {
            // Close selects cancellation first; release only rescues the deliberately broken RED reader afterward.
            Throwable closeFailure=null;
            try{loop.close();}catch(RuntimeException|Error failure){closeFailure=failure;}
            release.countDown(); assertTrue(serverExit.await(3,TimeUnit.SECONDS));
            server.stop(0); serverPool.shutdownNow(); assertTrue(serverPool.awaitTermination(3,TimeUnit.SECONDS));
            assertTrue(exited.await(3,TimeUnit.SECONDS));
            // A timed-out old close may have returned before reader release; await the exact owned execution.
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            while(loop.observation().active()!=0&&System.nanoTime()<end)Thread.sleep(10);
            assertEquals(0,loop.observation().active());
            if(observed.get()!=null)maintainNegativeFixture(task,observed.get());
            Files.writeString(receipt.resolve("loop-close.json"),json.writeValueAsString(Map.of("task",task,"active",loop.observation().active(),"closeFailure",closeFailure==null?"none":closeFailure.getClass().getSimpleName())),StandardOpenOption.CREATE_NEW);
          } catch(Exception|AssertionError cleanup){if(primary!=null)primary.addSuppressed(cleanup);else throw cleanup;}
        }
    }

    void maintainNegativeFixture(String task,TaskLease lease) throws Exception {
        Task05FixMaintenance.apply(source,task,receipt.resolve("accepted-"+task+".json"),receipt);
    }
}
