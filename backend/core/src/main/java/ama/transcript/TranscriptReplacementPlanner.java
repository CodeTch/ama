package ama.transcript;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Pure edit planning over a complete verified revision. An occurrence is data, never write authority. */
public final class TranscriptReplacementPlanner {
 public static final int MAX_TEXT_UNITS=8*1024*1024,MAX_OCCURRENCES=10000;
 public record Segment(String segmentId,String body){public Segment{uuid(segmentId);scalar(body);if(body.length()>MAX_TEXT_UNITS)throw invalid();}}
 public record Occurrence(String occurrenceId,String segmentId,int startScalar,int endScalar){}
 private record Match(Occurrence occurrence,int startChar,int endChar){}

 public List<Occurrence> search(String revision,List<Segment> segments,String query){
  return matches(revision,segments,query).stream().map(Match::occurrence).toList();
 }
 public List<Segment> replace(String revision,List<Segment> segments,String query,String replacement,List<String> occurrenceIds){
  scalar(replacement);if(replacement.length()>65536||occurrenceIds==null||occurrenceIds.isEmpty()||occurrenceIds.size()>MAX_OCCURRENCES)throw invalid();
  var selected=new HashSet<String>();for(String id:occurrenceIds)if(id==null||!id.matches("[0-9a-f]{64}")||!selected.add(id))throw invalid();
  var found=new LinkedHashMap<String,List<Match>>();
  for(var match:matches(revision,segments,query))if(selected.remove(match.occurrence().occurrenceId()))found.computeIfAbsent(match.occurrence().segmentId(),ignored->new ArrayList<>()).add(match);
  if(!selected.isEmpty())throw invalid();
  // Reserve the entire expansion before building even the first changed body.
  long total=0;for(var segment:segments){long count=found.getOrDefault(segment.segmentId(),List.of()).size();long length=segment.body().length()+count*(replacement.length()-(long)query.length());if(length<0||length>MAX_TEXT_UNITS-total)throw invalid();total+=length;}
  var result=new ArrayList<Segment>(segments.size());
  for(var segment:segments){
   var edits=found.get(segment.segmentId());if(edits==null){result.add(segment);continue;}
   int size=Math.toIntExact(segment.body().length()+edits.size()*(replacement.length()-(long)query.length()));var body=new StringBuilder(size);int after=0;
   for(var edit:edits){body.append(segment.body(),after,edit.startChar());body.append(replacement);after=edit.endChar();}
   body.append(segment.body(),after,segment.body().length());result.add(new Segment(segment.segmentId(),body.toString()));
  }
  return List.copyOf(result);
 }
 private List<Match> matches(String revision,List<Segment> segments,String query){
  uuid(revision);scalar(query);if(query.isEmpty()||query.length()>1024||segments==null||segments.isEmpty()||segments.size()>100000)throw invalid();
  var ids=new HashSet<String>();long total=0;for(var segment:segments){if(segment==null||!ids.add(segment.segmentId())||segment.body().length()>MAX_TEXT_UNITS-total)throw invalid();total+=segment.body().length();}
  var found=new ArrayList<Match>();int queryScalars=query.codePointCount(0,query.length());
  for(var segment:segments){
   int after=0,scalarAfter=0;String bodyHash=null;
   while(true){
    int start=segment.body().indexOf(query,after);if(start<0)break;if(found.size()>=MAX_OCCURRENCES||Thread.currentThread().isInterrupted())throw invalid();
    if(bodyHash==null)bodyHash=hash(segment.body());
    int scalarStart=scalarAfter+segment.body().codePointCount(after,start),scalarEnd=scalarStart+queryScalars,end=start+query.length();
    String id=identity(revision,segment.segmentId(),bodyHash,scalarStart,scalarEnd,query);
    found.add(new Match(new Occurrence(id,segment.segmentId(),scalarStart,scalarEnd),start,end));after=end;scalarAfter=scalarEnd;
   }
  }
  return List.copyOf(found);
 }
 private static String identity(String revision,String segment,String bodyHash,int start,int end,String query){
  var digest=digest();for(String field:List.of("ama.transcript-occurrence.v1",revision,segment,bodyHash,Integer.toString(start),Integer.toString(end),query)){byte[] bytes=field.getBytes(StandardCharsets.UTF_8);digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());digest.update(bytes);}return HexFormat.of().formatHex(digest.digest());
 }
 private static String hash(String value){return HexFormat.of().formatHex(digest().digest(value.getBytes(StandardCharsets.UTF_8)));}
 private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
 private static void uuid(String value){if(value==null||!value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")||value.equals("00000000-0000-0000-0000-000000000000"))throw invalid();}
 private static void scalar(String value){if(value==null)throw invalid();for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=value.length()||!Character.isLowSurrogate(value.charAt(i)))throw invalid();}else if(Character.isLowSurrogate(c))throw invalid();}}
 private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid or unbounded transcript replacement");}
}
