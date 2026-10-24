package ama.storage;
class Task09GenerationAuditRollbackIT extends Task09GenerationRollbackFixture { @Override String failurePrefix(){return "INSERT INTO audit_event";} }
