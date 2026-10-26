package ama.storage;
import java.util.*;import static org.junit.jupiter.api.Assertions.*;
/** Actual same-call SQL prefix proof. No exception stack or copied admission implementation. */
final class Task11Fix02WorkerPrefix {
 static final String BODY="SELECT request_manifest FROM publication_submission WHERE task_id=? AND OCTET_LENGTH(request_manifest)=? AND CHAR_LENGTH(request_manifest)=? AND OCTET_LENGTH(CONVERT(request_manifest USING utf16))/2=?";
 static Map<String,Object> verify(List<Map<String,Object>> normal,List<Map<String,Object>> denied){
  int body=-1;for(int i=0;i<normal.size();i++)if(BODY.equals(normal.get(i).get("sql"))){assertEquals(-1,body);body=i;}
  assertTrue(body>0,"Normal prepare must actually transfer target body once");assertEquals(body,denied.size(),"Earlier rejection cannot qualify");
  var prefix=normal.subList(0,body);assertEquals(shape(prefix),shape(denied),"Whole SQL and bound transaction partition must match up to target");
  assertTrue(denied.stream().noneMatch(r->BODY.equals(r.get("sql"))));assertTrue((Boolean)normal.get(body).get("transaction"));
  var last=denied.getLast();assertTrue((Boolean)last.get("transaction"));assertTrue(((Number)last.get("holder")).intValue()>0);assertTrue(((String)last.get("sql")).endsWith("FOR UPDATE"));
  assertEquals(normal.get(body-1).get("holder"),normal.get(body).get("holder"));assertEquals(normal.get(body-1).get("physical"),normal.get(body).get("physical"));
  long captures=denied.stream().filter(r->Boolean.TRUE.equals(r.get("transaction"))&&((String)r.get("sql")).endsWith("FOR UPDATE")).map(r->r.get("holder")).distinct().count();assertTrue(captures>=2,"Both real source capture transactions must complete their reads");
  return Map.of("normalBodyQueries",1,"deniedBodyQueries",0,"equalPrefixStatements",body,"lockedTransactionGroups",captures,"lastMetadataSql",last.get("sql"),"lastBoundHolder",last.get("holder"),"lastPhysicalConnection",last.get("physical"));
 }
 private static List<Map<String,Object>> shape(List<Map<String,Object>> rows){var holders=new LinkedHashMap<Object,Integer>();var physical=new LinkedHashMap<Object,Integer>();var result=new ArrayList<Map<String,Object>>();for(var r:rows){Object h=r.get("holder"),p=r.get("physical");result.add(Map.of("sql",r.get("sql"),"transaction",r.get("transaction"),"holder",((Number)h).intValue()==0?0:holders.computeIfAbsent(h,k->holders.size()+1),"physical",physical.computeIfAbsent(p,k->physical.size()+1)));}return result;}
}
