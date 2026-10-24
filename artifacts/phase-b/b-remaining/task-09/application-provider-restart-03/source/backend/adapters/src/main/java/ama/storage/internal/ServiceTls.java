package ama.storage.internal;

import java.io.*;
import java.nio.file.*;
import java.security.*;

import javax.net.ssl.*;

/** Controlled service PKI. No default trust store, anonymous mode, or trust-all fallback. */
public final class ServiceTls {
    private ServiceTls() {}

    public static SSLContext context(
            Path keyStore, char[] keyPassword, Path trustStore, char[] trustPassword) {
        if (keyStore == null || trustStore == null || keyPassword == null || trustPassword == null)
            throw new IllegalArgumentException("service PKI required");
        try {
            KeyStore keys = load(keyStore, keyPassword), trust = load(trustStore, trustPassword);
            boolean hasKey = false;
            var aliases = keys.aliases();
            while (aliases.hasMoreElements()) hasKey |= keys.isKeyEntry(aliases.nextElement());
            if (!hasKey || trust.size() == 0)
                throw new GeneralSecurityException("empty service PKI");
            KeyManagerFactory km =
                    KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            km.init(keys, keyPassword);
            TrustManagerFactory tm =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tm.init(trust);
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(km.getKeyManagers(), tm.getTrustManagers(), null);
            return ssl;
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalArgumentException("invalid service PKI");
        }
    }

    private static KeyStore load(Path path, char[] password)
            throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(path)) {
            store.load(in, password);
        }
        return store;
    }
}
