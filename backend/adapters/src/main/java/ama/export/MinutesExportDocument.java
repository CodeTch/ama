package ama.export;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Detached saved content only. This value does not confer meeting or file authorization. */
public record MinutesExportDocument(String title,String body) {
 public static final int MAX_INPUT_BYTES=1024*1024;
 public MinutesExportDocument {
  Objects.requireNonNull(title);Objects.requireNonNull(body);
  if(title.isBlank()||body.isBlank()||title.length()>MAX_INPUT_BYTES||body.length()>MAX_INPUT_BYTES)throw new IllegalArgumentException("Export input budget");
  valid(title);valid(body);
  if((long)title.getBytes(StandardCharsets.UTF_8).length+body.getBytes(StandardCharsets.UTF_8).length>MAX_INPUT_BYTES)throw new IllegalArgumentException("Export input budget");
 }
 private static void valid(String text){
  for(int i=0;i<text.length();i++){
   char c=text.charAt(i);
   if(Character.isHighSurrogate(c)){if(++i>=text.length()||!Character.isLowSurrogate(text.charAt(i)))throw new IllegalArgumentException("Invalid export scalar");}
   else if(Character.isLowSurrogate(c)||c<32&&c!='\n'&&c!='\r'&&c!='\t'||c==0xfffe||c==0xffff)throw new IllegalArgumentException("Invalid export scalar");
  }
 }
}
