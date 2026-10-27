package ama.meeting;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.Actor;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Authentic held gate -> complete protected numeric metadata -> shared reservation -> exact-PK transfer. */
final class BoundedMeetingMetadata {
 private record Size(boolean nil,long bytes,long points,long units){}
 private record Row(List<String> keys,List<Size> sizes){}
 static final class Selection {
  private final BoundedMeetingMetadata issuer;private final String table;private final List<String> keys,fields;private final List<Row> rows;private boolean consumed;
  private Selection(BoundedMeetingMetadata issuer,String table,List<String> keys,List<String> fields,List<Row> rows){this.issuer=issuer;this.table=table;this.keys=keys;this.fields=fields;this.rows=List.copyOf(rows);}
  int size(){return rows.size();}
 }
 private final DataSource source;private final JdbcTemplate jdbc;private final JdbcMeetingAccess access;private final Actor actor;private final String meeting;private final MeetingAccess.Locked held;private final Object holder;private final WorkspaceReadBudget budget;
 private int phase;private String lastParent="",lastChildren="";private final Set<String> parents=new HashSet<>();
 BoundedMeetingMetadata(DataSource source,JdbcMeetingAccess access,Actor actor,String meeting,MeetingAccess.Locked held,WorkspaceReadBudget budget) {
  this.source=source;this.access=access;this.actor=actor;this.meeting=meeting;this.held=held;this.budget=budget;
  access.requireProjectionSource(source);holder=TransactionSynchronizationManager.getResource(source);jdbc=new JdbcTemplate(source);jdbc.setQueryTimeout(5);check();
 }
 Selection capture(String table,String shape,String predicate,int maximum,Object...args) {
  check();if(phase>=3||maximum<1||maximum>256)throw unavailable();
  List<String> keys;
  if(table.equals("meeting_info_revision")){if(phase!=0)throw unavailable();phase=1;keys=List.of("id");}
  else if(table.equals("roster_revision")){if(phase>1||args.length<1||!(args[0] instanceof String id)||id.compareTo(lastParent)<=0)throw unavailable();lastParent=(String)args[0];parents.add(lastParent);keys=List.of("id");phase=1;}
  else if(table.equals("roster_member")){if(args.length!=1||!(args[0] instanceof String id)||!parents.contains(id)||id.compareTo(lastChildren)<=0)throw unavailable();lastChildren=(String)args[0];keys=List.of("roster_id","member_id");phase=2;}
  else throw unavailable();
  var fields=List.of(shape.split(" "));long maximumRows=maximum+1L;
  // Fixed PKs and numeric length/null metadata are also admitted before this SELECT and allocation.
  long metadataBytes=Math.multiplyExact(maximumRows,Math.addExact(keys.size()*36L,fields.size()*32L));
  budget.reserve(maximumRows,Math.multiplyExact(maximumRows,keys.size()+fields.size()),metadataBytes,maximumRows*keys.size()*36L,maximumRows*keys.size()*36L,4,false);
  var projections=new ArrayList<String>(keys);
  for(int i=0;i<fields.size();i++){String f=fields.get(i).replace("#","");projections.add("("+f+" IS NULL) __n"+i);projections.add("COALESCE(OCTET_LENGTH("+f+"),0) __b"+i);projections.add("COALESCE(CHAR_LENGTH("+f+"),0) __p"+i);projections.add("COALESCE(OCTET_LENGTH(CONVERT("+f+" USING utf16)) DIV 2,0) __u"+i);}
  Object[] parameters=Arrays.copyOf(args,args.length+1);parameters[args.length]=maximumRows;
  var rows=jdbc.query("SELECT "+String.join(",",projections)+" FROM "+table+" WHERE "+predicate+" ORDER BY "+String.join(",",keys)+" LIMIT ? FOR UPDATE",result->{
   var captured=new ArrayList<Row>();
   while(result.next()) {
    budget.check();if(captured.size()>=maximum)throw unavailable();var pk=new ArrayList<String>();
    for(String key:keys){String value=result.getString(key);try{ama.contract.OutboxContracts.uuid(value);}catch(IllegalArgumentException invalid){throw unavailable();}pk.add(value);}
    var sizes=new ArrayList<Size>();
    for(int i=0;i<fields.size();i++){boolean nil=result.getBoolean("__n"+i);long b=result.getLong("__b"+i),p=result.getLong("__p"+i),u=result.getLong("__u"+i);if(b<0||p<0||u<0||p>b||p>u||nil&&(b!=0||p!=0||u!=0))throw unavailable();sizes.add(new Size(nil,b,p,u));}
    captured.add(new Row(List.copyOf(pk),List.copyOf(sizes)));
   }
   return List.copyOf(captured);
  },parameters);
  if(new HashSet<>(rows.stream().map(Row::keys).toList()).size()!=rows.size())throw unavailable();
  for(var row:rows)for(var size:row.sizes)budget.reserve(1,1,size.bytes,size.points,size.units,4,true);
  check();return new Selection(this,table,keys,fields,rows);
 }
 List<Map<String,Object>> read(Selection selected) {
  check();if(selected==null||selected.issuer!=this||selected.consumed)throw unavailable();selected.consumed=true;phase=3;if(selected.rows.isEmpty())return List.of();
  var parameters=new ArrayList<Object>();var clauses=new ArrayList<String>();
  for(var row:selected.rows){clauses.add("("+String.join(" AND ",selected.keys.stream().map(k->k+"=?").toList())+")");parameters.addAll(row.keys);}
  var columns=new LinkedHashSet<>(selected.keys);selected.fields.forEach(f->columns.add(f.replace("#","")));
  var result=jdbc.query("SELECT "+String.join(",",columns)+" FROM "+selected.table+" WHERE "+String.join(" OR ",clauses)+" ORDER BY "+String.join(",",selected.keys),rs->{
   var output=new ArrayList<Map<String,Object>>();
   while(rs.next()) {
    budget.check();if(output.size()>=selected.rows.size())throw unavailable();var expected=selected.rows.get(output.size());
    for(int k=0;k<selected.keys.size();k++)if(!expected.keys.get(k).equals(rs.getString(selected.keys.get(k))))throw unavailable();
    var row=new LinkedHashMap<String,Object>();
    for(int i=0;i<selected.fields.size();i++) {
     String field=selected.fields.get(i),name=field.replace("#","");var measured=expected.sizes.get(i);String value=rs.getString(name);
     if((value==null)!=measured.nil||value!=null&&(utf8(value)!=measured.bytes||value.codePointCount(0,value.length())!=measured.points||value.length()!=measured.units))throw unavailable();
     if(field.endsWith("#")){try{row.put(name,value==null?null:Long.parseLong(value));}catch(NumberFormatException e){throw unavailable();}}else row.put(name,value);
    }
    output.add(Collections.unmodifiableMap(row));
   }
   if(output.size()!=selected.rows.size())throw unavailable();return List.copyOf(output);
  },parameters.toArray());
  check();return result;
 }
 private void check(){budget.check();if(holder==null||!TransactionSynchronizationManager.isActualTransactionActive()||TransactionSynchronizationManager.getResource(source)!=holder)throw unavailable();budget.reserve(6,30,2048,1024,1280,4,false);access.requirePrepared(held,actor,meeting,List.of(),MeetingAccess.Action.READ);}
 private static long utf8(String text){long bytes=0;for(int i=0;i<text.length();i++){char c=text.charAt(i);int p=c;if(Character.isHighSurrogate(c)){if(++i>=text.length()||!Character.isLowSurrogate(text.charAt(i)))throw unavailable();p=Character.toCodePoint(c,text.charAt(i));}else if(Character.isLowSurrogate(c))throw unavailable();bytes+=p<=0x7f?1:p<=0x7ff?2:p<=0xffff?3:4;}return bytes;}
 private static WorkspaceReadBudget.Unavailable unavailable(){return new WorkspaceReadBudget.Unavailable();}
}
