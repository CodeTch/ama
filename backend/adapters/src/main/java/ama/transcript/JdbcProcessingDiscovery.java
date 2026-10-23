package ama.transcript;
import ama.access.MeetingAccess;
import ama.contract.ConfigArchiveContracts.ArchiveReadBudget;
import ama.contract.IdentityContracts.Actor;
import ama.contract.ProcessingContracts.*;
import ama.db.TransactionRunner;
import ama.storage.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import java.util.function.*;
import static ama.contract.ProcessingContracts.*;
import static ama.transcript.TranscriptReadGraph.*;
/** Persisted, bounded owner projections. No provider tokens, storage paths, bodies or capabilities escape. */
public final class JdbcProcessingDiscovery {
    private static final ArchiveReadBudget READ=new ArchiveReadBudget(65536,262144,16,32,8,4096,8,255,65536,65536,262144,65536,64,4096,32768);
    private record InputProofs(Map<String,ama.contract.TaskContracts.TaskSpec> specs,boolean valid) {
    }
    private static final ama.contract.MinutesContracts.CanonicalBudget PRODUCER=new ama.contract.MinutesContracts.CanonicalBudget(65536,8,128,1024,4096);
    private static final String UPLOAD="id meeting_id purpose display_name state expected_size# expected_sha256 created_at updated_at completed_file_id cancel_epoch# backend_id";
    private static final String FILE="id meeting_id display_name size_bytes# sha256 state";
    private static final String INPUT=BoundedTranscriptMetadata.INPUT+" created_at";
    private static final String TASK=BoundedTranscriptMetadata.TASK+" state attempt# available_at created_at last_error output_kind output_id";
    private final JdbcTemplate j;
    private final TransactionRunner tx;
    private final MeetingAccess access;
    private final FilePurposePolicy purposes;
    private final FileBackendCatalog backends;
    private final FileRegistry registry;
    private final Map<String,Object> budgets;
    private final BooleanSupplier available;
    public JdbcProcessingDiscovery(DataSource source,TransactionRunner tx,MeetingAccess access,FilePurposePolicy purposes,FileBackendCatalog backends,FileRegistry registry,Map<String,Object> budgets,BooleanSupplier available,long partSize) {
        j=new JdbcTemplate(source);
        j.setQueryTimeout(5);
        this.tx=tx;
        this.access=access;
        this.purposes=purposes;
        this.backends=backends;
        this.registry=registry;
        this.budgets=Map.copyOf(budgets);
        this.available=available;
        if(partSize<=0)throw new IllegalArgumentException("Positive part size required");
        this.partSize=partSize;
    }
    public Map<String,Object> capabilities(Actor actor,String meeting) {
        independent();
        ama.contract.OutboxContracts.uuid(meeting);
        var result=tx.required(()-> {
            var held=access.lock(actor,meeting);
            access.require(held,MeetingAccess.Action.READ);
            var root=root(meeting);
            boolean owner=Objects.equals(root.get("owner_id"),actor.enterpriseId());
            return map("revision",number(root,"revision"),"revisionToken",Long.toString(number(root,"revision")),"accessRevision",number(root,"access_revision"),"accessRevisionToken",Long.toString(number(root,"access_revision")),"canSubmit",false,"readOnlyReason",owner?null:"OWNER_REQUIRED","owner",owner,"writable",writable(root));
        });
        boolean owner=(Boolean)result.remove("owner"),writable=(Boolean)result.remove("writable");
        boolean ready=owner&&available.getAsBoolean();
        boolean exhausted=((Number)result.get("revision")).longValue()>=Long.MAX_VALUE-1;
        result.put("canSubmit",owner&&writable&&ready&&!exhausted);
        if(owner)result.put("readOnlyReason",!writable?"READ_ONLY":exhausted?"REVISION_EXHAUSTED":ready?null:"PROCESSING_UNAVAILABLE");
        result.put("intake",ready?"AVAILABLE":"UNAVAILABLE");
        result.put("execution","NOT_INSTALLED");
        result.put("businessReady",false);
        result.put("formats",List.of("TXT"));
        result.put("purpose","TRANSCRIPT_INPUT");
        result.put("materialMode","EMPTY");
        result.put("budgets",budgets);
        return result;
    }
    public Page uploads(Actor actor,String meeting,int limit,String after) {
        page(limit,50);
        cursor(after);
        return owner(actor,meeting,(gate,root)-> {
            var read=reader();
            var rows=read.select("upload_session",UPLOAD,"meeting_id=? AND id>?","id",limit+1,meeting,after==null?"":after);
            return page(rows,limit,row->uploadSummary(row),"id");
        });
    }
    public Map<String,Object> upload(Actor actor,String meeting,String upload) {
        ama.contract.OutboxContracts.uuid(upload);
        return owner(actor,meeting,(gate,root)-> {
            var rows=reader().select("upload_session",UPLOAD,"id=? AND meeting_id=?","id",2,upload,meeting);
            if(rows.size()!=1)throw missing();
            var row=rows.getFirst();
            var result=uploadSummary(row);
            result.put("accepted",map("uploadId",upload,"partSize",partSize,"partSizeToken",Long.toString(partSize)));
            boolean common=number(row,"cancel_epoch")==number(root,"cancel_epoch");
            try {
                purposes.require(actor,meeting,text(row,"purpose"));
            }
            catch(FilePurposePolicy.Unavailable denied) {
                common=false;
            }
            var actions=new ArrayList<String>();
            var retries=new ArrayList<String>();
            String reason=common?null:"SOURCE_EPOCH";
            String state=text(row,"state");
            String route=null;
            try {
                route=backends.historicalStatus(text(row,"backend_id"));
            }
            catch(ama.contract.FileContracts.Unavailable unavailable) {
                reason="SOURCE_ROUTE";
            }
            if(common) {
                if(state.equals("COMPLETED")) {
                    retries.add("COMPLETE");
                    retries.add("ABORT");
                    reason=null;
                }
                else if(state.equals("ABORTED")) {
                    if(route!=null)retries.add("ABORT");
                }
                else if(writable(root)&&route!=null) {
                    if(state.equals("OPEN")) {
                        actions.add("ABORT");
                        if(route.equals("WRITABLE")) {
                            actions.addFirst("COMPLETE");
                            actions.addFirst("UPLOAD_PARTS");
                        }
                    }
                    else if(state.equals("FINALIZING"))actions.add("COMPLETE");
                }
                else if(!writable(root))reason="READ_ONLY";
            }
            result.put("allowedActions",List.copyOf(actions));
            result.put("idempotentRetries",List.copyOf(retries));
            result.put("blockedReason",reason);
            Object completed=null;
            if(row.get("completed_file_id")!=null) {
                var files=reader().select("file_object",FILE,"id=? AND meeting_id=?","id",2,row.get("completed_file_id"),meeting);
                if(files.size()!=1)throw unavailable();
                completed=completed(files.getFirst(),row,eligibility(actor,gate,root,List.of(files.getFirst())).get(text(files.getFirst(),"id")));
            }
            result.put("completed",completed);
            return result;
        });
    }
    private final long partSize;
    public Page parts(Actor actor,String meeting,String upload,int limit,long after) {
        ama.contract.OutboxContracts.uuid(upload);
        page(limit,500);
        if(after<0)throw new IllegalArgumentException("Invalid part cursor");
        return owner(actor,meeting,(gate,root)-> {
            if(reader().select("upload_session","id","id=? AND meeting_id=?","id",2,upload,meeting).size()!=1)throw missing();
            var rows=reader().select("upload_part","upload_id part_no# size_bytes# sha256","upload_id=? AND part_no>?","part_no",limit+1,upload,after);
            return page(rows,limit,row->map("part",number(row,"part_no"),"sizeBytes",number(row,"size_bytes"),"sizeBytesToken",Long.toString(number(row,"size_bytes")),"sha256",row.get("sha256")),"part_no");
        });
    }
    public Page completedFiles(Actor actor,String meeting,int limit,String after) {
        page(limit,50);
        cursor(after);
        return owner(actor,meeting,(gate,root)-> {
            var rows=reader().select("file_object",FILE,"meeting_id=? AND id>? AND EXISTS(SELECT 1 FROM upload_session u WHERE u.completed_file_id=file_object.id AND u.state='COMPLETED')","id",limit+1,meeting,after==null?"":after);
            var selected=rows.subList(0,Math.min(limit,rows.size()));
            var eligible=eligibility(actor,gate,root,selected);
            return page(rows,limit,row-> {
                var sessions=reader().select("upload_session",UPLOAD,"completed_file_id=? AND state='COMPLETED'","id",2,text(row,"id"));
                if(sessions.size()!=1)throw unavailable();
                return completed(row,sessions.getFirst(),eligible.get(text(row,"id")));
            },"id");
        });
    }
    public Page inputs(Actor actor,String meeting,int limit,String after) {
        page(limit,50);
        cursor(after);
        var proofs=proofs(meeting,j.queryForList("SELECT id FROM transcription_input WHERE meeting_id=? AND id>? ORDER BY id LIMIT ?",String.class,meeting,after==null?"":after,limit));
        return owner(actor,meeting,(gate,root)-> {
            var rows=reader().select("transcription_input",INPUT,"meeting_id=? AND id>?","id",limit+1,meeting,after==null?"":after);
            return page(rows,limit,row-> {
                verifyProof(row,proofs);
                return inputSummary(row);
            },"id");
        });
    }
    public Map<String,Object> input(Actor actor,String meeting,String input) {
        ama.contract.OutboxContracts.uuid(input);
        var proofs=proofs(meeting,List.of(input));
        return owner(actor,meeting,(gate,root)->inputDetail(meeting,input,proofs));
    }
    private Map<String,Object> inputDetail(String meeting,String input,InputProofs proofs) {
        var read=reader();
        var inputs=read.select("transcription_input",INPUT,"id=? AND meeting_id=?","id",2,input,meeting);
        if(inputs.size()!=1)throw missing();
        var row=inputs.getFirst();
        var result=inputSummary(row);
        String batch=text(row,"batch_id");
        var batches=read.select("input_batch",BoundedTranscriptMetadata.BATCH,"id=? AND meeting_id=?","id",2,batch,meeting);
        if(batches.size()!=1||!"TRANSCRIPT".equals(batches.getFirst().get("kind"))||number(batches.getFirst(),"base_revision")!=number(row,"base_revision"))throw unavailable();
        var profiles=read.select("input_batch_fingerprint_profile","batch_id profile","batch_id=?","batch_id",2,batch);
        if(profiles.size()!=1||!BatchFingerprint.PROFILE.equals(profiles.getFirst().get("profile")))throw unavailable();
        var members=read.select("input_batch_file",BoundedTranscriptMetadata.MEMBER,"batch_id=?","ordinal",64,batch);
        if(members.isEmpty())throw unavailable();
        var files=new ArrayList<Map<String,Object>>();
        var unique=new HashSet<String>();
        long ordinal=1;
        for(var member:members) {
            String id=text(member,"file_id");
            if(number(member,"ordinal")!=ordinal++||!unique.add(id))throw unavailable();
            var objects=read.select("file_object","id meeting_id size_bytes# sha256","id=?","id",2,id);
            if(objects.size()!=1)throw unavailable();
            var file=objects.getFirst();
            if(!meeting.equals(file.get("meeting_id"))||!Objects.equals(file.get("sha256"),member.get("sha256")))throw unavailable();
            var originals=read.select("upload_session","id meeting_id purpose expected_size# expected_sha256 completed_file_id","completed_file_id=? AND state='COMPLETED'","id",2,id);
            if(originals.size()!=1)throw unavailable();
            var original=originals.getFirst();
            if(!meeting.equals(original.get("meeting_id"))||!"TRANSCRIPT_INPUT".equals(original.get("purpose"))||number(file,"size_bytes")!=number(original,"expected_size")||!Objects.equals(file.get("sha256"),original.get("expected_sha256")))throw unavailable();
            files.add(map("ordinal",number(member,"ordinal"),"fileId",id,"uploadId",original.get("id"),"sha256",file.get("sha256"),"sizeBytes",number(file,"size_bytes"),"sizeBytesToken",Long.toString(number(file,"size_bytes"))));
        }
        var tasks=read.select("durable_task",TASK,"input_kind='TRANSCRIPTION_INPUT' AND input_id=?","id",2,input);
        if(tasks.size()!=1)throw unavailable();
        verifyTask(tasks.getFirst(),row);
        verifyProof(row,proofs);
        result.put("taskId",tasks.getFirst().get("id"));
        result.put("batchProfile",BatchFingerprint.PROFILE);
        result.put("batchFingerprint",batches.getFirst().get("input_fingerprint"));
        result.put("cancelEpochToken",Long.toString(number(row,"cancel_epoch")));
        result.put("files",List.copyOf(files));
        return result;
    }
    public Page tasks(Actor actor,String meeting,String scope,int limit,String after) {
        page(limit,50);
        cursor(after);
        if(!Set.of("active","all").contains(scope))throw new IllegalArgumentException("Invalid task scope");
        var proofs=proofs(meeting,j.queryForList("SELECT input_id FROM durable_task WHERE subject_kind='MEETING' AND subject_id=? AND task_type='TRANSCRIPT_IMPORT_V1' AND id>?"+(scope.equals("active")?" AND state IN ('READY','RUNNING')":"")+" ORDER BY id LIMIT ?",String.class,meeting,after==null?"":after,limit));
        return owner(actor,meeting,(gate,root)-> {
            var rows=reader().select("durable_task",TASK,"subject_kind='MEETING' AND subject_id=? AND task_type='TRANSCRIPT_IMPORT_V1' AND id>?"+(scope.equals("active")?" AND state IN ('READY','RUNNING')":""),"id",limit+1,meeting,after==null?"":after);
            return page(rows,limit,row->taskSummary(meeting,row,proofs),"id");
        });
    }
    public Map<String,Object> task(Actor actor,String meeting,String task) {
        ama.contract.OutboxContracts.uuid(task);
        var proofs=proofs(meeting,j.queryForList("SELECT input_id FROM durable_task WHERE id=? AND subject_kind='MEETING' AND subject_id=? AND task_type='TRANSCRIPT_IMPORT_V1' LIMIT 2",String.class,task,meeting));
        return owner(actor,meeting,(gate,root)-> {
            var rows=reader().select("durable_task",TASK,"id=? AND subject_kind='MEETING' AND subject_id=? AND task_type='TRANSCRIPT_IMPORT_V1'","id",2,task,meeting);
            if(rows.size()!=1)throw missing();
            return taskSummary(meeting,rows.getFirst(),proofs);
        });
    }
    private Map<String,Object> taskSummary(String meeting,Map<String,Object> row,InputProofs proofs) {
        var inputs=reader().select("transcription_input",INPUT,"id=? AND meeting_id=?","id",2,row.get("input_id"),meeting);
        if(inputs.size()!=1)throw unavailable();
        var input=inputs.getFirst();
        verifyTask(row,input);
        verifyProof(input,proofs);
        String state=text(row,"state");
        if(!Set.of("READY","RUNNING","SUCCEEDED","FAILED","CANCELLED","STALE").contains(state))throw unavailable();
        Object output=null;
        if(state.equals("SUCCEEDED")) {
            if(!"TRANSCRIPT_REVISION".equals(row.get("output_kind"))||row.get("output_id")==null)throw unavailable();
            if(j.queryForObject("SELECT COUNT(*) FROM transcript_revision WHERE id=? AND kind='RAW' AND parent_id IS NULL AND meeting_id=? AND batch_id=? AND processing_input_id=? AND input_fingerprint=? AND producer_task_id=?",Long.class,row.get("output_id"),meeting,input.get("batch_id"),input.get("id"),input.get("input_fingerprint"),row.get("id"))!=1)throw unavailable();
            output=map("kind","TRANSCRIPT_REVISION","id",row.get("output_id"));
        }
        else if(row.get("output_id")!=null||row.get("output_kind")!=null)throw unavailable();
        String code=(String)row.get("last_error");
        if(code!=null&&!Set.of("ATTEMPT_LIMIT","SUBJECT_CHANGED","LEASE_EXPIRED","PROCESSING_INPUT_INVALID","PROCESSING_UNAVAILABLE").contains(code))code="PROCESSING_UNAVAILABLE";
        return map("taskId",row.get("id"),"type",row.get("task_type"),"pool",row.get("pool"),"meetingId",meeting,"inputId",input.get("id"),"batchId",input.get("batch_id"),"state",state,"businessVersion",number(row,"business_version"),"businessVersionToken",Long.toString(number(row,"business_version")),"cancelEpochToken",Long.toString(number(row,"cancel_epoch")),"attempt",number(row,"attempt"),"attemptToken",Long.toString(number(row,"attempt")),"maxAttempts",number(row,"max_attempts"),"availableAt",time(row.get("available_at")),"createdAt",time(row.get("created_at")),"errorCode",code,"output",output);
    }
    private InputProofs proofs(String meeting,List<String> inputs) {
        independent();
        var result=new HashMap<String,ama.contract.TaskContracts.TaskSpec>();
        try {
            for(String input:new LinkedHashSet<>(inputs)) {
                var rows=reader().select("durable_task",BoundedTranscriptMetadata.TASK,"input_kind='TRANSCRIPTION_INPUT' AND input_id=?","id",2,input);
                if(rows.size()!=1)return new InputProofs(Map.of(),false);
                var spec=TranscriptReadGraph.spec(rows.getFirst());
                if(!meeting.equals(spec.subject().id())||!input.equals(spec.input().id())||!TranscriptImportProducer.compute(meeting,input,spec.inputFingerprint(),spec.businessVersion(),spec.cancelEpoch(),PRODUCER,()->false).equals(spec.producerKey()))return new InputProofs(Map.of(),false);
                result.put(input,spec);
            }
            return new InputProofs(Map.copyOf(result),true);
        }
        catch(ama.contract.MinutesContracts.Failure failure) {
            if(failure.code()==ama.contract.MinutesContracts.FailureCode.CANCELLED)throw failure;
            return new InputProofs(Map.of(),false);
        }
        catch(IllegalArgumentException corrupt) {
            return new InputProofs(Map.of(),false);
        }
    }
    private void verifyProof(Map<String,Object> input,InputProofs proofs) {
        var spec=proofs.specs().get(text(input,"id"));
        if(!proofs.valid()||spec==null)throw unavailable();
        var actual=reader().select("durable_task",BoundedTranscriptMetadata.TASK,"input_kind='TRANSCRIPTION_INPUT' AND input_id=?","id",2,input.get("id"));
        if(actual.size()!=1||!TranscriptReadGraph.spec(actual.getFirst()).equals(spec))throw unavailable();
        verifyTask(actual.getFirst(),input);
    }
    private void verifyTask(Map<String,Object> task,Map<String,Object> input) {
        if(!"TRANSCRIPT_IMPORT_V1".equals(task.get("task_type"))||!"TRANSCRIPT_IMPORT".equals(task.get("pool"))||!"MEETING".equals(task.get("subject_kind"))||!Objects.equals(task.get("subject_id"),input.get("meeting_id"))||!"TRANSCRIPTION_INPUT".equals(task.get("input_kind"))||!Objects.equals(task.get("input_id"),input.get("id"))||!Objects.equals(task.get("input_fingerprint"),input.get("input_fingerprint"))||number(task,"business_version")!=number(input,"base_revision")||number(task,"cancel_epoch")!=number(input,"cancel_epoch")||number(task,"priority")!=0||number(task,"max_attempts")!=3)throw unavailable();
    }
    private Map<String,Object> inputSummary(Map<String,Object> row) {
        if(!"IMPORT".equals(row.get("kind"))||!"EMPTY".equals(row.get("material_mode"))||row.get("source_revision_id")!=null||row.get("material_snapshot_id")!=null||row.get("model_config_version")!=null||row.get("prompt_version")!=null||row.get("parser_version")==null||number(row,"manifest_schema_version")!=1)throw unavailable();
        return map("inputId",row.get("id"),"batchId",row.get("batch_id"),"kind","IMPORT","materialMode","EMPTY","baseRevision",number(row,"base_revision"),"revisionToken",Long.toString(number(row,"base_revision")),"inputFingerprint",row.get("input_fingerprint"),"createdAt",time(row.get("created_at")));
    }
    private Map<String,String> eligibility(Actor actor,FileRegistry.LockedMeeting gate,Map<String,Object> root,List<Map<String,Object>> files) {
        var result=new HashMap<String,String>();
        if(files.isEmpty())return result;
        var candidates=new ArrayList<String>();
        var read=reader();
        for(var file:files) {
            String id=text(file,"id");
            result.put(id,"SOURCE_PROVENANCE");
            if(!"AVAILABLE".equals(file.get("state"))) {
                result.put(id,"SOURCE_STATE");
                continue;
            }
            var originals=read.select("upload_session","id meeting_id purpose cancel_epoch# backend_id expected_size# expected_sha256","completed_file_id=? AND state='COMPLETED'","id",2,id);
            if(originals.size()!=1)continue;
            var original=originals.getFirst();
            if(!Objects.equals(root.get("id"),original.get("meeting_id"))||number(file,"size_bytes")!=number(original,"expected_size")||!Objects.equals(file.get("sha256"),original.get("expected_sha256")))continue;
            if(!"TRANSCRIPT_INPUT".equals(original.get("purpose"))) {
                result.put(id,"SOURCE_PURPOSE");
                continue;
            }
            if(number(original,"cancel_epoch")!=number(root,"cancel_epoch")) {
                result.put(id,"SOURCE_EPOCH");
                continue;
            }
            try {
                purposes.require(actor,text(root,"id"),text(original,"purpose"));
            }
            catch(FilePurposePolicy.Unavailable denied) {
                result.put(id,"SOURCE_PURPOSE");
                continue;
            }
            try {
                backends.historicalStatus(text(original,"backend_id"));
            }
            catch(ama.contract.FileContracts.Unavailable missing) {
                result.put(id,"SOURCE_ROUTE");
                continue;
            }
            candidates.add(id);
        }
        if(registry==null||candidates.isEmpty())return result;
        if(candidates.size()>((Number)budgets.getOrDefault("maxFiles",0)).intValue())throw new Failure(503,"PROCESSING_READ_BUDGET_EXCEEDED");
        try {
            var receipt=registry.prelockSources(gate,List.copyOf(candidates),false).orElseThrow();
            var sources=registry.sources(gate,receipt,List.copyOf(candidates));
            for(var source:sources)result.put(source.file().fileId(),!writable(root)?"READ_ONLY":!"TRANSCRIPT_INPUT".equals(source.originalPurpose())?"SOURCE_PURPOSE":source.uploadCancelEpoch()!=number(root,"cancel_epoch")?"SOURCE_EPOCH":null);
        }
        catch(ama.contract.FileContracts.Conflict|ama.contract.FileContracts.NotFound|ama.contract.FileContracts.Unavailable changed) {
            // The complete selected candidate union changed. Unknown eligibility is unavailable, never a partial grant.
        }
        return result;
    }
    private Map<String,Object> completed(Map<String,Object> file,Map<String,Object> upload,String reason) {
        return map("fileId",file.get("id"),"uploadId",upload.get("id"),"displayName",file.get("display_name"),"sizeBytes",number(file,"size_bytes"),"sizeBytesToken",Long.toString(number(file,"size_bytes")),"sha256",file.get("sha256"),"fileState",file.get("state"),"originalPurpose",upload.get("purpose"),"intakeEligibility",map("state",reason==null?"ELIGIBLE":Set.of("SOURCE_ROUTE","SOURCE_PROVENANCE").contains(reason)?"UNAVAILABLE":"INELIGIBLE","reason",reason),"businessReadEligibility","NOT_EVALUATED");
    }
    private Map<String,Object> uploadSummary(Map<String,Object> row) {
        return map("uploadId",row.get("id"),"purpose",row.get("purpose"),"displayName",row.get("display_name"),"state",row.get("state"),"expectedSize",number(row,"expected_size"),"expectedSizeToken",Long.toString(number(row,"expected_size")),"expectedSha256",row.get("expected_sha256"),"createdAt",time(row.get("created_at")),"updatedAt",time(row.get("updated_at")),"completedFileId",row.get("completed_file_id"));
    }
    private <T>T owner(Actor actor,String meeting,BiFunction<FileRegistry.LockedMeeting,Map<String,Object>,T> action) {
        independent();
        ama.contract.OutboxContracts.uuid(meeting);
        try {
            return tx.required(()-> {
                FileRegistry.LockedMeeting gate=null;
                if(registry!=null)gate=registry.lock(actor,meeting,MeetingAccess.Action.EDIT);
                else access.require(access.lock(actor,meeting),MeetingAccess.Action.EDIT);
                return action.apply(gate,root(meeting));
            });
        }
        catch(ama.contract.MinutesContracts.Failure failure) {
            if(failure.code()==ama.contract.MinutesContracts.FailureCode.CANCELLED)throw failure;
            throw new Failure(503,failure.code()==ama.contract.MinutesContracts.FailureCode.BUDGET_EXCEEDED?"PROCESSING_READ_BUDGET_EXCEEDED":"PROCESSING_UNAVAILABLE");
        }
    }
    private Map<String,Object> root(String meeting) {
        return j.queryForMap("SELECT id,owner_id,revision,access_revision,cancel_epoch,ever_published,stage,lifecycle,governance_state FROM meeting WHERE id=?",meeting);
    }
    private static boolean writable(Map<String,Object> row) {
        return number(row,"ever_published")==0&&!"PUBLISHED".equals(row.get("stage"))&&"ACTIVE".equals(row.get("lifecycle"))&&"ACTIVE".equals(row.get("governance_state"));
    }
    private BoundedTranscriptMetadata reader() {
        return new BoundedTranscriptMetadata(j,READ);
    }
    private static void independent() {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Standalone processing discovery required");
    }
    private static void page(int limit,int max) {
        if(limit<1||limit>max)throw new IllegalArgumentException("Invalid page limit");
    }
    private static void cursor(String value) {
        if(value!=null)ama.contract.OutboxContracts.uuid(value);
    }
    private static Failure missing() {
        return new Failure(404,"PROCESSING_NOT_FOUND");
    }
    private static String time(Object value) {
        return value==null?null:LocalDateTime.parse(value.toString().replace(' ','T')).toInstant(ZoneOffset.UTC).toString();
    }
    private static Page page(List<Map<String,Object>> rows,int limit,Function<Map<String,Object>,Map<String,Object>> mapper,String cursor) {
        var items=new ArrayList<Map<String,Object>>();
        for(int n=0;n<Math.min(rows.size(),limit);n++)items.add(mapper.apply(rows.get(n)));
        return new Page(items,rows.size()>limit?String.valueOf(rows.get(limit-1).get(cursor)):null);
    }
    private static Map<String,Object> map(Object... pairs) {
        var result=new LinkedHashMap<String,Object>();
        for(int n=0;n<pairs.length;n+=2)result.put((String)pairs[n],pairs[n+1]);
        return result;
    }
}
