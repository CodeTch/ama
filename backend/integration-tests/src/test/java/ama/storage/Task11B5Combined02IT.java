package ama.storage;
/** Independent second owner/window after the archived01test-response-path failure; no old resource lookup. */
class Task11B5Combined02IT extends Task11B5CombinedIT {
 @Override javax.sql.DataSource createSource(){return ama.publication.Task11B5Source02.open();}
}
