package ama.storage;
/** Real expired claim followed by a fresh genuine session and receipt-only close recovery. */
class Task09LookupAuthorityIT extends Task09DeniedLookupRecoveryIT {
 @Override String lookupMode(){return "authority";}
}
