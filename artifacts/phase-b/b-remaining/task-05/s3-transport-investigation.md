# S3 actual body abort investigation

The first real body lifetime test failed at 2 seconds with a 350ms lifetime and a deliberately longer 20s socket timeout, before the proxy release. Diagnostic repeat reproduced this. The retained `s3-blocked-d348e1c2-90bb-4f2a-aa09-1e732ec4634f.txt` shows the body watchdog WAITING in MeteredStream.close/ReentrantLock, via ResponseInputStream.abort/IoUtils.closeQuietlyV2, while the actual reader is blocked in NioSocketImpl.timedRead/MeteredStream.read. This is the actual URLConnection ownership bottleneck; no outer future was accepted as cancellation.

Pinned AWS SDK 2.56.0 primary source consulted:
- https://raw.githubusercontent.com/aws/aws-sdk-java-v2/2.56.0/core/sdk-core/src/main/java/software/amazon/awssdk/core/ResponseInputStream.java
- https://raw.githubusercontent.com/aws/aws-sdk-java-v2/2.56.0/http-clients/apache5-client/src/main/java/software/amazon/awssdk/http/apache5/Apache5HttpClient.java
- https://raw.githubusercontent.com/aws/aws-sdk-java-v2/2.56.0/http-clients/apache-client/src/main/java/software/amazon/awssdk/http/apache/ApacheHttpClient.java

The Apache5 implementation exposes native request abort and immediate connection-manager closure; Apache4 is deprecated in this pinned source. Production now explicitly selects `software.amazon.awssdk:apache5-client` at the existing BOM 2.56.0 with maxConnections=admission, finite connection acquisition/connect/socket/API deadlines, maxAttempts=1, no inherited proxies, no redirects or implicit retries. S3FileStorage owns the SDK client and closes it. The regression test constructs the exact production client factory at 20s socket/API deadlines while the owned body remains 350ms, so reduced socket timeout cannot mask the abort assertion. Signed S3 identities/keys/layout/immutable receipts remain the existing protocol.
