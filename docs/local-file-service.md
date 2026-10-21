# Local file service infrastructure

This is the B-04-local infrastructure checkpoint. It does not add a business file registry, upload SQL tables, meeting endpoints, S3, or company identity integration. Database design review B-00 and later business integration gates remain separate.

## Process and trust boundary

Build `./mvnw -pl backend/file-service -am package`; start the resulting `backend/file-service/target/ama-file-service-0.1.0-SNAPSHOT-exec.jar` with one protected properties-file argument. The file service runs under a dedicated low-privilege Linux x86_64 service account on the cluster-selected active node. No root directory or sentinel is created at startup. No S3 configuration or database connection is needed. Restrict this endpoint to the private service network; do not proxy `/internal/v1/` through browser ingress.

Deployment must provision:

- A mounted-volume root owned by the service account, mode 0700; all internal directories use 0700 and files use 0600. Unrelated users must not be able to rename or write its ancestors. Root and all path components must be real directories, not symlinks. Out-of-band mutation by the service account, administrators, or storage tools is unsupported.
- A protected `.volume-id` regular file inside that root containing the configured volume identity (mode 0600). This is combined with the expected mountpoint, kernel mount ID, major:minor device, filesystem type, source, root inode/device, and current mount `rw` status. A sentinel by itself never admits IO.
- A cluster-managed regular role file with an exact current active-instance value. The role file must be owned by root or the service account and must not be group/world writable. The cluster lifecycle removes or changes it before retiring the active resource. Application checks do not implement leadership, establish fencing, or make an unsafe failover safe.
- PKCS12 key and trust stores for a controlled project service PKI. The server requires a trusted, valid client certificate and an explicitly allowlisted URI SAN with a READ or WRITE role. Client server authentication uses normal JSSE trust and HTTPS hostname verification; redirects and anonymous/trust-all modes are absent. No enterprise SSO/certificate format is assumed.

Properties (all deployment identity values below are placeholders, not operational defaults):

```properties
root=/mounted/ama/files
mountpoint=/mounted/ama
device=MAJOR:MINOR
filesystem=xfs
source=/dev/DEPLOYMENT_DEVICE
volumeId=DEPLOYMENT_VOLUME_ID
roleFile=/run/ama/file-active
activeRole=DEPLOYMENT_ACTIVE_INSTANCE
backendId=local-primary
bindAddress=PRIVATE_SERVICE_ADDRESS
port=8443
keyStore=/etc/ama/file-service.p12
trustStore=/etc/ama/service-ca.p12
writerIdentities=spiffe://PROJECT/api,spiffe://PROJECT/worker
readerIdentities=spiffe://PROJECT/reader
```

Supply `AMA_FILE_KEYSTORE_PASSWORD` and `AMA_FILE_TRUSTSTORE_PASSWORD` through the protected service environment. Do not put secrets into repository configuration. Restart the process when changing key/trust material or role allowlists. The JVM and operating system account are trusted; do not set JVM switches that disable JSSE hostname verification.

## Durable layout and protocol

All data operations are relative to no-follow directory descriptors. Logical key strings are never used as filesystem path components. Each object uses `objects/<sha256-of-canonical-key-json>/key` and an immutable `data` file. The key metadata is durably published before the data. Staging files and multipart `uploads/<UUID>/` journals reside on the same protected filesystem. `stat` derives size and SHA-256 from the actual stored bytes; it does not trust a checksum sidecar.

Publication streams into an exclusive 0600 staging file with a 64 KiB buffer, checks actual length and SHA-256, fsyncs file data/metadata, links atomically without replacing an existing name, fsyncs the destination directory, removes the staging link, reopens and verifies the published inode, forces its link-count metadata, and syncs changed directories/ancestors before ACK. Named directory identities are revalidated before ACK, including object and upload directories. Same key/same content replay re-verifies and re-syncs existing data; different content returns CONFLICT. Every accepted multipart part is immutable and durable. Directory `flock` serializes a session across threads/processes. A completion decision is persisted only after receipt/size/full-hash verification and before publishing the final object. A killed process can resume accepted parts or replay a frozen/completed result. Abort is idempotent; abort after a completion decision leaves that decision and any completed object intact. Abort tombstones remain; completed journals remain for replay. Stale unacknowledged staging files are retained as recoverable garbage; no automatic cleanup policy is introduced.

The internal client uses POST `/internal/v1/{put,stat,read,delete,begin,part,complete,abort,list}`. `X-Ama-Request` is a base64url JSON internal metadata record; put/part bodies contain streamed binary bytes. A separate authenticated `health` route verifies the current volume/role and reads the provisioned sentinel. There are no public routes and no business authorization claims. Internal metadata is technically bounded (128 KiB encoded request header, 1 MiB persisted metadata/JSON response); these are protocol bounds on generated logical IDs and receipts, not product limits on filenames or file sizes. List accepts 1–1000 items and cursors are bound to backend, namespace and prefix. Multipart receipts must be contiguous from 1 in the completion request, with no accepted extra parts; arrival order is unrestricted.

Port reads use exact half-open intervals, including empty EOF. HTTP reads support one closed/open-ended/suffix byte range with 206; multiple, malformed, overflowing and out-of-bounds ranges return 416. Binary bodies are streamed and callers close read streams. Error responses contain fixed codes only. Internal authorization failures are mapped by the client to storage unavailability; physical paths, certificate material, and provider exception strings are never returned.

## Platform evidence and outstanding qualification

Native bridge: [JNA 5.18.1 upstream release](https://github.com/java-native-access/jna/releases/tag/5.18.1), pinned explicitly. TLS/HTTP uses the pinned JDK 21 JSSE and `jdk.httpserver`, with no extra TLS provider. JSON uses the Spring Boot-managed Jackson dependency. Native ABI offsets are explicitly limited to Linux x86_64; other platforms fail closed. The Linux bindings use [openat no-follow directory-relative semantics](https://man7.org/linux/man-pages/man2/openat.2.html), [linkat no-overwrite publication](https://man7.org/linux/man-pages/man2/linkat.2.html), and separate [file/directory fsync](https://man7.org/linux/man-pages/man2/fsync.2.html). Each enumeration opens its own directory description because [fdopendir consumes the descriptor's current offset](https://man7.org/linux/man-pages/man3/fdopendir.3p.html). JSSE setup follows [HttpsParameters](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.httpserver/com/sun/net/httpserver/HttpsParameters.html) and [HttpClient.Builder](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.Builder.html).

Canonical selected tests are `python3 tools/checks/run.py it LocalDurabilityIT` and `python3 tools/checks/run.py it FileStorageContractIT`, using the explicitly approved test environment required by the runner. The latter currently contains only the local backend parameter group. Fixtures generate their own CA/keys under ignored `target/local-fixtures`, bind loopback, use temporary real Linux directories, and kill/restart only their own file-service processes. Local tests make no MySQL calls. Fault injection is an explicit Java dependency around real IO, with no runtime switch to disable production mount/role checks.

These checks demonstrate ordering, rejection, streaming and process recovery on the exercised Linux fixture. They do not qualify RHEL 8.10 XFS, DRBD, power loss, hypervisor/barrier behavior, STONITH or dual-primary prevention. Actual mount/device/volume/role/PKI values and F-02 qualification remain deployment prerequisites. No host mounts, company services, fencing or third node are created by this implementation.
