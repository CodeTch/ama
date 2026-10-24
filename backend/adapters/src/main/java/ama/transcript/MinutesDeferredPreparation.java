package ama.transcript;
import java.util.Set;
import ama.contract.MinutesContracts;
/** Only named ordinary preflight failures can yield to a concurrently accepted receipt.
 * In particular PROFILE/PROVENANCE/CAPABILITY are ambiguous integrity failures, not availability tokens.
 * Call only after fresh authorization and exact request matching, before returning a raced receipt. */
final class MinutesDeferredPreparation {
 private MinutesDeferredPreparation() {}
 static void beforeReplay(RuntimeException failure) {
  if(failure==null)return;
  if(Thread.currentThread().isInterrupted()||!ordinary(failure))throw failure;
 }
 private static boolean ordinary(RuntimeException failure) {
  if(failure instanceof MinutesContracts.Failure f)return Set.of(MinutesContracts.FailureCode.ARCHIVE_IO_UNAVAILABLE,MinutesContracts.FailureCode.BUDGET_EXCEEDED).contains(f.code());
  return failure instanceof ama.contract.TranscriptContracts.Failure f&&Set.of("REVISION_CONFLICT","REVISION_EXHAUSTED","CURRENT_SAVED_BODY_REQUIRED","CURRENT_SAVED_TARGET_REQUIRED","EXCLUSION_REVISION_CONFLICT","EDITOR_INTENT_REQUIRED","MEETING_NOT_WRITABLE","REVIEW_SEQUENCE_EXHAUSTED","HISTORY_EXHAUSTED").contains(f.code());
 }
}
