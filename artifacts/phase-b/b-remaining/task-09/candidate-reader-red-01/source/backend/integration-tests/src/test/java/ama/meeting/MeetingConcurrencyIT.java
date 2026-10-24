package ama.meeting;

import ama.access.MeetingAccess;
import ama.audit.JdbcAuditAdapter;
import ama.contract.IdentityContracts.*;
import ama.contract.MeetingContracts.*;
import ama.db.*;
import ama.support.MysqlTestDatabase;
import ama.task.*;
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

/** Real MySQL transactions gated at actual JDBC boundaries; no timing guesses or SQL substitutes. */
class MeetingConcurrencyIT {
    final DataSource actual=MysqlTestDatabase.dataSource();
    final JdbcTemplate j=new JdbcTemplate(actual);
    final String owner="owner-"+UUID.randomUUID(),member="member-"+UUID.randomUUID();
    final Actor actor=new Actor(owner,Status.ACTIVE,Set.of(Role.USER));
    @BeforeEach void identities(){
        new SchemaMigrator(actual).migrate();
        for(String id:List.of(owner,member))j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",id,id);
    }
    record Runtime(MeetingService meetings,RosterService rosters,JdbcMeetingAccess access,TransactionRunner tx){}
    Runtime runtime(DataSource source){
        var tx=new TransactionRunner(source);var fences=new JdbcSubjectFences(source,Set.of("MEETING"));
        var outbox=new JdbcOutbox(source,new MeetingEventReferences(source).validators());var access=new JdbcMeetingAccess(source,tx,fences);
        var repository=new JdbcMeetingRepository(source,tx,access,fences,outbox,new JdbcAuditAdapter(source),id->{if(!id.equals("controlled-category"))throw new IllegalArgumentException("Not in test dictionary");});
        return new Runtime(new MeetingService(repository),new RosterService(repository),access,tx);
    }
    SaveInfo info(long revision,List<String> members){return new SaveInfo(revision,"saved",null,null,null,members,List.of());}
    @Test void identityDisabledWhileConfirmationWaitsCannotReceiveGrant()throws Exception{
        var reachedIdentity=new CountDownLatch(1);var heldIdentity=new CountDownLatch(1);var release=new CountDownLatch(1);
        var confirmer=new AtomicReference<Thread>();
        var source=new ProbeSource(actual,new Probe(){public void before(String sql,Map<Integer,Object> args){if(Thread.currentThread()==confirmer.get()&&sql.contains("FROM enterprise_identity")&&member.equals(args.get(1)))reachedIdentity.countDown();}});
        var runtime=runtime(source);String id=runtime.meetings.create(actor,"create","trace").id();runtime.meetings.save(actor,id,"draft",info(0,List.of(member)),"trace");
        try(var threads=Executors.newFixedThreadPool(2)){
            var disabled=threads.submit(()->new TransactionRunner(actual).required(()->{j.update("UPDATE enterprise_identity SET status='DISABLED' WHERE enterprise_id=?",member);heldIdentity.countDown();await(release);return true;}));
            try{
                assertTrue(heldIdentity.await(10,TimeUnit.SECONDS));
                var confirmation=threads.submit(()->{confirmer.set(Thread.currentThread());return runtime.rosters.confirm(actor,id,"confirmation",new ConfirmRoster(1L),"trace");});
                assertTrue(reachedIdentity.await(10,TimeUnit.SECONDS));release.countDown();assertTrue(disabled.get(10,TimeUnit.SECONDS));
                assertInstanceOf(Conflict.class,assertThrows(ExecutionException.class,()->confirmation.get(10,TimeUnit.SECONDS)).getCause());
            }finally{release.countDown();}
        }
        assertEquals(1L,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Long.class,id));
        assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM meeting_access_grant WHERE meeting_id=?",Integer.class,id));
    }
    @Test void readWaitingForRevocationSeesCommittedRosterNotOldSnapshot()throws Exception{
        var writer=new AtomicReference<Thread>();var reader=new AtomicReference<Thread>();
        var ready=new CountDownLatch(1);var readerAtIdentity=new CountDownLatch(1);var release=new CountDownLatch(1);
        var source=new ProbeSource(actual,new Probe(){
            public void before(String sql,Map<Integer,Object> args){if(Thread.currentThread()==reader.get()&&sql.contains("FROM enterprise_identity"))readerAtIdentity.countDown();}
            public void beforeCommit(){if(Thread.currentThread()==writer.get()){ready.countDown();await(release);}}
        });
        var runtime=runtime(source);String id=runtime.meetings.create(actor,"create","trace").id();runtime.meetings.save(actor,id,"draft",info(0,List.of(member)),"trace");
        runtime.rosters.confirm(actor,id,"confirm",new ConfirmRoster(1L),"trace");runtime.meetings.save(actor,id,"remove",info(2,List.of()),"trace");
        try(var threads=Executors.newFixedThreadPool(2)){
            var revoked=threads.submit(()->{writer.set(Thread.currentThread());return runtime.rosters.confirm(actor,id,"revoke",new ConfirmRoster(3L),"trace");});
            try{
                assertTrue(ready.await(10,TimeUnit.SECONDS));
                var read=threads.submit(()->{reader.set(Thread.currentThread());return runtime.meetings.read(new Actor(member,Status.ACTIVE,Set.of(Role.USER)),id);});
                assertTrue(readerAtIdentity.await(10,TimeUnit.SECONDS));release.countDown();assertEquals(4,revoked.get(10,TimeUnit.SECONDS).revision());
                assertInstanceOf(MeetingAccess.AccessDeniedException.class,assertThrows(ExecutionException.class,()->read.get(10,TimeUnit.SECONDS)).getCause());
            }finally{release.countDown();}
        }
    }
    @Test void concurrentConfirmationsHaveOneVersionAndOneEffect()throws Exception{
        var runtime=runtime(actual);String id=runtime.meetings.create(actor,"create","trace").id();runtime.meetings.save(actor,id,"draft",info(0,List.of(member)),"trace");
        try(var threads=Executors.newFixedThreadPool(2)){
            var go=new CountDownLatch(1);
            var one=threads.submit(()->{go.await();try{runtime.rosters.confirm(actor,id,"one",new ConfirmRoster(1L),"trace");return 200;}catch(Conflict e){return 409;}});
            var two=threads.submit(()->{go.await();try{runtime.rosters.confirm(actor,id,"two",new ConfirmRoster(1L),"trace");return 200;}catch(Conflict e){return 409;}});go.countDown();
            assertEquals(List.of(200,409),java.util.stream.Stream.of(one.get(10,TimeUnit.SECONDS),two.get(10,TimeUnit.SECONDS)).sorted().toList());
        }
        assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM roster_revision WHERE meeting_id=? AND state='CONFIRMED'",Integer.class,id));
        assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM task_outbox WHERE subject_id=? AND event_type='MEETING_ROSTER_CONFIRMED'",Integer.class,id));
    }
    @Test void actualServiceLocksSortedIdentitiesBeforeRootAndFlushesLast(){
        String prefix="sort-"+UUID.randomUUID()+"-",bmp=prefix+"\uE000",nonBmp=prefix+"\uD800\uDC00";
        for(String person:List.of(bmp,nonBmp))j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",person,person);
        var sqls=new ArrayList<String>();var identities=new ArrayList<String>();
        var source=new ProbeSource(actual,new Probe(){public void before(String sql,Map<Integer,Object> args){sqls.add(sql);if(sql.contains("FROM enterprise_identity")&&sql.contains("FOR UPDATE"))identities.add((String)args.get(1));}});
        var runtime=runtime(source);String id=runtime.meetings.create(actor,"create","trace").id();sqls.clear();identities.clear();
        runtime.meetings.save(actor,id,"save",new SaveInfo(0L,"saved","controlled-category","summary",java.time.LocalDate.of(2026,10,21),List.of(nonBmp,member,bmp),List.of()),"trace");
        // U+E000 precedes U+10000 in UTF-8/binary order; Java natural UTF-16 order reverses them.
        var expected=List.of(member,owner,bmp,nonBmp);
        assertEquals(expected,identities);
        assertEquals(expected,j.queryForList("SELECT enterprise_id FROM enterprise_identity WHERE enterprise_id IN (?,?,?,?) ORDER BY enterprise_id COLLATE utf8mb4_0900_bin",String.class,member,owner,bmp,nonBmp));
        int lastIdentity=-1,subject=-1,root=-1,audit=-1,outbox=-1;
        for(int i=0;i<sqls.size();i++){String sql=sqls.get(i);if(sql.contains("FROM enterprise_identity")&&sql.contains("FOR UPDATE"))lastIdentity=i;if(sql.contains("FROM task_subject"))subject=i;if(sql.startsWith("SELECT * FROM meeting WHERE"))root=i;if(sql.startsWith("INSERT INTO audit_event"))audit=i;if(sql.startsWith("INSERT INTO task_outbox("))outbox=i;}
        assertTrue(lastIdentity<subject&&subject<root&&root<audit&&audit<outbox);
        assertTrue(sqls.getLast().startsWith("UPDATE task_outbox_sequence"),sqls.toString());
        System.out.println("B03 binary identity locking matched actual MySQL utf8mb4_0900_bin: ASCII then U+E000 then U+10000; final SQL="+sqls.getLast());
        assertEquals("controlled-category",j.queryForObject("SELECT category_id FROM meeting_search_projection WHERE meeting_id=?",String.class,id));
        assertEquals(java.time.LocalDate.of(2026,10,21),runtime.meetings.read(actor,id).occurredOn());
        assertThrows(IllegalArgumentException.class,()->runtime.meetings.save(actor,id,"bad-category",new SaveInfo(1L,"saved","invented",null,null,List.of(),List.of()),"trace"));
    }
    @Test void guessedFutureCasCannotBypassCompleteRemovedIdentityPrelock()throws Exception{
        var original=runtime(actual);String id=original.meetings.create(actor,"create","trace").id();
        var discovering=new CountDownLatch(1);var resume=new CountDownLatch(1);var first=new AtomicBoolean(true);
        var source=new ProbeSource(actual,new Probe(){public void before(String sql,Map<Integer,Object> args){
            if(sql.contains("FROM enterprise_identity")&&sql.contains("FOR UPDATE")&&first.compareAndSet(true,false)){discovering.countDown();await(resume);}
        }});
        var delayed=runtime(source);
        try(var threads=Executors.newSingleThreadExecutor()){
            var confirmation=threads.submit(()->delayed.rosters.confirm(actor,id,"future",new ConfirmRoster(3L),"trace"));
            try{
                assertTrue(discovering.await(10,TimeUnit.SECONDS));
                original.meetings.save(actor,id,"add",info(0,List.of(member)),"trace");
                original.rosters.confirm(actor,id,"grant",new ConfirmRoster(1L),"trace");
                original.meetings.save(actor,id,"draft-empty",info(2,List.of()),"trace");
                resume.countDown();
                assertInstanceOf(Conflict.class,assertThrows(ExecutionException.class,()->confirmation.get(10,TimeUnit.SECONDS)).getCause());
            }finally{resume.countDown();}
        }
        assertEquals(3L,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Long.class,id));
        assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM meeting_access_grant WHERE meeting_id=?",Integer.class,id));
    }
    interface Probe {default void before(String sql,Map<Integer,Object> args){} default void beforeCommit(){} }
    static void await(CountDownLatch gate){try{if(!gate.await(10,TimeUnit.SECONDS))throw new AssertionError("JDBC gate timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
    static final class ProbeSource extends AbstractDataSource {
        final DataSource delegate;final Probe probe;
        ProbeSource(DataSource delegate,Probe probe){this.delegate=delegate;this.probe=probe;}
        @Override public Connection getConnection()throws SQLException{
            var connection=delegate.getConnection();connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
                if(method.getName().equals("commit"))probe.beforeCommit();
                Object result=invoke(connection,method,args);
                if(method.getName().equals("prepareStatement")){
                    String sql=(String)args[0];var params=new HashMap<Integer,Object>();Object statement=result;
                    return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PreparedStatement.class},(p,m,a)->{
                        if(m.getName().startsWith("set")&&a!=null&&a.length>=2&&a[0] instanceof Integer n)params.put(n,a[1]);
                        if(m.getName().startsWith("execute"))probe.before(sql,params);
                        return invoke(statement,m,a);
                    });
                }
                return result;
            });
        }
        @Override public Connection getConnection(String user,String password)throws SQLException{return getConnection();}
        static Object invoke(Object target,Method method,Object[] args)throws Throwable{try{return method.invoke(target,args);}catch(InvocationTargetException e){throw e.getCause();}}
    }
}
