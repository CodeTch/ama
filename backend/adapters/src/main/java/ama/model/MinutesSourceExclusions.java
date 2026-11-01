package ama.model;
import ama.canonical.CanonicalValue;import java.util.*;import static ama.config.WireValues.*;import static ama.contract.MinutesContracts.*;import static ama.contract.MinutesContracts.FailureCode.*;
/** Direct physical overlap only. A blocked whole-document copy is never fed back as a
 * newly selected exclusion: excluding one paragraph must not transitively exclude its siblings.
 * Callers still own archive/input/source authenticity and the operation's authorization. */
public final class MinutesSourceExclusions {
 private record Pin(String revision,String document,String parser,String textHash,String file){}
 private record Source(String alias,CanonicalValue locator,Pin pin,boolean whole){}
 private record Selection(List<Source> sources,Set<CanonicalValue> exact,Set<Pin> whole,Set<Pin> partial){}
 private static Pin pin(CanonicalValue value){
  if(!(value instanceof CanonicalValue.ObjectValue object))throw fail(INPUT_INVALID);var values=new HashMap<String,CanonicalValue>();for(var e:object.entries())require(values.put(e.key(),e.value())==null,INPUT_INVALID);String kind=text(values.get("kind"));if(kind.equals("TRANSCRIPT"))return null;
  boolean knowledge=kind.equals("KNOWLEDGE");require(knowledge||kind.equals("FILE"),INPUT_INVALID);if(values.get("materialRevisionId") instanceof CanonicalValue.Null)return null;
  String revision=text(values.get("materialRevisionId")),document=text(values.get("parsedDocumentId")),parser=text(values.get("parserVersion")),hash=text(values.get("parsedTextSha256")),file=text(values.get(knowledge?"contentFileId":"fileId"));uuid(revision);uuid(document);uuid(file);hash(hash);opaque(parser);return new Pin(revision,document,parser,hash,file);
 }
 private static Selection selection(Map<String,CanonicalValue> manifest){
  var sources=new ArrayList<Source>();var byLocator=new HashMap<CanonicalValue,Source>();var aliases=new HashSet<String>();var wire=array(manifest.get("sources"));require(wire.size()<=100000,BUDGET_EXCEEDED);
  for(var value:wire){var fields=fields(value,"sourceId","locator","role","sourceVersion","sourceContentHash");var locator=fields.get("locator");if(!(locator instanceof CanonicalValue.ObjectValue object))throw fail(INPUT_INVALID);var kind=object.entries().stream().filter(e->e.key().equals("kind")).map(CanonicalValue.Entry::value).findFirst().orElseThrow(()->fail(INPUT_INVALID));var source=new Source(text(fields.get("sourceId")),locator,pin(fields.get("sourceVersion")),text(kind).equals("KNOWLEDGE_VERSION"));require(aliases.add(source.alias())&&byLocator.put(source.locator(),source)==null,INPUT_INVALID);sources.add(source);}
  var exclusion=fields(manifest.get("exclusion"),"mode","revisionId","revision","contentHash","entries");var entries=array(exclusion.get("entries"));require(entries.size()<=100000,BUDGET_EXCEEDED);var exact=new HashSet<CanonicalValue>();var whole=new HashSet<Pin>();var partial=new HashSet<Pin>();
  for(var value:entries){var entry=fields(value,"locatorId","locator","category","reasonHash");var selected=byLocator.get(entry.get("locator"));require(selected!=null,SOURCE_NOT_IN_INPUT);exact.add(selected.locator());if(selected.pin()!=null)(selected.whole()?whole:partial).add(selected.pin());}
  return new Selection(List.copyOf(sources),Set.copyOf(exact),Set.copyOf(whole),Set.copyOf(partial));
 }
 private static boolean blocked(Source source,Selection selected){return selected.exact().contains(source.locator())||source.pin()!=null&&(selected.whole().contains(source.pin())||source.whole()&&selected.partial().contains(source.pin()));}
 public static Set<String> aliases(Map<String,CanonicalValue> manifest){var selected=selection(manifest);var result=new HashSet<String>();for(var source:selected.sources())if(blocked(source,selected))result.add(source.alias());return Set.copyOf(result);}
 public static Set<CanonicalValue> locations(Map<String,CanonicalValue> manifest){var selected=selection(manifest);var result=new HashSet<CanonicalValue>();for(var source:selected.sources())if(blocked(source,selected))result.add(source.locator());return Set.copyOf(result);}
 private MinutesSourceExclusions(){}
}
