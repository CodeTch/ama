from pathlib import Path
p=Path('backend/adapters/src/main/java/ama/transcript/ImportTaskDomainPolicy.java');s=p.read_text();needle='''  var files=new TreeSet<String>();
  for(var entry:batches.entrySet()) {require(reader.select("input_batch_file",BoundedTranscriptMetadata.MEMBER,"batch_id=?","ordinal",authority.readBudget().maxBatchFiles(),entry.getKey()).equals(entry.getValue().members),PROVENANCE_UNAVAILABLE);files.addAll(entry.getValue().files.keySet());}
  require(files.size()<=authority.readBudget().maxBatchFiles(),BUDGET_EXCEEDED);'''
replacement='''  for(var entry:batches.entrySet())require(reader.select("input_batch_file",BoundedTranscriptMetadata.MEMBER,"batch_id=?","ordinal",authority.readBudget().maxBatchFiles(),entry.getKey()).equals(entry.getValue().members),PROVENANCE_UNAVAILABLE);
  for(var graph:p.graphs()) {
   var tasks=new BoundedTranscriptMetadata(j,authority.readBudget()).select("durable_task",BoundedTranscriptMetadata.TASK,"input_kind='TRANSCRIPTION_INPUT' AND input_id=?","id",2,text(graph.input,"id"));require(tasks.equals(List.copyOf(graph.tasks.values())),PROVENANCE_UNAVAILABLE);
  }
  // A stale queue acquisition can retire its task without reviving obsolete byte/source permission.
  // The queue still locks/rechecks the actual task and decides CANCELLED/STALE before validateInput.
  var current=p.graphs().stream().filter(graph->number(graph.root,"revision")==graph.expected.businessVersion()&&number(graph.root,"cancel_epoch")==graph.expected.cancelEpoch()).toList();
  if(current.isEmpty())return true;
  for(var graph:current)require(number(graph.root,"ever_published")==0&&!"PUBLISHED".equals(graph.root.get("stage")),PROVENANCE_UNAVAILABLE);
  var files=new TreeSet<String>();for(var graph:current)files.addAll(graph.files.keySet());
  require(files.size()<=authority.readBudget().maxBatchFiles(),BUDGET_EXCEEDED);'''
assert needle in s;s=s.replace(needle,replacement)
# Only source validation loop changes; the preceding all-graph immutable validation remains intact.
a=s.index('  var actual=new HashMap<String,FileRegistry.CompletedSource>();');b=s.index('\n  return true;',a);part=s[a:b].replace('for(var graph:p.graphs())','for(var graph:current)');tail='''   var tasks=new BoundedTranscriptMetadata(j,authority.readBudget()).select("durable_task",BoundedTranscriptMetadata.TASK,"input_kind='TRANSCRIPTION_INPUT' AND input_id=?","id",2,text(graph.input,"id"));require(tasks.equals(List.copyOf(graph.tasks.values())),PROVENANCE_UNAVAILABLE);
''';part=part.replace(tail,'');s=s[:a]+part+s[b:];p.write_text(s)
p=Path('backend/adapters/src/main/java/ama/transcript/JdbcProcessingDiscovery.java');s=p.read_text().replace('eligibility(gate,root,','eligibility(actor,gate,root,').replace('private Map<String,String> eligibility(FileRegistry.LockedMeeting gate,','private Map<String,String> eligibility(Actor actor,FileRegistry.LockedMeeting gate,')
s=s.replace('for(var source:sources)result.put(source.file().fileId(),!writable(root)?"READ_ONLY":!"TRANSCRIPT_INPUT".equals(source.originalPurpose())?"SOURCE_PURPOSE":source.uploadCancelEpoch()!=number(root,"cancel_epoch")?"SOURCE_EPOCH":null);','''for(var source:sources) {
    String reason=!writable(root)?"READ_ONLY":!"TRANSCRIPT_INPUT".equals(source.originalPurpose())?"SOURCE_PURPOSE":source.uploadCancelEpoch()!=number(root,"cancel_epoch")?"SOURCE_EPOCH":null;
    try{purposes.require(actor,text(root,"id"),source.originalPurpose());}catch(FilePurposePolicy.Unavailable denied){reason="SOURCE_PURPOSE";}
    result.put(source.file().fileId(),reason);
   }''');p.write_text(s)
