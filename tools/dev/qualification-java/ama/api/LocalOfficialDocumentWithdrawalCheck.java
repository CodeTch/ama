package ama.api;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.*;
import ama.contract.TranscriptContracts;
import ama.minutes.ControlledMinutesDataSource;
import ama.storage.*;
import ama.task.PreparationCancellation;
import ama.transcript.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.springframework.core.env.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Genuine document transport barriers and current identity/provider authority; no business SQL fixtures. */
public final class LocalOfficialDocumentWithdrawalCheck {
    static final ObjectMapper JSON=new ObjectMapper();
    static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    static final PreparationCancellation NEVER=new PreparationCancellation(){public boolean cancelled(){return false;}public AutoCloseable register(Runnable abort){return ()->{};}};
    static final class Barrier implements PreparationCancellation {
        final String mode;
        final CountDownLatch arrived=new CountDownLatch(1),resume=new CountDownLatch(1);
        final AtomicBoolean once=new AtomicBoolean();
        final AtomicInteger registrations=new AtomicInteger();
        Barrier(String mode){this.mode=mode;}
        public boolean cancelled(){return Thread.currentThread().isInterrupted();}
        void pause()throws Exception {
            check(!TransactionSynchronizationManager.isActualTransactionActive(),"Document network barrier must own no DB transaction");
            check(once.compareAndSet(false,true),"Exactly one actual network barrier required");
            arrived.countDown();check(resume.await(20,TimeUnit.SECONDS),"Bounded document barrier release required");
        }
        public AutoCloseable register(Runnable abort){
            registrations.incrementAndGet();
            if(mode.equals("PROVIDER"))try{pause();}catch(Exception failure){throw new IllegalStateException("Actual document registration interrupted",failure);}
            return ()->{if(mode.equals("IDENTITY"))pause();};
        }
    }
    static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    static void provider(Path root,Path output,boolean read)throws Exception {
        var command=new ArrayList<>(List.of("python3",root.resolve("tools/providers/controlled_knowledge.py").toString(),"--config",root.resolve(".tools/local-app/knowledge/provider.json").toString(),"--grant-actor","dev-member"));
        if(read)command.add("--grant-read");
        var process=new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).redirectOutput(output.resolve(read?"provider-restore.log":"provider-revoke.log").toFile()).start();
        try{check(process.waitFor(15,TimeUnit.SECONDS)&&process.exitValue()==0,"Explicit provider grant command must succeed");}
        finally{if(process.isAlive()){process.destroy();if(!process.waitFor(3,TimeUnit.SECONDS)){process.destroyForcibly();process.waitFor(3,TimeUnit.SECONDS);}}}
    }
    public static void main(String[] args)throws Exception {
        check("1".equals(System.getenv("AMA_LOCAL_DEV"))&&args.length==5,"Explicit current daily document qualification required");
        Path file=Path.of(args[0]).toRealPath(),output=Path.of(args[4]).toRealPath();
        check(file.getParent().endsWith(Path.of(".tools/local-app"))&&output.startsWith(file.getParent().resolve("read-withdrawal/document"))&&Files.size(file)<65536,"Fresh owned document evidence directory required");
        String meeting=args[1],publication=args[2],mode=args[3];UUID.fromString(meeting);UUID.fromString(publication);check(Set.of("IDENTITY","PROVIDER").contains(mode),"Exact authority mode required");
        var properties=new Properties();try(var in=Files.newInputStream(file)){properties.load(in);}
        String url=properties.getProperty("database.url");check(url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/ama_local_dev\\?.+"),"Owned daily DB required");
        var env=new StandardEnvironment();env.getPropertySources().addFirst(new PropertiesPropertySource("daily",properties));
        var source=ControlledMinutesDataSource.create(url,properties.getProperty("database.user"),properties.getProperty("database.password"));
        var jdbc=new JdbcTemplate(source);jdbc.setQueryTimeout(5);
        var originalIdentity=jdbc.queryForMap("SELECT enterprise_id,status FROM enterprise_identity WHERE enterprise_id='dev-member'");check("ACTIVE".equals(originalIdentity.get("status")),"Active exact daily member required");
        Files.writeString(output.resolve("original-identity.json"),JSON.writeValueAsString(originalIdentity),StandardOpenOption.CREATE_NEW);
        var configured=ConfiguredProcessing.read(env);var actor=new Actor("dev-member",Status.ACTIVE,Set.of(Role.USER));
        try(var definitions=ConfiguredBackends.read(env);var client=ConfiguredPublication.read(env,configured.read(),configured.index()).client()){
            var catalog=new FileBackendCatalog(source,definitions.backends(),definitions.defaultId());catalog.configure();
            var runtime=LocalAcquisitionBudgetCheck.build(source,env,catalog,client);runtime.minutes().requireCombined();var service=runtime.minutes().publications();check(service!=null,"Genuine combined publication service required");
            var before=LocalMeetingInfoRollbackCheck.snapshot(jdbc,meeting);byte[] baseline=service.officialDocument(actor,meeting,publication,NEVER);check(baseline.length>0,"Actual authorized document baseline required");String baselineSha=sha(baseline);
            var barrier=new Barrier(mode);var returned=new AtomicBoolean();var executor=Executors.newSingleThreadExecutor();var traffic=client.traffic();
            var read=executor.submit(()->{byte[] value=service.officialDocument(actor,meeting,publication,barrier);returned.set(true);return value;});
            boolean sourceChanged=false;String deniedCode=null;
            try{
                check(barrier.arrived.await(20,TimeUnit.SECONDS),"Actual transport barrier must be reached");
                check(barrier.registrations.get()==1,"Exactly one genuine document GET registration required");
                if(mode.equals("IDENTITY")){check(client.traffic().get("getWriteAttempts")==traffic.get("getWriteAttempts")+1,"Actual GET must have been written before identity withdrawal");check(jdbc.update("UPDATE enterprise_identity SET status='DISABLED' WHERE enterprise_id='dev-member' AND status='ACTIVE'")==1,"Exact member status withdrawal required");sourceChanged=true;}
                else{check(client.traffic().equals(traffic),"Provider withdrawal barrier must precede actual wire request");sourceChanged=true;provider(file.getParent().getParent().getParent(),output,false);}
                barrier.resume.countDown();
                try{read.get(30,TimeUnit.SECONDS);throw new AssertionError("Withdrawn authority must not return official HTML");}
                catch(ExecutionException failed){
                    Throwable cause=failed.getCause();
                    if(mode.equals("IDENTITY")){check(cause instanceof MeetingAccess.AccessDeniedException,"Fresh final current identity/meeting ACL denial required");deniedCode="CURRENT_MEETING_AUTHORITY_DENIED";}
                    else{check(cause instanceof TranscriptContracts.Failure f&&f.status()==403&&f.code().equals("OFFICIAL_DOCUMENT_FORBIDDEN"),"Exact current provider read403 required");deniedCode="OFFICIAL_DOCUMENT_FORBIDDEN";}
                }
                check(!returned.get()&&before.equals(LocalMeetingInfoRollbackCheck.snapshot(jdbc,meeting)),"No HTML or business mutation after withdrawal");
                check(client.traffic().get("getWriteAttempts")==traffic.get("getWriteAttempts")+1&&client.traffic().get("postWriteAttempts").equals(traffic.get("postWriteAttempts")),"One actual GET and no publication POST required");
            }finally{
                barrier.resume.countDown();executor.shutdownNow();executor.awaitTermination(5,TimeUnit.SECONDS);
                if(sourceChanged){if(mode.equals("IDENTITY"))jdbc.update("UPDATE enterprise_identity SET status=? WHERE enterprise_id='dev-member'",originalIdentity.get("status"));else provider(file.getParent().getParent().getParent(),output,true);}
            }
            check(originalIdentity.equals(jdbc.queryForMap("SELECT enterprise_id,status FROM enterprise_identity WHERE enterprise_id='dev-member'")),"Original identity restored");
            byte[] restored=service.officialDocument(actor,meeting,publication,NEVER);check(sha(restored).equals(baselineSha)&&before.equals(LocalMeetingInfoRollbackCheck.snapshot(jdbc,meeting)),"Restored authority must read original exact bytes with zero business changes");
            var report=Map.of("pass",true,"mode",mode,"meetingId",meeting,"publicationId",publication,"genuineFactoriesAndClient",true,"actualRootFreeTransportBarrier",true,"denial",deniedCode,"htmlReturnedAfterWithdrawal",false,"restoredOriginalDocumentSha256",baselineSha,"zeroBusinessChanges",true);
            Files.writeString(output.resolve("report.json"),JSON.writeValueAsString(report));System.out.println(JSON.writeValueAsString(report));
        }
    }
}
