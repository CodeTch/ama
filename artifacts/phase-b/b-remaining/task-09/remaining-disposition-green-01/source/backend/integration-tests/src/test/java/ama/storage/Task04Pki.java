package ama.storage;
import java.nio.file.*;
import java.util.*;
import static ama.storage.LocalFixture.*;
/** Exact new UUID custody, using the already inspected PKI commands on only the new directory. */
final class Task04Pki {
    static void create() throws Exception {
        Path parent=Path.of("target/local-fixtures").toAbsolutePath().normalize();
        Files.createDirectories(parent);
        Path fresh=parent.resolve("task04-"+UUID.randomUUID());
        if(!fresh.startsWith(parent)||Files.exists(fresh))throw new IllegalStateException("Task4 PKI custody collision");
        Files.createDirectory(fresh);
        privateMode(fresh,true);
        pki=fresh;
        run( "openssl", "req", "-x509", "-newkey", "rsa:2048", "-noenc", "-keyout", "ca.key", "-out", "ca.crt", "-days", "2", "-subj", "/CN=AMA local test CA");
        run( "openssl", "req", "-x509", "-newkey", "rsa:2048", "-noenc", "-keyout", "rogue-ca.key", "-out", "rogue-ca.crt", "-days", "2", "-subj", "/CN=Untrusted local test CA");
        for (String name : List.of("server", "writer", "reader", "outsider", "rogue", "expired")) {
            run( "openssl", "req", "-newkey", "rsa:2048", "-noenc", "-keyout", name + ".key", "-out", name + ".csr", "-subj", "/CN=" + name);
            String san = name.equals("server") ? "DNS:localhost" : "URI:spiffe://ama/" + (name.equals("expired") ? "writer" : name);
            Files.writeString( pki.resolve(name + ".ext"), "subjectAltName=" + san + "\nextendedKeyUsage=" + (name.equals("server") ? "serverAuth" : "clientAuth") + "\n" + "basicConstraints=CA:FALSE\n" + "keyUsage=digitalSignature,keyEncipherment\n");
            String ca = name.equals("rogue") ? "rogue-ca" : "ca";
            run( "openssl", "x509", "-req", "-in", name + ".csr", "-CA", ca + ".crt", "-CAkey", ca + ".key", "-CAcreateserial", "-out", name + ".crt", "-days", name.equals("expired") ? "-1" : "2", "-extfile", name + ".ext");
            run( "openssl", "pkcs12", "-export", "-inkey", name + ".key", "-in", name + ".crt", "-certfile", ca + ".crt", "-out", name + ".p12", "-passout", "pass:" + new String(PASSWORD));
        }
        run( Path.of(System.getProperty("java.home"), "bin/keytool").toString(), "-importcert", "-noprompt", "-alias", "ca", "-file", "ca.crt", "-keystore", "trust.p12", "-storetype", "PKCS12", "-storepass", new String(PASSWORD));
        run( Path.of(System.getProperty("java.home"), "bin/keytool").toString(), "-importcert", "-noprompt", "-alias", "rogue", "-file", "rogue-ca.crt", "-keystore", "rogue-trust.p12", "-storetype", "PKCS12", "-storepass", new String(PASSWORD));
    }
}
