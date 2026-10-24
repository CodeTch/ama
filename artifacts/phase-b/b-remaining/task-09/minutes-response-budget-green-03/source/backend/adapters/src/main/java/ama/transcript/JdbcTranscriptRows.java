package ama.transcript;

import ama.contract.ConfigArchiveContracts.ArchiveReadBudget;
import ama.contract.MinutesContracts.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BooleanSupplier;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Per-acquisition accounting; retained row locks make pre-transfer size gates stable. */
final class JdbcTranscriptRows {
 private final JdbcTemplate j;final ArchiveReadBudget budget;final BooleanSupplier cancel;
 private long count,edges,bytes,points;
 private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'");
 JdbcTranscriptRows(JdbcTemplate j,ArchiveReadBudget budget,BooleanSupplier cancel){this.j=j;this.budget=budget;this.cancel=cancel;check();}
 void check(){ama.contract.MinutesContracts.check(cancel);}
 void row(long links){check();require(count<budget.maxFactRows()&&links<=budget.maxFactEdges()-edges,BUDGET_EXCEEDED);count++;edges+=links;}
 void text(long octets,long scalars,long units){check();require(octets>=0&&scalars>=0&&units>=0&&octets<=budget.maxStringUtf8Bytes()&&units<=budget.maxStringUtf16()&&octets<=budget.maxTotalBytes()-bytes&&scalars<=budget.maxAggregateScalars()-points,BUDGET_EXCEEDED);bytes+=octets;points+=scalars;}
 List<FullRow> rows(Table table,String where,String order,Object... args){
  check();var textColumns=table.columns().stream().filter(c->c.kind()!=CellKind.UINT&&c.kind()!=CellKind.TIMESTAMP).toList();
  String measures=String.join(",",textColumns.stream().map(c->"COALESCE(OCTET_LENGTH("+c.name()+"),0),COALESCE(CHAR_LENGTH("+c.name()+"),0),COALESCE(OCTET_LENGTH(CONVERT("+c.name()+" USING utf16)) DIV 2,0)").toList());
  long remaining=budget.maxFactRows()-count;if(table==Table.INPUT_BATCH_FILE)remaining=Math.min(remaining,budget.maxBatchFiles());
  long limit=remaining==Long.MAX_VALUE?remaining:remaining+1;Object[] bound=Arrays.copyOf(args,args.length+1);bound[args.length]=limit;
  var sizes=j.query("SELECT "+measures+" FROM "+table.sqlName()+" WHERE "+where+" ORDER BY "+order+" LIMIT ? FOR UPDATE",rs->{
   var result=new ArrayList<long[]>();
   while(rs.next()){
    row(table.columns().stream().filter(c->c.kind()==CellKind.UUID).count());
    require(result.size()<Integer.MAX_VALUE-8&&result.size()<limit-1,BUDGET_EXCEEDED);
    var values=new long[textColumns.size()*3];for(int i=0;i<textColumns.size();i++){
     long b=rs.getLong(i*3+1),p=rs.getLong(i*3+2),u=rs.getLong(i*3+3);text(b,p,u);
     if(textColumns.get(i).name().equals("config_manifest"))require(b<=budget.maxManifestBytes(),BUDGET_EXCEEDED);
     values[i*3]=b;values[i*3+1]=p;values[i*3+2]=u;
    }result.add(values);
   }return result;
  },bound);
  String projection=String.join(",",table.columns().stream().map(ColumnSpec::name).toList());
  var result=j.query("SELECT "+projection+" FROM "+table.sqlName()+" WHERE "+where+" ORDER BY "+order+" LIMIT ?",rs->{
   var values=new ArrayList<FullRow>();while(rs.next()){
    check();require(values.size()<sizes.size(),READ_FAILED);var cells=new ArrayList<Cell>();int textIndex=0;
    for(var col:table.columns()){
     if(col.kind()==CellKind.UINT){var n=rs.getBigDecimal(col.name());try{cells.add(n==null?new NullCell():cell(n.longValueExact()));}catch(ArithmeticException e){throw fail(INPUT_INVALID);}}
     else if(col.kind()==CellKind.TIMESTAMP){var time=rs.getObject(col.name(),LocalDateTime.class);cells.add(time==null?new NullCell():cell(TIME.format(time)));}
     else {
      String v;if(col.name().equals("config_manifest"))v=stream(rs,col.name());else v=rs.getString(col.name());
      long[] expected=sizes.get(values.size());
      if(v!=null){scalars(v);require(v.length()==expected[textIndex*3+2]&&v.codePointCount(0,v.length())==expected[textIndex*3+1]&&ama.canonical.CanonicalValueCodec.utf8Length(v,cancel)==expected[textIndex*3],READ_FAILED);}
      else require(expected[textIndex*3]==0,READ_FAILED);
      textIndex++;cells.add(v==null?new NullCell():cell(v));
     }
    }values.add(new FullRow(table,cells));
   }return values;
  },bound);
  require(result.size()==sizes.size(),READ_FAILED);check();return result;
 }
 private String stream(ResultSet rs,String name)throws SQLException{
  try(var reader=rs.getCharacterStream(name)){
   if(reader==null)return null;var out=new StringBuilder();var buffer=new char[Math.min(1024,budget.maxStringUtf16())];
   for(int n;(n=reader.read(buffer))!=-1;){check();require(n<=budget.maxStringUtf16()-out.length(),BUDGET_EXCEEDED);out.append(buffer,0,n);}return out.toString();
  }catch(java.io.IOException e){throw fail(READ_FAILED);}
 }
}
