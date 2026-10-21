# B-05 integration pom.xml conflict resolution

- Original stage 1 blob: 213b66ab6468cf85dd1aa1ff420a51f3ee167511 (base)
- Original stage 2 blob: 3775c9b4fa9564aef98fa2eee1bd4772b903a55f (ours; S3 BOM)
- Original stage 3 blob: 60376c71dc1769edb7bcc97b376106419812dcf4 (theirs; worker module)
- Cherry-pick source commit: b3b6e80f1c73efa9afb853d14d18f449019601dd
- Resolved pom.xml blob SHA-1: 85b2160b761df3afb70ed3310bf9cdf228c3d559
- Validation: XML parsed; Spring Boot 4.1.1, Java 21, seven unique ordered modules (backend/contracts, backend/core, backend/adapters, backend/api, backend/file-service, backend/worker, backend/integration-tests), and S3 BOM software.amazon.awssdk:bom:2.56.0 with type pom and scope import verified. No conflict markers remain.
- Effect limited to the union of stage changes: preserves the existing six-module root and S3 BOM while adding backend/worker immediately before backend/integration-tests.
