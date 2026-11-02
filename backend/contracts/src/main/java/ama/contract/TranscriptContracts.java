package ama.contract;
import java.util.*;
import static ama.contract.MinutesContracts.*;
/** Stable protocol envelope; deployment admission is separate and follows accepted-result lookup. */
public final class TranscriptContracts {
 private TranscriptContracts(){}
 public enum Operation { SAVE, MARKS, RESTORE, CONFIRM, REPLACE, UNDO, CORRECTION, SPEAKERS }
 public enum CorrectionDecision { ACCEPT, REJECT }
 public record SpeakerEdit(List<String> segmentIds,String label,String enterpriseId){
  public SpeakerEdit{if(segmentIds==null||segmentIds.isEmpty()||segmentIds.size()>4096||new HashSet<>(segmentIds).size()!=segmentIds.size())throw new IllegalArgumentException("Explicit speaker segments required");segmentIds=List.copyOf(segmentIds);segmentIds.forEach(ama.contract.MinutesContracts::uuid);scalars(label);if(label.isBlank()||label.length()>256||!label.equals(label.strip()))throw new IllegalArgumentException("Bounded human speaker label required");if(enterpriseId!=null){scalars(enterpriseId);if(enterpriseId.isBlank()||enterpriseId.length()>255)throw new IllegalArgumentException("Invalid speaker identity");}}
 }
 public record Correction(String candidateId,CorrectionDecision decision){public Correction{uuid(candidateId);Objects.requireNonNull(decision);}}
 public record Edit(String segmentId,String body,List<Mark> marks){
  public Edit {uuid(segmentId);if(body!=null)scalars(body);if(marks==null||marks.size()>4||new HashSet<>(marks).size()!=marks.size())throw new Failure(422,"INVALID_MARKS");marks=marks.stream().sorted().toList();}
 }
 public record Replacement(String find,String replacement,List<String> occurrenceIds){
  public Replacement {
   scalars(find);scalars(replacement);
   if(find.isEmpty()||find.length()>1024||replacement.length()>65536||occurrenceIds==null||occurrenceIds.isEmpty()||occurrenceIds.size()>10000)throw new IllegalArgumentException("Invalid replacement selection");
   occurrenceIds=List.copyOf(occurrenceIds);var ids=new HashSet<String>();for(String id:occurrenceIds)if(id==null||!id.matches("[0-9a-f]{64}")||!ids.add(id))throw new IllegalArgumentException("Invalid occurrence identity");
  }
 }
 public record Command(Operation operation,long expectedRevision,String sourceRevisionId,List<Edit> segments,Replacement replacement,Correction correction,SpeakerEdit speakers){
  public Command(Operation operation,long expectedRevision,String sourceRevisionId,List<Edit> segments,Replacement replacement,Correction correction){this(operation,expectedRevision,sourceRevisionId,segments,replacement,correction,null);}
  public Command(Operation operation,long expectedRevision,String sourceRevisionId,List<Edit> segments,Replacement replacement){this(operation,expectedRevision,sourceRevisionId,segments,replacement,null);}
  public Command(Operation operation,long expectedRevision,String sourceRevisionId,List<Edit> segments){this(operation,expectedRevision,sourceRevisionId,segments,null);}
  public Command {Objects.requireNonNull(operation);uuid(sourceRevisionId);if(expectedRevision<0||expectedRevision>Long.MAX_VALUE-1)throw new IllegalArgumentException("Invalid revision");if(segments==null||segments.size()>100000)throw new Failure(413,"REQUEST_TOO_LARGE");segments=List.copyOf(segments);
   if((operation==Operation.REPLACE)!=(replacement!=null))throw new IllegalArgumentException("Replacement operation shape required");
   if((operation==Operation.SPEAKERS)!=(speakers!=null))throw new IllegalArgumentException("Speaker operation shape required");
   if((operation==Operation.CORRECTION)!=(correction!=null))throw new IllegalArgumentException("Correction operation shape required");
   if((operation==Operation.SAVE||operation==Operation.MARKS)==segments.isEmpty())throw new Failure(422,"INVALID_SEGMENTS");
   var ids=new HashSet<String>();long size=0;for(var s:segments){if(!ids.add(s.segmentId())||(operation==Operation.SAVE)!=(s.body()!=null))throw new Failure(422,"INVALID_SEGMENTS");size+=s.body()==null?0:s.body().length();if(size>8388608)throw new Failure(413,"REQUEST_TOO_LARGE");}
  }
 }
 public static final class Failure extends RuntimeException {private final int status;private final String code;public Failure(int status,String code){super(code,null,false,false);this.status=status;this.code=code;}public int status(){return status;}public String code(){return code;}}
}
