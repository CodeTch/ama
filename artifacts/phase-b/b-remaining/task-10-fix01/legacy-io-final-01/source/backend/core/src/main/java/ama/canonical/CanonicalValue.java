package ama.canonical;

import java.util.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Explicit binary value algebra; no implicit Java object conversion. */
public sealed interface CanonicalValue {
    record Null() implements CanonicalValue {}
    record Bool(boolean value) implements CanonicalValue {}
    record UInt(long value) implements CanonicalValue { public UInt { uint(value); } }
    record Scalar(String value) implements CanonicalValue { public Scalar { scalars(value); } }
    record Sequence(List<CanonicalValue> values) implements CanonicalValue {
        public Sequence {require(values!=null&&values.stream().allMatch(Objects::nonNull),INVALID_ARGUMENT);values=List.copyOf(values);}
    }
    record Entry(String key, CanonicalValue value) {
        public Entry {scalars(key);require(value!=null,INVALID_ARGUMENT);}
    }
    record ObjectValue(List<Entry> entries) implements CanonicalValue {
        public ObjectValue {
            require(entries!=null,INVALID_ARGUMENT);var keys=new HashSet<String>();
            for(var e:entries) {require(e!=null,INVALID_ARGUMENT);require(keys.add(e.key()),INPUT_INVALID);}
            entries=List.copyOf(entries);
        }
    }
    static Null nil() {return new Null();}
    static Scalar string(String value) {return new Scalar(value);}
    static UInt number(long value) {return new UInt(value);}
    static Sequence list(CanonicalValue... values) {return new Sequence(Arrays.asList(values));}
    static ObjectValue object(Entry... entries) {return new ObjectValue(Arrays.asList(entries));}
    static Entry field(String key,CanonicalValue value) {return new Entry(key,value);}
}
