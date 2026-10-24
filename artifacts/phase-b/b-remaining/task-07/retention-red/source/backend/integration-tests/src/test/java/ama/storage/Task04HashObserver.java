package ama.storage;
import java.security.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
/** Test-only SUN SHA-256 delegation; records the actual JDBC root boundary without changing bytes. */
public final class Task04HashObserver extends Provider {
    static volatile BooleanSupplier held=()->false;
    static final AtomicInteger inside=new AtomicInteger(),outside=new AtomicInteger();
    Task04HashObserver(String name) {
        super(name,"1.0","Task4 genuine SHA-256 boundary observer");
        put("MessageDigest.SHA-256",Digest.class.getName());
    }
    public static final class Digest extends MessageDigestSpi {
        private final MessageDigest delegate;
        public Digest() {
            try {
                delegate=MessageDigest.getInstance("SHA-256","SUN");
            }
            catch(GeneralSecurityException e) {
                throw new IllegalStateException(e);
            }
        }
        protected void engineUpdate(byte value) {
            delegate.update(value);
        }
        protected void engineUpdate(byte[] value,int offset,int length) {
            delegate.update(value,offset,length);
        }
        protected byte[] engineDigest() {
            (held.getAsBoolean()?inside:outside).incrementAndGet();
            return delegate.digest();
        }
        protected void engineReset() {
            delegate.reset();
        }
        protected int engineGetDigestLength() {
            return 32;
        }
    }
}
