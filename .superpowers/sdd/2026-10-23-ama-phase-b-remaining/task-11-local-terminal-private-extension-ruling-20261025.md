# Task11 R1 local terminal private extension — coordinator ruling

UTC: 2026-10-24T18:41:56.839175+00:00

Authority: adopted Task11 R1 §8.5 explicitly requires root review of a narrow private extension where the generic queue cannot retain the complete union. Parent reaffirmed this is delegated existing scope. Coordinator read §8.5, current queue DomainScope/claimOne/ValidatedLease/completion/finish/PREPARED termination and JdbcTaskPreparations actual-holder checks. This is the required bounded interface-direction ruling, not the eventual independent full Task11 SPEC/quality review.

Current gap: claimOne discards its DomainScope via convenience lockDomain; completeValidated receives only Row. Therefore existing arguments cannot prove a terminal hook's actual issuer/holder/prepared/phase. Retain existing genuine DomainScope and ValidatedLease. Add only fixed constructor-registered publication terminal policy; queue privately constructs LocalTerminalContext with exact queue/source/actual holder/registered policy/preparation/actual task/attempt/phase and one-use lifecycle. No caller-constructible authorization DTO, arbitrary public callback or ordinary-completion token reuse.

Validate complete selected/history bindings before mutation; perform real queue/attempt/PREPARED state-aware transition then local seal/publication projection in the same transaction; close context in finally and flush once last. Complete union is declared before any task lock; tasks before attempts before operations before executions. Hook neither discovers extra rows after task locks nor reacquires root nor performs IO/hash/parse under roots. Explicit owner recovery uses its separate issuer-bound LOCAL_PUBLICATION_FAILURE carrier, metadata-only predeclared union and actual source/holder/purpose.

Old retryAfterZeroEffect, external observation finish, nonpublication families retain their existing contract. Fixed RecordedFactRecoveryPolicy marker extends fact-only acquisition and must not authorize send/current domain write. Actual revoked path remains unvalidated.

First targeted behavior boundaries: genuine no-marker terminalization and immutable seal; forged/cross-holder/phase/attempt context rejection; atomic rollback across queue/attempt/external/local-seal/publication/outbox. Author must list specific affected old family/observation/retry regressions, not imply compilation covers them. Full final Task11 independent review must inspect this shared extension and the complete original R1.

Ruling: permit the minimal private extension under these boundaries — it preserves required actual acquisition identity instead of inventing a generic callback — cost if wrong is queue authority/atomicity regression, so real boundaries and affected regression evidence are mandatory before adoption.

Read-source SHA256:
{
  "backend/adapters/src/main/java/ama/task/JdbcTaskQueue.java": "906f741ca7315633e5555e263093105b5f3b293443552fc5e3e4ea763bc3f93b",
  "backend/adapters/src/main/java/ama/task/JdbcTaskPreparations.java": "ab71c24bb8b174ad3b9f7a2a4851675f6b11c997a0c92d1a17840c34bd0342d6"
}
