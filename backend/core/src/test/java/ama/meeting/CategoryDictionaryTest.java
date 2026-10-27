package ama.meeting;

import java.util.*;
import java.nio.file.*;
import java.io.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CategoryDictionaryTest {
 @TempDir Path temporary;
 @Test void protectedSnapshotAndValidationUseExactlyTheSameImmutableEntries()throws Exception {
  var dictionary=load(List.of(new CategoryDictionary.Category("b","类别乙"),new CategoryDictionary.Category("a","类别甲")));
  var budget=new WorkspaceReadBudget();var catalog=dictionary.catalog(budget);var metadata=catalog.metadata(budget);
  assertEquals(List.of("a","b"),metadata.stream().map(CategoryDictionary.Metadata::id).toList());
  dictionary.requireAvailableCategory("a");assertEquals("类别甲",catalog.read(metadata.getFirst(),budget).label());
  assertEquals("类".repeat(255),new CategoryDictionary.Category("类".repeat(255),"label").id());
  assertThrows(ama.contract.MeetingContracts.CategoryUnavailable.class,()->dictionary.requireAvailableCategory("missing"));
  assertThrows(UnsupportedOperationException.class,metadata::clear);
  var m=metadata.getFirst();var foreign=new CategoryDictionary.Metadata(m.id(),m.idBytes(),m.idScalars(),m.idUnits(),m.labelBytes(),m.labelScalars(),m.labelUnits());
  assertThrows(IllegalArgumentException.class,()->catalog.read(foreign,budget));
 }
 @Test void legacyValidatorCannotInventCatalogAndInvalidInputsFail()throws Exception {
  CategoryDictionary legacy=id->{};
  assertThrows(ama.contract.MeetingContracts.CategoryUnavailable.class,()->legacy.catalog(new WorkspaceReadBudget()));
  assertThrows(IOException.class,()->load(List.of(new CategoryDictionary.Category("same","one"),new CategoryDictionary.Category("same","two"))));
  assertThrows(IllegalArgumentException.class,()->new CategoryDictionary.Category("x".repeat(256),"label"));
  assertThrows(IllegalArgumentException.class,()->new CategoryDictionary.Category("id",String.valueOf((char)0)));
 }
 @Test void labelAcquisitionRequiresReservationAndAccumulatesAcrossRepeatedReads()throws Exception {
  var dictionary=load(List.of(new CategoryDictionary.Category("a","x".repeat(100))));var setup=new WorkspaceReadBudget();var catalog=dictionary.catalog(setup);var selected=catalog.metadata(setup).getFirst();
  // Test-only observation of the actual label map access; the catalog still comes from the real bounded loader.
  var field=CategoryDictionary.Catalog.class.getDeclaredField("entries");field.setAccessible(true);
  @SuppressWarnings("unchecked") var original=(SortedMap<String,CategoryDictionary.Category>)field.get(catalog);
  var labelReads=new java.util.concurrent.atomic.AtomicInteger();
  var observed=new TreeMap<String,CategoryDictionary.Category>(original){@Override public CategoryDictionary.Category get(Object key){labelReads.incrementAndGet();return super.get(key);}};
  field.set(catalog,Collections.unmodifiableSortedMap(observed));
  var limits=new WorkspaceReadBudget.Limits(700,10000,10000,10000,100,100,java.time.Duration.ofSeconds(5));var budget=new WorkspaceReadBudget(limits,()->false);
  assertEquals(100,catalog.read(selected,budget).label().length());var used=budget.usage();
  assertThrows(IllegalStateException.class,()->catalog.read(selected,budget));assertEquals(used,budget.usage());assertEquals(1,labelReads.get(),"Denied cumulative reservation must precede label map access");
  var cancelled=new WorkspaceReadBudget(limits,()->true);assertThrows(IllegalStateException.class,()->catalog.read(selected,cancelled));assertEquals(0,cancelled.usage().bytes());assertEquals(1,labelReads.get());
  var escaped=new WorkspaceReadBudget(new WorkspaceReadBudget.Limits(10000,1,10000,10000,100,100,java.time.Duration.ofSeconds(5)),()->false);
  assertThrows(IllegalStateException.class,()->catalog.read(selected,escaped));assertEquals(1,labelReads.get(),"Escaped response exhaustion must precede label acquisition");
 }
 @Test void initialFileAcquisitionRejectsWrongDigestAndInsufficientBudget()throws Exception {
  Path file=write(List.of(new CategoryDictionary.Category("a","A")));
  assertThrows(IOException.class,()->CategoryDictionary.load(file,"0".repeat(64),new WorkspaceReadBudget()));
  var tiny=new WorkspaceReadBudget(new WorkspaceReadBudget.Limits(1,1,1,1,1,1,java.time.Duration.ofSeconds(5)),()->false);
  assertThrows(IllegalStateException.class,()->CategoryDictionary.load(file,digest(file),tiny));assertEquals(0,tiny.usage().bytes());
 }
 CategoryDictionary load(List<CategoryDictionary.Category> entries)throws Exception {Path file=write(entries);return CategoryDictionary.load(file,digest(file),new WorkspaceReadBudget());}
 Path write(List<CategoryDictionary.Category> entries)throws Exception {
  Path file=temporary.resolve(UUID.randomUUID()+".categories");try(var out=new DataOutputStream(Files.newOutputStream(file))){out.writeInt(0x414d4143);out.writeByte(1);out.writeInt(entries.size());for(var e:entries)for(String value:List.of(e.id(),e.label())){byte[] bytes=value.getBytes(java.nio.charset.StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);}}return file;
 }
 String digest(Path file)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));}
}
