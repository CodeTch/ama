package ama.storage;
class Task09GenerationCandidateReadbackRollbackIT extends Task09GenerationRollbackFixture { @Override String failurePrefix(){return "SELECT id,meeting_id,input_id,task_id,minutes_revision_id,element_set_id";} }
