package ama.export;

import java.util.*;
import java.util.function.BooleanSupplier;

/** Deliberately small display grammar. Links/HTML/images remain inert text. */
final class MinutesExportBlocks {
 sealed interface Block permits Paragraph,Table {}
 record Paragraph(String text,int heading) implements Block {}
 record Table(List<List<String>> rows) implements Block {Table {rows=rows.stream().map(List::copyOf).toList();}}
 static List<Block> parse(String body,BooleanSupplier cancelled){
  var result=new ArrayList<Block>();String[] lines=body.split("\\R",-1);boolean code=false;
  for(int i=0;i<lines.length;i++){
   MinutesExportRenderer.check(cancelled);String line=lines[i];
   if(line.stripLeading().startsWith("```")){code=!code;result.add(new Paragraph(line,0));continue;}
   if(!code&&i+1<lines.length&&line.contains("|")){
    var head=cells(line);var separator=cells(lines[i+1]);
    if(head.size()>1&&head.size()<=32&&separator.size()==head.size()&&separator.stream().allMatch(value->value.matches(":?-{3,}:?"))){
     var rows=new ArrayList<List<String>>();rows.add(head);i++;
     while(i+1<lines.length&&lines[i+1].contains("|")){
      var row=cells(lines[i+1]);if(row.size()!=head.size())break;rows.add(row);i++;
     }
     result.add(new Table(rows));continue;
    }
   }
   int heading=0;if(!code){while(heading<line.length()&&line.charAt(heading)=='#')heading++;if(heading>6||heading==line.length()||line.charAt(heading)!=' ')heading=0;}
   result.add(new Paragraph(heading==0?line:line.substring(heading+1),heading));
  }
  return List.copyOf(result);
 }
 private static List<String> cells(String line){
  line=line.strip();if(line.startsWith("|"))line=line.substring(1);if(line.endsWith("|")&&!line.endsWith("\\|"))line=line.substring(0,line.length()-1);
  var values=new ArrayList<String>();var cell=new StringBuilder();
  for(int i=0;i<line.length();i++){
   char c=line.charAt(i);if(c=='\\'&&i+1<line.length()&&line.charAt(i+1)=='|'){cell.append('|');i++;}
   else if(c=='|'){values.add(cell.toString().strip());cell.setLength(0);}else cell.append(c);
  }
  values.add(cell.toString().strip());return values;
 }
}
