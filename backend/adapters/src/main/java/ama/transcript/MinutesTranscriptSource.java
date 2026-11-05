package ama.transcript;

import ama.contract.MinutesContracts.*;
import java.util.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Minimal source-bound projection from the already authorized, captured minutes graph. */
final class MinutesTranscriptSource {
 static Map<String,Object> read(List<FullRow> rows,List<Map<String,Object>> files,FullRow segment){
  var revision=one(rows,Table.TRANSCRIPT_REVISION,segment.string("revision_id"));
  var input=one(rows,Table.TRANSCRIPTION_INPUT,revision.string("processing_input_id"));
  var selected=files.stream().filter(f->segment.string("source_file_id").equals(f.get("id"))).toList();
  require(selected.size()==1,PROVENANCE_UNAVAILABLE);var file=selected.getFirst();
  require(revision.string("meeting_id").equals(file.get("meeting_id")),PROVENANCE_UNAVAILABLE);
  String speaker=segment.nullableString("speaker_id"),label=null;
  if(speaker!=null){var snapshot=one(rows,Table.TRANSCRIPT_SPEAKER_SNAPSHOT,speaker);require(revision.string("meeting_id").equals(snapshot.string("meeting_id")),PROVENANCE_UNAVAILABLE);label=snapshot.string("label");}
  return JdbcMinutesSavedQueries.map("revisionId",revision.string("id"),"segmentId",segment.string("segment_id"),"fileId",file.get("id"),"sha256",file.get("sha256"),"sizeBytesToken",file.get("size_bytes").toString(),"parserVersion",input.string("parser_version"),"ordinalToken",Long.toString(segment.number("ordinal")),"startMs",segment.nullableNumber("start_ms"),"endMs",segment.nullableNumber("end_ms"),"speakerLabel",label);
 }
 private static FullRow one(List<FullRow> rows,Table table,String id){var found=rows.stream().filter(r->r.table()==table&&id.equals(r.string("id"))).toList();require(found.size()==1,PROVENANCE_UNAVAILABLE);return found.getFirst();}
}
