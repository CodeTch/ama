from pathlib import Path
p=Path('backend/adapters/src/main/java/ama/transcript/JdbcTranscriptAuthority.java')
s=p.read_text();s=s.replace('private FileRegistry registry;','private FileRegistry registry;\n private FilePurposePolicy purposes;\n private final ImportTaskDomainPolicy taskPolicy;')
s=s.replace('policy=new InputBatchFileReferencePolicy(this);','policy=new InputBatchFileReferencePolicy(this);taskPolicy=new ImportTaskDomainPolicy(this,source);')
s=s.replace('var authority=new JdbcTranscriptAuthority(source,tx,access,budget,canonical);var policies=', 'var authority=new JdbcTranscriptAuthority(source,tx,access,budget,canonical);authority.purposes=Objects.requireNonNull(purposes);var policies=')
s=s.replace('independent();return new CanonicalValueCodec().sha256(', 'independent();return producerBeforeRoots(meeting,input,fingerprint,version,epoch);\n }\n String producerBeforeRoots(String meeting,String input,String fingerprint,long version,long epoch){\n  return new CanonicalValueCodec().sha256(')
s=s.replace('"cancelEpoch",epoch)),canonical,cancel);','"cancelEpoch",epoch)),canonical,()->false);')
needle=' public StoredProvenanceFacts detached(HeldProcessingFacts token){return held(token).facts;}'
addition='''
 public ama.task.TaskDomainPolicy taskPolicy(){return taskPolicy;}
 public ama.task.TaskDomainPolicy.PreparedDomain adoptCreated(FileRegistry.LockedMeeting gate,HeldProcessingFacts held,TaskSpec spec){return taskPolicy.adopt(gate,held,spec);}
 public List<FileRegistry.CompletedSource> originals(PreparedIntake prepared){
  if(prepared==null||prepared.issuer!=this)throw new IllegalArgumentException("Foreign preparation");return prepared.draft.orderedSources();
 }
 void requireCreatedTransition(FileRegistry.LockedMeeting gate,HeldProcessingFacts value,TaskSpec spec){
  requireHeld(value,gate,spec);var h=held(value);var prepared=h.reserved.prepared;var draft=prepared.draft;
  var root=root(draft.meetingId());writable(root);
  access.requirePrepared(registry.access(gate),draft.actor(),draft.meetingId(),List.of(draft.actor().enterpriseId()),MeetingAccess.Action.EDIT);
  require(number(root,"revision")==spec.businessVersion()&&number(root,"cancel_epoch")==spec.cancelEpoch()&&draft.revision()+1==spec.businessVersion()&&Objects.equals(root.get("owner_id"),draft.actor().enterpriseId()),FINGERPRINT_MISMATCH);
  require(j.queryForObject("SELECT COUNT(*) FROM task_subject WHERE subject_kind='MEETING' AND subject_id=? AND business_version=? AND cancel_epoch=? AND deleted=0",Long.class,draft.meetingId(),spec.businessVersion(),spec.cancelEpoch())==1,FINGERPRINT_MISMATCH);
 }
 ArchiveReadBudget readBudget(){return budget;}
 CanonicalBudget canonicalBudget(){return canonical;}
 MeetingAccess meetingAccess(){return access;}
 FileRegistry fileRegistry(){return registry;}
 void requireTaskPurpose(Actor actor,String meeting){purposes.require(actor,meeting,PURPOSE);}
'''
assert needle in s;s=s.replace(needle,needle+addition);p.write_text(s)
