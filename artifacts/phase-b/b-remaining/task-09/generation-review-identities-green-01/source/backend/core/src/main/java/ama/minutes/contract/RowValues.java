package ama.minutes.contract;
import ama.contract.MinutesContracts.*;
import ama.canonical.CanonicalValue;
import ama.canonical.CanonicalValueCodec;
import java.util.*;
import java.util.function.BooleanSupplier;
import static ama.canonical.CanonicalValue.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

public final class RowValues {
 private RowValues(){}
 public static CanonicalValue row(FullRow row) {
  var fields=new ArrayList<Entry>();
  for(int i=0;i<row.cells().size();i++){
   var c=row.cells().get(i);CanonicalValue value=switch(c){case NullCell ignored->nil();case TextCell t->string(t.value());case UIntCell n->number(n.value());};
   fields.add(field(row.table().columns().get(i).name(),value));
  }return new ObjectValue(fields);
 }
 public static int compare(FullRow a,FullRow b,BooleanSupplier cancelled){
  require(a.table()==b.table(),INVALID_ARGUMENT);
  for(var column:a.table().primaryKey()){
   check(cancelled);var av=a.value(column);var bv=b.value(column);
   int c=av instanceof UIntCell x&&bv instanceof UIntCell y?Long.compare(x.value(),y.value()):CanonicalValueCodec.compareUtf8(((TextCell)av).value(),((TextCell)bv).value(),cancelled);
   if(c!=0)return c;
  }return 0;
 }
 public static CanonicalValue rows(List<FullRow> rows,BooleanSupplier cancelled) {
  var copy=new ArrayList<>(rows);copy.sort((a,b)->compare(a,b,cancelled));var values=new ArrayList<CanonicalValue>();
  FullRow previous=null;for(var row:copy){check(cancelled);require(previous==null||compare(previous,row,cancelled)!=0,SOURCE_MEMBERSHIP_MISMATCH);values.add(row(row));previous=row;}
  return new Sequence(values);
 }
 public static CanonicalValue tables(List<FullRow> rows,Set<Table> tables,BooleanSupplier cancelled){
  var names=new ArrayList<>(tables);names.sort(Comparator.comparing(Table::sqlName));var values=new ArrayList<CanonicalValue>();
  for(var table:names){check(cancelled);values.add(object(field("name",string(table.sqlName())),field("rows",rows(rows.stream().filter(r->r.table()==table).toList(),cancelled))));}
  return object(field("tables",new Sequence(values)));
 }
 public static Locator locator(FullRow r){
  require(r.table()==Table.SOURCE_LOCATOR,INPUT_INVALID);
  Locator locator=switch(r.string("kind")){
   case "TRANSCRIPT"->new TranscriptLocator(r.string("transcript_revision_id"),r.string("segment_id"));
   case "AUDIO"->new AudioLocator(r.string("file_id"),r.number("start_ms"),r.number("end_ms"));
   case "PDF_PAGE","PPT_PAGE"->new PageLocator(PageKind.valueOf(r.string("kind")),r.string("file_id"),r.number("page_no"));
   case "WORD_PARAGRAPH","TEXT_PARAGRAPH"->new ParagraphLocator(ParagraphKind.valueOf(r.string("kind")),r.string("file_id"),r.number("paragraph_no"),r.nullableString("section_path"));
   case "KNOWLEDGE_VERSION"->new KnowledgeLocator(r.string("material_revision_id"));
   default->throw fail(LOCATOR_INVALID);
  };
  var relevant=switch(locator){
   case TranscriptLocator ignored->Set.of("transcript_revision_id","segment_id");
   case AudioLocator ignored->Set.of("file_id","start_ms","end_ms");
   case PageLocator ignored->Set.of("file_id","page_no");
   case ParagraphLocator ignored->Set.of("file_id","paragraph_no","section_path");
   case KnowledgeLocator ignored->Set.of("material_revision_id");
  };
  for(var c:r.table().columns())if(!Set.of("id","meeting_id","kind").contains(c.name())&&!relevant.contains(c.name()))require(r.value(c.name()) instanceof NullCell,LOCATOR_INVALID);
  return locator;
 }
 public static CanonicalValue usageKey(UsageKey key){
  return switch(key){
   case EvidenceUsage x->object(field("kind",string("EVIDENCE")),field("evidenceId",string(x.evidenceId())));
   case DetectionUsage x->object(field("kind",string("DETECTION")),field("detectionId",string(x.detectionId())),field("locatorId",string(x.locatorId())));
   case BackgroundUsage x->object(field("kind",string("BACKGROUND")),field("noteId",string(x.noteId())),field("locatorId",string(x.locatorId())));
   case ExclusionUsage x->object(field("kind",string("EXCLUSION")),field("exclusionRevisionId",string(x.exclusionRevisionId())),field("locatorId",string(x.locatorId())),field("category",string(x.category().name())));
  };
 }
 static List<String> usageOrder(UsageKey key){
  return switch(key){case EvidenceUsage x->List.of("0",x.evidenceId());case DetectionUsage x->List.of("1",x.detectionId(),x.locatorId());
   case BackgroundUsage x->List.of("2",x.noteId(),x.locatorId());case ExclusionUsage x->List.of("3",x.exclusionRevisionId(),x.locatorId(),x.category().name());};
 }
 public static int compareUsage(SourceUsage a,SourceUsage b,BooleanSupplier cancelled){
  var left=usageOrder(a.usage());var right=usageOrder(b.usage());
  for(int i=0;i<Math.min(left.size(),right.size());i++){int c=CanonicalValueCodec.compareUtf8(left.get(i),right.get(i),cancelled);if(c!=0)return c;}return Integer.compare(left.size(),right.size());
 }
 public static CanonicalValue origin(Origin origin){
  return switch(origin){
   case GenerationOrigin x->object(field("kind",string("GENERATION_INPUT")),field("inputId",string(x.inputId())),field("inputFingerprint",string(x.inputFingerprint())));
   case AppliedOrigin x->object(field("kind",string("APPLIED_INPUT")),field("inputId",string(x.inputId())),field("inputFingerprint",string(x.inputFingerprint())));
   case ConfirmationOrigin x->object(field("kind",string("TRANSCRIPT_CONFIRMATION")),field("confirmationId",string(x.confirmationId())),field("revisionId",string(x.revisionId())),field("contentHash",string(x.contentHash())));
  };
 }
 public static CanonicalValue usages(List<SourceUsage> usages,BooleanSupplier cancelled){
  var copy=new ArrayList<>(usages);copy.sort((a,b)->compareUsage(a,b,cancelled));var values=new ArrayList<CanonicalValue>();var keys=new HashSet<UsageKey>();
  for(var x:copy){check(cancelled);require(keys.add(x.usage()),SOURCE_MEMBERSHIP_MISMATCH);values.add(object(field("usage",usageKey(x.usage())),field("locatorId",string(x.locatorId())),field("origin",origin(x.origin())),field("role",string(x.role().name())),field("sourceVersion",ContractValues.sourceVersion(x.sourceVersion())),field("sourceContentHash",string(x.sourceContentHash()))));}
  return new Sequence(values);
 }
 public static CanonicalValue graph(SourceGraph graph,BooleanSupplier cancelled){return object(field("locatorRows",rows(graph.locatorRows(),cancelled)),field("sourceUsages",usages(graph.sourceUsages(),cancelled)));}
}
