package ama.minutes.contract;
import ama.contract.MinutesContracts.*;
import ama.canonical.CanonicalValueCodec;
import java.util.*;
import java.util.function.BooleanSupplier;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

public final class SourceCatalog {
 public Catalog build(CatalogFacts facts,CanonicalBudget budget,BooleanSupplier cancelled) {
  require(facts!=null&&budget!=null,INVALID_ARGUMENT);check(cancelled);
  require(facts.entries().size()<=budget.maxNodes()&&facts.entries().size()<=budget.maxContainerEntries(),BUDGET_EXCEEDED);
  var unique=new HashMap<Locator,CatalogFact>();var ordinals=new HashMap<Long,Locator>();
  var encoded=new HashMap<Locator,byte[]>();long bytes=0,nodes=0,metadata=0;
  for(var f:facts.entries()){
   check(cancelled);require(nodes<budget.maxNodes(),BUDGET_EXCEEDED);nodes++;
   long size=new CanonicalValueCodec().preflight(ContractValues.sourceVersion(f.sourceVersion()),budget,cancelled);require(size<=budget.maxEncodedBytes()-metadata,BUDGET_EXCEEDED);metadata+=size;
   if(f.bounds() instanceof DocumentBounds document){
    require(document.units().size()<=budget.maxContainerEntries()&&document.units().size()<=budget.maxNodes()-nodes,BUDGET_EXCEEDED);nodes+=document.units().size();
    for(var unit:document.units()){check(cancelled);if(unit.sectionPath()!=null){long n=CanonicalValueCodec.utf8Length(unit.sectionPath(),cancelled);require(n<=budget.maxStringBytes()&&n<=budget.maxEncodedBytes()-metadata,BUDGET_EXCEEDED);metadata+=n;}}
   }
   var previous=unique.putIfAbsent(f.locator(),f);require(previous==null||previous.equals(f),INPUT_INVALID);
   if(previous!=null)continue;
   if(f.locator() instanceof TranscriptLocator){var prior=ordinals.putIfAbsent(f.transcriptOrdinal(),f.locator());require(prior==null,INPUT_INVALID);}
   else {
    var value=new CanonicalValueCodec().encode(ContractValues.locator(f.locator()),budget,cancelled);
    require(value.length<=budget.maxEncodedBytes()-bytes,BUDGET_EXCEEDED);bytes+=value.length;encoded.put(f.locator(),value);
   }
  }
  for(var f:unique.values()){check(cancelled);validate(f,cancelled);}
  var sorted=new ArrayList<>(unique.values());check(cancelled);
  sorted.sort((a,b)->{check(cancelled);boolean at=a.locator() instanceof TranscriptLocator,bt=b.locator() instanceof TranscriptLocator;
   if(at!=bt)return at?-1:1;
   return at?Long.compare(a.transcriptOrdinal(),b.transcriptOrdinal()):Arrays.compareUnsigned(encoded.get(a.locator()),encoded.get(b.locator()));});
  var sources=new ArrayList<CatalogSource>();long n=0;
  for(var f:sorted){check(cancelled);sources.add(new CatalogSource("s"+(++n),f));}
  check(cancelled);return new Catalog(sources);
 }
 public static void validate(CatalogFact f,BooleanSupplier cancelled) {
  check(cancelled);var l=f.locator();var v=f.sourceVersion();var b=f.bounds();boolean valid=false;
  if(l instanceof TranscriptLocator x && v instanceof TranscriptVersion t && b instanceof TranscriptBounds u){
   valid=f.role()==Role.MEETING&&f.transcriptOrdinal()>0&&x.transcriptRevisionId().equals(t.revisionId())&&x.transcriptRevisionId().equals(u.revisionId())
    &&x.segmentId().equals(u.segmentId())&&t.sourceFileId().equals(u.sourceFileId());
  } else {
   require(f.role()==Role.BACKGROUND&&f.transcriptOrdinal()==0,LOCATOR_INVALID);
   if(l instanceof AudioLocator x && v instanceof FileVersion t && b instanceof AudioBounds u)
    valid=x.fileId().equals(t.fileId())&&x.fileId().equals(u.fileId())&&t.parsedDocumentId()==null&&x.endMs()<=u.durationMs();
   if((l instanceof PageLocator||l instanceof ParagraphLocator) && b instanceof DocumentBounds u){
    String file=l instanceof PageLocator x?x.fileId():((ParagraphLocator)l).fileId();
    boolean version=v instanceof FileVersion t && file.equals(t.fileId())&&u.materialRevisionId().equals(t.materialRevisionId())&&u.parsedDocumentId().equals(t.parsedDocumentId())&&u.parserVersion().equals(t.parserVersion())&&u.parsedTextSha256().equals(t.parsedTextSha256())
     ||v instanceof KnowledgeVersion k&&file.equals(k.contentFileId())&&u.materialRevisionId().equals(k.materialRevisionId())&&u.parsedDocumentId().equals(k.parsedDocumentId())&&u.parserVersion().equals(k.parserVersion())&&u.parsedTextSha256().equals(k.parsedTextSha256());
    var ordinals=new HashSet<Long>();int matches=0;
    for(var unit:u.units()){check(cancelled);require(ordinals.add(unit.ordinal()),LOCATOR_INVALID);
     if(l instanceof PageLocator x&&Objects.equals(unit.pageNo(),x.pageNo()))matches++;
     if(l instanceof ParagraphLocator x&&Objects.equals(unit.paragraphNo(),x.paragraphNo())&&Objects.equals(unit.sectionPath(),x.sectionPath()))matches++;}
    valid=version&&file.equals(u.fileId())&&matches==1;
   }
   if(l instanceof KnowledgeLocator x&&v instanceof KnowledgeVersion t&&b instanceof KnowledgeBounds u)
    valid=x.materialRevisionId().equals(t.materialRevisionId())&&x.materialRevisionId().equals(u.materialRevisionId())&&t.providerId().equals(u.providerId())&&t.externalId().equals(u.externalId())&&t.externalVersion().equals(u.externalVersion())&&t.contentFileId().equals(u.contentFileId())&&f.sourceContentHash().equals(u.sha256());
  }
  require(valid,LOCATOR_INVALID);
 }
}
