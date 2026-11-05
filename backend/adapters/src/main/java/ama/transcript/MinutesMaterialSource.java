package ama.transcript;
import ama.contract.MinutesContracts.*;import java.util.*;import static ama.contract.MinutesContracts.*;import static ama.contract.MinutesContracts.FailureCode.*;
/** Projection of an exact source version already derived from the target's complete stored graph. */
final class MinutesMaterialSource {
 record Content(String text,String materialRevisionId,String fileId){}
 static Content read(List<FullRow> rows,FullRow locator,SourceVersion version){
  String revision,document,parser,file;
  if(version instanceof FileVersion value){revision=value.materialRevisionId();document=value.parsedDocumentId();parser=value.parserVersion();file=value.fileId();}
  else if(version instanceof KnowledgeVersion value){revision=value.materialRevisionId();document=value.parsedDocumentId();parser=value.parserVersion();file=value.contentFileId();}
  else throw fail(CAPABILITY_UNAVAILABLE);
  var location=ama.minutes.contract.RowValues.locator(locator);
  require(revision!=null,PROVENANCE_UNAVAILABLE);
  // Physical locators identify a file and position; only a whole knowledge-version
  // locator contains a material revision. The target-derived version supplies the
  // exact revision/parser for both local and knowledge-backed physical excerpts.
  if(location instanceof KnowledgeLocator value)require(version instanceof KnowledgeVersion&&revision.equals(value.materialRevisionId()),SOURCE_MEMBERSHIP_MISMATCH);
  else if(location instanceof PageLocator value)require(file.equals(value.fileId()),SOURCE_MEMBERSHIP_MISMATCH);
  else if(location instanceof ParagraphLocator value)require(file.equals(value.fileId()),SOURCE_MEMBERSHIP_MISMATCH);
  else throw fail(CAPABILITY_UNAVAILABLE);
  var documents=rows.stream().filter(r->r.table()==Table.PARSED_DOCUMENT&&Objects.equals(document,r.string("id"))&&revision.equals(r.string("material_revision_id"))&&Objects.equals(parser,r.string("parser_version"))).toList();require(documents.size()==1,PROVENANCE_UNAVAILABLE);
  var units=rows.stream().filter(r->r.table()==Table.PARSED_UNIT&&document.equals(r.string("document_id"))).sorted(Comparator.comparingLong(r->r.number("ordinal"))).toList();require(units.size()==documents.getFirst().number("unit_count"),PROVENANCE_UNAVAILABLE);
  if("KNOWLEDGE_VERSION".equals(locator.string("kind"))){require(version instanceof KnowledgeVersion,SOURCE_MEMBERSHIP_MISMATCH);return new Content(String.join("\n\n",units.stream().map(r->r.string("body")).toList()),revision,file);}
  String kind=locator.string("kind");require(Set.of("PDF_PAGE","PPT_PAGE","WORD_PARAGRAPH","TEXT_PARAGRAPH").contains(kind),CAPABILITY_UNAVAILABLE);
  var selected=units.stream().filter(r->Set.of("PDF_PAGE","PPT_PAGE").contains(kind)?"PAGE".equals(r.string("kind"))&&Objects.equals(locator.nullableNumber("page_no"),r.nullableNumber("page_no")):"PARAGRAPH".equals(r.string("kind"))&&Objects.equals(locator.nullableNumber("paragraph_no"),r.nullableNumber("paragraph_no"))&&Objects.equals(locator.nullableString("section_path"),r.nullableString("section_path"))).toList();require(selected.size()==1,SOURCE_MEMBERSHIP_MISMATCH);
  return new Content(selected.getFirst().string("body"),revision,file);
 }
 private MinutesMaterialSource(){}
}
