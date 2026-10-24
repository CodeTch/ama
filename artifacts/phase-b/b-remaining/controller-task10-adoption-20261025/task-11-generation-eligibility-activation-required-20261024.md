# Task11 activation mandatory input: inherited generation eligibility projections

Root permits this inherited Task9 projection issue to remain explicitly OPEN until unified Task11 activation, subject to the independent Task10 reviewer's own severity/scope decision. This ruling is a stage boundary, not a finding waiver. No instruction to ignore or downgrade it; if reviewer determines Task10 must fix now, use ordinary fix-loop.

Exact current source `backend/adapters/src/main/java/ama/transcript/JdbcMinutesQueries.java` lines24,25,29 statically emits both TASK10_NOT_INSTALLED and TASK11_NOT_INSTALLED. Task10 new saved/API actual formal branch is blocked by missing Task11; current source still failclosed, but all legacy eligibility reasons cannot be claimed accurate. Task10 report must state this distinction.

Affected GET routes, prefix `/api/v1/meetings/{meetingId}/minutes` (MinutesController lines11,12,16):
- `/generation-inputs/current`: `eligible/reasons`.
- `/generation-inputs/{inputId}`: `currentReadiness.eligible/reasons`.
- `/candidates/{candidateId}`: `eligibility.canAdopt/reasons`.

At actual Task11 combined installation, replace inherited static TASK10 reason with real component/authority/readiness predicates. Do not replace it with unconditional true or merely remove every reason. When both Task10 and Task11 are genuinely installed, neither missing-installation reason may remain; actual candidate disposition/current fence/owner/source/publication/final/outstanding-work blockers still apply. If a genuine component is unavailable, state its actual reason. Preserve private no-store/auth/replay behavior. Add focused real HTTP checks for these exact three routes under actual combined runtime, including permitted versus blocked cases; do not infer qualification from component-only tests. Task12 browser consumes the resulting actual producer contract.

Task11 author brief and final cross-task closure must include this file. Until actual implementation+review evidence exists, this obligation remains OPEN and Task10 never claims all current eligibility projections are complete.
