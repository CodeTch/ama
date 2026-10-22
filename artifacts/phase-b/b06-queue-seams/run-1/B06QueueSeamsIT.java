package ama.task;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.*;
import ama.contract.TaskContracts.*;
import ama.contract.ExternalContracts.ExternalRequest;
import ama.db.*;
import ama.meeting.JdbcMeetingAccess;
import ama.support.MysqlTestDatabase;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import static org.junit.jupiter.api.Assertions.*;

/**
 * B06 prerequisite composition proof only. The deliberately narrow policy uses the current
 * lockRoot extension and actual B03 access. It is not a production meeting policy or runtime
 * producer: TEST_INFO points to a real info row, never a fabricated transcription manifest.
 */
class B06QueueSeamsIT {
    final DataSource actual=MysqlTestDatabase.dataSource();
    final List<String> trace=new CopyOnWriteArrayList<>();
    final AtomicReference<Thread> completer=new AtomicReference<>();
    volatile CountDownLatch completionAtIdentity;
    final DataSource source=new ProbeSource(actual,sql->{
        if(sql.contains("FOR UPDATE"))trace.add(Thread.currentThread().getName()+" "+sql);
        if(Thread.currentThread()==completer.get() && sql.contains("FROM enterprise_identity")
                && sql.contains("FOR UPDATE") && completionAtIdentity!=null)completionAtIdentity.countDown();
    });
    final JdbcTemplate j=new JdbcTemplate(source);
    final TransactionRunner tx=new TransactionRunner(source);
    final JdbcSubjectFences fences=new JdbcSubjectFences(source,Set.of("MEETING"));
    final JdbcMeetingAccess access=new JdbcMeetingAccess(source,tx,fences);
    final String owner="b06-seam-"+UUID.randomUUID();
    final Actor actor=new Actor(owner,Status.ACTIVE,Set.of(Role.USER));
    final String meeting=UUID.randomUUID().toString(),input=UUID.randomUUID().toString();
    final TaskSpec spec=new TaskSpec("B06_SEAM_TEST","B06_SEAM_"+UUID.randomUUID(),
            new SubjectRef("MEETING",meeting),TaskFixture.hash(input+"producer"),
            new TypedRef("TEST_INFO",input),TaskFixture.hash(input),0,0,0,3);
    final TaskDomainPolicy policy=new TaskDomainPolicy(){
        @Override public boolean lockRoot(TaskSpec s,boolean skip){
            // This is precisely the only current public queue extension; subject is already held.
            var held=access.lock(actor,s.subject().id());
            access.require(held,MeetingAccess.Action.EDIT);
            return true;
        }
        @Override public void validateInput(TaskSpec s){
            var rows=j.queryForList("SELECT meeting_id,revision FROM meeting_info_revision WHERE id=?",s.input().id());
            if(!s.equals(spec)||rows.size()!=1||!rows.getFirst().get("meeting_id").equals(meeting)
                    ||((Number)rows.getFirst().get("revision")).longValue()!=s.businessVersion()
                    ||!s.inputFingerprint().equals(TaskFixture.hash(input)))throw new IllegalArgumentException("Invalid seam input");
        }
        @Override public void validateOutput(TaskSpec s,TaskResult r){
            if(r.output()!=null&&!r.output().equals(s.input()))throw new IllegalArgumentException("Invalid seam output");
        }
        @Override public void validateExternalRequest(TaskSpec s,ExternalRequest r){throw new IllegalStateException("No external seam capability");}
    };
    final JdbcOutbox outbox=new JdbcOutbox(source,Map.of("TEST_INFO",event->{
        var rows=j.queryForList("SELECT meeting_id,revision FROM meeting_info_revision WHERE id=?",event.reference().id());
        if(rows.size()!=1||!rows.getFirst().get("meeting_id").equals(event.subject().id())
                ||((Number)rows.getFirst().get("revision")).longValue()!=event.subjectRevision())
            throw new IllegalArgumentException("Invalid seam event provenance");
    }));
    final JdbcTaskQueue queue=new JdbcTaskQueue(source,fences,outbox,Map.of("B06_SEAM_TEST",policy));

    @BeforeEach void setup(){
        new SchemaMigrator(source).migrate();
        tx.required(()->{
            j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",owner,owner);
            fences.create(spec.subject(),0,0);
            j.update("INSERT INTO meeting(id,owner_id,stage,created_at,updated_at) VALUES (?,?,'ENTRY',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",meeting,owner);
            j.update("INSERT INTO meeting_info_revision(id,meeting_id,revision,name,created_by,created_at) VALUES (?,?,0,'seam',?,UTC_TIMESTAMP(6))",input,meeting,owner);
            return null;
        });
        trace.clear();
    }

    @Test void standaloneClaimMustLockIdentityBeforeSubject(){
        String taskId=queue.enqueue(spec);
        trace.clear();
        var lease=queue.claim(spec.pool(),"SEAM_WORKER");
        assertTrue(lease.isPresent(),"real task must reach a real claim before lock-order assertion");
        assertEquals(taskId,lease.orElseThrow().taskId());
        List<String> sql=List.copyOf(trace);
        System.out.println("B06 standalone claim actual locking SQL="+sql);
        int identity=index(sql,"FROM enterprise_identity"),subject=index(sql,"FROM task_subject"),
                root=index(sql,"SELECT * FROM meeting WHERE"),task=index(sql,"FROM durable_task WHERE id");
        assertTrue(identity>=0&&subject>=0&&root>=0&&task>=0,"all real identity/subject/root/task locks must occur");
        assertTrue(identity<subject&&subject<root&&root<task,
                "canonical identity -> subject -> meeting -> task required; actual indexes="+List.of(identity,subject,root,task));
    }

