package ama.task;
import ama.contract.ExternalContracts.*;
/** Test child is terminated only after parent observes the real committed marker through another connection. */
public final class MarkerCrashProbe {public static void main(String[] args)throws Exception{var f=new TaskFixture();var q=f.queue();var row=q.row(args[0],false,false).orElseThrow();var lease=q.claim(row.spec().pool(),"marker-process").orElseThrow();var s=lease.spec();new JdbcExternalOperations(q).beginExternal(lease,new ExternalRequest("TEST_CALL",s.producerKey(),TaskFixture.hash(s.producerKey()),TaskFixture.hash(s.input().id()),"test-v1:"+s.input().id())).orElseThrow();Thread.sleep(120000);}}
