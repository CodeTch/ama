package ama.transcript;
import ama.access.MeetingAccess;
import ama.config.*;
import ama.config.json.*;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.IdentityContracts.Actor;
import ama.contract.MinutesContracts.*;
import ama.contract.TaskContracts.*;
import ama.minutes.contract.TranscriptConfirmationHash;
import ama.storage.FileRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;
import java.util.function.Supplier;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
/** Saved transcript S -> outside-root original archives/hash -> one fresh F gate. */
public final class JdbcSavedTranscripts {
 private final SavedTranscriptAdmission admission;
 private final JdbcTranscriptAuthority authority;private final Supplier<ConfigArchiveSource.Snapshot> archives;
 private final TranscriptAcquisitionBudget limits;private final CorrectionInstallation correction;private final JdbcTemplate j;
 CorrectionInstallation correctionFor(JdbcTranscriptAuthority expected){if(authority!=expected)throw fail(CAPABILITY_UNAVAILABLE);return correction;}
 void requireCorrection(JdbcTranscriptAuthority expected,CorrectionInstallation captured,boolean ready){correctionFor(expected).requireSame(captured);if(ready)correction.requireReady();}
 public JdbcSavedTranscripts(JdbcTranscriptAuthority authority,Supplier<ConfigArchiveSource.Snapshot> archives,TranscriptAcquisitionBudget limits,CorrectionInstallation correction){
  this(authority,archives,limits,correction,new ama.task.JdbcSubjectFences(authority.dataSource(),Set.of("MEETING")),new ama.task.JdbcOutbox(authority.dataSource(),new TranscriptEventReferences(authority.dataSource()).validators()),new ama.audit.JdbcAuditAdapter(authority.dataSource()),new SavedTranscriptTransitions(null,null,null,List.of(),List.of()));
 }
 public JdbcSavedTranscripts(JdbcTranscriptAuthority authority,Supplier<ConfigArchiveSource.Snapshot> archives,TranscriptAcquisitionBudget limits,CorrectionInstallation correction,ama.task.SubjectFencePort fences,ama.task.OutboxPort outbox,ama.audit.AuditPort audit,SavedTranscriptTransitions transitions){
  this.authority=Objects.requireNonNull(authority);this.archives=Objects.requireNonNull(archives);this.limits=Objects.requireNonNull(limits);this.correction=Objects.requireNonNull(correction);j=new JdbcTemplate(authority.dataSource());j.setQueryTimeout(5);admission=SavedTranscriptAdmission.capture(archives.get(),authority.readBudget(),authority.canonicalBudget());
  this.transitions=Objects.requireNonNull(transitions);this.fences=Objects.requireNonNull(fences);this.outbox=Objects.requireNonNull(outbox);this.audit=Objects.requireNonNull(audit);receipts=new JdbcSavedTranscriptReceipts(authority.dataSource(),authority.transactionRunner(),authority.savedAccess());
 }
 private final SavedTranscriptTransitions transitions;
 private final ama.task.SubjectFencePort fences;private final ama.task.OutboxPort outbox;private final ama.audit.AuditPort audit;private final JdbcSavedTranscriptReceipts receipts;
 public SavedTranscriptAdmission admission(){return admission;}
 private record Prepared(SavedTranscriptGraph graph,String hash,Artifact profile) {}
 private record Selection(SavedTranscriptGraph.Discovery discovered,RuntimeException failure) {}
 private Selection discover(String meeting,String revision,TranscriptAcquisitionBudget.Ledger budget){
  try{return new Selection(SavedTranscriptGraph.discover(j,meeting,revision,limits,budget),null);}catch(RuntimeException failure){cancellation(failure);return new Selection(null,failure);}
 }
 private SavedTranscriptGraph acquire(Actor actor,String meeting,Selection selection,JdbcTranscriptAuthority.SavedPurpose purpose,TranscriptAcquisitionBudget.Ledger budget){
  return authority.transactionRunner().required(()->{
   var gate=authority.fileRegistry().lock(actor,meeting,purpose==JdbcTranscriptAuthority.SavedPurpose.CURRENT?MeetingAccess.Action.READ:MeetingAccess.Action.EDIT);
   if(selection.failure()!=null)throw selection.failure();var scope=authority.savedScope(gate,actor,meeting,purpose);
   return SavedTranscriptGraph.acquire(authority,j,gate,scope,actor,meeting,purpose,selection.discovered(),limits,budget);
  });
 }
 private record Write(Prepared prepared,List<FullRow> created,String resultId,CorrectionInstallation installation) {}
 public Map<String,Object> mutate(Actor actor,String meeting,String key,ama.contract.TranscriptContracts.Command command,String trace){
  independent();uuid(meeting);uuid(trace);var identity=JdbcSavedTranscriptReceipts.identity(actor,meeting,key,command);
  var replay=receipts.lookup(actor,meeting,identity,command);if(replay.isPresent())return replay.orElseThrow();
  var budget=limits.ledger(authority.readBudget(),admission.canonical(),()->false);var selection=discover(meeting,command.sourceRevisionId(),budget);
  SavedTranscriptGraph snapshot;
  try{snapshot=acquire(actor,meeting,selection,JdbcTranscriptAuthority.SavedPurpose.MUTATION,budget);}
  catch(RuntimeException failure){cancellation(failure);if(!ordinaryPreparationFailure(failure))throw failure;var winner=receipts.lookup(actor,meeting,identity,command);if(winner.isPresent())return winner.orElseThrow();throw failure;}
  Write write=null;RuntimeException error=null;JdbcTranscriptAuthority.PreparedMinutesTransition pending=null;
  try{transitions.requireControlledPublication();writable(snapshot,command);write=plan(actor,prepare(snapshot,budget),command,budget);pending=authority.prepareMinutesTransition(actor,meeting,selection.discovered().ancestry().values().stream().map(row->row.get(4)).filter(Objects::nonNull).toList());}catch(RuntimeException failure){cancellation(failure);error=failure;}
  final var expected=write;final var failed=error;final var contribution=pending;
  return authority.transactionRunner().required(()->{
   var stored=receipts.claim(actor,meeting,identity);var gate=authority.fileRegistry().lock(actor,meeting,MeetingAccess.Action.EDIT);JdbcSavedTranscriptReceipts.match(stored);
   if(failed!=null&&!ordinaryPreparationFailure(failed))throw failed;
   if(stored.result()!=null)return receipts.restore(meeting,stored,command,true);
   if(failed!=null)throw failed;
   var scope=authority.savedScope(gate,actor,meeting,JdbcTranscriptAuthority.SavedPurpose.MUTATION);
   var transition=authority.beginMinutesTransition(contribution,actor,meeting,gate);
   var fresh=SavedTranscriptGraph.acquire(authority,j,gate,scope,actor,meeting,JdbcTranscriptAuthority.SavedPurpose.MUTATION,selection.discovered(),limits,budget,transition);
   writable(fresh,command);require(fresh.equals(expected.prepared().graph()),PROVENANCE_UNAVAILABLE);
   var held=authority.sealSavedGraph(scope,gate,actor,meeting,fresh);authority.requireSavedHeld(held,gate,actor,meeting);transitions.requireControlledPublication();
   boolean confirming=command.operation()==ama.contract.TranscriptContracts.Operation.CONFIRM;
   if(confirming){correction.requireSame(expected.installation());correction.requireReady();}
   boolean advance=!confirming||!expected.resultId().equals(fresh.root().get("transcript_confirmation_id"));long revision=command.expectedRevision();
   if(advance&&revision>=Long.MAX_VALUE-1)throw new ama.contract.TranscriptContracts.Failure(409,"REVISION_EXHAUSTED");
   var subject=fences.lock(new SubjectRef("MEETING",meeting),false).orElseThrow(()->fail(PROVENANCE_UNAVAILABLE));
   require(!subject.deleted()&&subject.businessVersion()==revision&&subject.cancelEpoch()==SavedTranscriptGraph.num(fresh.root(),"cancel_epoch"),PROVENANCE_UNAVAILABLE);
   if(!confirming)require(fresh.maximumHistory()<=revision&&j.queryForObject("SELECT COUNT(*) FROM transcript_revision WHERE meeting_id=? AND revision=?",Long.class,meeting,revision+1)==0,PROVENANCE_UNAVAILABLE);
   for(var row:expected.created())insert(row);
   var read=new SavedTranscriptRows(j,budget,authority.readBudget().maxManifestBytes());var actual=new ArrayList<FullRow>();
   if(!expected.created().isEmpty()){
    if(confirming)actual.addAll(read.rows(Table.TRANSCRIPT_CONFIRMATION,"id=?","id",1,true,expected.resultId()));
    else {actual.addAll(read.rows(Table.TRANSCRIPT_REVISION,"id=?","id",1,true,expected.resultId()));var segments=read.deferRows(Table.TRANSCRIPT_SEGMENT,"revision_id=?","segment_id",(int)Math.min(Integer.MAX_VALUE-2L,authority.readBudget().maxFactRows()),true,expected.resultId());var marks=read.deferRows(Table.TRANSCRIPT_SEGMENT_MARK,"revision_id=?","segment_id,mark",(int)Math.min(Integer.MAX_VALUE-2L,authority.readBudget().maxFactRows()),true,expected.resultId());actual.addAll(segments.get());actual.addAll(marks.get());}
    require(actual.size()==expected.created().size()&&new HashSet<>(actual).equals(new HashSet<>(expected.created())),PROVENANCE_UNAVAILABLE);
   }
   if(advance){
    revision++;
    String update=confirming?"transcript_confirmation_id=?":"transcript_revision_id=?,transcript_confirmation_id=NULL";
    if(j.update("UPDATE meeting SET "+update+",revision=?,final_confirmation_id=NULL,needs_generation=1,updated_at=UTC_TIMESTAMP(6) WHERE id=? AND revision=? AND cancel_epoch=? AND ever_published=0 AND stage<>'PUBLISHED' AND lifecycle='ACTIVE' AND governance_state='ACTIVE'",expected.resultId(),revision,meeting,command.expectedRevision(),subject.cancelEpoch())!=1)throw new ama.contract.TranscriptContracts.Failure(409,"REVISION_CONFLICT");
    require(j.update("UPDATE meeting_search_projection SET meeting_revision=? WHERE meeting_id=?",revision,meeting)==1,PROVENANCE_UNAVAILABLE);fences.advance(subject,revision,subject.cancelEpoch(),false);authority.finishMinutesTransition(transition,revision);
   }
   receipts.accepted(identity,expected.resultId());var response=receipts.restore(meeting,new JdbcSavedTranscriptReceipts.Stored(expected.resultId(),200,true),command,false);
   if(advance){String event=confirming?"TRANSCRIPT_CONFIRMED":"TRANSCRIPT_SAVED";audit.append(new ama.contract.AuditContracts.AuditEvent(actor.enterpriseId(),event,meeting,revision,"SUCCEEDED",trace,java.time.Instant.now()));outbox.append(new ama.contract.OutboxContracts.EventDraft(event,null,new SubjectRef("MEETING",meeting),revision,new ama.contract.OutboxContracts.Reference(confirming?"TRANSCRIPT_CONFIRMATION":"TRANSCRIPT_REVISION",expected.resultId())));}
   outbox.flush();return response;
  });
 }
 private static boolean ordinaryPreparationFailure(RuntimeException error){
  if(error instanceof Failure f)return Set.of(PROFILE_UNAVAILABLE,CAPABILITY_UNAVAILABLE,BUDGET_EXCEEDED,READ_FAILED).contains(f.code());
  if(error instanceof ama.contract.FileContracts.Unavailable||error instanceof ama.contract.FileContracts.Conflict||error instanceof ama.contract.FileContracts.NotFound)return true;
  return error instanceof ama.contract.TranscriptContracts.Failure f&&Set.of("REVISION_CONFLICT","CURRENT_REVISION_REQUIRED","MEETING_NOT_WRITABLE","REVISION_EXHAUSTED").contains(f.code());
 }
 private void writable(SavedTranscriptGraph graph,ama.contract.TranscriptContracts.Command command){
  var root=graph.root();if(SavedTranscriptGraph.num(root,"revision")!=command.expectedRevision())throw new ama.contract.TranscriptContracts.Failure(409,"REVISION_CONFLICT");
  if(SavedTranscriptGraph.num(root,"ever_published")!=0||!"ACTIVE".equals(root.get("lifecycle"))||!"ACTIVE".equals(root.get("governance_state"))||"PUBLISHED".equals(root.get("stage")))throw new ama.contract.TranscriptContracts.Failure(409,"MEETING_NOT_WRITABLE");
  boolean working=graph.selectedRow().string("kind").equals("WORKING");var op=command.operation();
  if((op==ama.contract.TranscriptContracts.Operation.CONFIRM||op==ama.contract.TranscriptContracts.Operation.MARKS)&&!working)throw new ama.contract.TranscriptContracts.Failure(422,"WORKING_REQUIRED");
  if(op!=ama.contract.TranscriptContracts.Operation.RESTORE&&working&&!graph.selected().equals(root.get("transcript_revision_id")))throw new ama.contract.TranscriptContracts.Failure(409,"CURRENT_REVISION_REQUIRED");
  if(op==ama.contract.TranscriptContracts.Operation.CONFIRM)correction.requireReady();
  else require(canAppend(graph),BUDGET_EXCEEDED);
 }
 private boolean canAppend(SavedTranscriptGraph graph){
  // The source is the entire retained chain. Its new child must remain readable under both bounds.
  return graph.selected()!=null&&graph.rows().stream().filter(r->r.table()==Table.TRANSCRIPT_REVISION).count()<Math.min(limits.revisionDepth(),limits.revisionNodes());
 }
 private Write plan(Actor actor,Prepared prepared,ama.contract.TranscriptContracts.Command command,TranscriptAcquisitionBudget.Ledger budget){
  var graph=prepared.graph();var created=new ArrayList<FullRow>();String id=UUID.randomUUID().toString();String time=java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'").format(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS));
  if(command.operation()==ama.contract.TranscriptContracts.Operation.CONFIRM){
   var existing=graph.rows().stream().filter(r->r.table()==Table.TRANSCRIPT_CONFIRMATION).toList();require(existing.size()<=1,PROVENANCE_UNAVAILABLE);
   if(existing.isEmpty())created.add(planned(budget,Table.TRANSCRIPT_CONFIRMATION,id,graph.meeting(),graph.selected(),prepared.hash(),actor.enterpriseId(),time));else id=existing.getFirst().string("id");
   if(graph.root().get("transcript_confirmation_id")!=null)require(id.equals(graph.root().get("transcript_confirmation_id")),PROVENANCE_UNAVAILABLE);
  }else{
   var parent=graph.selectedRow();created.add(planned(budget,Table.TRANSCRIPT_REVISION,id,graph.meeting(),command.expectedRevision()+1,"WORKING",parent.string("batch_id"),graph.selected(),parent.string("input_fingerprint"),actor.enterpriseId(),time,parent.string("processing_input_id"),null));
   var segments=graph.segments(graph.selected());boolean restore=command.operation()==ama.contract.TranscriptContracts.Operation.RESTORE;
   if(!restore&&segments.size()!=command.segments().size())throw new ama.contract.TranscriptContracts.Failure(422,"COMPLETE_SEGMENTS_REQUIRED");boolean content=false;
   for(int i=0;i<segments.size();i++){
    var segment=segments.get(i);var edit=restore?null:command.segments().get(i);if(edit!=null&&!segment.string("segment_id").equals(edit.segmentId()))throw new ama.contract.TranscriptContracts.Failure(422,"SEGMENT_IDENTITY_CHANGED");
    String body=command.operation()==ama.contract.TranscriptContracts.Operation.SAVE?edit.body():segment.string("body");content|=!body.isBlank();
    created.add(planned(budget,Table.TRANSCRIPT_SEGMENT,id,segment.string("segment_id"),segment.number("ordinal"),body,segment.string("source_file_id"),segment.nullableNumber("source_paragraph"),segment.nullableNumber("start_ms"),segment.nullableNumber("end_ms"),segment.nullableString("speaker_id")));
    List<Mark> marks=restore?graph.rows().stream().filter(m->m.table()==Table.TRANSCRIPT_SEGMENT_MARK&&m.string("revision_id").equals(graph.selected())&&m.string("segment_id").equals(segment.string("segment_id"))).map(m->Mark.valueOf(m.string("mark"))).toList():edit.marks();
    for(var mark:marks)created.add(planned(budget,Table.TRANSCRIPT_SEGMENT_MARK,id,segment.string("segment_id"),mark.name()));
   }
   if(!content)throw new ama.contract.TranscriptContracts.Failure(422,"EMPTY_TRANSCRIPT");
  }
  return new Write(prepared,List.copyOf(created),id,correction);
 }
 private static FullRow planned(TranscriptAcquisitionBudget.Ledger budget,Table table,Object...values){
  budget.row(table.columns().size());for(var value:values)if(value instanceof String text)budget.text(ama.canonical.CanonicalValueCodec.utf8Length(text,()->false),text.codePointCount(0,text.length()),text.length(),8,true);
  var cells=new ArrayList<Cell>();for(var value:values)cells.add(value==null?new NullCell():value instanceof Long number?cell(number):cell((String)value));return new FullRow(table,cells);
 }
 private void insert(FullRow row){
  var args=new ArrayList<Object>();for(var column:row.table().columns()){var value=row.value(column.name());Object actual=value instanceof NullCell?null:value instanceof UIntCell n?n.value():((TextCell)value).value();if(actual!=null&&column.kind()==CellKind.TIMESTAMP)actual=java.time.LocalDateTime.ofInstant(java.time.Instant.parse((String)actual),java.time.ZoneOffset.UTC);args.add(actual);}
  String columns=String.join(",",row.table().columns().stream().map(ColumnSpec::name).toList());String placeholders=String.join(",",Collections.nCopies(args.size(),"?"));require(j.update("INSERT INTO "+row.table().sqlName()+"("+columns+") VALUES ("+placeholders+")",args.toArray())==1,PROVENANCE_UNAVAILABLE);
 }
 public Map<String,Object> readOwner(Actor actor,String meeting,String revision){uuid(revision);return read(actor,meeting,revision,JdbcTranscriptAuthority.SavedPurpose.OWNER_INSPECTION);}
 public Map<String,Object> readCurrent(Actor actor,String meeting){return read(actor,meeting,null,JdbcTranscriptAuthority.SavedPurpose.CURRENT);}
 public Map<String,Object> history(Actor actor,String meeting,int limit,Long before){
  independent();uuid(meeting);if(limit<1||limit>50||before!=null&&before<0)throw new IllegalArgumentException("Invalid history cursor");
  return authority.transactionRunner().required(()->{
   var gate=authority.fileRegistry().lock(actor,meeting,MeetingAccess.Action.EDIT);authority.savedScope(gate,actor,meeting,JdbcTranscriptAuthority.SavedPurpose.OWNER_INSPECTION);
   var budget=limits.ledger(authority.readBudget(),admission.canonical(),()->false);var read=new SavedTranscriptRows(j,budget,authority.readBudget().maxManifestBytes());var root=read.one(SavedTranscriptGraph.ROOT,meeting);
   String where="meeting_id=? AND kind IN ('RAW','WORKING')"+(before==null?"":" AND revision<?");Object[] args=before==null?new Object[]{meeting}:new Object[]{meeting,before};
   // Explicit page sentinel is a metadata-only bounded selection; full graph endpoints never paginate.
   for(int i=0;i<=limit;i++){budget.row(1);budget.text(36,36,36,2,false);}
   var ids=j.queryForList("SELECT id FROM transcript_revision WHERE "+where+" ORDER BY revision DESC LIMIT ?",String.class,append(args,limit+1));
   var selected=new ArrayList<FullRow>();for(var id:new TreeSet<>(ids))selected.addAll(read.rows(Table.TRANSCRIPT_REVISION,"id=?","id",1,true,id));
   selected.sort(Comparator.comparingLong((FullRow r)->r.number("revision")).reversed());boolean more=selected.size()>limit;var items=new ArrayList<Map<String,Object>>();
   for(var row:selected.subList(0,Math.min(limit,selected.size()))){var item=new LinkedHashMap<String,Object>();item.put("id",row.string("id"));item.put("kind",row.string("kind"));item.put("revision",row.number("revision"));item.put("revisionToken",Long.toString(row.number("revision")));item.put("createdBy",row.string("created_by"));item.put("createdAt",row.string("created_at"));items.add(Collections.unmodifiableMap(item));}
   var result=new LinkedHashMap<String,Object>();result.put("items",List.copyOf(items));result.put("hasMore",more);result.put("nextCursor",more?items.getLast().get("revisionToken"):null);result.put("observation",observation(root));return Collections.unmodifiableMap(result);
  });
 }
 private static Object[] append(Object[] args,Object extra){var out=Arrays.copyOf(args,args.length+1);out[args.length]=extra;return out;}
 public Map<String,Object> readConfirmation(Actor actor,String meeting,String confirmation){
  independent();uuid(meeting);uuid(confirmation);var ids=j.queryForList("SELECT revision_id FROM transcript_confirmation WHERE id=? AND meeting_id=?",String.class,confirmation,meeting);
  if(ids.size()!=1)return authority.transactionRunner().required(()->{authority.fileRegistry().lock(actor,meeting,MeetingAccess.Action.EDIT);throw new ama.contract.FileContracts.NotFound();});
  var result=readOwner(actor,meeting,ids.getFirst());var actual=result.get("selectedConfirmation");if(!(actual instanceof Map<?,?> value)||!confirmation.equals(value.get("id")))throw new ama.contract.FileContracts.NotFound();return result;
 }
 public Map<String,Object> marks(Actor actor,String meeting,String revision){
  var full=revision==null?readCurrent(actor,meeting):readOwner(actor,meeting,revision);var selected=(Map<?,?>)full.get("selected");var result=new LinkedHashMap<String,Object>();result.put("revisionId",selected==null?null:selected.get("id"));result.put("complete",true);result.put("observation",full.get("observation"));result.put("sourceBytes",full.get("sourceBytes"));result.put("storedInspection",full.get("storedInspection"));
  var marks=new ArrayList<Map<String,Object>>();if(selected!=null)for(var value:(List<?>)selected.get("segments")){var segment=(Map<?,?>)value;marks.add(Map.of("segmentId",segment.get("segmentId"),"marks",segment.get("marks")));}result.put("segments",List.copyOf(marks));return Collections.unmodifiableMap(result);
 }
 private Map<String,Object> read(Actor actor,String meeting,String revision,JdbcTranscriptAuthority.SavedPurpose purpose){
  independent();uuid(meeting);var budget=limits.ledger(authority.readBudget(),admission.canonical(),()->false);var selection=discover(meeting,revision,budget);
  var snapshot=acquire(actor,meeting,selection,purpose,budget);Prepared prepared=null;RuntimeException error=null;
  try{prepared=prepare(snapshot,budget);}catch(RuntimeException failure){cancellation(failure);error=failure;}
  final var expected=prepared;final var failed=error;
  return authority.transactionRunner().required(()->{
   var gate=authority.fileRegistry().lock(actor,meeting,purpose==JdbcTranscriptAuthority.SavedPurpose.CURRENT?MeetingAccess.Action.READ:MeetingAccess.Action.EDIT);
   if(failed!=null)throw failed;var scope=authority.savedScope(gate,actor,meeting,purpose);
   var fresh=SavedTranscriptGraph.acquire(authority,j,gate,scope,actor,meeting,purpose,selection.discovered(),limits,budget);
   require(fresh.equals(expected.graph()),PROVENANCE_UNAVAILABLE);
   boolean owner=actor.enterpriseId().equals(fresh.root().get("owner_id"));
   if(!owner&&fresh.selected()!=null)require(fresh.root().get("transcript_confirmation_id")!=null&&fresh.rows().stream().anyMatch(r->r.table()==Table.TRANSCRIPT_CONFIRMATION&&r.string("id").equals(fresh.root().get("transcript_confirmation_id"))),CAPABILITY_UNAVAILABLE);
   if(purpose==JdbcTranscriptAuthority.SavedPurpose.CURRENT&&fresh.selected()!=null)require(fresh.selectedRow().string("kind").equals("WORKING"),PROVENANCE_UNAVAILABLE);
   return projection(expected,owner);
  });
 }
 private Prepared prepare(SavedTranscriptGraph graph,TranscriptAcquisitionBudget.Ledger budget){
  independent();if(graph.selected()==null)return new Prepared(graph,null,null);
  for(var original:admission.originals())budget.capture((Long)original.get("bytes"),(Long)original.get("scalars"),(Long)original.get("utf16"),16);
  var snapshot=archives.get();require(snapshot.indexSha256().equals(admission.indexSha()),PROFILE_UNAVAILABLE);var input=graph.input();
  var originals=snapshot.index().entries().stream().map(ConfigArchiveIndex.Entry::ref).filter(r->r.artifactType()==ArtifactType.PROCESSING_CONFIG&&r.version().equals(input.string("processing_config_version"))).toList();
  require(originals.size()==1,PROFILE_UNAVAILABLE);
  var raw=snapshot.loadClosure(originals,authority.readBudget(),ama.canonical.CanonicalValueCodec.utf8Length(input.string("config_manifest"),()->false),()->false);
  var binding=new ConfigBindings(new StrictConfigArchiveReader(),new StrictProcessingManifestReader());var facts=graph.facts(authority.readBudget().maxFactRows());
  var verified=binding.verifyProcessing(input,facts,raw,authority.readBudget(),()->false);
  var expected=new TaskSpec("TRANSCRIPT_IMPORT_V1","TRANSCRIPT_IMPORT",new SubjectRef("MEETING",graph.meeting()),authority.producer(graph.meeting(),input.string("id"),verified.fingerprint(),input.number("base_revision"),input.number("cancel_epoch"),()->false),new TypedRef("TRANSCRIPTION_INPUT",input.string("id")),verified.fingerprint(),input.number("base_revision"),0,input.number("cancel_epoch"),3);
  require(TranscriptReadGraph.spec(graph.task()).equals(expected),PROVENANCE_UNAVAILABLE);
  var members=graph.rows().stream().filter(r->r.table()==Table.INPUT_BATCH_FILE).sorted(Comparator.comparingLong(r->r.number("ordinal"))).toList();var byFile=new HashMap<String,ContentFile>();for(var f:facts.files())byFile.put(f.id(),f);
  var fingerprintMembers=new ArrayList<BatchFingerprint.Member>();long order=1;for(var m:members){require(m.number("ordinal")==order++,PROVENANCE_UNAVAILABLE);var f=byFile.get(m.string("file_id"));require(f!=null&&f.sha256().equals(m.string("sha256")),PROVENANCE_UNAVAILABLE);fingerprintMembers.add(new BatchFingerprint.Member(m.number("ordinal"),f.id(),f.sha256(),f.sizeBytes()));}
  var batch=graph.rows().stream().filter(r->r.table()==Table.INPUT_BATCH).findFirst().orElseThrow(()->fail(PROVENANCE_UNAVAILABLE));
  require(new BatchFingerprint().compute(graph.meeting(),batch.string("id"),"TRANSCRIPT",batch.number("base_revision"),fingerprintMembers,authority.canonicalBudget(),()->false).equals(batch.string("input_fingerprint")),FINGERPRINT_MISMATCH);
  var revisions=graph.rows().stream().filter(r->r.table()==Table.TRANSCRIPT_REVISION).toList();
  for(var r:revisions){
   require(r.string("batch_id").equals(input.string("batch_id"))&&r.string("processing_input_id").equals(input.string("id"))&&r.string("input_fingerprint").equals(verified.fingerprint()),PROVENANCE_UNAVAILABLE);
   var segments=graph.segments(r.string("id"));require(!segments.isEmpty()&&segments.size()<=verified.parseBudget().maxSegments(),BUDGET_EXCEEDED);long ordinal=1;boolean content=false;
   for(var segment:segments){require(segment.number("ordinal")==ordinal++&&segment.nullableNumber("start_ms")==null&&segment.nullableNumber("end_ms")==null&&segment.nullableString("speaker_id")==null&&segment.nullableNumber("source_paragraph")!=null&&segment.number("source_paragraph")>0&&byFile.containsKey(segment.string("source_file_id")),PROVENANCE_UNAVAILABLE);content|=!segment.string("body").isBlank();}
   require(content,INPUT_INVALID);
   var segmentIds=segments.stream().map(s->s.string("segment_id")).collect(java.util.stream.Collectors.toSet());
   for(var mark:graph.rows())if(mark.table()==Table.TRANSCRIPT_SEGMENT_MARK&&mark.string("revision_id").equals(r.string("id")))require(segmentIds.contains(mark.string("segment_id")),PROVENANCE_UNAVAILABLE);
   if(r.string("kind").equals("RAW"))require(r.number("revision")==expected.businessVersion()&&r.nullableString("parent_id")==null&&r.string("producer_task_id").equals(graph.task().get("id")),PROVENANCE_UNAVAILABLE);
   else {
    require(r.string("kind").equals("WORKING")&&r.nullableString("producer_task_id")==null&&r.nullableString("parent_id")!=null,PROVENANCE_UNAVAILABLE);var parentRow=revisions.stream().filter(candidate->candidate.string("id").equals(r.string("parent_id"))).findFirst().orElseThrow(()->fail(PROVENANCE_UNAVAILABLE));require(parentRow.number("revision")<r.number("revision"),PROVENANCE_UNAVAILABLE);var parent=graph.segments(r.string("parent_id"));require(parent.size()==segments.size(),PROVENANCE_UNAVAILABLE);
    for(int i=0;i<segments.size();i++)for(String field:List.of("segment_id","ordinal","source_file_id","source_paragraph","start_ms","end_ms","speaker_id"))require(segments.get(i).value(field).equals(parent.get(i).value(field)),PROVENANCE_UNAVAILABLE);
   }
  }
  var context=binding.begin(facts,raw,authority.readBudget(),()->false);var profile=context.confirmation(input);context.finish();String hash=null;
  if(graph.selectedRow().string("kind").equals("WORKING")){
   var gb=new GraphBudget(admission.canonical(),limits.revisionDepth(),limits.revisionNodes(),authority.readBudget().maxFactRows());
   hash=new TranscriptConfirmationHash(binding,raw,authority.readBudget()).hashDraft(new ConfirmationDraft(graph.meeting(),graph.selected(),facts),profile,gb,()->false);
   for(var c:graph.rows())if(c.table()==Table.TRANSCRIPT_CONFIRMATION)require(c.string("content_hash").equals(hash),FINGERPRINT_MISMATCH);
  }
  return new Prepared(graph,hash,profile);
 }
 private Map<String,Object> projection(Prepared p,boolean owner){
  var g=p.graph();var out=new LinkedHashMap<String,Object>();out.put("meetingId",g.meeting());out.put("observation",observation(g.root()));out.put("storedInspection","AVAILABLE");
  out.put("selected",g.selected()==null?null:detail(g,owner));out.put("sourceBytes",g.sourceBytes());out.put("correction",correction.projection());
  boolean transitionsReady;try{transitions.requireControlledPublication();transitionsReady=true;}catch(Failure unavailable){transitionsReady=false;}
  boolean writable=transitionsReady&&owner&&SavedTranscriptGraph.num(g.root(),"ever_published")==0&&"ACTIVE".equals(g.root().get("lifecycle"))&&"ACTIVE".equals(g.root().get("governance_state"))&&!"PUBLISHED".equals(g.root().get("stage"))&&SavedTranscriptGraph.num(g.root(),"revision")<Long.MAX_VALUE-1;
  boolean bytes=g.selected()!=null&&g.sourceBytes().stream().allMatch(s->"ELIGIBLE".equals(s.get("eligibility")));boolean current=g.selected()!=null&&g.selected().equals(g.root().get("transcript_revision_id"));
  out.put("capabilities",Map.of("canReadHistory",owner,"canSave",writable&&bytes&&canAppend(g)&&(current||g.selectedRow().string("kind").equals("RAW")),"canRestore",writable&&bytes&&canAppend(g),"canReadSources",bytes,"canConfirm",writable&&bytes&&current&&g.selectedRow().string("kind").equals("WORKING")&&"NOT_APPLICABLE".equals(correction.projection().readiness())));
  out.put("needsGeneration",SavedTranscriptGraph.num(g.root(),"needs_generation")!=0);
  Map<String,Object> confirmation=null;for(var c:g.rows())if(c.table()==Table.TRANSCRIPT_CONFIRMATION){confirmation=Map.of("id",c.string("id"),"revisionId",c.string("revision_id"),"contentHash",c.string("content_hash"),"confirmedBy",c.string("confirmed_by"),"confirmedAt",c.string("confirmed_at"),"profile",p.profile().version(),"isCurrent",c.string("id").equals(g.root().get("transcript_confirmation_id")));}
  out.put("confirmation",confirmation!=null&&Boolean.TRUE.equals(confirmation.get("isCurrent"))?confirmation:null);if(owner)out.put("selectedConfirmation",confirmation);
  var files=new HashMap<String,Map<String,Object>>();for(var file:g.files())files.put((String)file.get("id"),file);var sources=new ArrayList<Map<String,Object>>();g.rows().stream().filter(r->r.table()==Table.INPUT_BATCH_FILE).sorted(Comparator.comparingLong(r->r.number("ordinal"))).forEach(r->{var file=files.get(r.string("file_id"));sources.add(Map.of("fileId",r.string("file_id"),"sha256",r.string("sha256"),"sizeBytes",file.get("size_bytes"),"sizeBytesToken",file.get("size_bytes").toString(),"ordinal",r.number("ordinal")));});out.put("sources",List.copyOf(sources));
  if(g.selected()!=null){out.put("parserVersion",g.input().string("parser_version"));out.put("processingConfigVersion",g.input().string("processing_config_version"));out.put("confirmationHashProfile",p.profile().version());}
  return Collections.unmodifiableMap(out);
 }
 private Map<String,Object> detail(SavedTranscriptGraph g,boolean owner){
  var r=g.selectedRow();var out=new LinkedHashMap<String,Object>();out.put("id",g.selected());out.put("kind",r.string("kind"));out.put("revision",r.number("revision"));out.put("revisionToken",Long.toString(r.number("revision")));
  if(owner)for(String name:List.of("parent_id","batch_id","processing_input_id","input_fingerprint","created_by","created_at","producer_task_id"))out.put(switch(name){case "parent_id"->"parentId";case "batch_id"->"batchId";case "processing_input_id"->"processingInputId";case "input_fingerprint"->"inputFingerprint";case "created_by"->"createdBy";case "created_at"->"createdAt";default->"producerTaskId";},r.nullableString(name));
  var segments=new ArrayList<Map<String,Object>>();for(var s:g.segments(g.selected())){
   var item=new LinkedHashMap<String,Object>();item.put("segmentId",s.string("segment_id"));item.put("ordinal",s.number("ordinal"));item.put("body",s.string("body"));item.put("sourceFileId",s.string("source_file_id"));item.put("sourceParagraph",s.nullableNumber("source_paragraph"));item.put("startMs",s.nullableNumber("start_ms"));item.put("endMs",s.nullableNumber("end_ms"));item.put("speakerId",s.nullableString("speaker_id"));
   item.put("marks",g.rows().stream().filter(m->m.table()==Table.TRANSCRIPT_SEGMENT_MARK&&m.string("revision_id").equals(g.selected())&&m.string("segment_id").equals(s.string("segment_id"))).map(m->Mark.valueOf(m.string("mark"))).sorted().map(Enum::name).toList());segments.add(Collections.unmodifiableMap(item));
  }out.put("segments",List.copyOf(segments));return Collections.unmodifiableMap(out);
 }
 static Map<String,Object> observation(Map<String,Object> root){var o=new LinkedHashMap<String,Object>();o.put("meetingRevision",root.get("revision"));o.put("revisionToken",root.get("revision").toString());o.put("accessRevision",root.get("access_revision"));o.put("accessRevisionToken",root.get("access_revision").toString());o.put("currentSavedRevisionId",root.get("transcript_revision_id"));o.put("currentConfirmationId",root.get("transcript_confirmation_id"));return Collections.unmodifiableMap(o);}
 private static void independent(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Saved transcript preparation requires no ambient transaction");}
 private static void cancellation(RuntimeException error){if(Thread.currentThread().isInterrupted()||error instanceof Failure f&&f.code()==CANCELLED)throw error;}
}
