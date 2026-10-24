package ama.storage.s3;

import ama.contract.StorageContracts.*;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Fresh JVM worker. Its only recovery input is the original token and fixed configuration. */
public final class S3Process {
    public static void main(String[] args) throws Exception {
        var config = S3Fixture.config("s3", URI.create(args[0]), args[1], args[2]);
        try (var s = new S3FileStorage(config)) {
            System.out.println("READY");
            System.out.flush();
            new BufferedReader(new InputStreamReader(System.in)).readLine();
            StoredBlob result;
            if (args[3].equals("resume")) result = s.resume(new UploadToken("s3", args[4]));
            else if (args[3].equals("put") || args[3].equals("put-generated")) {
                byte[] data =
                        args[3].equals("put")
                                ? args[5].getBytes(StandardCharsets.UTF_8)
                                : new byte[Integer.parseInt(args[6])];
                if (args[3].equals("put-generated"))
                    Arrays.fill(data, (byte) Integer.parseInt(args[5]));
                result =
                        s.put(
                                new StorageKey("s3", "meeting", args[4]),
                                new ByteArrayInputStream(data),
                                data.length,
                                ControlCodec.hash(data));
            } else {
                var t = new UploadToken("s3", args[4]);
                List<PartReceipt> receipts = new ArrayList<>();
                for (int i = 6; i < args.length; i++) {
                    String[] p = args[i].split(",", 4);
                    receipts.add(
                            new PartReceipt(
                                    Integer.parseInt(p[0]), Long.parseLong(p[1]), p[2], p[3]));
                }
                result = s.complete(t, receipts, args[5]);
            }
            System.out.println("OK " + result.size() + " " + result.sha256());
        } catch (StorageException e) {
            System.out.println("ERROR " + e.code());
            System.exit(2);
        }
    }
}
