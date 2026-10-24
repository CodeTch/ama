package ama.storage;
import ama.task.*;
import ama.worker.WorkerLoop;
import ama.contract.TaskContracts.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Task05FixS3OpenIT extends Task05FixFixture {
    @Test void genuineS3ControlOpenExitsAtBatchDeadlineBeforeProxyRelease() throws Exception {open(false,false);}
    @Test void genuineS3ControlOpenExitsOnRealRenewalLossBeforeProxyRelease() throws Exception {open(true,false);}
    @Test void genuineS3ErrorOpenExitsAtBatchDeadlineBeforeProxyRelease() throws Exception {open(false,true);}
    @Test void genuineS3HeadersOpenExitsAtBatchDeadlineBeforeProxyRelease() throws Exception {open(false,false,true);}
    void open(boolean lost,boolean error) throws Exception {open(lost,error,false);}
    void open(boolean lost,boolean error,boolean headers) throws Exception {
        String task=accept(List.of(upload("Original S3 source\nSecond")));
        var queue=app.getBean(JdbcTaskQueue.class);var normal=settings();
        var settings=new TranscriptImportRuntime.Settings(normal.archives(),normal.archiveBudget(),normal.aggregate(),Duration.ofSeconds(lost?30:1));
        var fault=s3Proxy.arm(headers?"pause":error?"stall-error":"stall-body",path->path.startsWith("GET ")&&path.contains("/"+s3Fixture.config.controlBucket()+"/"));
        var exited=new CountDownLatch(1);var lease=new AtomicReference<TaskLease>();var aborts=new AtomicInteger();
        var loop=WorkerLoop.contextual(queue,Map.of("TRANSCRIPT_IMPORT",1),Map.of("TRANSCRIPT_IMPORT_V1",(claimed,cancel)->{
            assertEquals(task,claimed.taskId());lease.set(claimed);
            PreparationCancellation counted=new PreparationCancellation(){
                public boolean cancelled(){return cancel.cancelled();}
                public AutoCloseable register(Runnable abort){return cancel.register(()->{aborts.incrementAndGet();abort.run();});}
            };
            try{return TranscriptImportRuntime.prepare(queue,claimed,settings,counted);}finally{exited.countDown();}
        }),"TASK05_FIX_S3_OPEN");
        Throwable primary=null;long started=System.nanoTime();
        try {
            loop.start();assertTrue(fault.reached.await(5,TimeUnit.SECONDS));
            if(lost){var current=lease.get();assertEquals(1,j.update("UPDATE durable_task SET expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=? AND state='RUNNING' AND attempt=? AND lease_owner=?",task,current.attempt(),current.leaseOwner()));}
            boolean done=exited.await(lost?8:3,TimeUnit.SECONDS);
            Files.writeString(receipt.resolve("s3-open-observation.json"),json.writeValueAsString(Map.of("task",task,"renewalLoss",lost,"errorBody",error,"preparationExitedBeforeProxyRelease",done,"releaseCount",fault.release.getCount(),"registeredAbortCount",aborts.get(),"elapsedMillis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started),"batchMillis",settings.batchDeadline().toMillis(),"transportMillis",20000)),StandardOpenOption.CREATE_NEW);
            assertTrue(done,"Genuine S3 preparation must exit its control/error open before proxy release");assertEquals(1,fault.release.getCount());
            if(lost)assertEquals(1,aborts.get());
            assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM transcript_revision WHERE producer_task_id=?",Integer.class,task));
            assertNull(j.queryForObject("SELECT output_id FROM durable_task WHERE id=?",String.class,task));
        }catch(Exception|AssertionError failure){primary=failure;throw failure;}finally{
            try{
                try{loop.close();}catch(IllegalStateException timeout){Files.writeString(receipt.resolve("close-before-release.txt"),timeout.getClass().getSimpleName());}
                fault.release.countDown();assertTrue(exited.await(3,TimeUnit.SECONDS));
                long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(loop.observation().active()!=0&&System.nanoTime()<end)Thread.sleep(10);
                assertEquals(0,loop.observation().active());
                Task05FixMaintenance.apply(source,task,receipt.resolve("accepted-"+task+".json"),receipt);
            }catch(Exception|AssertionError cleanup){if(primary!=null)primary.addSuppressed(cleanup);else throw cleanup;}
        }
    }
}
