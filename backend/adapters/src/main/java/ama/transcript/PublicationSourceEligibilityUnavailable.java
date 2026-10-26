package ama.transcript;

/** Only a positively identified current source eligibility denial. Never an original receipt,
 * graph integrity, budget, cancellation, or transport failure. It escapes the acquisition
 * transaction before a historical lookup may downgrade to immutable fact observation. */
final class PublicationSourceEligibilityUnavailable extends RuntimeException {
 PublicationSourceEligibilityUnavailable(){super("Current publication source unavailable");}
}
