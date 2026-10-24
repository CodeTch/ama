package ama.model;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Strict provider-facing store. The lower journal is a storage primitive; every provider
 * operation additionally reloads and independently verifies the durable envelope and payload. */
public final class ControlledMinutesProviderStore implements AutoCloseable {
 private final ControlledMinutesStore journal;
 private final Path requests,envelopes;
 public ControlledMinutesProviderStore(Path root,String explicitMode)throws IOException {this(root,explicitMode,4096);}
 ControlledMinutesProviderStore(Path root,String explicitMode,int executionLimit)throws IOException {
  journal=new ControlledMinutesStore(root,explicitMode,executionLimit);requests=root.toAbsolutePath().normalize().resolve("requests");envelopes=root.toAbsolutePath().normalize().resolve("envelopes");
  try{ControlledMinutesStore.directory(requests);ControlledMinutesStore.directory(envelopes);for(var receipt:journal.entries())validated(receipt);}
  catch(IOException|RuntimeException e){journal.close();throw e;}
 }
 public synchronized ControlledMinutesStore.Receipt reserve(byte[] body,String header)throws IOException {
  if(body==null||body.length>ControlledMinutesProtocol.MAX_REQUEST)throw new IllegalArgumentException("Controlled request budget exceeded");
  byte[] exact=body.clone();var request=ControlledMinutesProtocol.verify(exact,header);var old=journal.lookup(request.executionId());
  if(old!=null)validated(old); // Accepted records must never be repaired from a duplicate request.
  ControlledMinutesStore.immutable(requests.resolve(request.executionId()+".json"),exact);
  ControlledMinutesStore.immutable(envelopes.resolve(request.executionId()+".json"),request.envelope());
  var receipt=journal.reserve(request.executionId(),request.logicalBusinessKey(),request.logicalRequestHash(),request.logicalRequestHash(),request.payloadSha256(),request.payload());
  validated(receipt);return receipt;
 }
 public synchronized ControlledMinutesStore.Receipt lookup(String execution)throws IOException {
  var receipt=journal.lookup(execution);if(receipt!=null)validated(receipt);return receipt;
 }
 public synchronized ControlledMinutesStore.Receipt commitSuccess(String execution,byte[] output)throws IOException {
  var receipt=journal.lookup(execution);if(receipt==null)throw new IllegalArgumentException("Unknown controlled execution");validated(receipt);return journal.commitSuccess(execution,output);
 }
 public synchronized ControlledMinutesStore.Receipt reject(String execution,String code)throws IOException {
  var receipt=journal.lookup(execution);if(receipt==null)throw new IllegalArgumentException("Unknown controlled execution");validated(receipt);return journal.reject(execution,code);
 }
 public synchronized byte[] result(String id)throws IOException {
  var receipt=journal.entries().stream().filter(r->id.equals(r.resultId())).findFirst().orElseThrow(()->new FileNotFoundException("Controlled result unavailable"));validated(receipt);return journal.result(id);
 }
 synchronized List<String> pending()throws IOException {
  var ids=new ArrayList<String>();for(var r:journal.entries()){validated(r);if(r.state().equals("UNKNOWN"))ids.add(r.executionId());}return List.copyOf(ids);
 }
 synchronized ControlledMinutesProtocol.VerifiedRequest request(String execution)throws IOException {
  var receipt=journal.lookup(execution);if(receipt==null)throw new IllegalArgumentException("Unknown controlled execution");return validated(receipt);
 }
 private ControlledMinutesProtocol.VerifiedRequest validated(ControlledMinutesStore.Receipt receipt)throws IOException {
  byte[] body=read(requests.resolve(receipt.executionId()+".json"),ControlledMinutesProtocol.MAX_REQUEST);
  byte[] envelope=read(envelopes.resolve(receipt.executionId()+".json"),ControlledMinutesProtocol.MAX_ENVELOPE_HEADER*3/4);
  var request=ControlledMinutesProtocol.verify(body,Base64.getUrlEncoder().withoutPadding().encodeToString(envelope));
  if(!request.executionId().equals(receipt.executionId())||!request.logicalBusinessKey().equals(receipt.logicalBusinessKey())||!request.logicalRequestHash().equals(receipt.logicalRequestHash())||!receipt.executionRequestHash().equals(request.logicalRequestHash())||!request.payloadSha256().equals(receipt.payloadSha256())||!Arrays.equals(request.payload(),journal.payload(receipt.executionId())))throw new IOException("Controlled durable envelope mapping mismatch");
  return request;
 }
 private static byte[] read(Path path,int maximum)throws IOException {
  if(Files.isSymbolicLink(path)||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Controlled request metadata unavailable");long size=Files.size(path);if(size<1||size>maximum)throw new IOException("Controlled request metadata budget exceeded");
  try(var in=Files.newInputStream(path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){byte[] bytes=in.readNBytes((int)size);if(bytes.length!=size||in.read()!=-1)throw new IOException("Controlled request metadata changed");return bytes;}
 }
 @Override public synchronized void close()throws IOException {journal.close();}
}
