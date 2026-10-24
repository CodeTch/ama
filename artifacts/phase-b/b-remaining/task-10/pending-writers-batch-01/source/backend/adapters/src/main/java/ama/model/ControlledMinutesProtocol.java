package ama.model;

import ama.canonical.*;
import ama.config.CanonicalJson;
import ama.config.WireValues;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.ConfigArchiveContracts.Ref;
import ama.contract.MinutesContracts.*;
import ama.minutes.contract.ContractValues;
import java.util.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
import static ama.config.WireValues.*;

/** Controlled wire consistency. It does not attest DB facts, archives, or permission to send. */
public final class ControlledMinutesProtocol {
 public static final String VERSION="ama.controlled-minutes-http.v1", MODEL="CONTROLLED_MINUTES_V1", HEADER="X-Ama-Minutes-Request-Envelope";
 public static final int MAX_REQUEST=16*1024*1024,MAX_ENVELOPE_HEADER=8192,MAX_HEADERS=16384,MAX_HEADER_COUNT=32,MAX_HEADER_NAME=128;
 static final CanonicalBudget CANONICAL=new CanonicalBudget(16*1024*1024,32,400000,4*1024*1024,100000);
 public static final class VerifiedRequest {
  private final String executionId,logicalKey,requestHash,payloadHash,inputFingerprint;
  private final byte[] payload,envelope;private final CanonicalValue.ObjectValue payloadValue;
  private VerifiedRequest(String id,String key,String request,String ph,String fp,byte[] payload,byte[] envelope,CanonicalValue.ObjectValue value){executionId=id;logicalKey=key;requestHash=request;payloadHash=ph;inputFingerprint=fp;this.payload=payload;this.envelope=envelope;payloadValue=value;}
  public String executionId(){return executionId;}public String logicalBusinessKey(){return logicalKey;}public String logicalRequestHash(){return requestHash;}public String payloadSha256(){return payloadHash;}public String inputFingerprint(){return inputFingerprint;}
  public byte[] payload(){return payload.clone();}public byte[] envelope(){return envelope.clone();}CanonicalValue.ObjectValue payloadValue(){return payloadValue;}
 }
 public static VerifiedRequest verify(byte[] body,String header){
  require(body!=null&&body.length<=MAX_REQUEST,BUDGET_EXCEEDED);
  require(header!=null&&!header.isEmpty()&&header.length()<=MAX_ENVELOPE_HEADER&&header.matches("[A-Za-z0-9_-]+")&&header.length()%4!=1,INVALID_SCHEMA);
  byte[] envelope;try{envelope=Base64.getUrlDecoder().decode(header);}catch(IllegalArgumentException e){throw fail(INVALID_SCHEMA);}
  require(Base64.getUrlEncoder().withoutPadding().encodeToString(envelope).equals(header),INVALID_SCHEMA);
  var data=BoundedModelJson.parse(envelope,MAX_ENVELOPE_HEADER*3/4);require(Arrays.equals(envelope,CanonicalJson.encode(data,MAX_ENVELOPE_HEADER*3/4,()->false)),INVALID_SCHEMA);
  var request=BoundedModelJson.parse(body,MAX_REQUEST);require(Arrays.equals(body,CanonicalJson.encode(request,MAX_REQUEST,()->false)),INVALID_SCHEMA);
  var b=fields(request,"protocolVersion","logicalBusinessKey","logicalRequestHash","executionRequestHash","executionId","executionNo","payloadSha256","payload");
  var e=fields(data,"logicalBusinessKey","inputId","inputFingerprint","protocolVersion","modelIdentity","payloadSha256","roots");
  literal(b.get("protocolVersion"),VERSION);literal(e.get("protocolVersion"),VERSION);literal(e.get("modelIdentity"),MODEL);require(number(b.get("executionNo"))==1,CAPABILITY_UNAVAILABLE);
  String execution=text(b.get("executionId")),input=text(e.get("inputId")),fingerprint=text(e.get("inputFingerprint"));uuid(execution);uuid(input);hash(fingerprint);
  String key=text(e.get("logicalBusinessKey"));require(key.equals("minutes:"+input)&&key.equals(text(b.get("logicalBusinessKey"))),INPUT_INVALID);
  var roots=array(e.get("roots"));require(!roots.isEmpty()&&roots.size()<=128,BUDGET_EXCEEDED);Ref previous=null;var kinds=EnumSet.noneOf(ArtifactType.class);
  for(var value:roots){Ref ref=WireValues.ref(value);require(Set.of(ArtifactType.PROMPT_CONFIG,ArtifactType.MODEL_CONFIG,ArtifactType.PROCESSING_CONFIG,ArtifactType.REVIEW_HASH).contains(ref.artifactType()),PROFILE_UNAVAILABLE);
   if(previous!=null){int order=previous.artifactType().name().compareTo(ref.artifactType().name());require(order<0||order==0&&CanonicalValueCodec.compareUtf8(previous.version(),ref.version(),()->false)<0,INVALID_SCHEMA);}previous=ref;kinds.add(ref.artifactType());}
  require(kinds.containsAll(Set.of(ArtifactType.PROMPT_CONFIG,ArtifactType.MODEL_CONFIG,ArtifactType.PROCESSING_CONFIG)),PROFILE_UNAVAILABLE);
  require(b.get("payload") instanceof CanonicalValue.ObjectValue,INVALID_SCHEMA);var payloadValue=(CanonicalValue.ObjectValue)b.get("payload");var payload=CanonicalJson.encode(payloadValue,MAX_REQUEST,()->false);
  var p=fields(payloadValue,"protocolVersion","modelIdentity","parameters","instructionText","inputManifest","transcript","template","pinnedElements","exclusions");literal(p.get("protocolVersion"),VERSION);literal(p.get("modelIdentity"),MODEL);text(p.get("instructionText"));
  var params=fields(p.get("parameters"),"temperatureMicros","maxOutputTokens","seed");require(number(params.get("temperatureMicros"))==0&&number(params.get("maxOutputTokens"))==4096&&number(params.get("seed"))==0,CAPABILITY_UNAVAILABLE);
  var manifest=fields(p.get("inputManifest"),"meetingId","inputId","confirmation","segments","batch","materials","template","exclusion","elements","sources","configs","scope","base");
  uuid(text(manifest.get("meetingId")));require(input.equals(text(manifest.get("inputId"))),INPUT_INVALID);
  var scope=fields(manifest.get("scope"),"kind","target");literal(scope.get("kind"),"FULL");WireValues.nil(scope.get("target"));var materials=fields(manifest.get("materials"),"mode","items");literal(materials.get("mode"),"EMPTY");require(array(materials.get("items")).isEmpty(),CAPABILITY_UNAVAILABLE);
  var template=fields(p.get("template"),"revisionId","name","themes");uuid(text(template.get("revisionId")));text(template.get("name"));var themes=array(template.get("themes"));require(themes.size()==Category.values().length,INVALID_SCHEMA);for(int i=0;i<themes.size();i++)literal(themes.get(i),Category.values()[i].name());
  var manifestTemplate=fields(manifest.get("template"),"mode","revisionId","revision","contentHash");require(template.get("revisionId").equals(manifestTemplate.get("revisionId")),INPUT_INVALID);
  var aliases=new HashSet<String>();for(var item:array(p.get("transcript"))){var segment=fields(item,"sourceId","segmentId","body","marks");require(aliases.add(text(segment.get("sourceId"))),INPUT_INVALID);uuid(text(segment.get("segmentId")));text(segment.get("body"));var marks=new HashSet<String>();for(var value:array(segment.get("marks"))){String mark=text(value);try{Mark.valueOf(mark);}catch(IllegalArgumentException ex){throw fail(INVALID_SCHEMA);}require(marks.add(mark),INVALID_SCHEMA);}}
  var pinned=fields(p.get("pinnedElements"),"mode","rows");require(Set.of("EMPTY","PINNED").contains(text(pinned.get("mode"))),INVALID_SCHEMA);if(text(pinned.get("mode")).equals("EMPTY"))require(array(pinned.get("rows")).isEmpty(),INVALID_SCHEMA);else array(pinned.get("rows"));
  var exclusions=fields(p.get("exclusions"),"revisionId","entries");uuid(text(exclusions.get("revisionId")));var manifestExclusion=fields(manifest.get("exclusion"),"mode","revisionId","revision","contentHash","entries");require(exclusions.get("revisionId").equals(manifestExclusion.get("revisionId")),INPUT_INVALID);
  for(var item:array(exclusions.get("entries"))){var exclusion=fields(item,"sourceId","locatorId","category","reasonHash");text(exclusion.get("sourceId"));uuid(text(exclusion.get("locatorId")));try{SensitiveKind.valueOf(text(exclusion.get("category")));}catch(IllegalArgumentException ex){throw fail(INVALID_SCHEMA);}if(!(exclusion.get("reasonHash") instanceof CanonicalValue.Null))hash(text(exclusion.get("reasonHash")));}
  String ph=sha(payload);require(ph.equals(text(b.get("payloadSha256")))&&ph.equals(text(e.get("payloadSha256"))),FINGERPRINT_MISMATCH);
  var codec=new CanonicalValueCodec();require(codec.sha256(ContractValues.envelope("ama.generation-input.v2","GENERATION_INPUT",p.get("inputManifest")),CANONICAL,()->false).equals(fingerprint),FINGERPRINT_MISMATCH);
  String rh=codec.sha256(ContractValues.envelope("ama.minutes-model-request.v1","MODEL_REQUEST",data),CANONICAL,()->false);require(rh.equals(text(b.get("logicalRequestHash")))&&rh.equals(text(b.get("executionRequestHash"))),FINGERPRINT_MISMATCH);
  return new VerifiedRequest(execution,key,rh,ph,fingerprint,payload,envelope,payloadValue);
 }
 static String sha(byte[] bytes){return HexFormat.of().formatHex(CanonicalValueCodec.digest().digest(bytes));}
 private ControlledMinutesProtocol(){}
}
