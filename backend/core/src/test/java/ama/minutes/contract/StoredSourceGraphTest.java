package ama.minutes.contract;
import ama.contract.MinutesContracts.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
import static org.junit.jupiter.api.Assertions.*;

class StoredSourceGraphTest {
 @Test void artifactFactBudgetCountsUniqueAcquisitionAndRefusesWholeSnapshotExcess(){
  var bytes=ama.contract.ConfigArchiveContracts.ArchiveBytes.capture(new byte[32],32,32,()->false);var artifact=new Artifact("synthetic",bytes.sha256(()->false),bytes);
  var definitions=new EnumMap<ProfileKind,Artifact>(ProfileKind.class);for(var kind:ProfileKind.values())definitions.put(kind,artifact);var profiles=new Profiles(definitions);
  var first=new GenerationConfiguration(id(8),artifact,artifact,profiles,id(9),List.of());var second=new GenerationConfiguration(id(18),artifact,artifact,profiles,id(9),List.of());
  var tight=new GraphBudget(new CanonicalBudget(32,10,100,100,100),10,10,100);
  assertDoesNotThrow(()->new StoredFactsIndex(new StoredProvenanceFacts(List.of(),List.of(),List.of(first,second),List.of(),100),tight,()->false));
  fails(BUDGET_EXCEEDED,()->new StoredFactsIndex(new StoredProvenanceFacts(List.of(),List.of(),List.of(first),List.of(),100),new GraphBudget(new CanonicalBudget(31,10,100,100,100),10,10,100),()->false));
  var other=ama.contract.ConfigArchiveContracts.ArchiveBytes.capture(new byte[16],32,32,()->false);var extra=new Artifact("extra",other.sha256(()->false),other);
  var changed=new GenerationConfiguration(id(18),extra,artifact,profiles,id(9),List.of());
  fails(BUDGET_EXCEEDED,()->new StoredFactsIndex(new StoredProvenanceFacts(List.of(),List.of(),List.of(first,changed),List.of(),100),tight,()->false));
 }
 static final String H="0".repeat(64),T="2026-10-22T00:00:00.000000Z";
 static String id(int n){return "00000000-0000-0000-0000-%012d".formatted(n);}
 static FullRow row(Table table,Object... values){
  assertEquals(table.columns().size(),values.length,table.sqlName());var cells=new ArrayList<Cell>();for(var v:values)cells.add(v==null?new NullCell():v instanceof Number n?cell(n.longValue()):cell((String)v));return new FullRow(table,cells);
 }
 static Artifact artifact(String v){byte[] bytes=v.getBytes(StandardCharsets.UTF_8);var owned=ama.contract.ConfigArchiveContracts.ArchiveBytes.capture(bytes,BUDGET.canonical().maxEncodedBytes(),BUDGET.canonical().maxEncodedBytes(),()->false);return new Artifact(v,owned.sha256(()->false),owned);}
 static Profiles profiles(){var p=new EnumMap<ProfileKind,Artifact>(ProfileKind.class);for(var k:ProfileKind.values())p.put(k,artifact(switch(k){
  case CANONICAL_CODEC->"ama.canonical-value.v1";case CONFIRMATION_HASH->"ama.transcript-confirmation.v1";case GENERATION_HASH->"ama.generation-input.v2";case ROWSET_HASH->"ama.rowset.v1";case REVIEW_HASH->"ama.result-review.v2";case FINAL_SOURCES->"ama.final-sources.v2";default->k.name()+".v1";}));return new Profiles(p);}
 static GenerationConfiguration config(int input,String parser){return new GenerationConfiguration(id(input),artifact("prompt"),artifact("model"),profiles(),id(9),List.of(new ParserSelection(id(30),parser)));}
 static List<FullRow> base(){
  var rows=new ArrayList<FullRow>();
  rows.add(row(Table.INPUT_BATCH,id(2),id(1),"TRANSCRIPT",H,1L,T));
  rows.add(row(Table.INPUT_BATCH_FILE,id(2),1L,id(3),H));
  rows.add(row(Table.TRANSCRIPTION_INPUT,id(4),id(1),id(2),"IMPORT",null,null,"EMPTY","pc","txt",null,null,1L,"unresolved-manifest",H,1L,0L,T));
  rows.add(row(Table.TRANSCRIPT_REVISION,id(5),id(1),1L,"WORKING",id(2),null,H,"human",T,id(4),null));
  rows.add(row(Table.TRANSCRIPT_SEGMENT,id(5),id(6),1L,"meeting 7",id(3),4L,null,null,null));
  rows.add(row(Table.TRANSCRIPT_CONFIRMATION,id(7),id(1),id(5),H,"human",T));
  rows.add(row(Table.TEMPLATE_REVISION,id(9),id(90),1L,null,"default","themes",1L,T));
  rows.add(row(Table.EXCLUSION_REVISION,id(10),id(1),1L,T));
  rows.add(row(Table.ELEMENT_SET,id(11),id(1),1L,T));
  return rows;
 }
 static FullRow input(int input,String snapshot,String inputSet){return row(Table.GENERATION_INPUT,id(input),id(1),id(7),snapshot,id(9),id(10),inputSet,"prompt","model",1L,H,"FULL",null);}
 static FullRow locator(int locator){return row(Table.SOURCE_LOCATOR,id(locator),id(1),"PDF_PAGE",null,null,id(31),null,1L,null,null,null,null);}
 static StoredProvenanceFacts materials(){
  var rows=new ArrayList<>(base());rows.add(input(8,id(32),null));rows.add(input(18,id(32),null));
  rows.add(row(Table.MATERIAL_REFERENCE,id(33),id(1),"LOCAL",null,null,id(31)));
  rows.add(row(Table.MATERIAL_REVISION,id(30),id(33),1L,null,id(31),H,T));
  rows.add(row(Table.MATERIAL_SNAPSHOT,id(32),id(1),1L,H,T));
  rows.add(row(Table.MATERIAL_SNAPSHOT_ITEM,id(32),id(30),1L));
  rows.add(row(Table.PARSED_DOCUMENT,id(34),id(30),"p1",id(35),1L));rows.add(row(Table.PARSED_UNIT,id(34),1L,"PAGE",1L,null,null,"one"));
  rows.add(row(Table.PARSED_DOCUMENT,id(36),id(30),"p2",id(37),1L));rows.add(row(Table.PARSED_UNIT,id(36),1L,"PAGE",1L,null,null,"two"));
  rows.add(locator(14));
  rows.add(row(Table.BACKGROUND_NOTE,id(16),id(11),id(8),1L,"N1"));rows.add(row(Table.BACKGROUND_NOTE_SOURCE,id(16),id(14)));
  rows.add(row(Table.BACKGROUND_NOTE,id(17),id(11),id(18),2L,"N2"));rows.add(row(Table.BACKGROUND_NOTE_SOURCE,id(17),id(14)));
  return new StoredProvenanceFacts(rows,List.of(new ContentFile(id(3),H,9),new ContentFile(id(31),H,9),new ContentFile(id(35),"1".repeat(64),3),new ContentFile(id(37),"2".repeat(64),3)),List.of(config(8,"p1"),config(18,"p2")),List.of(),100000L);
 }
 static final GraphBudget BUDGET=new GraphBudget(new CanonicalBudget(1000000,100,100000,100000,100000),30,100,10000);
 static final GraphSelection SET=new GraphSelection(id(1),null,id(11),null,null);
 static void fails(FailureCode c,Runnable r){assertEquals(c,assertThrows(Failure.class,r::run).code());}
 @Test void onePhysicalLocatorKeepsBothActualNoteInputsAndParserContexts(){
  var g=new StoredSourceGraph().derive(materials(),SET,BUDGET,()->false);
  assertEquals(1,g.locatorRows().size());assertEquals(2,g.sourceUsages().size());
  assertEquals(new BackgroundUsage(id(16),id(14)),g.sourceUsages().get(0).usage());
  assertEquals(new GenerationOrigin(id(8),H),g.sourceUsages().get(0).origin());
  assertEquals("p1",((FileVersion)g.sourceUsages().get(0).sourceVersion()).parserVersion());
  assertEquals("p2",((FileVersion)g.sourceUsages().get(1).sourceVersion()).parserVersion());
  var f=materials();var reversed=new ArrayList<>(f.rows());Collections.reverse(reversed);
  assertEquals(g,new StoredSourceGraph().derive(new StoredProvenanceFacts(reversed,f.files(),f.configurations(),f.evidenceAnchors(),100000L),SET,BUDGET,()->false));
 }
 @Test void suppliedMembershipMustBeExactBijection(){
  var f=materials();var g=new StoredSourceGraph().derive(f,SET,BUDGET,()->false);
  fails(SOURCE_MEMBERSHIP_MISMATCH,()->new StoredSourceGraph().verify(new SourceGraph(g.locatorRows(),List.of()),f,SET,BUDGET,()->false));
  fails(SOURCE_MEMBERSHIP_MISMATCH,()->new StoredSourceGraph().verify(new SourceGraph(g.locatorRows(),List.of(g.sourceUsages().getFirst(),g.sourceUsages().getFirst())),f,SET,BUDGET,()->false));
 }
 @Test void ordinaryCopiedLocalElementNeverAcceptsAttractiveHistoricalInput(){
  var f=materials();var rows=new ArrayList<>(f.rows());
  rows.add(row(Table.MINUTES_ELEMENT,id(12),id(11),"DISCUSSION",1L,"x","PARTIAL"));
  var a=OutputValidation.anchor("x",0,1,()->false);
  rows.add(row(Table.EVIDENCE_LINK,id(15),id(14),id(12),null,"PARTIAL",OutputValidation.anchorText(a)));
  var facts=new StoredProvenanceFacts(rows,f.files(),f.configurations(),List.of(new EvidenceAnchor(id(15),a)),100000L);
  fails(PROVENANCE_UNAVAILABLE,()->new StoredSourceGraph().derive(facts,SET,BUDGET,()->false));
 }
 @Test void ambiguousPinnedParserSelectionRejectsInsteadOfChoosingOrder(){
  var f=materials();var rows=new ArrayList<>(f.rows());
  rows.add(row(Table.PARSED_DOCUMENT,id(38),id(30),"p1",id(35),1L));rows.add(row(Table.PARSED_UNIT,id(38),1L,"PAGE",1L,null,null,"one"));
  var facts=new StoredProvenanceFacts(rows,f.files(),f.configurations(),List.of(),100000L);
  fails(SOURCE_CONTEXT_AMBIGUOUS,()->new StoredSourceGraph().derive(facts,SET,BUDGET,()->false));
 }
 @Test void cycleBudgetAndCancellationApplyAcrossAllOrigins(){
  var f=materials();var rows=new ArrayList<>(f.rows());rows.removeIf(r->r.table()==Table.GENERATION_INPUT&&r.string("id").equals(id(8)));rows.add(input(8,id(32),id(11)));
  var facts=new StoredProvenanceFacts(rows,f.files(),f.configurations(),List.of(),100000L);
  fails(INPUT_INVALID,()->new StoredSourceGraph().derive(facts,SET,BUDGET,()->false));
  fails(BUDGET_EXCEEDED,()->new StoredSourceGraph().derive(f,SET,new GraphBudget(BUDGET.canonical(),30,1,10000),()->false));
  fails(CANCELLED,()->new StoredSourceGraph().derive(f,SET,BUDGET,()->true));
 }

