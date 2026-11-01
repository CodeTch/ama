package ama.transcript;

import java.security.*;
import java.util.*;

/** Strict cryptographic wire verification only; no caller data confers a local access gate. */
final class KnowledgeGovernanceClaims {
 private static final Set<String> PINS=Set.of("providerId","storeInstanceId","clientId","delegationKeyId","assertionKeyId");
 private KnowledgeGovernanceClaims(){}
 static Map<String,Object> verify(PublicKey key,Map<String,String> pins,String token,boolean fresh,long now){
  if(key==null||!pins.keySet().equals(PINS)||token==null||token.length()>8192||token.chars().filter(c->c=='.').count()!=1||now<1)throw invalid();
  String[] parts=token.split("\\.",-1);byte[] raw=ControlledKnowledgeClient.unbase64(parts[0]),signed=ControlledKnowledgeClient.unbase64(parts[1]);
  if(raw.length>6144||signed.length!=64)throw invalid();
  try{var verifier=Signature.getInstance("Ed25519");verifier.initVerify(key);verifier.update(raw);if(!verifier.verify(signed))throw invalid();}catch(GeneralSecurityException failure){throw invalid();}
  var value=new LinkedHashMap<>(ControlledKnowledgeClient.parse(raw,6144));if(!Arrays.equals(raw,ControlledKnowledgeClient.canonical(value)))throw invalid();
  for(String pin:PINS)if(!Objects.equals(pins.get(pin),value.get(pin)))throw invalid();
  var exact=new HashSet<String>(PINS);exact.addAll(Set.of("schema","documentId","publicationId","actorId","state","revisionToken","eventId"));
  exact.addAll(fresh?Set.of("issuedAt","expiresAt"):Set.of("verifiedAt","commandKey","requestSha256","reasonSha256","action"));
  if(!value.keySet().equals(exact)||!(fresh?"ama.controlled-kb-governance-state.v1":"ama.controlled-kb-governance-receipt.v1").equals(value.get("schema")))throw invalid();
  match(value,"documentId","doc-[0-9a-f]{32}");match(value,"publicationId","[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");match(value,"eventId","[0-9a-f]{32}");
  String actor=string(value,"actorId");if(actor.isBlank()||actor.codePointCount(0,actor.length())>255||actor.chars().anyMatch(c->c<32||c>=127&&c<=159))throw invalid();
  String state=string(value,"state");if(!Set.of("ACTIVE","WITHDRAWN","OBSOLETE").contains(state))throw invalid();
  match(value,"revisionToken","0|[1-9][0-9]{0,18}");long revision;try{revision=Long.parseLong(string(value,"revisionToken"));}catch(NumberFormatException malformed){throw invalid();}if(revision==Long.MAX_VALUE)throw invalid();
  if(fresh){
   long issued=number(value,"issuedAt"),expires=number(value,"expiresAt");if(issued<1||issued>now+2||expires<=now||expires>now+5||expires<issued||expires-issued>5)throw invalid();value.put("issuedAt",issued);value.put("expiresAt",expires);
  }else{
   match(value,"commandKey","[A-Za-z0-9_.:-]{1,128}");match(value,"requestSha256","[0-9a-f]{64}");match(value,"reasonSha256","[0-9a-f]{64}");long verified=number(value,"verifiedAt");if(verified<1||verified>now+2||revision<1)throw invalid();value.put("verifiedAt",verified);
   String action=string(value,"action");if(!("WITHDRAW".equals(action)&&"WITHDRAWN".equals(state)||"OBSOLETE".equals(action)&&"OBSOLETE".equals(state)))throw invalid();
  }
  return Collections.unmodifiableMap(value);
 }
 private static String string(Map<String,Object> value,String field){if(!(value.get(field) instanceof String s))throw invalid();return s;}
 private static void match(Map<String,Object> value,String field,String pattern){if(!string(value,field).matches(pattern))throw invalid();}
 private static long number(Map<String,Object> value,String field){Object n=value.get(field);if(!(n instanceof Byte||n instanceof Short||n instanceof Integer||n instanceof Long))throw invalid();return ((Number)n).longValue();}
 private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid controlled governance assertion");}
}
