package ama.storage;
class Task09GenerationOutboxRollbackIT extends Task09GenerationRollbackFixture { @Override String failurePrefix(){return "INSERT INTO task_outbox";} }
