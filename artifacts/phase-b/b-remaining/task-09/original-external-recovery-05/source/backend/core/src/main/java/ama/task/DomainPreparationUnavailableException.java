package ama.task;

/** Non-acknowledgment: retain the same authoritative external fact; never resend its request. */
public final class DomainPreparationUnavailableException extends RuntimeException {
    public DomainPreparationUnavailableException() {
        super("Domain preparation unavailable; retain and resubmit the same authoritative fact after configuration is corrected, never repeat the external request");
    }
}
