package ama.meeting;

import ama.contract.MeetingContracts.CategoryUnavailable;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Validation and bounded label acquisition share one proven immutable local snapshot. */
@FunctionalInterface
public interface CategoryDictionary {
 void requireAvailableCategory(String categoryId);
 default Catalog catalog(WorkspaceReadBudget budget){throw new CategoryUnavailable();}
 record Category(String id,String label) {
  public Category {
   WorkspaceText.check(id,1020,true);ama.contract.IdentityContracts.stableId(id);WorkspaceText.check(label,1024,true);
   if(id.codePoints().anyMatch(Character::isISOControl)||label.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid category metadata");
  }
 }
 record Metadata(String id,long idBytes,long idScalars,long idUnits,long labelBytes,long labelScalars,long labelUnits){}
 /** Only load() constructs this capability, after bounded initial acquisition and exact digest validation. */
 final class Catalog {
  private final String version;private final SortedMap<String,Category> entries;private final List<Metadata> metadata;
  private Catalog(String version,SortedMap<String,Category> entries){
   this.version=version;this.entries=Collections.unmodifiableSortedMap(entries);
   this.metadata=entries.values().stream().map(c->new Metadata(c.id(),utf8(c.id()),c.id().codePointCount(0,c.id().length()),c.id().length(),utf8(c.label()),c.label().codePointCount(0,c.label().length()),c.label().length())).toList();
  }
  public String version(WorkspaceReadBudget budget){budget.reserve(1,1,64,64,64,2,false);return version;}
  public List<Metadata> metadata(WorkspaceReadBudget budget){
   // Known bounded fixed metadata and IDs are reserved before returning/copying the descriptor list.
   long bytes=0,points=0,units=0;
   for(var m:metadata){bytes=Math.addExact(bytes,Math.addExact(m.idBytes(),64));points=Math.addExact(points,m.idScalars());units=Math.addExact(units,m.idUnits());}
   budget.reserve(metadata.size(),metadata.size()*8L,bytes,points,units,3,false);return metadata;
  }
  public Category read(Metadata selected,WorkspaceReadBudget budget){
   // The exact immutable metadata object belongs to this version; a public record is not authority.
   if(metadata.stream().noneMatch(m->m==selected))throw new IllegalArgumentException("Foreign category metadata");
   budget.reserve(1,2,Math.addExact(selected.idBytes(),selected.labelBytes()),Math.addExact(selected.idScalars(),selected.labelScalars()),Math.addExact(selected.idUnits(),selected.labelUnits()),4,true);
   budget.check();return entries.get(selected.id());
  }
 }
 /** Explicit controlled/local binary snapshot: AMAC magic, version1, count, length-prefixed strict UTF-8 ID/label pairs.
  * No production directory is installed by default. The caller supplies the reviewed file and exact SHA256.
  */
 static CategoryDictionary load(Path file,String expectedSha256,WorkspaceReadBudget initial) throws IOException {
  if(expectedSha256==null||!expectedSha256.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Exact category digest required");
  Objects.requireNonNull(initial).check();
  if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(file))throw new IOException("Category snapshot unavailable");
  byte[] bytes;
  try(var channel=FileChannel.open(file,Set.of(StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS))) {
   long size=channel.size();if(size<9||size>1024*1024)throw new IOException("Category snapshot size unavailable");
   // Six copies cover initial buffer, parser byte copies, decoder UTF16, Strings and retained snapshot.
   initial.reserve(256,2048,size,size,size,6,false);
   bytes=new byte[Math.toIntExact(size)];var buffer=ByteBuffer.wrap(bytes);
   while(buffer.hasRemaining()){initial.check();if(channel.read(buffer)<0)throw new IOException("Category snapshot changed");}
   if(channel.size()!=size)throw new IOException("Category snapshot changed");
  }
  try {
   if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(expectedSha256))throw new IOException("Category snapshot digest mismatch");
  }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
  var entries=new TreeMap<String,Category>();
  try(var input=new DataInputStream(new ByteArrayInputStream(bytes))) {
   if(input.readInt()!=0x414d4143||input.readUnsignedByte()!=1)throw new IOException("Category snapshot format unavailable");
   int count=input.readInt();if(count<0||count>256)throw new IOException("Category snapshot count unavailable");
   for(int i=0;i<count;i++){initial.check();var entry=new Category(field(input,1020),field(input,1024));if(entries.putIfAbsent(entry.id(),entry)!=null)throw new IOException("Duplicate category");}
   if(input.available()!=0)throw new IOException("Trailing category snapshot data");
  }
  initial.check();var catalog=new Catalog(expectedSha256,entries);
  return new CategoryDictionary() {
   public void requireAvailableCategory(String id){if(id==null||!entries.containsKey(id))throw new CategoryUnavailable();}
   public Catalog catalog(WorkspaceReadBudget budget){budget.check();return catalog;}
  };
 }
 private static String field(DataInputStream input,int maximum)throws IOException {
  int size=input.readInt();if(size<1||size>maximum||size>input.available())throw new IOException("Category field size unavailable");
  byte[] bytes=input.readNBytes(size);
  try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
  catch(CharacterCodingException e){throw new IOException("Malformed category Unicode",e);}
 }
 private static long utf8(String text){long bytes=0;for(int i=0;i<text.length();){int c=text.codePointAt(i);bytes+=c<=0x7f?1:c<=0x7ff?2:c<=0xffff?3:4;i+=Character.charCount(c);}return bytes;}
}
