package ama.localdev;

import ama.worker.*;
import ama.transcript.*;
import ama.storage.*;
import ama.minutes.ControlledMinutesDataSource;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.lang.reflect.*;
import java.sql.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.core.env.*;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Additional real discovery and bounded repeated-renewal boundaries; production runtime owns all retries. */
public final class LocalMinutesPreparationBoundaryCheck {
    static final ObjectMapper JSON=new ObjectMapper();
    static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    static final class Pause {
        final CountDownLatch arrived=new CountDownLatch(1),resume=new CountDownLatch(1);
    }
    static final class Source extends AbstractDataSource {
        final DataSource delegate;final String phase,task;final Pause[] pauses;
        final AtomicInteger entered=new AtomicInteger();volatile boolean armed;
        Source(DataSource source,String phase,String task){delegate=source;this.phase=phase;this.task=task;pauses=new Pause[phase.equals("EXHAUST")?3:1];Arrays.setAll(pauses,n->new Pause());}
        static Object invoke(Object o,Method m,Object[] args)throws Throwable{try{return m.invoke(o,args);}catch(InvocationTargetException failure){throw failure.getCause();}}
        Object proxy(Object o,Class<?> type,InvocationHandler handler){return Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},handler);}
        void pause()throws Exception {int index=entered.getAndIncrement();if(index>=pauses.length)return;var pause=pauses[index];pause.arrived.countDown();check(pause.resume.await(25,TimeUnit.SECONDS),"Bounded actual preparation release required");}
        Connection wrap(Connection actual){var captured=new AtomicBoolean();return(Connection)proxy(actual,Connection.class,(p,m,a)->{
            if(m.getName().equals("equals"))return p==a[0];if(m.getName().equals("hashCode"))return System.identityHashCode(p);
            Object value=invoke(actual,m,a);boolean target=Thread.currentThread().getName().startsWith("ama-work-MINUTES_GENERATE-");
            if(armed&&target&&phase.equals("EXHAUST")&&m.getName().equals("commit")&&captured.get())pause();
            if(m.getName().equals("prepareStatement")&&a[0] instanceof String sql&&value instanceof PreparedStatement statement){
                var parameters=new HashMap<Integer,Object>();
                return proxy(statement,PreparedStatement.class,(s,sm,sa)->{
                    if(sm.getName().startsWith("set")&&sa!=null&&sa.length>=2&&sa[0] instanceof Integer index)parameters.put(index,sa[1]);
                    Object result=invoke(statement,sm,sa);
                    if(armed&&target&&sm.getName().equals("executeQuery")){
                        if(sql.contains("FROM minutes_element")&&sql.contains("FOR UPDATE"))captured.set(true);
                        if(phase.equals("DISCOVERY")&&entered.get()==0&&sql.contains("FROM external_execution")&&!sql.contains("FOR UPDATE")&&parameters.containsValue(task)&&StackWalker.getInstance().walk(frames->frames.anyMatch(frame->frame.getClassName().equals("ama.transcript.MinutesPendingDiscovery")&&frame.getMethodName().equals("read")))){
                            check(actual.getAutoCommit(),"Discovery boundary must be an actual autocommit metadata read");pause();
                        }
                    }
                    return result;
                });
            }
            return value;
        });}
        public Connection getConnection()throws SQLException{return wrap(delegate.getConnection());}
        public Connection getConnection(String user,String password)throws SQLException{return wrap(delegate.getConnection(user,password));}
        void release(){armed=false;for(var pause:pauses)pause.resume.countDown();}
    }
    static Map<String,Object> task(JdbcTemplate jdbc,String task){return jdbc.queryForMap("SELECT id,state,attempt,lease_owner,expires_at,last_error,output_kind,output_id FROM durable_task WHERE id=?",task);}
    static void physicalRootFree(DataSource source,String meeting)throws Exception {
        try(var connection=source.getConnection()){
            connection.setAutoCommit(false);
            try(var identities=connection.prepareStatement("SELECT enterprise_id FROM enterprise_identity WHERE enterprise_id IN ('dev-member','dev-owner') ORDER BY enterprise_id FOR UPDATE NOWAIT")){identities.executeQuery().close();}
            try(var root=connection.prepareStatement("SELECT id FROM meeting WHERE id=? FOR UPDATE NOWAIT")){root.setString(1,meeting);try(var rows=root.executeQuery()){check(rows.next(),"Exact root must exist");}}
            connection.rollback();
        }
    }
    static WorkerLoop external(TranscriptWorkerRuntimeProvider.Runtime runtime)throws Exception {
        // Read-only diagnostic access to the actual runtime's public Observation; never mints a domain capability.
        var field=runtime.getClass().getDeclaredField("external");field.setAccessible(true);return(WorkerLoop)field.get(runtime);
    }
    public static void main(String[] args)throws Exception {
        check("1".equals(System.getenv("AMA_LOCAL_DEV"))&&args.length==4,"Explicit current daily boundary qualification required");
        Path file=Path.of(args[0]).toRealPath(),fixture=Path.of(args[1]).toRealPath(),output=Path.of(args[2]).toRealPath();String phase=args[3];check(Set.of("DISCOVERY","EXHAUST").contains(phase),"Exact preparation boundary required");
        check(file.getParent().endsWith(Path.of(".tools/local-app"))&&output.startsWith(file.getParent().resolve("heartbeat-preparation")),"Fresh current daily evidence required");
        var properties=new Properties();try(var input=Files.newInputStream(file)){properties.load(input);}
        String url=properties.getProperty("database.url");check(url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/ama_local_dev\\?.+"),"Exact owned daily DB required");
        var fixtureValue=JSON.readTree(Files.readString(fixture));String task=fixtureValue.get("taskId").asText(),meeting=fixtureValue.get("meetingId").asText();UUID.fromString(task);UUID.fromString(meeting);
        var original=ControlledMinutesDataSource.create(url,properties.getProperty("database.user"),properties.getProperty("database.password"));var source=new Source(original,phase,task);var jdbc=new JdbcTemplate(source);jdbc.setQueryTimeout(5);
        check(jdbc.queryForList("SELECT id FROM durable_task WHERE state IN ('READY','RUNNING','RECONCILING') ORDER BY id",String.class).equals(List.of(task)),"Sole genuine pending generation required");
        var env=new StandardEnvironment();env.getPropertySources().addFirst(new PropertiesPropertySource("daily",properties));var configured=ConfiguredProcessing.read(env);var definitions=ConfiguredBackends.read(env);
        var provider=new TranscriptWorkerRuntimeProvider(source,definitions,configured.read(),configured.canonical(),configured.preparation(),"LOCAL_BOUNDARY_"+output.getFileName(),1,ConfiguredMinutes.read(env,configured.read(),configured.index()),ConfiguredPublication.read(env,configured.read(),configured.index()));
        try(var runtime=(TranscriptWorkerRuntimeProvider.Runtime)provider.create()){
            source.armed=true;runtime.start();boolean exhausted=false;var renewals=new ArrayList<Map<String,Object>>();
            try{
                var worker=external(runtime);
                for(int index=0;index<source.pauses.length;index++){
                    var pause=source.pauses[index];long admission=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);boolean arrived=false;
                    while(System.nanoTime()<admission){
                        if(pause.arrived.await(100,TimeUnit.MILLISECONDS)){arrived=true;break;}
                        if(phase.equals("EXHAUST")&&index>0&&worker.observation().active()==0){exhausted=true;break;}
                    }
                    if(exhausted)break;check(arrived,"Exact actual preparation phase must be reached");
                    var before=task(jdbc,task);check("RUNNING".equals(before.get("state"))&&((Number)before.get("attempt")).longValue()==1,"Only original first preparation may be paused");physicalRootFree(original,meeting);
                    long started=System.nanoTime(),wait=started+TimeUnit.SECONDS.toNanos(9);Map<String,Object> after;
                    do{after=task(jdbc,task);if(((java.time.LocalDateTime)after.get("expires_at")).isAfter((java.time.LocalDateTime)before.get("expires_at")))break;Thread.sleep(50);}while(System.nanoTime()<wait);
                    check("RUNNING".equals(after.get("state"))&&((Number)after.get("attempt")).longValue()==1&&before.get("lease_owner").equals(after.get("lease_owner"))&&((java.time.LocalDateTime)after.get("expires_at")).isAfter((java.time.LocalDateTime)before.get("expires_at")),"Actual same-owner/attempt heartbeat must advance expiry");
                    long advanced=java.time.Duration.between((java.time.LocalDateTime)before.get("expires_at"),(java.time.LocalDateTime)after.get("expires_at")).toMillis();check(advanced<9000,"Single-renewal boundary must release promptly after the first actual heartbeat");
                    renewals.add(Map.of("phase",phase,"ordinal",index+1,"physicalIdentityAndRootNowaitSucceeded",true,"sameOwnerAndAttempt",true,"expiryAdvanced",true,"expiryAdvanceMillis",advanced,"pauseMillis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)));pause.resume.countDown();
                }
                if(phase.equals("EXHAUST")){
                    long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(worker.observation().active()!=0&&System.nanoTime()<end)Thread.sleep(50);
                    check(worker.observation().active()==0,"Original preparation must terminate after bounded own-renewal reacquisitions");exhausted=true;source.release();
                    var held=task(jdbc,task);check("RUNNING".equals(held.get("state"))&&((Number)held.get("attempt")).longValue()==1,"Bounded rejected task remains original unmarked leased attempt");
                    check(jdbc.queryForObject("SELECT COUNT(*) FROM external_execution WHERE task_id=?",Long.class,task)==0&&jdbc.queryForObject("SELECT COUNT(*) FROM generation_candidate WHERE task_id=?",Long.class,task)==0,"No marker, external execution or candidate before fresh normal attempt");
                    Files.writeString(output.resolve("bounded-rejection.json"),JSON.writeValueAsString(Map.of("firstAttemptTerminated",true,"actualPauses",renewals.size(),"maxPauses",3,"executions",0,"candidates",0,"taskState","RUNNING","attempt",1)));
                }
                Files.writeString(output.resolve("renewals.json"),JSON.writeValueAsString(renewals));
                long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(80);Map<String,Object> result=null;
                while(System.nanoTime()<end){result=task(jdbc,task);if(Set.of("SUCCEEDED","FAILED","STALE","CANCELLED").contains(result.get("state")))break;Thread.sleep(200);}
                var attempts=jdbc.queryForList("SELECT attempt,outcome,error_code FROM task_attempt WHERE task_id=? ORDER BY attempt",task);
                long executions=jdbc.queryForObject("SELECT COUNT(*) FROM external_execution WHERE task_id=?",Long.class,task),candidates=jdbc.queryForObject("SELECT COUNT(*) FROM generation_candidate WHERE task_id=?",Long.class,task);
                boolean pass="SUCCEEDED".equals(result.get("state"))&&((Number)result.get("attempt")).longValue()==(phase.equals("DISCOVERY")?1:2)&&executions==1&&candidates==1&&(!phase.equals("EXHAUST")||exhausted&&renewals.size()<=3);
                var report=Map.of("pass",pass,"phase",phase,"meetingId",meeting,"taskId",task,"genuineRuntime",true,"actualOwnRenewalPauses",renewals.size(),"boundedFirstAttemptRejectedBeforeExternalEffects",exhausted,"attempts",attempts,"executionCount",executions,"candidateCount",candidates);
                Files.writeString(output.resolve("report.json"),JSON.writeValueAsString(report));System.out.println(JSON.writeValueAsString(report));check(pass,"Expected preparation boundary outcome required");
            }finally{source.release();}
        }
    }
}
