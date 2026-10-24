package ama.storage;
import java.util.*;
/** Pure fixture lifecycle decisions. No state here grants a send, retry or no-effect proof. */
final class Task11PublicationWaiter {
 enum Decision { WAIT, SUCCESS, TERMINAL_FAILURE, UNRESOLVED, LOCAL_STOP, TIMEOUT }
 record State(String task,String operation,String execution,boolean handlerDone,boolean handlerFailed) {
  boolean hasExternal(){return operation!=null||execution!=null;}
  boolean both(String value){return value.equals(operation)&&value.equals(execution);}
  boolean unresolved(){return hasExternal()&&!both("SUCCEEDED")&&!both("FAILED");}
 }
 static Decision decide(State s,boolean expired){
  if(s.handlerDone()){
   if(!s.handlerFailed()&&"SUCCEEDED".equals(s.task())&&s.both("SUCCEEDED"))return Decision.SUCCESS;
   if(s.unresolved())return Decision.UNRESOLVED;
   if(Set.of("FAILED","STALE","CANCELLED").contains(s.task())&&s.both("FAILED"))return Decision.TERMINAL_FAILURE;
   if(s.handlerFailed()&&!s.hasExternal())return Decision.LOCAL_STOP;
  }
  return expired?Decision.TIMEOUT:Decision.WAIT;
 }
 /** Capture before closing worker, then fresh capture after close; unknown close outcome retains provider. */
 static boolean retainProvider(State before,State after,boolean workerClosed,boolean anyOtherUnresolved){return !workerClosed||after==null||before.unresolved()||after.unresolved()||anyOtherUnresolved;}
 static void expect(Decision wanted,State actual,boolean expired){if(decide(actual,expired)!=wanted)throw new AssertionError(wanted+" "+actual);}
 public static void main(String[] args){int checks=0;
  for(String task:List.of("READY","RUNNING","RECONCILING"))for(String external:List.of("PREPARED","MARKED","UNKNOWN")){expect(Decision.WAIT,new State(task,external,external,false,false),false);checks++;}
  expect(Decision.WAIT,new State("RECONCILING","MARKED","MARKED",false,false),false);checks++;
  expect(Decision.WAIT,new State("SUCCEEDED","SUCCEEDED","SUCCEEDED",false,false),false);checks++;
  expect(Decision.SUCCESS,new State("SUCCEEDED","SUCCEEDED","SUCCEEDED",true,false),false);checks++;
  expect(Decision.UNRESOLVED,new State("RECONCILING","UNKNOWN","UNKNOWN",true,false),false);checks++;
  expect(Decision.UNRESOLVED,new State("RECONCILING","MARKED","MARKED",true,true),false);checks++;
  expect(Decision.TIMEOUT,new State("RECONCILING","MARKED","MARKED",false,false),true);checks++;
  expect(Decision.TIMEOUT,new State("SUCCEEDED","UNKNOWN","UNKNOWN",false,false),true);checks++;
  expect(Decision.LOCAL_STOP,new State("RETRYABLE",null,null,true,true),false);checks++;
  expect(Decision.TERMINAL_FAILURE,new State("FAILED","FAILED","FAILED",true,false),false);checks++;
  var marked=new State("RECONCILING","MARKED","MARKED",false,false);var success=new State("SUCCEEDED","SUCCEEDED","SUCCEEDED",true,false);
  if(!retainProvider(marked,success,true,false)||!retainProvider(success,success,false,false)||!retainProvider(success,null,true,false)||!retainProvider(success,success,true,true)||retainProvider(success,success,true,false))throw new AssertionError("Provider retention policy");checks+=5;
  System.out.println("{\"checks\":"+checks+",\"passed\":true,\"businessCalls\":0,\"databaseCalls\":0,\"providerStarts\":0}");
 }
}
