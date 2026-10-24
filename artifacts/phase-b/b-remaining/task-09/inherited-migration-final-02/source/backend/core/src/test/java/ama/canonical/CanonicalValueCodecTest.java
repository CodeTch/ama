package ama.canonical;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static ama.canonical.CanonicalValue.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

class CanonicalValueCodecTest {
    static final CanonicalBudget B=new CanonicalBudget(100000,100,10000,10000,10000);
    final CanonicalValueCodec codec=new CanonicalValueCodec();
    byte[] encode(CanonicalValue v) {return codec.encode(v,B,()->false);}
    void fails(FailureCode c,Runnable r) {var e=assertThrows(Failure.class,r::run);assertEquals(c,e.code());assertEquals(c.name(),e.getMessage());assertNull(e.getCause());assertEquals(0,e.getSuppressed().length);}
    @Test void literalPrimitiveVectors() {
        var values=List.of(nil(),string("😀"),object(field("b",number(0)),field("a",list())),list(nil(),new Bool(false),new Bool(true),number(0),string("A"),list()));
        String[] bytes={"00","040000000000000004f09f9880","0600000000000000020400000000000000016105000000000000000004000000000000000162030000000000000000","05000000000000000600010203000000000000000004000000000000000141050000000000000000"};
        String[] hashes={"6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d","e22bf5a057b67a2d9c1d2948f1203f4f8e2a943c6ccbe03d164c8faf870ae453","180d46d61132a992ce7ff0404617740c794b6018f270b9cc123744e9276a2721","33b33e193a27eb68d2d6e31339ae49c1414650e94436f982a77903daeca7870d"};
        for(int i=0;i<values.size();i++) {assertEquals(bytes[i],HexFormat.of().formatHex(encode(values.get(i))));assertEquals(hashes[i],codec.sha256(values.get(i),B,()->false));}
    }
    @Test void unsignedUtf8SortPreservesListOrderAndExactControls() {
        assertEquals("060000000000000002040000000000000003ee808000040000000000000004f090808002",
            HexFormat.of().formatHex(encode(object(field("𐀀",new Bool(true)),field("\ue000",nil())))));
        assertFalse(Arrays.equals(encode(list(number(1),number(2))),encode(list(number(2),number(1)))));
        assertFalse(Arrays.equals(encode(string("a\r\nb")),encode(string("a\nb"))));
        assertFalse(Arrays.equals(encode(nil()),encode(string(""))));
        assertFalse(Arrays.equals(encode(list(string("a|b"),string("c"))),encode(list(string("a"),string("b|c")))));
        var data=object(field("opaque",string("a|b"+(char)0+"c")));
        var first=object(field("profile",string("p1")),field("kind",string("K1")),field("data",data));
        var otherProfile=object(field("profile",string("p2")),field("kind",string("K1")),field("data",data));
        var otherKind=object(field("profile",string("p1")),field("kind",string("K2")),field("data",data));
        assertNotEquals(codec.sha256(first,B,()->false),codec.sha256(otherProfile,B,()->false));
        assertNotEquals(codec.sha256(first,B,()->false),codec.sha256(otherKind,B,()->false));
        assertFalse(Arrays.equals(encode(list()),encode(object())));
    }
    @Test void frozenEntriesRejectDuplicateBeforeConversionAndRejectLoneSurrogates() {
        fails(INPUT_INVALID,()->object(field("x",nil()),field("x",number(1))));
        fails(INVALID_SCALAR,()->string("\ud800"));
        var entries=new ArrayList<Entry>();entries.add(field("a",number(1)));
        var value=new ObjectValue(entries);entries.clear();assertEquals(1,value.entries().size());
        assertThrows(UnsupportedOperationException.class,()->value.entries().clear());
    }
    @Test void everyCanonicalBudgetBoundaryAndPlusOne() {
        var value=list(string("😀"));
        assertEquals(22,codec.encode(value,new CanonicalBudget(22,2,2,4,1),()->false).length);
        for(var b:List.of(new CanonicalBudget(21,2,2,4,1),new CanonicalBudget(22,1,2,4,1),
            new CanonicalBudget(22,2,1,4,1),new CanonicalBudget(22,2,2,3,1))) fails(BUDGET_EXCEEDED,()->codec.encode(value,b,()->false));
        fails(BUDGET_EXCEEDED,()->codec.encode(list(nil(),nil()),new CanonicalBudget(100,4,10,10,1),()->false));
        // Arithmetic is preflighted even with a huge declared output allowance; no huge allocation needed.
        assertEquals(22,codec.encode(value,new CanonicalBudget(Long.MAX_VALUE,2,2,4,1),()->false).length);
    }
    @Test void cancellationDuringScanSortAndHashNeverReturnsSuccess() {
        fails(CANCELLED,()->codec.encode(string("secret"),B,()->true));
        var count=new AtomicInteger();fails(CANCELLED,()->codec.sha256(list(string("a"),string("b")),B,()->count.incrementAndGet()>3));
        Thread.currentThread().interrupt();try {fails(CANCELLED,()->encode(nil()));assertTrue(Thread.currentThread().isInterrupted());}finally{Thread.interrupted();}
    }
}
