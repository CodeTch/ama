package ama.transcript;
import ama.contract.MinutesContracts.*;import java.util.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.security.*;import static ama.contract.MinutesContracts.*;import static ama.contract.MinutesContracts.FailureCode.*;
/** Immutable human attribution. Source coordinates and RAW identities are never relaxed. */
public final class TranscriptSpeakerProof {
 private TranscriptSpeakerProof(){}
 public static String digest(String id,String meeting,String label,String enterprise){
  try{var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);for(String value:List.of("ama.human-speaker.v1",id,meeting,label,enterprise==null?"":enterprise)){byte[] text=value.getBytes(StandardCharsets.UTF_8);out.writeInt(text.length);out.write(text);}return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));}catch(IOException|NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
 }
 public static void validate(List<FullRow> rows){
  if(rows.stream().noneMatch(r->r.table()==Table.TRANSCRIPT_SPEAKER_SNAPSHOT||r.table()==Table.TRANSCRIPT_SPEAKER_CHANGE||r.table()==Table.TRANSCRIPT_SEGMENT&&r.nullableString("speaker_id")!=null))return;
  var snapshots=new HashMap<String,FullRow>();var revisions=new HashMap<String,FullRow>();var segments=new HashMap<String,Map<String,FullRow>>();var proofs=new HashMap<String,Map<String,FullRow>>();
  for(var row:rows){
   if(row.table()==Table.TRANSCRIPT_SPEAKER_SNAPSHOT){String id=row.string("id"),label=row.string("label"),enterprise=row.nullableString("enterprise_id");require(snapshots.put(id,row)==null&&label.length()<=256&&!label.isBlank()&&label.equals(label.strip())&&"HUMAN".equals(row.string("identity_source"))&&(enterprise==null||!enterprise.isBlank()&&enterprise.length()<=255)&&digest(id,row.string("meeting_id"),label,enterprise).equals(row.string("payload_sha256")),PROVENANCE_UNAVAILABLE);}
   if(row.table()==Table.TRANSCRIPT_REVISION)require(revisions.put(row.string("id"),row)==null,PROVENANCE_UNAVAILABLE);
   if(row.table()==Table.TRANSCRIPT_SEGMENT)require(segments.computeIfAbsent(row.string("revision_id"),k->new HashMap<>()).put(row.string("segment_id"),row)==null,PROVENANCE_UNAVAILABLE);
   if(row.table()==Table.TRANSCRIPT_SPEAKER_CHANGE)require(proofs.computeIfAbsent(row.string("revision_id"),k->new HashMap<>()).put(row.string("segment_id"),row)==null,PROVENANCE_UNAVAILABLE);
  }
  for(var entry:segments.entrySet()){
   var revision=revisions.get(entry.getKey());require(revision!=null,PROVENANCE_UNAVAILABLE);var changes=proofs.getOrDefault(entry.getKey(),Map.of());var used=new HashSet<String>();
   for(var segment:entry.getValue().values()){
    String speaker=segment.nullableString("speaker_id");if(speaker!=null){var snapshot=snapshots.get(speaker);require(snapshot!=null&&snapshot.string("meeting_id").equals(revision.string("meeting_id")),PROVENANCE_UNAVAILABLE);}
    if("RAW".equals(revision.string("kind"))){require(speaker==null&&changes.isEmpty(),PROVENANCE_UNAVAILABLE);continue;}
    var parent=revisions.get(revision.nullableString("parent_id"));require(parent!=null&&parent.string("meeting_id").equals(revision.string("meeting_id")),PROVENANCE_UNAVAILABLE);var previous=segments.getOrDefault(parent.string("id"),Map.of()).get(segment.string("segment_id"));require(previous!=null,PROVENANCE_UNAVAILABLE);String before=previous.nullableString("speaker_id");
    if(!Objects.equals(before,speaker)){var proof=changes.get(segment.string("segment_id"));require(proof!=null&&Objects.equals(before,proof.nullableString("before_speaker_id"))&&Objects.equals(speaker,proof.nullableString("after_speaker_id")),PROVENANCE_UNAVAILABLE);used.add(segment.string("segment_id"));}
   }
   require(used.equals(changes.keySet()),PROVENANCE_UNAVAILABLE);
  }
  require(segments.keySet().containsAll(proofs.keySet()),PROVENANCE_UNAVAILABLE);
 }
}
