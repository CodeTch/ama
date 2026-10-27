package ama.api;

import ama.contract.IdentityContracts.Actor;
import ama.transcript.*;
import java.util.*;

/** Converts the exact replay-normalized values to the existing guarded command ports. */
final class MinutesSavedDispatch {
 private MinutesSavedDispatch() {}
 static Map<String,Object> execute(MinutesEditorRuntime runtime,Actor actor,String meeting,String key,
                                   String operation,MinutesSavedWire.Command command,String trace) {
  String[] value=command.semantics();long expected=command.expected();
  return switch(operation) {
   case "MINUTES_ADOPT" -> runtime.saved().adopt(actor,meeting,key,expected,value[0],value[1],value[2],trace);
   case "MINUTES_REJECT" -> runtime.decisions().reject(actor,meeting,key,expected,value[0],trace);
   case "MINUTES_DISPOSE" -> runtime.decisions().dispose(actor,meeting,key,expected,value[0],trace);
   case "MINUTES_BODY_SAVE" -> runtime.saved().saveBody(actor,meeting,key,expected,value[0],value[1],value[2],anchors(value),trace);
   case "MINUTES_ELEMENT_EDIT" -> runtime.saved().saveElement(actor,meeting,key,expected,value[0],value[1],value[2],anchors(value),trace);
   case "MINUTES_OPEN_QUESTION" -> runtime.saved().openQuestion(actor,meeting,key,expected,value[0],value[1],value[2],anchors(value),trace);
   case "MINUTES_ACTION_FIELDS" -> runtime.saved().saveActionFields(actor,meeting,key,expected,value[0],value[1],new JdbcMinutesSavedCommands.ActionFields(value[3],value[4],value[5]),trace);
   case "MINUTES_BODY_RESTORE" -> runtime.saved().restoreBody(actor,meeting,key,expected,value[0],trace);
   case "MINUTES_SET_RESTORE" -> runtime.saved().restoreSet(actor,meeting,key,expected,value[0],trace);
   case "MINUTES_TEMPLATE" -> runtime.settings().selectTemplate(actor,meeting,key,expected,value[0],trace);
   case "MINUTES_EXCLUSIONS" -> runtime.settings().replaceExclusions(actor,meeting,key,expected,value[0],exclusions(value),trace);
   case "MINUTES_REVIEW" -> runtime.reviews().review(actor,meeting,key,expected,value[0],value[1],value[2],value[3],value[4],counter(value[5]),counter(value[6]),trace);
   default -> throw new IllegalArgumentException("Unknown minutes operation");
  };
 }
 private static List<JdbcMinutesSavedCommands.Reanchor> anchors(String[] values) {
  var result=new ArrayList<JdbcMinutesSavedCommands.Reanchor>();
  for(int i=3;i<values.length;i+=3)result.add(new JdbcMinutesSavedCommands.Reanchor(values[i],Long.parseLong(values[i+1]),Long.parseLong(values[i+2])));
  return List.copyOf(result);
 }
 private static List<JdbcMinutesSettingsCommands.Exclusion> exclusions(String[] values) {
  var result=new ArrayList<JdbcMinutesSettingsCommands.Exclusion>();
  for(int i=1;i<values.length;i+=3)result.add(new JdbcMinutesSettingsCommands.Exclusion(values[i],values[i+1],values[i+2]));
  return List.copyOf(result);
 }
 private static Long counter(String value){return value==null?null:Long.valueOf(value);}
}