    @Test void ambientEnqueueMustReuseAuthenticMeetingCapability(){
        Throwable failure=null;
        try{
            tx.required(()->{
                var held=access.lock(actor,meeting);
                access.require(held,MeetingAccess.Action.EDIT);
                queue.enqueue(spec);
                outbox.flush();
                return null;
            });
        }catch(RuntimeException observed){failure=observed;}
        int tasks=j.queryForObject("SELECT COUNT(*) FROM durable_task WHERE producer_key=?",Integer.class,spec.producerKey());
        int events=j.queryForObject("SELECT COUNT(*) FROM task_outbox WHERE subject_id=?",Integer.class,meeting);
        System.out.println("B06 ambient actual="+(failure==null?"success":failure.getClass().getSimpleName()+": "+failure.getMessage())
                +"; persisted tasks="+tasks+"; persisted events="+events+"; locking SQL="+trace);
        assertNull(failure,"authenticated ambient enqueue must reuse the already-held B03 capability without a repeated identity phase");
        assertEquals(1,tasks);
        assertEquals(1,events);
    }

    @Test void disabledIdentityActuallyBlocksCompletionAndPreventsEffects()throws Exception{
        queue.enqueue(spec);
        TaskLease lease=queue.claim(spec.pool(),"SEAM_DISABLE_WORKER").orElseThrow();
        var disabledHeld=new CountDownLatch(1);
        var releaseDisabled=new CountDownLatch(1);
        completionAtIdentity=new CountDownLatch(1);
        var effects=new AtomicInteger();
        trace.clear();
        try(var threads=Executors.newFixedThreadPool(2)){
            var disable=threads.submit(()->tx.required(()->{
                j.update("UPDATE enterprise_identity SET status='DISABLED' WHERE enterprise_id=?",owner);
                disabledHeld.countDown();
                await(releaseDisabled);
                return true;
            }));
            try{
                assertTrue(disabledHeld.await(10,TimeUnit.SECONDS),"disable transaction must hold real identity");
                var finish=threads.submit(()->{
                    completer.set(Thread.currentThread());
                    return queue.completeWithEffects(lease,new TaskResult(Outcome.SUCCEEDED,spec.input(),null),()->{
                        effects.incrementAndGet();
                        j.update("UPDATE meeting SET stage='TRANSCRIPT' WHERE id=?",meeting);
                    });
                });
                assertTrue(completionAtIdentity.await(10,TimeUnit.SECONDS),"completion must reach actual identity SELECT FOR UPDATE");
                assertFalse(finish.isDone(),"completion must wait while disable transaction owns identity");
                releaseDisabled.countDown();
                assertTrue(disable.get(10,TimeUnit.SECONDS));
                assertInstanceOf(MeetingAccess.AccessDeniedException.class,
                        assertThrows(ExecutionException.class,()->finish.get(10,TimeUnit.SECONDS)).getCause());
            }finally{releaseDisabled.countDown();}
        }
        assertEquals(0,effects.get(),"disabled owner must have no completion effects");
        assertEquals("ENTRY",j.queryForObject("SELECT stage FROM meeting WHERE id=?",String.class,meeting));
        assertEquals("RUNNING",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,lease.taskId()));
        assertNull(j.queryForObject("SELECT output_id FROM durable_task WHERE id=?",String.class,lease.taskId()));
        System.out.println("B06 disable proof: stale Actor.status="+actor.status()+"; current shared identity=DISABLED; effects=0; task=RUNNING; actual locking SQL="+trace);
    }
    static int index(List<String> sql,String part){for(int i=0;i<sql.size();i++)if(sql.get(i).contains(part))return i;return -1;}
    static void await(CountDownLatch latch){try{if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("JDBC gate timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
    interface Probe {void before(String sql);}
    static final class ProbeSource extends AbstractDataSource {
        final DataSource delegate;final Probe probe;
        ProbeSource(DataSource delegate,Probe probe){this.delegate=delegate;this.probe=probe;}
        @Override public Connection getConnection()throws SQLException{
            var real=delegate.getConnection();
            real.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
                Object value=invoke(real,m,a);
                if(m.getName().equals("prepareStatement")){
                    String sql=(String)a[0];PreparedStatement statement=(PreparedStatement)value;
                    statement.setQueryTimeout(10);
                    return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PreparedStatement.class},(p2,m2,a2)->{
                        if(m2.getName().startsWith("execute"))probe.before(sql);
                        return invoke(statement,m2,a2);
                    });
                }
                return value;
            });
        }
        @Override public Connection getConnection(String u,String p)throws SQLException{return getConnection();}
        static Object invoke(Object target,Method method,Object[] args)throws Throwable{try{return method.invoke(target,args);}catch(InvocationTargetException e){throw e.getCause();}}
    }
}
