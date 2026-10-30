package ama.localdev;

import ama.meeting.MeetingEventReferences;import ama.meeting.JdbcMeetingAccess;import ama.access.*;import ama.storage.*;import ama.task.*;import ama.transcript.*;
import ama.minutes.ControlledMinutesDataSource;import ama.db.TransactionRunner;
import ama.contract.TaskContracts.TaskLease;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.reflect.*;import java.sql.*;import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.core.env.*;

/** Real daily-local qualification only. No SQL business fixtures or test-only preparation retry. */
public final class LocalPublicationRenewalCheck {
    static class Barrier {
        final String phase;final CountDownLatch arrived=new CountDownLatch(1),resume=new CountDownLatch(1);
        final AtomicBoolean once=new AtomicBoolean();
        Barrier(String phase){this.phase=phase;}
        void pause()throws Exception{if(once.compareAndSet(false,true)){arrived.countDown();if(!resume.await(15,TimeUnit.SECONDS))throw new IllegalStateException("Qualification release timeout");}}
    }
    static final class RepeatBarrier extends Barrier {
        record Event(CountDownLatch release){}
        final BlockingQueue<Event> events=new LinkedBlockingQueue<>();
        RepeatBarrier(){super("AFTER_DISCOVERY");}
        @Override void pause()throws Exception{var event=new Event(new CountDownLatch(1));events.put(event);if(!event.release().await(15,TimeUnit.SECONDS))throw new IllegalStateException("Repeated qualification release timeout");}
    }
    static final class TracedSource extends AbstractDataSource {
        final DataSource delegate;volatile Barrier barrier;
        TracedSource(DataSource delegate){this.delegate=delegate;}
        boolean target(){return Thread.currentThread().getName().equals("ama-local-prepare");}
        static Object invoke(Object object,Method method,Object[] args)throws Throwable{try{return method.invoke(object,args);}catch(InvocationTargetException cause){throw cause.getCause();}}
        Object proxy(Object object,Class<?> type,InvocationHandler call){return Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},call);}
        Connection connection(Connection actual){return (Connection)proxy(actual,Connection.class,(p,m,a)->{
            if(m.getName().equals("equals"))return p==a[0];if(m.getName().equals("hashCode"))return System.identityHashCode(p);
            Object result=invoke(actual,m,a);var gate=barrier;
            if(gate!=null&&target()&&m.getName().equals("commit")&&gate.phase.equals("AFTER_CAPTURE_COMMIT"))gate.pause();
            if(m.getName().equals("prepareStatement")&&a[0] instanceof String sql&&result instanceof PreparedStatement statement)return statement(statement,sql);
            return result;
        });}
        PreparedStatement statement(PreparedStatement actual,String sql){return (PreparedStatement)proxy(actual,PreparedStatement.class,(p,m,a)->{
            Object result=invoke(actual,m,a);
            if(m.getName().equals("executeQuery")&&result instanceof ResultSet rows)return proxy(rows,ResultSet.class,(r,rm,ra)->{
                Object value=invoke(rows,rm,ra);var gate=barrier;
                if(gate!=null&&target()&&rm.getName().equals("close")&&gate.phase.equals("AFTER_DISCOVERY")&&!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()&&sql.contains("publication_count")&&sql.contains("FROM external_execution"))gate.pause();
                return value;
            });
            return result;
        });}
        public Connection getConnection()throws SQLException{return connection(delegate.getConnection());}
        public Connection getConnection(String user,String password)throws SQLException{return connection(delegate.getConnection(user,password));}
    }
    static Map<String,Object> effects(JdbcTemplate j,String meeting,String publication,String task){
        var result=new LinkedHashMap<String,Object>();
        result.put("meeting",j.queryForMap("SELECT id,revision,cancel_epoch,stage,final_confirmation_id,ever_published FROM meeting WHERE id=?",meeting));
        result.put("publication",j.queryForMap("SELECT id,state,revision,confirmation_id,business_key FROM publication WHERE id=?",publication));
        result.put("submissions",j.queryForObject("SELECT COUNT(*) FROM publication_submission WHERE task_id=?",Long.class,task));
        result.put("executions",j.queryForObject("SELECT COUNT(*) FROM external_execution WHERE task_id=?",Long.class,task));
        result.put("publicationAttempts",j.queryForObject("SELECT COUNT(*) FROM publication_attempt WHERE publication_id=?",Long.class,publication));
        result.put("mappings",j.queryForObject("SELECT COUNT(*) FROM external_document_mapping WHERE publication_id=?",Long.class,publication));
        return result;
    }
    public static void main(String[] args)throws Exception {
        if(!"1".equals(System.getenv("AMA_LOCAL_DEV"))||args.length!=2)throw new IllegalArgumentException("Explicit local qualification required");
        Path configuration=Path.of(args[0]).toRealPath();
        if(!configuration.getParent().endsWith(Path.of(".tools/local-app"))||Files.size(configuration)>65536)throw new IllegalArgumentException("Daily local configuration required");
        var properties=new Properties();try(var in=Files.newInputStream(configuration)){properties.load(in);}
        String url=properties.getProperty("database.url");if(url==null||!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/ama_local_dev\\?.+"))throw new IllegalArgumentException("Dedicated local database required");
        var env=new StandardEnvironment();env.getPropertySources().addFirst(new PropertiesPropertySource("daily-local",properties));
        var source=new TracedSource(ControlledMinutesDataSource.create(url,properties.getProperty("database.user"),properties.getProperty("database.password")));
        var j=new JdbcTemplate(source);j.setQueryTimeout(5);var json=new ObjectMapper();var fixture=json.readTree(Files.readString(Path.of(args[1])));
        String task=fixture.get("taskId").asText(),meeting=fixture.get("meetingId").asText(),publication=fixture.get("publicationId").asText();
        for(String value:List.of(task,meeting,publication))UUID.fromString(value);
        var settings=ConfiguredProcessing.read(env);var definitions=ConfiguredBackends.read(env);
        var tx=new TransactionRunner(source,30);var fences=new JdbcSubjectFences(source,Set.of("MEETING"));var access=new JdbcMeetingAccess(source,tx,fences);
        var catalog=new FileBackendCatalog(source,definitions.backends(),definitions.defaultId());catalog.configure();
        var validators=new HashMap<String,OutboxReferenceValidator>(new MeetingEventReferences(source).validators());
        validators.putAll(new TranscriptEventReferences(source).validators());validators.putAll(new MinutesEventReferences(source).validators());
        var outbox=new JdbcOutbox(source,validators);
        var components=JdbcTranscriptAuthority.create(source,tx,access,new TranscriptFilePurposePolicy(),catalog,Map.of(),settings.read(),settings.canonical());
        var queue=TranscriptImportRuntime.install(source,fences,outbox,components);
        var minutes=ConfiguredMinutes.read(env,settings.read(),settings.index());minutes.preparation(components.authority());
        var publicationConfig=ConfiguredPublication.read(env,settings.read(),settings.index());
        try(var client=publicationConfig.client()){
            var service=new JdbcPublicationService(components.authority(),publicationConfig.archives(),publicationConfig.pin(),publicationConfig.bounds(),null,queue,outbox,client);
            new JdbcPublicationLocalRecovery(components.authority(),publicationConfig.bounds(),queue,outbox);
            var pending=j.queryForList("SELECT id FROM durable_task WHERE pool='KNOWLEDGE_PUBLISH' AND state IN ('READY','RUNNING','RECONCILING') ORDER BY id",String.class);
            if(!pending.equals(List.of(task)))throw new IllegalStateException("Exactly the queued qualification task must be pending");
            long claimDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);Optional<TaskLease> claimed;int polls=0;
            do{polls++;claimed=queue.claim("KNOWLEDGE_PUBLISH","LOCAL_RENEWAL_QUALIFICATION");if(claimed.isPresent())break;Thread.sleep(250);}while(System.nanoTime()<claimDeadline);
            TaskLease lease=claimed.orElseThrow(()->new IllegalStateException("No qualification lease admitted within bounded polling"));
            if(!lease.taskId().equals(task))throw new IllegalStateException("Unexpected claimed task");
            var initial=effects(j,meeting,publication,task);var results=new ArrayList<Map<String,Object>>();
            var executor=Executors.newSingleThreadExecutor(r->new Thread(r,"ama-local-prepare"));
            try{
                for(String phase:List.of("AFTER_DISCOVERY","AFTER_CAPTURE_COMMIT")){
                    if(!queue.renew(lease))throw new IllegalStateException("Qualification lease must be live");
                    var gate=new Barrier(phase);source.barrier=gate;
                    var future=executor.submit(()->service.prepare(lease));
                    if(!gate.arrived.await(15,TimeUnit.SECONDS)){gate.resume.countDown();throw new IllegalStateException("Exact preparation barrier not reached: "+phase);}
                    var before=j.queryForMap("SELECT state,attempt,lease_owner,expires_at FROM durable_task WHERE id=?",task);
                    boolean renewed=queue.renew(lease);
                    var after=j.queryForMap("SELECT state,attempt,lease_owner,expires_at FROM durable_task WHERE id=?",task);
                    var unchangedBefore=new LinkedHashMap<>(before);var unchangedAfter=new LinkedHashMap<>(after);Object prior=unchangedBefore.remove("expires_at"),next=unchangedAfter.remove("expires_at");
                    if(!renewed||Objects.equals(prior,next)||!unchangedBefore.equals(unchangedAfter))throw new IllegalStateException("Only exact successful same-lease expiry renewal is permitted");
                    gate.resume.countDown();String outcome="PREPARED",code="NONE";
                    try{if(future.get(25,TimeUnit.SECONDS)==null)throw new IllegalStateException("Missing prepared request");}
                    catch(ExecutionException failure){outcome="REJECTED";Throwable cause=failure.getCause();code=cause instanceof ama.contract.MinutesContracts.Failure f?f.code().name():cause.getClass().getSimpleName();}
                    finally{source.barrier=null;gate.resume.countDown();}
                    boolean zeroEffects=initial.equals(effects(j,meeting,publication,task));if(!zeroEffects)throw new IllegalStateException("Preparation must not commit marker, document or business changes");
                    results.add(Map.of("phase",phase,"renewed",renewed,"onlyOwnExpiryChanged",true,"outcome",outcome,"code",code,"zeroBusinessAndExternalEffects",zeroEffects));
                }
                // Every recapture is interrupted by a genuine successful renewal: the finite bound must reject.
                if(!queue.renew(lease))throw new IllegalStateException("Live lease required before bounded test");
                var repeated=new RepeatBarrier();source.barrier=repeated;var bounded=executor.submit(()->service.prepare(lease));
                for(int n=0;n<3;n++){
                    var event=repeated.events.poll(15,TimeUnit.SECONDS);if(event==null)throw new IllegalStateException("Expected recapture was not observed");
                    if(!queue.renew(lease)){event.release().countDown();throw new IllegalStateException("Repeated genuine renewal failed");}event.release().countDown();
                }
                String boundedCode="NONE";try{bounded.get(25,TimeUnit.SECONDS);}catch(ExecutionException rejected){if(rejected.getCause() instanceof ama.contract.MinutesContracts.Failure f)boundedCode=f.code().name();else throw rejected;}
                source.barrier=null;if(!boundedCode.equals("PROVENANCE_UNAVAILABLE")||!repeated.events.isEmpty()||!initial.equals(effects(j,meeting,publication,task)))throw new IllegalStateException("Recapture bound must reject without effects");
                results.add(Map.of("phase","REPEATED_RENEWAL_BOUND","outcome","EXPECTED_REJECTION","code",boundedCode,"genuineRenewals",3,"zeroBusinessAndExternalEffects",true));
                var spec=lease.spec();var wrongSpec=new ama.contract.TaskContracts.TaskSpec(spec.type(),spec.pool(),spec.subject(),spec.producerKey(),spec.input(),"f".repeat(64),spec.businessVersion(),spec.priority(),spec.cancelEpoch(),spec.maxAttempts());
                for(var test:List.of(Map.entry("WRONG_OWNER",new TaskLease(task,lease.attempt(),"OTHER_QUALIFICATION_OWNER",lease.expiresAt(),spec)),Map.entry("WRONG_ATTEMPT",new TaskLease(task,lease.attempt()+1,lease.leaseOwner(),lease.expiresAt(),spec)),Map.entry("WRONG_BINDING",new TaskLease(task,lease.attempt(),lease.leaseOwner(),lease.expiresAt(),wrongSpec)))){
                    if(!queue.renew(lease))throw new IllegalStateException("Live original lease required for negative control");String code="NONE";
                    try{service.prepare(test.getValue());}catch(ama.contract.MinutesContracts.Failure rejected){code=rejected.code().name();}
                    if(!code.equals("PROVENANCE_UNAVAILABLE")||!initial.equals(effects(j,meeting,publication,task)))throw new IllegalStateException("Changed lease/binding must reject without effects");
                    results.add(Map.of("phase",test.getKey(),"outcome","EXPECTED_REJECTION","code",code,"zeroBusinessAndExternalEffects",true));
                }
            }finally{if(source.barrier!=null)source.barrier.resume.countDown();executor.shutdownNow();executor.awaitTermination(5,TimeUnit.SECONDS);definitions.close();}
            boolean pass=results.size()==6&&results.stream().allMatch(x->Set.of("PREPARED","EXPECTED_REJECTION").contains(x.get("outcome")));
            System.out.println(json.writeValueAsString(Map.of("pass",pass,"taskId",task,"meetingId",meeting,"publicationId",publication,"phases",results)));
            if(!pass)System.exit(1);
        }
    }
}
