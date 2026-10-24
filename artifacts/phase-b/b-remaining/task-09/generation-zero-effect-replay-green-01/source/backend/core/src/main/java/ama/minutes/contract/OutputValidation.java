package ama.minutes.contract;
import ama.contract.MinutesContracts.*;
import ama.canonical.CanonicalValueCodec;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BooleanSupplier;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

public final class OutputValidation {
 public ValidatedOutput validate(Output output,ValidationContext context,BooleanSupplier cancelled){
  require(output!=null&&context!=null,INVALID_ARGUMENT);check(cancelled);
  require(context.scope()==Scope.FULL,CAPABILITY_UNAVAILABLE);
  require(output.inputFingerprint().equals(context.inputFingerprint())&&context.inputFingerprint().equals(context.taskFingerprint()),FINGERPRINT_MISMATCH);
  var allKeys=new HashSet<String>();var elements=new HashMap<String,Element>();
  for(var c:Category.values())for(var e:output.elements().get(c)){check(cancelled);require(allKeys.add(e.key()),INVALID_SCHEMA);elements.put(e.key(),e);}
  var used=new HashSet<String>();
  for(var d:output.sensitive()){check(cancelled);require(allKeys.add(d.key()),INVALID_SCHEMA);used.addAll(d.sourceIds().values());}
  for(var n:output.background()){check(cancelled);require(allKeys.add(n.key()),INVALID_SCHEMA);used.addAll(n.sourceIds().values());}
  var pairs=new HashSet<TargetAlias>();var anchored=new ArrayList<AnchoredEvidence>();
  for(var e:output.evidence()){
   check(cancelled);String target=output.body();
   if(e.target() instanceof ElementTarget t){require(elements.containsKey(t.elementKey()),TARGET_INVALID);target=elements.get(t.elementKey()).text();}
   var a=anchor(target,e.target().start(),e.target().end(),cancelled);anchored.add(new AnchoredEvidence(e,a));
   for(var id:e.sourceIds().values()){check(cancelled);require(pairs.add(new TargetAlias(e.target(),id)),SOURCE_MEMBERSHIP_MISMATCH);used.add(id);}
  }
  var refs=new HashSet<String>();
  for(var ref:output.sourceRefs()){
   check(cancelled);require(refs.add(ref.sourceId()),SOURCE_MEMBERSHIP_MISMATCH);
   require(context.catalog().source(ref.sourceId()).fact().locator().equals(ref.locator()),LOCATOR_INVALID);
  }
  require(refs.equals(used),SOURCE_MEMBERSHIP_MISMATCH);
  for(var id:used){check(cancelled);context.catalog().source(id);}
  check(cancelled);return new ValidatedOutput(output,anchored);
 }
 private record TargetAlias(Target target,String alias){}
 public static long scalarCount(String text,BooleanSupplier cancelled){
  long n=0;for(int i=0;i<text.length();){check(cancelled);char c=text.charAt(i);
   if(Character.isHighSurrogate(c)){require(i+1<text.length()&&Character.isLowSurrogate(text.charAt(i+1)),INVALID_SCALAR);i+=2;}
   else {require(!Character.isLowSurrogate(c),INVALID_SCALAR);i++;}n++;}return n;
 }
 public static String textHash(String text,BooleanSupplier cancelled) {
  scalars(text);var digest=CanonicalValueCodec.digest();
  // Fixed chunks keep cancellation observable without allocating a second full target.
  for(int i=0;i<text.length();) {check(cancelled);int end=(int)Math.min((long)i+1024,text.length());
   if(end<text.length()&&Character.isHighSurrogate(text.charAt(end-1)))end--;
   digest.update(text.substring(i,end).getBytes(StandardCharsets.UTF_8));i=end;}
  check(cancelled);return HexFormat.of().formatHex(digest.digest());
 }
 public static Anchor anchor(String text,long start,long end,BooleanSupplier cancelled){
  require(start>=0&&start<end&&end<=scalarCount(text,cancelled),TARGET_INVALID);
  return new Anchor(start,end,textHash(text,cancelled));
 }
 public static String anchorText(Anchor a){
  return "{\"schemaVersion\":\"ama.anchor.v1\",\"unit\":\"UNICODE_SCALAR\",\"start\":"+a.start()+",\"end\":"+a.end()+",\"targetTextSha256\":\""+a.targetTextSha256()+"\"}";
 }
}
