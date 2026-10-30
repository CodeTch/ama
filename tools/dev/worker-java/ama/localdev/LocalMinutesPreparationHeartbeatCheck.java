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

/** Observe own lease renewal across an actual S-commit/root-free preparation interval. */
public final class LocalMinutesPreparationHeartbeatCheck {
    static final ObjectMapper JSON=new ObjectMapper();
    static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    static final class Source extends AbstractDataSource {
        final DataSource delegate;
        final AtomicBoolean paused=new AtomicBoolean();
        final CountDownLatch arrived=new CountDownLatch(1),resume=new CountDownLatch(1);
        volatile boolean armed;
        Source(DataSource source){delegate=source;}
        static Object invoke(Object o,Method m,Object[] args)throws Throwable{try{return m.invoke(o,args);}catch(InvocationTargetException failure){throw failure.getCause();}}
        Object proxy(Object o,Class<?> type,InvocationHandler handler){return Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},handler);}
        Connection wrap(Connection actual){
            var captured=new AtomicBoolean();
            return(Connection)proxy(actual,Connection.class,(p,m,a)->{
                if(m.getName().equals("equals"))return p==a[0];if(m.getName().equals("hashCode"))return System.identityHashCode(p);
                Object value=invoke(actual,m,a);
                boolean target=Thread.currentThread().getName().startsWith("ama-work-MINUTES_GENERATE-");
                if(armed&&target&&m.getName().equals("commit")&&captured.get()&&paused.compareAndSet(false,true)){
                    arrived.countDown();check(resume.await(25,TimeUnit.SECONDS),"Bounded post-commit preparation pause required");
                }
                if(m.getName().equals("prepareStatement")&&a[0] instanceof String sql&&value instanceof PreparedStatement statement){
                    return proxy(statement,PreparedStatement.class,(s,sm,sa)->{
                        Object result=invoke(statement,sm,sa);
                        if(armed&&target&&sm.getName().equals("executeQuery")&&sql.contains("FROM minutes_element")&&sql.contains("FOR UPDATE"))captured.set(true);
                        return result;
                    });
                }
                return value;
            });
        }
        public Connection getConnection()throws SQLException{return wrap(delegate.getConnection());}
        public Connection getConnection(String user,String password)throws SQLException{return wrap(delegate.getConnection(user,password));}
    }
    static Map<String,Object> task(JdbcTemplate jdbc,String task){return jdbc.queryForMap("SELECT id,state,attempt,lease_owner,expires_at,last_error,output_kind,output_id FROM durable_task WHERE id=?",task);}
    public static void main(String[] args)throws Exception {
        check("1".equals(System.getenv("AMA_LOCAL_DEV"))&&args.length==3,"Explicit daily heartbeat qualification required");
        Path file=Path.of(args[0]).toRealPath(),fixture=Path.of(args[1]).toRealPath(),output=Path.of(args[2]).toRealPath();
        check(file.getParent().endsWith(Path.of(".tools/local-app"))&&output.startsWith(file.getParent().resolve("heartbeat-preparation")),"Fresh owned daily evidence required");
        var properties=new Properties();try(var input=Files.newInputStream(file)){properties.load(input);}
        String url=properties.getProperty("database.url");check(url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/ama_local_dev\\?.+"),"Exact owned daily DB required");
        var target=JSON.readTree(Files.readString(fixture));String task=target.get("taskId").asText(),meeting=target.get("meetingId").asText();UUID.fromString(task);UUID.fromString(meeting);
        var original=ControlledMinutesDataSource.create(url,properties.getProperty("database.user"),properties.getProperty("database.password"));var source=new Source(original);var jdbc=new JdbcTemplate(source);jdbc.setQueryTimeout(5);
        check(jdbc.queryForList("SELECT id FROM durable_task WHERE state IN ('READY','RUNNING','RECONCILING') ORDER BY id",String.class).equals(List.of(task)),"Sole fresh minutes task required");
        var env=new StandardEnvironment();env.getPropertySources().addFirst(new PropertiesPropertySource("daily",properties));var configured=ConfiguredProcessing.read(env);var definitions=ConfiguredBackends.read(env);
        var provider=new TranscriptWorkerRuntimeProvider(source,definitions,configured.read(),configured.canonical(),configured.preparation(),"LOCAL_HEARTBEAT_"+output.getFileName(),1,ConfiguredMinutes.read(env,configured.read(),configured.index()),ConfiguredPublication.read(env,configured.read(),configured.index()));
        try(var runtime=(TranscriptWorkerRuntimeProvider.Runtime)provider.create()){
            source.armed=true;runtime.start();
            try{
                check(source.arrived.await(20,TimeUnit.SECONDS),"Actual minutes S capture commit must be reached");
                var before=task(jdbc,task);check("RUNNING".equals(before.get("state"))&&((Number)before.get("attempt")).longValue()==1,"Pause must be in first actual preparation");
                // A fresh physical connection can lock both identities then root NOWAIT: the paused commit owns no row locks.
                try(var connection=original.getConnection()){
                    connection.setAutoCommit(false);
                    try(var identities=connection.prepareStatement("SELECT enterprise_id FROM enterprise_identity WHERE enterprise_id IN ('dev-member','dev-owner') ORDER BY enterprise_id FOR UPDATE NOWAIT")){identities.executeQuery().close();}
                    try(var root=connection.prepareStatement("SELECT id FROM meeting WHERE id=? FOR UPDATE NOWAIT")){root.setString(1,meeting);try(var rows=root.executeQuery()){check(rows.next(),"Actual root-free probe must find meeting");}}
                    connection.rollback();
                }
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);while(System.nanoTime()<deadline)Thread.sleep(100);
                var renewed=task(jdbc,task);check("RUNNING".equals(renewed.get("state"))&&((Number)renewed.get("attempt")).longValue()==1&&Objects.equals(before.get("lease_owner"),renewed.get("lease_owner")),"Heartbeat must retain same active attempt/owner");
                check(((java.time.LocalDateTime)renewed.get("expires_at")).isAfter((java.time.LocalDateTime)before.get("expires_at")),"Actual own lease expiry must advance while S is paused");
                Files.writeString(output.resolve("renewal.json"),JSON.writeValueAsString(Map.of("taskId",task,"actualSCommit",true,"physicalIdentityAndRootNowaitSucceeded",true,"sameOwnerAndAttempt",true,"expiryAdvanced",true)));
                source.resume.countDown();
                long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(80);Map<String,Object> result=null;
                while(System.nanoTime()<end){result=task(jdbc,task);if(Set.of("SUCCEEDED","FAILED","STALE","CANCELLED").contains(result.get("state")))break;Thread.sleep(200);}
                var attempts=jdbc.queryForList("SELECT attempt,outcome,error_code FROM task_attempt WHERE task_id=? ORDER BY attempt",task);
                var report=new LinkedHashMap<String,Object>();report.put("meetingId",meeting);report.put("taskId",task);report.put("genuineRuntime",true);report.put("actualSCommit",true);report.put("physicalRootFree",true);report.put("ownExpiryRenewed",true);report.put("attempts",attempts);report.put("executionCount",jdbc.queryForObject("SELECT COUNT(*) FROM external_execution WHERE task_id=?",Long.class,task));report.put("candidateCount",jdbc.queryForObject("SELECT COUNT(*) FROM generation_candidate WHERE task_id=?",Long.class,task));report.put("terminalState",result.get("state"));boolean pass="SUCCEEDED".equals(result.get("state"))&&((Number)result.get("attempt")).longValue()==1;check(((Number)report.get("executionCount")).longValue()==1&&((Number)report.get("candidateCount")).longValue()==1,"One actual external execution and candidate per task required");report.put("passWithoutRetry",pass);
                Files.writeString(output.resolve("report.json"),JSON.writeValueAsString(report));System.out.println(JSON.writeValueAsString(report));check(pass,"Own valid heartbeat must not discard the first prepared generation");
            }finally{source.resume.countDown();source.armed=false;}
        }
    }
}