 @Test void completeIndependentOccurrenceGraphPreservesBothLabelsAndEitherParserHash()throws Exception{
  var f=materials();var rows=new ArrayList<>(f.rows());
  rows.add(row(Table.SENSITIVE_DETECTION,id(19),id(11),id(8),"INTERNAL_BUSINESS_DATA",1L));rows.add(row(Table.SENSITIVE_DETECTION_SOURCE,id(19),id(14)));
  rows.add(row(Table.SENSITIVE_DETECTION,id(20),id(11),id(18),"POLITICAL_SENSITIVE",1L));rows.add(row(Table.SENSITIVE_DETECTION_SOURCE,id(20),id(14)));
  var facts=new StoredProvenanceFacts(rows,f.files(),f.configurations(),List.of(),100000L);var graph=new StoredSourceGraph().derive(facts,SET,BUDGET,()->false);
  var codec=new ama.canonical.CanonicalValueCodec();var value=RowValues.graph(graph,()->false);
  assertEquals(HashProfilesTest.vector("occurrence-graph",".hex"),HexFormat.of().formatHex(codec.encode(value,BUDGET.canonical(),()->false)));
  String digest=codec.sha256(value,BUDGET.canonical(),()->false);assertEquals(HashProfilesTest.vector("occurrence-graph",".sha256"),digest);
  for(var fileId:List.of(id(35),id(37))){
   var files=new ArrayList<>(f.files());files.replaceAll(file->file.id().equals(fileId)?new ContentFile(file.id(),"3".repeat(64),file.sizeBytes()):file);
   var changed=new StoredSourceGraph().derive(new StoredProvenanceFacts(rows,files,f.configurations(),List.of(),100000L),SET,BUDGET,()->false);
   assertNotEquals(digest,codec.sha256(RowValues.graph(changed,()->false),BUDGET.canonical(),()->false));
  }
 }
 @Test void orphanStoredMembershipCannotBeIgnoredAsAnUnrelatedUsage(){
  var f=materials();var rows=new ArrayList<>(f.rows());rows.add(row(Table.BACKGROUND_NOTE_SOURCE,id(999),id(14)));
  fails(SOURCE_MEMBERSHIP_MISMATCH,()->new StoredSourceGraph().derive(new StoredProvenanceFacts(rows,f.files(),f.configurations(),List.of(),100000L),SET,BUDGET,()->false));
 }
}
