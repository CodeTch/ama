package ama.model;
import ama.task.PreparationCancellation;
import java.io.*;import java.util.*;
import static ama.config.WireValues.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
/** Fixed controlled protocol client. Only this instance's actual authenticated exchanges issue receipts. */
public final class ControlledMinutesClient implements AutoCloseable {
 private final ControlledMinutesHttpTransport transport;
 public ControlledMinutesClient(ControlledMinutesHttpTransport transport){this.transport=Objects.requireNonNull(transport);}
 public static final class Request {
  private final ControlledMinutesClient issuer;private final ControlledMinutesProtocol.VerifiedRequest binding;private final ControlledMinutesHttpTransport.Outbound post,lookup;
  private Request(ControlledMinutesClient issuer,ControlledMinutesProtocol.VerifiedRequest binding,ControlledMinutesHttpTransport.Outbound post,ControlledMinutesHttpTransport.Outbound lookup){this.issuer=issuer;this.binding=binding;this.post=post;this.lookup=lookup;}
  public String executionId(){return binding.executionId();}public String requestHash(){return binding.logicalRequestHash();}public String payloadHash(){return binding.payloadSha256();}public String businessKey(){return binding.logicalBusinessKey();}
 }
 public static final class Receipt {
  private final ControlledMinutesClient issuer;private final Request request;private final String state,result,outputHash,receipt,code;private final byte[] output;private final java.time.Instant verifiedAt;
  private Receipt(ControlledMinutesClient issuer,Request request,String state,String result,String outputHash,String receipt,String code,byte[] output){this.issuer=issuer;this.request=request;this.state=state;this.result=result;this.outputHash=outputHash;this.receipt=receipt;this.code=code;this.output=output;verifiedAt=java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);}
  public String state(){return state;}public String resultId(){return result;}public String outputHash(){return outputHash;}public String receiptId(){return receipt;}public String code(){return code;}public byte[] output(){return output==null?null:output.clone();}public java.time.Instant verifiedAt(){return verifiedAt;}
 }
 public Request prepare(byte[] body,String envelope)throws IOException {
  var binding=ControlledMinutesProtocol.verify(body,envelope);var post=transport.prepare("POST","/v1/minutes/executions",Map.of(ControlledMinutesProtocol.HEADER,envelope),body);var lookup=transport.prepare("GET","/v1/minutes/executions/"+binding.executionId(),Map.of("X-Ama-Logical-Business-Key",binding.logicalBusinessKey(),"X-Ama-Logical-Request-Hash",binding.logicalRequestHash(),"X-Ama-Execution-Request-Hash",binding.logicalRequestHash(),"X-Ama-Payload-Sha256",binding.payloadSha256(),"X-Ama-Execution-No","1"),new byte[0]);return new Request(this,binding,post,lookup);
 }
 public Receipt execute(Request request,PreparationCancellation cancel)throws IOException {owned(request);return receive(request,request.post,cancel);}
 public Receipt lookup(Request request,PreparationCancellation cancel)throws IOException {owned(request);return receive(request,request.lookup,cancel);}
 public void require(Receipt receipt,Request request){owned(request);if(receipt==null||receipt.issuer!=this||receipt.request!=request)throw new IllegalArgumentException("Foreign controlled receipt or request binding");}
 private static void require(boolean condition,ama.contract.MinutesContracts.FailureCode code){ama.contract.MinutesContracts.require(condition,code);}
 private void owned(Request request){if(request==null||request.issuer!=this)throw new IllegalArgumentException("Foreign controlled request");}
 private Receipt receive(Request request,ControlledMinutesHttpTransport.Outbound outbound,PreparationCancellation cancel)throws IOException {
  byte[] metadata=read(outbound,cancel,65536);var value=BoundedModelJson.parse(metadata,65536);var entries=value.entries();var stateEntry=entries.stream().filter(e->e.key().equals("state")).toList();require(stateEntry.size()==1,INVALID_SCHEMA);String state=text(stateEntry.getFirst().value());require(Set.of("UNKNOWN","SUCCEEDED","FAILED_ZERO_EFFECT").contains(state),INVALID_SCHEMA);var keys=new ArrayList<>(List.of("protocolVersion","state","executionId","executionNo","logicalBusinessKey","logicalRequestHash","executionRequestHash","payloadSha256"));if(state.equals("SUCCEEDED"))keys.addAll(List.of("resultId","outputSha256","outputBytes"));else if(state.equals("FAILED_ZERO_EFFECT"))keys.addAll(List.of("receiptId","basis","code"));var fields=fields(value,keys.toArray(String[]::new));var expected=request.binding;
  require(ControlledMinutesProtocol.VERSION.equals(text(fields.get("protocolVersion")))&&expected.executionId().equals(text(fields.get("executionId")))&&number(fields.get("executionNo"))==1&&expected.logicalBusinessKey().equals(text(fields.get("logicalBusinessKey")))&&expected.logicalRequestHash().equals(text(fields.get("logicalRequestHash")))&&expected.logicalRequestHash().equals(text(fields.get("executionRequestHash")))&&expected.payloadSha256().equals(text(fields.get("payloadSha256"))),PROVENANCE_UNAVAILABLE);
  String result=null,outputHash=null,receipt=null,code=null;byte[] output=null;
  if(state.equals("SUCCEEDED")){result=text(fields.get("resultId"));require(result.startsWith("cmr-"),INVALID_SCHEMA);uuid(result.substring(4));outputHash=text(fields.get("outputSha256"));hash(outputHash);long length=number(fields.get("outputBytes"));require(length>=0&&length<=ControlledMinutesStore.MAX_OUTPUT,BUDGET_EXCEEDED);var get=transport.prepare("GET","/v1/minutes/results/"+result,Map.of(),new byte[0]);output=read(get,cancel,ControlledMinutesStore.MAX_OUTPUT);require(output.length==length&&HexFormat.of().formatHex(ama.canonical.CanonicalValueCodec.digest().digest(output)).equals(outputHash),PROVENANCE_UNAVAILABLE);}
  else if(state.equals("FAILED_ZERO_EFFECT")){receipt=text(fields.get("receiptId"));require(receipt.startsWith("cmz-")&&"DEFINITIVE_REJECTION".equals(text(fields.get("basis"))),INVALID_SCHEMA);uuid(receipt.substring(4));code=text(fields.get("code"));require(code.matches("[A-Z][A-Z0-9_]{0,63}"),INVALID_SCHEMA);}
  check(cancel::cancelled);return new Receipt(this,request,state,result,outputHash,receipt,code,output);
 }
 private byte[] read(ControlledMinutesHttpTransport.Outbound outbound,PreparationCancellation cancel,int limit)throws IOException {try(var exchange=transport.exchange(outbound,cancel,limit)){if(exchange.status()!=200)throw new IOException("Controlled endpoint did not return an authoritative receipt");var bytes=exchange.body().readNBytes(limit+1);if(bytes.length>limit)throw new IOException("Controlled response budget exceeded");return bytes;}}
 public void close(){transport.close();}
}
