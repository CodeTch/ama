# Task5 root pinned SDK source check

Root browsed AWS SDK Java tag2.56.0 primary source, no latest-version assumption.

- https://raw.githubusercontent.com/aws/aws-sdk-java-v2/2.56.0/http-clients/apache5-client/src/main/java/software/amazon/awssdk/http/apache5/Apache5HttpClient.java : prepareRequest abort delegates to native request abort (lines239–255), response body exposes same native abort (343–345), client close uses connection manager IMMEDIATE (258–263); redirect/retry handling disabled in transport (180–182).
- https://raw.githubusercontent.com/aws/aws-sdk-java-v2/2.56.0/core/sdk-core/src/main/java/software/amazon/awssdk/core/ResponseInputStream.java : abort delegates then closes stream (149–157), read cancels SDK initial-no-read timer (95–116). Its timer is therefore not a full body lifetime implementation.

This confirms supported pinned integration points only. Actual selected production factory, owned watchdog lifetime, socket timeout longer than lifetime, release/close races, once-only capacity and stalled-body termination need final source and evidence review. No source adoption or runtime pass claim here.
