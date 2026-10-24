package ama.config;
import ama.canonical.CanonicalValue;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.ConfigArchiveContracts.Ref;
import static ama.contract.ConfigArchiveContracts.version;
import java.util.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
import static ama.canonical.CanonicalValue.*;
public final class WireValues {
 private WireValues(){}
 public static Map<String,CanonicalValue> fields(CanonicalValue value,String... expected){
  require(value instanceof ObjectValue,INVALID_SCHEMA);var out=new LinkedHashMap<String,CanonicalValue>();
  for(var e:((ObjectValue)value).entries())require(out.putIfAbsent(e.key(),e.value())==null,INVALID_JSON);
  var want=Set.of(expected);for(var key:out.keySet())require(want.contains(key),UNKNOWN_FIELD);require(out.keySet().equals(want),INVALID_SCHEMA);return out;
 }
 public static String text(CanonicalValue value){require(value instanceof Scalar,INVALID_SCHEMA);return ((Scalar)value).value();}
 public static long number(CanonicalValue value){require(value instanceof UInt,INVALID_NUMBER);return ((UInt)value).value();}
 public static int positiveInt(CanonicalValue v){long n=number(v);require(n>0&&n<=Integer.MAX_VALUE,INVALID_NUMBER);return (int)n;}
 public static long positiveLong(CanonicalValue v){return positive(number(v));}
 public static List<CanonicalValue> array(CanonicalValue v){require(v instanceof Sequence,INVALID_SCHEMA);return ((Sequence)v).values();}
 public static void nil(CanonicalValue v){require(v instanceof Null,INVALID_SCHEMA);}
 public static void literal(CanonicalValue v,String expected){require(text(v).equals(expected),PROFILE_UNAVAILABLE);}
 public static Ref ref(CanonicalValue v){
  var f=fields(v,"artifactType","version","sha256");ArtifactType type;
  try{type=ArtifactType.valueOf(text(f.get("artifactType")));}catch(IllegalArgumentException e){throw fail(PROFILE_UNAVAILABLE);}
  return new Ref(type,text(f.get("version")),text(f.get("sha256")));
 }
}
