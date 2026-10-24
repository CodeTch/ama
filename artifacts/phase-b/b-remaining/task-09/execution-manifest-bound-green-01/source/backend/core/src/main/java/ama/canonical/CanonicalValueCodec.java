package ama.canonical;

import ama.contract.MinutesContracts.CanonicalBudget;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.BooleanSupplier;
import static ama.canonical.CanonicalValue.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Whole-tree preflight followed by iterative bounded encoding. Hashing never copies the complete output. */
public final class CanonicalValueCodec {
    public static final String PROFILE="ama.canonical-value.v1";
    public byte[] encode(CanonicalValue value,CanonicalBudget budget,BooleanSupplier cancelled) {
        long size=preflight(value,budget,cancelled);
        require(size<=Integer.MAX_VALUE,BUDGET_EXCEEDED);
        var out=new ByteArrayOutputStream((int)Math.min(size,8192));
        emit(value,out::write,cancelled);byte[] result=out.toByteArray();check(cancelled);return result;
    }
    public String sha256(CanonicalValue value,CanonicalBudget budget,BooleanSupplier cancelled) {
        preflight(value,budget,cancelled);var digest=digest();
        emit(value,b->digest.update((byte)b),cancelled);check(cancelled);
        var result=HexFormat.of().formatHex(digest.digest());check(cancelled);return result;
    }
    public static MessageDigest digest() {
        try {return MessageDigest.getInstance("SHA-256");}
        catch(NoSuchAlgorithmException impossible) {throw new IllegalStateException("SHA-256");}
    }
    public static int compareUtf8(String a,String b,BooleanSupplier cancelled) {
        int i=0,j=0;
        while(i<a.length()&&j<b.length()) {check(cancelled);int x=a.codePointAt(i),y=b.codePointAt(j);
            if(x!=y)return Integer.compare(x,y);i+=Character.charCount(x);j+=Character.charCount(y);}
        return Integer.compare(a.length()-i,b.length()-j);
    }
    public static long utf8Length(String value,BooleanSupplier cancelled) {
        long size=0;
        for(int i=0;i<value.length();) {check(cancelled);char ch=value.charAt(i);int c=value.codePointAt(i);
            require(!Character.isSurrogate(ch)||Character.isHighSurrogate(ch)&&i+1<value.length()&&Character.isLowSurrogate(value.charAt(i+1)),INVALID_SCALAR);
            size+=c<0x80?1:c<0x800?2:c<0x10000?3:4;i+=Character.charCount(c);}
        return size;
    }
    private record Scan(Iterator<CanonicalValue> values,int depth) {}
    public long preflight(CanonicalValue value,CanonicalBudget b,BooleanSupplier cancelled) {
        require(value!=null&&b!=null&&cancelled!=null,INVALID_ARGUMENT);check(cancelled);
        long bytes=0,nodes=0;var stack=new ArrayDeque<Scan>();stack.push(new Scan(List.of(value).iterator(),1));
        while(!stack.isEmpty()) {
            check(cancelled);var frame=stack.peek();if(!frame.values.hasNext()){stack.pop();continue;}
            require(frame.depth<=b.maxDepth(),BUDGET_EXCEEDED);var v=frame.values.next();
            require(nodes<b.maxNodes(),BUDGET_EXCEEDED);nodes++;
            long size=1;Iterator<CanonicalValue> children=null;
            if(v instanceof UInt) size=9;
            else if(v instanceof Scalar s) {long n=utf8Length(s.value(),cancelled);require(n<=b.maxStringBytes(),BUDGET_EXCEEDED);size=add(9,n);}
            else if(v instanceof Sequence s) {require(s.values().size()<=b.maxContainerEntries(),BUDGET_EXCEEDED);size=9;children=s.values().iterator();}
            else if(v instanceof ObjectValue o) {
                require(o.entries().size()<=b.maxContainerEntries(),BUDGET_EXCEEDED);size=9;
                // Keys are encoded scalar nodes and participate in every applicable bound.
                children=new Iterator<>() {final Iterator<Entry> it=o.entries().iterator();Entry current;boolean key=true;
                    public boolean hasNext(){return !key||it.hasNext();}
                    public CanonicalValue next(){if(key){current=it.next();key=false;return string(current.key());}key=true;return current.value();}};
            }
            bytes=add(bytes,size);require(bytes<=b.maxEncodedBytes(),BUDGET_EXCEEDED);
            if(children!=null&&children.hasNext()) {
                require(frame.depth<Integer.MAX_VALUE,BUDGET_EXCEEDED);stack.push(new Scan(children,frame.depth+1));
            }
        }
        check(cancelled);return bytes;
    }
    private static long add(long a,long b) {require(b<=Long.MAX_VALUE-a,BUDGET_EXCEEDED);return a+b;}
    @FunctionalInterface private interface Sink {void write(int b);}
    private static void u64(long n,Sink sink) {for(int shift=56;shift>=0;shift-=8)sink.write((int)(n>>>shift)&255);}
    private static void utf8(String s,Sink sink,BooleanSupplier cancelled) {
        for(int i=0;i<s.length();) {check(cancelled);int c=s.codePointAt(i);i+=Character.charCount(c);
            if(c<0x80)sink.write(c);
            else if(c<0x800){sink.write(0xc0|c>>6);sink.write(0x80|c&63);}
            else if(c<0x10000){sink.write(0xe0|c>>12);sink.write(0x80|c>>6&63);sink.write(0x80|c&63);}
            else {sink.write(0xf0|c>>18);sink.write(0x80|c>>12&63);sink.write(0x80|c>>6&63);sink.write(0x80|c&63);}
        }
    }
    private static void emit(CanonicalValue root,Sink sink,BooleanSupplier cancelled) {
        var stack=new ArrayDeque<Iterator<CanonicalValue>>();stack.push(List.of(root).iterator());
        while(!stack.isEmpty()) {check(cancelled);var it=stack.peek();if(!it.hasNext()){stack.pop();continue;}var v=it.next();
            switch(v) {
                case Null ignored -> sink.write(0);
                case Bool b -> sink.write(b.value()?2:1);
                case UInt n -> {sink.write(3);u64(n.value(),sink);}
                case Scalar s -> {sink.write(4);u64(utf8Length(s.value(),cancelled),sink);utf8(s.value(),sink,cancelled);}
                case Sequence s -> {sink.write(5);u64(s.values().size(),sink);stack.push(s.values().iterator());}
                case ObjectValue o -> {
                    var entries=new ArrayList<>(o.entries());check(cancelled);
                    entries.sort((a,b)->compareUtf8(a.key(),b.key(),cancelled));check(cancelled);
                    sink.write(6);u64(entries.size(),sink);
                    stack.push(new Iterator<>() {final Iterator<Entry> iter=entries.iterator();Entry current;boolean key=true;
                        public boolean hasNext(){return !key||iter.hasNext();}
                        public CanonicalValue next(){if(key){current=iter.next();key=false;return string(current.key());}key=true;return current.value();}});
                }
            }
        }
    }
}
