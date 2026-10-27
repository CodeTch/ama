package ama.meeting;

import java.nio.charset.StandardCharsets;

/** Nonallocating strict UTF-8 admission before every encoded copy of local navigation metadata. */
public final class WorkspaceText {
 private WorkspaceText(){}
 public static void check(String value,int maxBytes,boolean requireNonblank) {
  if(value==null||value.length()>maxBytes)throw invalid();
  int bytes=0;boolean nonblank=false;
  for(int i=0;i<value.length();i++) {
   char c=value.charAt(i);int point=c;
   if(Character.isHighSurrogate(c)){if(i+1>=value.length()||!Character.isLowSurrogate(value.charAt(i+1)))throw invalid();point=Character.toCodePoint(c,value.charAt(++i));}
   else if(Character.isLowSurrogate(c))throw invalid();
   bytes+=point<=0x7f?1:point<=0x7ff?2:point<=0xffff?3:4;
   if(bytes>maxBytes)throw invalid();
   if(!Character.isWhitespace(point))nonblank=true;
  }
  if(requireNonblank&&!nonblank)throw invalid();
 }
 static byte[] encode(String value,int maxBytes,boolean requireNonblank){check(value,maxBytes,requireNonblank);return value.getBytes(StandardCharsets.UTF_8);}
 private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid or oversized workspace text");}
}
