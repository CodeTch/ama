package ama.model;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.*;
import static java.nio.file.StandardOpenOption.*;

/** Explicit controlled fixture store. A receipt is data, never an application/DB capability.
 * One exclusive process owns a bounded append journal. Immutable files are forced before their
 * journal reference; each complete record is checksummed and forced before it becomes visible. */
public final class ControlledMinutesStore implements AutoCloseable {
 public static final int MAX_PAYLOAD=16*1024*1024, MAX_OUTPUT=8*1024*1024;
 private static final int MAX_RECORD=4096, MAX_EXECUTIONS=4096;
 private static final long MAX_JOURNAL=64L*1024*1024;
 public record Receipt(String state,String executionId,String logicalBusinessKey,String logicalRequestHash,
   String executionRequestHash,String payloadSha256,long payloadBytes,String resultId,String outputSha256,
   long outputBytes,String receiptId,String code) {}
 private final Path root,payloads,results;
 private FileChannel lockChannel,journal;
 private FileLock lock;
 private final Map<String,Receipt> executions=new HashMap<>();
 private final Map<String,String> keys=new HashMap<>(),resultOwners=new HashMap<>();
 private boolean closed,poisoned;
 public ControlledMinutesStore(Path directory,String mode)throws IOException {
  if(!"CONTROLLED_INSTALLATION".equals(mode))throw new IllegalArgumentException("Explicit controlled installation required");
  root=directory.toAbsolutePath().normalize();payloads=root.resolve("payloads");results=root.resolve("results");
  try {
   directory(root);directory(payloads);directory(results);
   rejectLink(root.resolve("store.lock"));lockChannel=FileChannel.open(root.resolve("store.lock"),CREATE,WRITE,LinkOption.NOFOLLOW_LINKS);
   lock=lockChannel.tryLock();if(lock==null)throw new IOException("Controlled store already owned");
   rejectLink(root.resolve("journal.bin"));journal=FileChannel.open(root.resolve("journal.bin"),CREATE,READ,WRITE,LinkOption.NOFOLLOW_LINKS);
   if(journal.size()>MAX_JOURNAL)throw new IOException("Controlled journal budget exceeded");
   replay();syncDirectory(root);
  }catch(IOException|RuntimeException e){try{close();}catch(IOException suppressed){e.addSuppressed(suppressed);}throw e;}
 }
 public synchronized Receipt reserve(String executionId,String logicalBusinessKey,String logicalRequestHash,
    String executionRequestHash,String payloadSha256,byte[] payload)throws IOException {
  live();uuid(executionId);key(logicalBusinessKey);hash(logicalRequestHash);hash(executionRequestHash);hash(payloadSha256);
  if(!logicalRequestHash.equals(executionRequestHash)||payload==null||payload.length>MAX_PAYLOAD)throw new IllegalArgumentException("Invalid initial request");
  byte[] owned=payload.clone();if(!sha(owned).equals(payloadSha256))throw new IllegalArgumentException("Payload digest mismatch");
  Receipt next=new Receipt("UNKNOWN",executionId,logicalBusinessKey,logicalRequestHash,executionRequestHash,payloadSha256,owned.length,null,null,0,null,null);
  Receipt old=executions.get(executionId);
  if(old!=null){if(!sameRequest(old,next))throw conflict();verifyPayload(old);return old;}
  if(keys.containsKey(logicalBusinessKey))throw conflict();if(executions.size()>=MAX_EXECUTIONS)throw new IOException("Controlled execution budget exceeded");
  immutable(payloads.resolve(executionId+".json"),owned);append(next);return next;
 }
 public synchronized Receipt lookup(String executionId)throws IOException {
  live();uuid(executionId);Receipt value=executions.get(executionId);if(value!=null){verifyPayload(value);verifyOutput(value);}return value;
 }
 public synchronized byte[] payload(String executionId)throws IOException {
  live();Receipt value=required(executionId);return checked(payloads.resolve(value.executionId()+".json"),value.payloadBytes(),value.payloadSha256(),MAX_PAYLOAD);
 }
 public synchronized Receipt commitSuccess(String executionId,byte[] output)throws IOException {
  live();Receipt old=required(executionId);if(output==null||output.length>MAX_OUTPUT)throw new IllegalArgumentException("Output budget exceeded");
  byte[] owned=output.clone();String digest=sha(owned);
  if(old.state().equals("SUCCEEDED")){if(old.outputBytes()!=owned.length||!old.outputSha256().equals(digest))throw conflict();verifyOutput(old);return old;}
  if(!old.state().equals("UNKNOWN"))throw conflict();verifyPayload(old);
  String resultId="cmr-"+UUID.randomUUID();immutable(results.resolve(resultId+".json"),owned);
  Receipt next=new Receipt("SUCCEEDED",old.executionId(),old.logicalBusinessKey(),old.logicalRequestHash(),old.executionRequestHash(),old.payloadSha256(),old.payloadBytes(),resultId,digest,owned.length,null,null);
  append(next);return next;
 }
 public synchronized Receipt reject(String executionId,String code)throws IOException {
  live();if(code==null||!code.matches("[A-Z][A-Z0-9_]{0,63}"))throw new IllegalArgumentException("Invalid rejection code");
  Receipt old=required(executionId);
  if(old.state().equals("FAILED_ZERO_EFFECT")){if(!old.code().equals(code))throw conflict();return old;}
  if(!old.state().equals("UNKNOWN"))throw conflict();verifyPayload(old);
  Receipt next=new Receipt("FAILED_ZERO_EFFECT",old.executionId(),old.logicalBusinessKey(),old.logicalRequestHash(),old.executionRequestHash(),old.payloadSha256(),old.payloadBytes(),null,null,0,"cmz-"+UUID.randomUUID(),code);
  append(next);return next;
 }
 public synchronized byte[] result(String resultId)throws IOException {
  live();if(resultId==null||!resultId.matches("cmr-[0-9a-f-]{36}"))throw new IllegalArgumentException("Invalid result identity");
  uuid(resultId.substring(4));String owner=resultOwners.get(resultId);if(owner==null)throw new FileNotFoundException("Controlled result unavailable");
  Receipt value=required(owner);verifyPayload(value);return checked(results.resolve(resultId+".json"),value.outputBytes(),value.outputSha256(),MAX_OUTPUT);
 }
 private Receipt required(String id){uuid(id);Receipt value=executions.get(id);if(value==null)throw new IllegalArgumentException("Unknown controlled execution");return value;}
 private void append(Receipt value)throws IOException {
  byte[] body=encode(value);if(body.length>MAX_RECORD||body.length+36>MAX_JOURNAL-journal.size())throw new IOException("Controlled journal budget exceeded");
  try {
   journal.position(journal.size());write(journal,ByteBuffer.allocate(4).putInt(body.length).flip());write(journal,ByteBuffer.wrap(body));write(journal,ByteBuffer.wrap(digest(body)));journal.force(true);
   apply(value); // No in-memory success can precede the durable record.
  }catch(IOException|RuntimeException e){poisoned=true;throw e;}
 }
 private void replay()throws IOException {
  long valid=0; journal.position(0);
  while(journal.position()<journal.size()) {
   long start=journal.position();ByteBuffer size=ByteBuffer.allocate(4);
   if(!read(journal,size)){preserveTail(start);break;}
   int length=size.flip().getInt();if(length<=0||length>MAX_RECORD)throw new IOException("Controlled journal integrity failure");
   if(journal.size()-journal.position()<length+32L){preserveTail(start);break;}
   ByteBuffer body=ByteBuffer.allocate(length),checksum=ByteBuffer.allocate(32);if(!read(journal,body)||!read(journal,checksum))throw new IOException("Controlled journal changed");
   if(!MessageDigest.isEqual(digest(body.array()),checksum.array()))throw new IOException("Controlled journal checksum mismatch");
   Receipt value=decode(body.array());verifyPayload(value);verifyOutput(value);
   try{apply(value);}catch(RuntimeException e){throw new IOException("Controlled journal mapping conflict");}
   valid=journal.position();
  }
  journal.position(journal.size());
 }
 private void preserveTail(long start)throws IOException {
  long length=journal.size()-start;if(length>MAX_RECORD+36L)throw new IOException("Invalid controlled journal suffix");
  ByteBuffer suffix=ByteBuffer.allocate((int)length);journal.position(start);if(!read(journal,suffix))throw new IOException("Controlled journal changed");
  immutable(root.resolve("torn-"+UUID.randomUUID()+".bin"),suffix.array());
  // Only an incomplete, never-acknowledged suffix is removed; its exact bytes remain durable.
  journal.truncate(start);journal.force(true);syncDirectory(root);
 }
 private void apply(Receipt next) {
  Receipt old=executions.get(next.executionId());
  if(old==null){if(!next.state().equals("UNKNOWN")||keys.containsKey(next.logicalBusinessKey())||executions.size()>=MAX_EXECUTIONS)throw conflict();keys.put(next.logicalBusinessKey(),next.executionId());}
  else if(!sameRequest(old,next)||!old.state().equals("UNKNOWN")||next.state().equals("UNKNOWN"))throw conflict();
  if(next.resultId()!=null){if(resultOwners.putIfAbsent(next.resultId(),next.executionId())!=null)throw conflict();}
  executions.put(next.executionId(),next);
 }
 private void verifyPayload(Receipt value)throws IOException {checked(payloads.resolve(value.executionId()+".json"),value.payloadBytes(),value.payloadSha256(),MAX_PAYLOAD);}
 private void verifyOutput(Receipt value)throws IOException {if(value.state().equals("SUCCEEDED"))checked(results.resolve(value.resultId()+".json"),value.outputBytes(),value.outputSha256(),MAX_OUTPUT);}
 private static byte[] checked(Path path,long length,String expected,int cap)throws IOException {
  if(length<0||length>cap||Files.isSymbolicLink(path)||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)!=length)throw new IOException("Controlled immutable file integrity failure");
  try(var in=Files.newInputStream(path,READ,LinkOption.NOFOLLOW_LINKS)) {
   byte[] bytes=in.readNBytes(Math.toIntExact(length));if(bytes.length!=length||in.read()!=-1||!sha(bytes).equals(expected))throw new IOException("Controlled immutable file checksum mismatch");return bytes;
  }
 }
 private static void immutable(Path target,byte[] content)throws IOException {
  rejectLink(target);
  if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)){checked(target,content.length,sha(content),content.length);return;}
  Path temp=Files.createTempFile(target.getParent(),"pending-",".bin",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
  try(var ch=FileChannel.open(temp,WRITE,LinkOption.NOFOLLOW_LINKS)){write(ch,ByteBuffer.wrap(content));ch.force(true);}
  // The exclusive store lock prevents another writer from replacing this identity.
  Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE);syncDirectory(target.getParent());
 }
 private static void directory(Path path)throws IOException {
  rejectLink(path);if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS))Files.createDirectory(path,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
  if(!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Controlled store directory required");
 }
 private static void rejectLink(Path path)throws IOException {if(Files.isSymbolicLink(path))throw new IOException("Controlled store symlink rejected");}
 private static void syncDirectory(Path path)throws IOException {try(var ch=FileChannel.open(path,READ)){ch.force(true);}}
 private static void write(FileChannel channel,ByteBuffer data)throws IOException {while(data.hasRemaining())channel.write(data);}
 private static boolean read(FileChannel channel,ByteBuffer data)throws IOException {while(data.hasRemaining())if(channel.read(data)<0)return false;return true;}
 private static byte[] encode(Receipt r)throws IOException {
  var bytes=new ByteArrayOutputStream();try(var d=new DataOutputStream(bytes)) {
   d.writeInt(1);for(String s:List.of(r.state(),r.executionId(),r.logicalBusinessKey(),r.logicalRequestHash(),r.executionRequestHash(),r.payloadSha256()))d.writeUTF(s);
   d.writeLong(r.payloadBytes());for(String s:Arrays.asList(r.resultId(),r.outputSha256(),r.receiptId(),r.code())){d.writeBoolean(s!=null);if(s!=null)d.writeUTF(s);}d.writeLong(r.outputBytes());
  }return bytes.toByteArray();
 }
 private static Receipt decode(byte[] bytes)throws IOException {
  try(var d=new DataInputStream(new ByteArrayInputStream(bytes))){
   if(d.readInt()!=1)throw new IOException("Controlled journal version unavailable");String state=d.readUTF(),id=d.readUTF(),key=d.readUTF(),lh=d.readUTF(),eh=d.readUTF(),ph=d.readUTF();long pl=d.readLong();
   String[] opt=new String[4];for(int i=0;i<4;i++)opt[i]=d.readBoolean()?d.readUTF():null;long ol=d.readLong();if(d.read()!=-1)throw new IOException("Controlled journal trailing bytes");
   uuid(id);key(key);hash(lh);hash(eh);hash(ph);if(!lh.equals(eh)||pl<0||pl>MAX_PAYLOAD)throw conflict();
   switch(state){
    case "UNKNOWN"->{if(Arrays.stream(opt).anyMatch(Objects::nonNull)||ol!=0)throw conflict();}
    case "SUCCEEDED"->{if(opt[0]==null||!opt[0].startsWith("cmr-")||opt[2]!=null||opt[3]!=null||ol<0||ol>MAX_OUTPUT)throw conflict();uuid(opt[0].substring(4));hash(opt[1]);}
    case "FAILED_ZERO_EFFECT"->{if(opt[0]!=null||opt[1]!=null||ol!=0||opt[2]==null||!opt[2].startsWith("cmz-")||opt[3]==null||!opt[3].matches("[A-Z][A-Z0-9_]{0,63}"))throw conflict();uuid(opt[2].substring(4));}
    default->throw conflict();
   }
   Receipt r=new Receipt(state,id,key,lh,eh,ph,pl,opt[0],opt[1],ol,opt[2],opt[3]);if(!Arrays.equals(bytes,encode(r)))throw conflict();return r;
  }catch(RuntimeException e){throw new IOException("Controlled journal schema failure");}
 }
 private static boolean sameRequest(Receipt a,Receipt b){return a.executionId().equals(b.executionId())&&a.logicalBusinessKey().equals(b.logicalBusinessKey())&&a.logicalRequestHash().equals(b.logicalRequestHash())&&a.executionRequestHash().equals(b.executionRequestHash())&&a.payloadSha256().equals(b.payloadSha256())&&a.payloadBytes()==b.payloadBytes();}
 private static void uuid(String s){if(s==null||!s.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")||!UUID.fromString(s).toString().equals(s))throw new IllegalArgumentException("Invalid controlled identity");}
 private static void key(String s){if(s==null||!s.startsWith("minutes:"))throw new IllegalArgumentException("Invalid logical key");uuid(s.substring(8));}
 private static void hash(String s){if(s==null||!s.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid digest");}
 private static byte[] digest(byte[] b){try{return MessageDigest.getInstance("SHA-256").digest(b);}catch(Exception e){throw new IllegalStateException("SHA-256 unavailable");}}
 private static String sha(byte[] b){return HexFormat.of().formatHex(digest(b));}
 private static IllegalStateException conflict(){return new IllegalStateException("Immutable controlled execution conflict");}
 private void live(){if(closed||poisoned)throw new IllegalStateException("Controlled store unavailable");}
 @Override public synchronized void close()throws IOException {
  if(closed)return;closed=true;IOException failure=null;
  try{if(journal!=null)journal.close();}catch(IOException e){failure=e;}
  try{if(lock!=null&&lock.isValid())lock.release();}catch(IOException e){failure=e;}
  try{if(lockChannel!=null)lockChannel.close();}catch(IOException e){failure=e;}
  if(failure!=null)throw failure;
 }
}
