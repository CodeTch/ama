package ama.storage;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FileBackendDefinitionsTest {
    @Test void allBackendsAreAttemptedWithBoundedSuppressionAndInterruptedFailurePreserved() throws Exception {
        var first=new IOException("FIRST_BACKEND_CLOSE");var count=new AtomicInteger();var backends=new ArrayList<FileBackendCatalog.Backend>();
        for(int n=0;n<12;n++){int index=n;backends.add(backend(n,()->{count.incrementAndGet();if(index==0)throw first;if(index==1)throw new InterruptedException("INTERRUPTED_BACKEND_CLOSE");throw new IllegalStateException("LATER_BACKEND_CLOSE");}));}
        try {
            var definitions=new FileBackendDefinitions(backends,null);
            assertSame(first,assertThrows(IOException.class,definitions::close));
            assertEquals(12,count.get());assertEquals(8,first.getSuppressed().length);assertTrue(Thread.currentThread().isInterrupted());
        }finally{Thread.interrupted();}
    }
    @Test void errorFromFirstBackendCannotSkipSecondBackend() throws Exception {
        var first=new AssertionError("BACKEND_CLOSE_ERROR");var count=new AtomicInteger();
        var definitions=new FileBackendDefinitions(List.of(backend(0,()->{count.incrementAndGet();throw first;}),backend(1,count::incrementAndGet)),null);
        assertSame(first,assertThrows(AssertionError.class,definitions::close));assertEquals(2,count.get());
    }
    static FileBackendCatalog.Backend backend(int n,AutoCloseable close) {
        var storage=(FileStoragePort)Proxy.newProxyInstance(FileBackendDefinitionsTest.class.getClassLoader(),new Class<?>[]{FileStoragePort.class,AutoCloseable.class},(proxy,method,args)->{
            if(method.getName().equals("close")){close.close();return null;}throw new AssertionError("Unexpected storage call");
        });
        var definition=new ManagedBackendId.Local("https://localhost:443","owned-close-"+n,".","ext4");
        return new FileBackendCatalog.Backend(definition.id(),"local",1,"READ_ONLY",storage,definition);
    }
}
