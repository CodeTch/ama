package ama.storage;
/** Continue exact no2 after the preserved harness failure; natural expiry, real next attempt. */
class Task11Fix02CompositionWorkerContinuationIT extends Task11Fix02CompositionWorkerIT {
 @Override String startingTaskState(){return "RUNNING";}
 @Override int startingAttempt(){return 1;}
 @Override String workerOwner(){return "FIX02_COMPOSITION_CONTINUATION";}
}
