package ama.storage;

import ama.contract.IdentityContracts.Actor;

/** Controlled local policy. No network or late domain locks; no default business purposes. */
@FunctionalInterface
public interface FilePurposePolicy {
    void require(Actor actor, String meetingId, String purpose);

    final class Unavailable extends RuntimeException {
        public Unavailable() {
            super("File purpose unavailable");
        }
    }
}
