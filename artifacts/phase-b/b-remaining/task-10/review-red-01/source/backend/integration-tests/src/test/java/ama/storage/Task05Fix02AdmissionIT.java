package ama.storage;

import ama.contract.TaskContracts.*;
import ama.task.*;
import ama.worker.WorkerLoop;
import ama.storage.s3.Task05Fix02Proxy;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Task05Fix02AdmissionIT extends Task05Fix02Fixture {
    @Test void genuineS3AdmissionDeadlineExitsBeforeEitherPermitHolderRelease() throws Exception { saturated(false); }
    @Test void genuineS3AdmissionRealRenewalLossExitsBeforeEitherPermitHolderRelease() throws Exception { saturated(true); }

    void saturated(boolean loss) throws Exception {
        String first=accept(List.of(upload("Holder Alpha")));newMeeting();
        String second=accept(List.of(upload("Holder Beta")));newMeeting();
        String target=accept(List.of(upload("Genuine target")));
        var tasks=List.of(first,second,target);var queue=app.getBean(JdbcTaskQueue.class);var normal=settings();
        var holderSettings=new TranscriptImportRuntime.Settings(normal.archives(),normal.archiveBudget(),normal.aggregate(),Duration.ofSeconds(60));
        var targetSettings=new TranscriptImportRuntime.Settings(normal.archives(),normal.archiveBudget(),normal.aggregate(),Duration.ofSeconds(loss?30:1));
        var firstFault=s3Proxy.arm("stall-body",path->path.startsWith("GET ")&&path.contains("/"+s3Fixture.config.controlBucket()+"/"));
        var secondFault=new AtomicReference<Task05Fix02Proxy.Fault>();var secondArmed=new CountDownLatch(1);
        var targetExited=new CountDownLatch(1);var holdExecution=new CountDownLatch(1);
        var targetThread=new AtomicReference<Thread>();var targetLease=new AtomicReference<TaskLease>();
        var aborts=new AtomicInteger();var failures=new ConcurrentHashMap<String,String>();var events=new CopyOnWriteArrayList<Map<String,Object>>();
        var ioField=actualStoragePort.getClass().getDeclaredField("io");ioField.setAccessible(true);Object io=ioField.get(actualStoragePort);
        var requestsField=io.getClass().getDeclaredField("requests");requestsField.setAccessible(true);var permits=(Semaphore)requestsField.get(io);
        assertEquals(2,permits.availablePermits());
        var loop=WorkerLoop.contextual(queue,Map.of("TRANSCRIPT_IMPORT",3),Map.of("TRANSCRIPT_IMPORT_V1",(lease,cancel)->{
            assertTrue(tasks.contains(lease.taskId()),"Only this case's genuine accepted tasks may be claimed");
            boolean isTarget=target.equals(lease.taskId());
            if(second.equals(lease.taskId())) {
                assertTrue(firstFault.reached.await(5,TimeUnit.SECONDS));
                secondFault.set(s3Proxy.arm("stall-body",path->path.startsWith("GET ")&&path.contains("/"+s3Fixture.config.controlBucket()+"/")));
                secondArmed.countDown();
            }
            if(isTarget){
                assertTrue(secondArmed.await(6,TimeUnit.SECONDS));assertTrue(secondFault.get().reached.await(5,TimeUnit.SECONDS));
                targetThread.set(Thread.currentThread());targetLease.set(lease);
            }
            PreparationCancellation counted=new PreparationCancellation(){
                public boolean cancelled(){return cancel.cancelled();}
                public AutoCloseable register(Runnable abort){return cancel.register(()->{if(isTarget)aborts.incrementAndGet();abort.run();});}
            };
            try{return TranscriptImportRuntime.prepare(queue,lease,isTarget?targetSettings:holderSettings,counted);}
            catch(Exception failure){failures.put(lease.taskId(),failure.getClass().getSimpleName());throw failure;}
            finally {
                if(isTarget){
                    event(events,"target-preparation-exited");targetExited.countDown();
                    // Keep the same owned execution from reclaiming its deliberately expired fixture lease.
                    // Actual source preparation has returned/failed; close interrupts and joins this bounded test gate.
                    holdExecution.await(12,TimeUnit.SECONDS);
                }
            }
        }),"TASK05_FIX02_ADMISSION");
        Throwable primary=null;
        try {
            loop.start();assertTrue(secondArmed.await(7,TimeUnit.SECONDS));assertTrue(secondFault.get().reached.await(5,TimeUnit.SECONDS));
            long until=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(800);boolean waiting=false;
            List<String> stack=List.of();Thread reader=null;
            while(System.nanoTime()<until){
                reader=targetThread.get();
                if(reader!=null){
                    stack=Arrays.stream(reader.getStackTrace()).map(x->x.getClassName()+"."+x.getMethodName()).toList();
                    waiting=stack.contains("ama.storage.s3.S3Objects.acquire")&&stack.contains("java.util.concurrent.Semaphore.tryAcquire");
                    if(waiting)break;
                }
                Thread.sleep(5);
            }
            assertTrue(waiting,"Observe the real target waiting inside existing S3 semaphore admission before any SDK request");
            assertTrue(actualReadThreads.contains(reader),"Genuine selected source reached actual read outside its authorization transaction");
            assertFalse(probe.rootsHeld.contains(reader));assertEquals(0,permits.availablePermits());
            int requestsBefore=s3Proxy.requestsSeen.get();
            Files.writeString(receipt.resolve("admission-wait.json"),json.writeValueAsString(Map.of("tasks",tasks,"target",target,"targetThread",reader.getName(),"state",reader.getState().toString(),"stack",stack,"permits",permits.availablePermits(),"requestsSeen",requestsBefore,"firstHeld",firstFault.release.getCount(),"secondHeld",secondFault.get().release.getCount(),"utc",Instant.now().toString())),StandardOpenOption.CREATE_NEW);
            event(events,"target-admission-wait-observed");
            if(loss){var lease=targetLease.get();assertEquals(1,j.update("UPDATE durable_task SET expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=? AND state='RUNNING' AND attempt=? AND lease_owner=?",target,lease.attempt(),lease.leaseOwner()));event(events,"target-persisted-lease-expired");}
            long began=System.nanoTime();boolean exited=targetExited.await(loss?8:3,TimeUnit.SECONDS);
            Files.writeString(receipt.resolve("admission-result.json"),json.writeValueAsString(fields("target",target,"loss",loss,"targetExitedBeforeHolderRelease",exited,"elapsedAfterObservedWaitMillis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-began),"batchMillis",targetSettings.batchDeadline().toMillis(),"transportMillis",20000,"firstHeld",firstFault.release.getCount(),"secondHeld",secondFault.get().release.getCount(),"requestsBefore",requestsBefore,"requestsAfter",s3Proxy.requestsSeen.get(),"targetWorkerAborts",aborts.get())),StandardOpenOption.CREATE_NEW);
            assertTrue(exited,"Genuine target preparation must exit admission before either occupied permit is released");
            assertEquals(1,firstFault.release.getCount());assertEquals(1,secondFault.get().release.getCount());
            assertEquals(requestsBefore,s3Proxy.requestsSeen.get(),"Cancelled admission must execute no target HTTP");
            assertEquals(0,permits.availablePermits(),"Target cannot release permits it never acquired");
            if(loss)assertEquals(1,aborts.get(),"Actual target renewal loss signals its lifetime once");
            for(String task:tasks){assertNull(j.queryForObject("SELECT output_id FROM durable_task WHERE id=?",String.class,task));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM transcript_revision WHERE producer_task_id=?",Integer.class,task));}
        }catch(Exception|AssertionError failure){primary=failure;throw failure;}finally{
            try {
                event(events,"worker-close-begin");loop.close();event(events,"worker-close-return");holdExecution.countDown();
                assertEquals(0,loop.observation().active());assertTrue(targetExited.await(2,TimeUnit.SECONDS));
                assertFalse(targetThread.get()!=null&&targetThread.get().isAlive(),"Exact target worker thread must be joined");
                assertEquals(2,permits.availablePermits(),"Every actual holder abort returns one permit, with no over-release");
                assertTrue(permits.tryAcquire(2));assertFalse(permits.tryAcquire());permits.release(2);
                event(events,"all-owned-executions-joined-permits-exactly-two");
                firstFault.release.countDown();if(secondFault.get()!=null)secondFault.get().release.countDown();event(events,"proxy-stall-holders-released-route-still-open");
                for(String task:tasks){event(events,"maintenance-before-"+task);Task05Fix02Maintenance.apply(source,task,receipt.resolve("accepted-"+task+".json"),receipt);event(events,"maintenance-after-"+task);}
            }catch(Exception|AssertionError cleanup){if(primary!=null)primary.addSuppressed(cleanup);else throw cleanup;}
            finally {
                firstFault.release.countDown();if(secondFault.get()!=null)secondFault.get().release.countDown();holdExecution.countDown();
                s3Proxy.close();s3Proxy=null;event(events,"proxy-route-closed-and-threads-joined");
                Files.writeString(receipt.resolve("admission-lifecycle.json"),json.writeValueAsString(Map.of("events",events,"observedFailures",failures)),StandardOpenOption.CREATE_NEW);
            }
        }
    }
    static void event(List<Map<String,Object>> events,String action){events.add(Map.of("action",action,"utc",Instant.now().toString()));}
    static Map<String,Object> fields(Object... pairs){var values=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)values.put((String)pairs[i],pairs[i+1]);return values;}
}
