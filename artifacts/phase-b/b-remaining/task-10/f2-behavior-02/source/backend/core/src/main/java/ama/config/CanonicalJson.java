package ama.config;
import ama.canonical.CanonicalValue;
import ama.canonical.CanonicalValueCodec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BooleanSupplier;
import static ama.canonical.CanonicalValue.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
/** Finite typed formatter J. Parsing is exclusively Jackson streaming. */
public final class CanonicalJson {
 private CanonicalJson(){}
 public static byte[] encode(CanonicalValue value,long limit,BooleanSupplier cancelled){
  require(limit>0,INVALID_ARGUMENT);var out=new Writer(limit,cancelled);out.value(value,1);check(cancelled);return out.out.toByteArray();
 }
 public static boolean matches(CanonicalValue value,ama.contract.ConfigArchiveContracts.ArchiveBytes expected,BooleanSupplier cancelled){
  var writer=new Writer(expected.size(),cancelled,expected.openStream());writer.value(value,1);
  try{check(cancelled);return writer.expected.read()==-1;}catch(java.io.IOException impossible){throw fail(READ_FAILED);}
 }
 private static final class Writer {
  final long limit;final BooleanSupplier cancelled;final ByteArrayOutputStream out=new ByteArrayOutputStream();final java.io.InputStream expected;
  Writer(long limit,BooleanSupplier cancelled){this(limit,cancelled,null);}
  Writer(long limit,BooleanSupplier cancelled,java.io.InputStream expected){this.limit=limit;this.cancelled=cancelled;this.expected=expected;}
  void bytes(byte[] bytes){
   check(cancelled);if(expected!=null){for(byte b:bytes){check(cancelled);try{require(expected.read()==Byte.toUnsignedInt(b),INVALID_SCHEMA);}catch(java.io.IOException impossible){throw fail(READ_FAILED);}}}
   else {require(bytes.length<=limit-out.size()&&bytes.length<=Integer.MAX_VALUE-out.size(),BUDGET_EXCEEDED);out.writeBytes(bytes);}
  }
  void ascii(String value){bytes(value.getBytes(StandardCharsets.US_ASCII));}
  void string(String value){
   scalars(value);bytes(new byte[]{34});for(int i=0;i<value.length();){check(cancelled);int c=value.codePointAt(i);i+=Character.charCount(c);
    if(c==34||c==92)bytes(new byte[]{92,(byte)c});
    else if(c<32)bytes(new byte[]{92,117,48,48,(byte)Character.forDigit(c>>>4,16),(byte)Character.forDigit(c&15,16)});
    else bytes(new String(Character.toChars(c)).getBytes(StandardCharsets.UTF_8));
   }bytes(new byte[]{34});
  }
  void value(CanonicalValue v,int depth){
   check(cancelled);require(depth<=32,INVALID_SCHEMA);
   switch(v){
    case Null ignored->ascii("null");case Bool b->ascii(b.value()?"true":"false");case UInt n->ascii(Long.toString(n.value()));case Scalar s->string(s.value());
    case Sequence s->{ascii("[");boolean first=true;for(var item:s.values()){if(!first)ascii(",");first=false;value(item,depth+1);}ascii("]");}
    case ObjectValue o->{var entries=new ArrayList<>(o.entries());entries.sort((a,b)->CanonicalValueCodec.compareUtf8(a.key(),b.key(),cancelled));ascii("{");boolean first=true;for(var e:entries){if(!first)ascii(",");first=false;string(e.key());ascii(":");value(e.value(),depth+1);}ascii("}");}
   }
  }
 }
}
