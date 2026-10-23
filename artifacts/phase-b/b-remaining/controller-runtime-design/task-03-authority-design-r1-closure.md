# Task 3 authority design R1 closure matrix

Design-only revision; every row is proposed closure pending independent bounded re-review, not implementation/test PASS. Original design and independent review remain immutable.

| ID | Concrete revision | Required implementation evidence |
|---|---|---|
| ADR-01 | Public JdbcMeetingAccess.lock/tryLockIdentities use status-only capture in the same issuer; shared ordering/holder/ACL; full Person/name path remains for roster; one repository coverage accessor changes to ids() | No display_name transfer on new registry/queue paths even for huge/supplementary names; skip-locked returns before root; changed owner no late identity; exact full roster name preserved |
| ADR-02 | Unchanged legacy FileReferencePolicy; new CompleteReadPolicy requires implemented task-prelock/finalizer methods; dispatch by registered policy/private Proof; new read rejects legacy; all scope tasks before finalization attempts | Legacy download positive, new request denied for legacy, B06 finish exactly once, missing/throwing finalizer no open, genuine alternative-reference fallback, generic current-only eligibility |
| ADR-03 | sources(expectedGate,receipt,exactFileIds) authenticates private issuer/source/holder/gate/phase/full unique set; B06 order check separate; same checked receipt transitions BINDING -> CONSUMED once | Forged/foreign/ended/suspended/wrong-gate/subset/extra/duplicate/consumed negatives before effects; exact positive once; wrong ordinal graph with identical set denied |

Also frozen: complete domain rank, all task UUIDs before any attempt; actual successful RAW producer chain for current/history without RUNNING/current-version conditions; genuine unfinished live lease for worker. Original atomic new-row seal, one bindAll handoff, strict epochs/fences and private post-commit stream opening remain.

R1 design SHA256: 6fcb1f1d816cd96d389f83e67b0ceaba3298c4657cda3ed8bdcc27f3ab6537e9
Original design SHA256: 121b58e04c0077d8a7e46b5efbae49d5d0b2955308d2ad486fdb0c017a0155ba
Original review SHA256: 4574ca23691aca4b740006045d84b1ab07d4b8a232e5e0f837ea8735f2083797

No source, Git, DB, Docker, tests or private environment operations. No Task 3 implementation activation or Phase B completion claim.

Debt shorthand correction: intake clears transcript/final confirmations, sets needs_generation=1 and preserves the saved transcript pointer. It never clears generation debt. Exact pre-correction R1 is preserved as task-03-authority-design-r1-pre-debt-typo.md, SHA256 47b4999f864a592efa3ce040fa68442e5b09677c6921e90636a5b91d253766fa.
