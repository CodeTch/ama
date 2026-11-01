package ama.transcript;

import java.security.*;
import java.util.*;

/** Exact per-actor, exact-version-batch read proof. It never grants a local meeting ACL. */
final class KnowledgeMaterialClaims {
 private static final Set<String> PINS=Set.of("providerId","storeInstanceId","clientId","delegationKeyId","assertionKeyId");
 private KnowledgeMaterialClaims(){}
 static String itemsHash(List<Map<String,String>> items){
  if(items==null||items.isEmpty()||items.size()>64||new HashSet<>(items).size()!=items.size())throw invalid();var values=new ArrayList<ama.canonical.CanonicalValue>();
  for(var item:items){if(!item.keySet().equals(Set.of("libraryId","documentId","version")))throw invalid();var fields=new ArrayList<ama.canonical.CanonicalValue.Entry>();for(String key:List.of("documentId","libraryId","version")){String value=item.get(key);if(value==null||!value.matches(key.equals("version")?"[0-9a-f]{64}":"[A-Za-z0-9_-]{1,128}"))throw invalid();fields.add(ama.canonical.CanonicalValue.field(key,ama.canonical.CanonicalValue.string(value)));}values.add(new ama.canonical.CanonicalValue.ObjectValue(fields));}
  return ControlledKnowledgeClient.digest(ama.config.CanonicalJson.encode(new ama.canonical.CanonicalValue.Sequence(values),65536,()->false));
 }
 static Map<String,Object> verify(PublicKey key,Map<String,String> pins,String token,String actor,String itemsHash,long now){
  if(key==null||!pins.keySet().equals(PINS)||token==null||token.length()>8192||token.chars().filter(c->c=='.').count()!=1||now<1||actor==null||actor.isBlank()||actor.codePointCount(0,actor.length())>255||itemsHash==null||!itemsHash.matches("[0-9a-f]{64}"))throw invalid();
  String[] parts=token.split("\\.",-1);byte[] raw=ControlledKnowledgeClient.unbase64(parts[0]),signature=ControlledKnowledgeClient.unbase64(parts[1]);
  if(raw.length>6144||signature.length!=64)throw invalid();
  try{var verifier=Signature.getInstance("Ed25519");verifier.initVerify(key);verifier.update(raw);if(!verifier.verify(signature))throw invalid();}catch(GeneralSecurityException failure){throw invalid();}
  var claims=new LinkedHashMap<>(ControlledKnowledgeClient.parse(raw,6144));
  var exact=new HashSet<>(PINS);exact.addAll(Set.of("schema","actorId","itemsSha256","issuedAt","expiresAt"));
  if(!claims.keySet().equals(exact)||!"ama.controlled-material-authority.v1".equals(claims.get("schema"))||!actor.equals(claims.get("actorId"))||!itemsHash.equals(claims.get("itemsSha256")))throw invalid();
  for(String pin:PINS)if(!Objects.equals(pins.get(pin),claims.get(pin)))throw invalid();
  long issued=number(claims,"issuedAt"),expires=number(claims,"expiresAt");
  if(issued<1||issued>now+2||expires<=now||expires>now+5||expires<=issued||expires-issued>5)throw invalid();
  if(!Arrays.equals(raw,ControlledKnowledgeClient.canonical(claims)))throw invalid();
  claims.put("issuedAt",issued);claims.put("expiresAt",expires);
  return Map.copyOf(claims);
 }
 private static long number(Map<String,Object> claims,String field){Object n=claims.get(field);if(!(n instanceof Byte||n instanceof Short||n instanceof Integer||n instanceof Long))throw invalid();return ((Number)n).longValue();}
 private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid controlled material authority assertion");}
}
