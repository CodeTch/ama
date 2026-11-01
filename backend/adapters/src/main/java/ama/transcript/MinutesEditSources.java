package ama.transcript;
import ama.contract.MinutesContracts.*;import java.util.*;import static ama.contract.MinutesContracts.*;import static ama.contract.MinutesContracts.FailureCode.*;
/** Additional current-selection fence after complete stored provenance has been verified.
 * This helper grants no read or write capability and cannot replace the caller's S/A/F proof. */
final class MinutesEditSources {
 static void requireCurrent(Map<String,Object> root,List<FullRow> rows,Collection<SourceVersion> versions){
  String confirmation=(String)root.get("transcript_confirmation_id"),snapshot=(String)root.get("material_snapshot_id");require(confirmation!=null,CAPABILITY_UNAVAILABLE);
  var confirmations=rows.stream().filter(r->r.table()==Table.TRANSCRIPT_CONFIRMATION&&confirmation.equals(r.string("id"))).toList();require(confirmations.size()==1,PROVENANCE_UNAVAILABLE);
  var members=new HashSet<String>();for(var r:rows)if(r.table()==Table.MATERIAL_SNAPSHOT_ITEM&&Objects.equals(snapshot,r.string("snapshot_id")))members.add(r.string("material_revision_id"));
  var documents=new HashMap<String,FullRow>();for(var r:rows)if(r.table()==Table.PARSED_DOCUMENT)require(documents.put(r.string("id"),r)==null,PROVENANCE_UNAVAILABLE);
  for(var version:versions){
   if(version instanceof TranscriptVersion transcript){require(confirmation.equals(transcript.confirmationId())&&confirmations.getFirst().string("revision_id").equals(transcript.revisionId()),CAPABILITY_UNAVAILABLE);continue;}
   String revision,document,parser;
   if(version instanceof FileVersion file){revision=file.materialRevisionId();document=file.parsedDocumentId();parser=file.parserVersion();}
   else if(version instanceof KnowledgeVersion knowledge){revision=knowledge.materialRevisionId();document=knowledge.parsedDocumentId();parser=knowledge.parserVersion();}
   else throw fail(CAPABILITY_UNAVAILABLE);
   require(snapshot!=null&&revision!=null&&members.contains(revision),CAPABILITY_UNAVAILABLE);var parsed=documents.get(document);require(parsed!=null&&revision.equals(parsed.string("material_revision_id"))&&Objects.equals(parser,parsed.string("parser_version")),PROVENANCE_UNAVAILABLE);
  }
 }
 private MinutesEditSources(){}
}
