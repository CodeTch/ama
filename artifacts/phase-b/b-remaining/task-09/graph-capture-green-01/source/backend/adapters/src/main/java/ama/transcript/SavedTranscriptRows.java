package ama.transcript;

import ama.contract.MinutesContracts.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Retained metadata row locks, complete PK sets, then exact-key content transfers. */
final class SavedTranscriptRows {
 private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'");
 private final JdbcTemplate j;private final TranscriptAcquisitionBudget.Ledger budget;private final long manifest;
 SavedTranscriptRows(JdbcTemplate j,TranscriptAcquisitionBudget.Ledger budget,long manifest){this.j=j;this.budget=budget;this.manifest=manifest;}
 record Shape(String table,List<String> keys,List<String> columns,Set<String> numbers,Set<String> timestamps) {}
 static Shape shape(Table t){return new Shape(t.sqlName(),t.primaryKey(),t.columns().stream().map(ColumnSpec::name).toList(),t.columns().stream().filter(c->c.kind()==CellKind.UINT).map(ColumnSpec::name).collect(java.util.stream.Collectors.toSet()),t.columns().stream().filter(c->c.kind()==CellKind.TIMESTAMP).map(ColumnSpec::name).collect(java.util.stream.Collectors.toSet()));}
 static Shape metadata(String table,String keys,String columns){var fields=List.of(columns.split(" "));return new Shape(table,List.of(keys.split(",")),fields.stream().map(f->f.replace("#","")).toList(),fields.stream().filter(f->f.endsWith("#")).map(f->f.replace("#","")).collect(java.util.stream.Collectors.toSet()),Set.of());}
 List<FullRow> rows(Table t,String where,String order,int max,boolean projected,Object...args){
  return deferRows(t,where,order,max,projected,args).get();
 }
 java.util.function.Supplier<List<FullRow>> deferRows(Table table,String where,String order,int max,boolean projected,Object...args){
  var shape=shape(table);var admitted=admit(shape,where,order,max,projected,args);
  return ()->transfer(shape,admitted).stream().map(m->new FullRow(table,table.columns().stream().map(c->{Object v=m.get(c.name());return v==null?(Cell)new NullCell():v instanceof Long n?cell(n):cell((String)v);}).toList())).toList();
 }
 Map<String,Object> one(Shape shape,String id){var found=read(shape,"id=?","id",1,false,id);require(found.size()==1,PROVENANCE_UNAVAILABLE);return found.getFirst();}
 private record Measured(List<Object> key,List<long[]> sizes) {}
 List<Map<String,Object>> read(Shape shape,String where,String order,int maximum,boolean projected,Object...args){return deferRead(shape,where,order,maximum,projected,args).get();}
 java.util.function.Supplier<List<Map<String,Object>>> deferRead(Shape shape,String where,String order,int maximum,boolean projected,Object...args){var admitted=admit(shape,where,order,maximum,projected,args);return ()->transfer(shape,admitted);}
 /** ID-only membership proof after the genuine gate; it grants no task authority. */
 List<String> inputTaskIds(String input,int maximum){
  budget.check();require(maximum>0,BUDGET_EXCEEDED);long sentinel=(long)maximum+1;
  // The driver may buffer the complete bounded result. Reserve even its sentinel before execution,
  // including detached list, sorted union and comparison copies, not merely before getString().
  for(long i=0;i<sentinel;i++){budget.row(1);budget.capture(36,36,36,4);}
  return j.query("SELECT id FROM durable_task FORCE INDEX(task_input_lookup) WHERE input_kind='TRANSCRIPTION_INPUT' AND input_id=? ORDER BY id LIMIT ?",rs->{
   var ids=new ArrayList<String>();while(rs.next()){budget.check();require(ids.size()<maximum,BUDGET_EXCEEDED);String id=rs.getString(1);uuid(id);ids.add(id);}return List.copyOf(ids);
  },input,sentinel);
 }
 void reserveTaskId(){budget.row(1);budget.capture(36,36,36,4);}
 private List<Measured> admit(Shape shape,String where,String order,int maximum,boolean projected,Object...args){
  budget.check();require(maximum>0,BUDGET_EXCEEDED);
  var texts=shape.columns.stream().filter(c->!shape.numbers.contains(c)&&!shape.timestamps.contains(c)).toList();
  var lengths=texts.stream().map(c->"COALESCE(OCTET_LENGTH("+c+"),0),COALESCE(CHAR_LENGTH("+c+"),0),COALESCE(OCTET_LENGTH(CONVERT("+c+" USING utf16)) DIV 2,0)").toList();
  long limit=Math.min(maximum,budget.remainingRows())+1;Object[] parameters=Arrays.copyOf(args,args.length+1);parameters[args.length]=limit;
  String measured=String.join(",",shape.keys)+(lengths.isEmpty()?"":","+String.join(",",lengths));
  var admitted=j.query("SELECT "+measured+" FROM "+shape.table+" WHERE "+where+" ORDER BY "+order+" LIMIT ? FOR UPDATE",rs->{
   var result=new ArrayList<Measured>();while(rs.next()){
    budget.check();require(result.size()<maximum&&result.size()<limit-1,BUDGET_EXCEEDED);budget.row(shape.columns.size());
    var sizes=new ArrayList<long[]>();int offset=shape.keys.size();
    // Reserve before any content-bearing query or growing owned row/cell/string captures.
    for(int i=0;i<texts.size();i++){
     long b=rs.getLong(offset+3*i+1),p=rs.getLong(offset+3*i+2),u=rs.getLong(offset+3*i+3);
     if(texts.get(i).equals("config_manifest"))require(b<=manifest,BUDGET_EXCEEDED);
     budget.text(b,p,u,8,projected);sizes.add(new long[]{b,p,u});
    }
    for(var ignored:shape.timestamps)budget.text(27,27,27,8,projected);
    if(projected)budget.responseOverhead(512+shape.columns.size()*64L);
    var key=new ArrayList<Object>();for(String k:shape.keys){Object value=shape.numbers.contains(k)?exact(rs,k):rs.getString(k);if(value instanceof String str)require(str.length()<=64,PROVENANCE_UNAVAILABLE);key.add(value);}
    result.add(new Measured(List.copyOf(key),sizes));
   }return result;
  },parameters);
  return List.copyOf(admitted);
 }
 private List<Map<String,Object>> transfer(Shape shape,List<Measured> admitted){
  var result=new ArrayList<Map<String,Object>>();
  String exactWhere=String.join(" AND ",shape.keys.stream().map(k->k+"=?").toList());
  for(var row:admitted){
   budget.check();var found=j.query("SELECT "+String.join(",",shape.columns)+" FROM "+shape.table+" WHERE "+exactWhere,rs->{
    require(rs.next(),READ_FAILED);var value=new LinkedHashMap<String,Object>();int i=0;
    for(String col:shape.columns){
     if(shape.numbers.contains(col))value.put(col,exact(rs,col));
     else if(shape.timestamps.contains(col)){var time=rs.getObject(col,LocalDateTime.class);value.put(col,time==null?null:TIME.format(time));}
     else {String text=rs.getString(col);var length=row.sizes.get(i++);if(text==null)require(length[0]==0&&length[1]==0&&length[2]==0,READ_FAILED);else {scalars(text);require(text.length()==length[2]&&text.codePointCount(0,text.length())==length[1]&&ama.canonical.CanonicalValueCodec.utf8Length(text,()->false)==length[0],READ_FAILED);}value.put(col,text);}
    }
    require(!rs.next(),READ_FAILED);for(int k=0;k<shape.keys.size();k++)require(Objects.equals(row.key.get(k),value.get(shape.keys.get(k))),READ_FAILED);
    return Collections.unmodifiableMap(value);
   },row.key.toArray());result.add(found);
  }
  budget.check();return List.copyOf(result);
 }
 private static Long exact(ResultSet r,String name)throws SQLException{var n=r.getBigDecimal(name);try{return n==null?null:n.longValueExact();}catch(ArithmeticException e){throw fail(PROVENANCE_UNAVAILABLE);}}
}
