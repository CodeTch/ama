package ama.material;

import ama.contract.MinutesContracts.*;
import java.util.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Exact immutable snapshot/parser choice. A persisted input must have its own context, never current pointers. */
public final class MaterialBinding {
 public static List<ParserSelection> selections(StoredProvenanceFacts facts,String meeting,String snapshot,String input){
  if(snapshot==null)return List.of();var rows=facts.rows();one(rows,Table.MATERIAL_SNAPSHOT,"id",snapshot);var selected=new ArrayList<ParserSelection>();
  var contexts=matching(rows,Table.SOURCE_CONTEXT,"generation_input_id",input);boolean stored=!matching(rows,Table.GENERATION_INPUT,"id",input).isEmpty();if(stored)require(contexts.size()==1,PROVENANCE_UNAVAILABLE);else require(contexts.isEmpty(),PROVENANCE_UNAVAILABLE);
  FullRow context=contexts.isEmpty()?null:contexts.getFirst();if(context!=null)require(meeting.equals(context.string("meeting_id"))&&snapshot.equals(context.nullableString("material_snapshot_id")),SOURCE_MEMBERSHIP_MISMATCH);
  var members=matching(rows,Table.MATERIAL_SNAPSHOT_ITEM,"snapshot_id",snapshot);require(!members.isEmpty(),SOURCE_MEMBERSHIP_MISMATCH);var ids=new HashSet<String>();
  for(var member:members){String revision=member.string("material_revision_id");require(ids.add(revision),SOURCE_MEMBERSHIP_MISMATCH);var documents=matching(rows,Table.PARSED_DOCUMENT,"material_revision_id",revision);FullRow document;
   if(context==null){require(documents.size()==1,SOURCE_CONTEXT_AMBIGUOUS);document=documents.getFirst();}else{var choice=matching(rows,Table.SOURCE_CONTEXT_MATERIAL,"context_id",context.string("id")).stream().filter(r->revision.equals(r.string("material_revision_id"))).toList();require(choice.size()==1,PROVENANCE_UNAVAILABLE);document=one(rows,Table.PARSED_DOCUMENT,"id",choice.getFirst().string("parsed_document_id"));require(revision.equals(document.string("material_revision_id")),SOURCE_MEMBERSHIP_MISMATCH);}
   var material=one(rows,Table.MATERIAL_REVISION,"id",revision);var reference=one(rows,Table.MATERIAL_REFERENCE,"id",material.string("reference_id"));require(meeting.equals(reference.string("meeting_id")),SOURCE_MEMBERSHIP_MISMATCH);var profile=one(rows,Table.PARSED_DOCUMENT_PROFILE,"document_id",document.string("id"));String parser=document.string("parser_version");require(parser.equals(profile.string("parser_config_version"))&&MaterialProfiles.parser(profile.string("format")).equals(parser),PROFILE_UNAVAILABLE);selected.add(new ParserSelection(revision,parser));
  }
  if(context!=null)require(matching(rows,Table.SOURCE_CONTEXT_MATERIAL,"context_id",context.string("id")).size()==ids.size(),SOURCE_MEMBERSHIP_MISMATCH);return List.copyOf(selected);
 }
 public static List<FullRow> matching(List<FullRow> rows,Table table,String column,String id){return rows.stream().filter(r->r.table()==table&&Objects.equals(r.nullableString(column),id)).toList();}
 public static FullRow one(List<FullRow> rows,Table table,String column,String id){var found=matching(rows,table,column,id);require(found.size()==1,found.size()>1?SOURCE_CONTEXT_AMBIGUOUS:PROVENANCE_UNAVAILABLE);return found.getFirst();}
 private MaterialBinding(){}
}
