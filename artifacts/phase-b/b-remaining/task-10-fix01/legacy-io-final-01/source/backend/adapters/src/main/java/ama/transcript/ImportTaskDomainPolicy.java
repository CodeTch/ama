package ama.transcript;
import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.*;
import ama.contract.TaskContracts.*;
import ama.storage.*;
import ama.task.*;
import java.util.*;
import static ama.transcript.TranscriptReadGraph.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
/** The authority creates this policy before exposing its assembly; it cannot issue byte grants. */
final class ImportTaskDomainPolicy implements TaskDomainPolicy {
    private final JdbcTranscriptAuthority authority;
    private final JdbcTaskPreparations issuer;
    private final org.springframework.jdbc.core.JdbcTemplate j;
    private static final class Prepared {
        final Actor owner;final MeetingAccess.LockedIdentities identities;final List<TranscriptReadGraph> graphs;final MinutesPendingDiscovery.Plan pending;
        FileRegistry.LockedMeeting completionGate;
        Prepared(Actor owner,MeetingAccess.LockedIdentities identities,List<TranscriptReadGraph> graphs,MinutesPendingDiscovery.Plan pending){this.owner=owner;this.identities=identities;this.graphs=graphs;this.pending=pending;}
        Actor owner(){return owner;}MeetingAccess.LockedIdentities identities(){return identities;}List<TranscriptReadGraph> graphs(){return graphs;}
    }
    private record Adopted(JdbcTranscriptAuthority.HeldProcessingFacts held,FileRegistry.LockedMeeting gate,TaskSpec spec) {
    }
    ImportTaskDomainPolicy(JdbcTranscriptAuthority authority,javax.sql.DataSource source) {
        this.authority=authority;
        issuer=new JdbcTaskPreparations(source);
        j=new org.springframework.jdbc.core.JdbcTemplate(source);
        j.setQueryTimeout(5);
    }
    PreparedDomain adopt(FileRegistry.LockedMeeting gate,JdbcTranscriptAuthority.HeldProcessingFacts held,TaskSpec spec) {
        authority.requireCreatedTransition(gate,held,spec);
        return issuer.issue(List.of(spec),Acquisition.TASK_MUTATION,new Adopted(held,gate,spec),true);
    }
    public Optional<PreparedDomain> prepare(List<TaskSpec> specs,Acquisition purpose,boolean skip) {
        if(purpose!=Acquisition.TASK_MUTATION||specs==null||specs.isEmpty())throw new DomainPreparationUnavailableException();
        var first=specs.getFirst();
        var graphs=new ArrayList<TranscriptReadGraph>();
        String owner=null;
        require(specs.size()<=authority.readBudget().maxBatchFiles(),BUDGET_EXCEEDED);
        for(var spec:specs) {
            shape(spec);
            require(spec.subject().equals(first.subject()),PROVENANCE_UNAVAILABLE);
            var ids=j.queryForList("SELECT id FROM durable_task WHERE input_kind='TRANSCRIPTION_INPUT' AND input_id=? ORDER BY id LIMIT 2",String.class,spec.input().id());
            require(ids.size()==1,PROVENANCE_UNAVAILABLE);
            var members=j.queryForList("SELECT f.file_id FROM input_batch_file f JOIN transcription_input i ON i.batch_id=f.batch_id WHERE i.id=? ORDER BY f.ordinal LIMIT 1",String.class,spec.input().id());
            require(members.size()==1,PROVENANCE_UNAVAILABLE);
            var graph=TranscriptReadGraph.discover(j,authority.readBudget(),authority.canonicalBudget(),spec.subject().id(),null,spec.input().id(),ids.getFirst(),members.getFirst(),row->authority.producerBeforeRoots(spec.subject().id(),text(row,"id"),text(row,"input_fingerprint"),number(row,"base_revision"),number(row,"cancel_epoch")));
            require(graph.expected.equals(spec),PROVENANCE_UNAVAILABLE);
            String actual=text(graph.root,"owner_id");
            if(owner==null)owner=actual;
            require(owner.equals(actual),PROVENANCE_UNAVAILABLE);
            graphs.add(graph);
        }
        var pending=MinutesPendingDiscovery.discover(authority.dataSource(),first.subject().id());
        var actor=new Actor(owner,Status.ACTIVE,Set.of(ama.contract.IdentityContracts.Role.USER));
        var identities=authority.meetingAccess().tryLockIdentities(actor,List.of(owner),skip);
        if(identities.isEmpty())return Optional.empty();
        return Optional.of(issuer.issue(specs,purpose,new Prepared(actor,identities.orElseThrow(),List.copyOf(graphs),pending),true));
    }
    public void requirePrepared(List<TaskSpec> specs,Acquisition purpose,PreparedDomain prepared) {
        issuer.require(specs,purpose,prepared);
    }
    public boolean permitsDomainEffects(PreparedDomain prepared) {
        return issuer.permitsEffects(prepared);
    }
    public boolean lockRoot(TaskSpec spec,boolean skip) {
        throw new DomainPreparationUnavailableException();
    }
    public boolean lockRoots(List<TaskSpec> specs,boolean skip,PreparedDomain prepared) {
        try {
            return lockAll(specs,skip,prepared);
        }
        catch(ama.contract.FileContracts.NotFound missing) {
            if(skip)return false;
            throw missing;
        }
        catch(ama.contract.MinutesContracts.Failure raced) {
            if(skip&&(raced.code()==PROVENANCE_UNAVAILABLE||raced.code()==PROFILE_UNAVAILABLE))return false;
            throw raced;
        }
    }
    private boolean lockAll(List<TaskSpec> specs,boolean skip,PreparedDomain prepared) {
        issuer.require(specs,Acquisition.TASK_MUTATION,prepared);
        Object value=issuer.value(prepared);
        if(value instanceof Adopted adopted) {
            authority.requireCreatedTransition(adopted.gate(),adopted.held(),adopted.spec());
            return true;
        }
        if(!(value instanceof Prepared p))throw new DomainPreparationUnavailableException();
        var root=authority.meetingAccess().lockPrepared(p.identities(),specs.getFirst().subject().id(),skip);
        if(root.isEmpty())return false;
        authority.meetingAccess().requirePrepared(root.orElseThrow(),p.owner(),specs.getFirst().subject().id(),List.of(p.owner().enterpriseId()),MeetingAccess.Action.EDIT);
        var gate=authority.fileRegistry().adopt(p.owner(),specs.getFirst().subject().id(),root.orElseThrow(),MeetingAccess.Action.EDIT);
        var reader=new BoundedTranscriptMetadata(j,authority.readBudget(),true,skip);
        for(var graph:p.graphs())require(reader.one("meeting",BoundedTranscriptMetadata.ROOT,text(graph.root,"id")).equals(graph.root),PROVENANCE_UNAVAILABLE);
        // Whole input/batch/profile/member union precedes every source session/file, across all specs.
        var inputs=new TreeMap<String,TranscriptReadGraph>();
        var batches=new TreeMap<String,TranscriptReadGraph>();
        for(var graph:p.graphs()) {
            inputs.put(text(graph.input,"id"),graph);
            batches.put(text(graph.batch,"id"),graph);
        }
        for(var entry:inputs.entrySet())require(reader.one("transcription_input",BoundedTranscriptMetadata.INPUT,entry.getKey()).equals(entry.getValue().input),PROVENANCE_UNAVAILABLE);
        for(var entry:batches.entrySet())require(reader.one("input_batch",BoundedTranscriptMetadata.BATCH,entry.getKey()).equals(entry.getValue().batch),PROVENANCE_UNAVAILABLE);
        for(var entry:batches.entrySet())require(reader.select("input_batch_fingerprint_profile","batch_id profile","batch_id=?","batch_id",2,entry.getKey()).equals(List.of(entry.getValue().profile)),PROFILE_UNAVAILABLE);
        for(var entry:batches.entrySet())require(reader.select("input_batch_file",BoundedTranscriptMetadata.MEMBER,"batch_id=?","ordinal",authority.readBudget().maxBatchFiles(),entry.getKey()).equals(entry.getValue().members),PROVENANCE_UNAVAILABLE);
        for(var graph:p.graphs()) {
            var tasks=new BoundedTranscriptMetadata(j,authority.readBudget()).select("durable_task",BoundedTranscriptMetadata.TASK,"input_kind='TRANSCRIPTION_INPUT' AND input_id=?","id",2,text(graph.input,"id"));
            require(tasks.equals(List.copyOf(graph.tasks.values())),PROVENANCE_UNAVAILABLE);
        }
        MinutesPendingDiscovery.lock(authority.dataSource(),p.pending,MinutesPendingDiscovery.DOMAIN);
        // A stale queue acquisition can retire its task without reviving obsolete byte/source permission.
        // The queue still locks/rechecks the actual task and decides CANCELLED/STALE before validateInput.
        var current=p.graphs().stream().filter(graph->number(graph.root,"revision")==graph.expected.businessVersion()&&number(graph.root,"cancel_epoch")==graph.expected.cancelEpoch()).toList();
        if(current.isEmpty()){capacity(p);return true;}
        for(var graph:current)require(number(graph.root,"ever_published")==0&&!"PUBLISHED".equals(graph.root.get("stage")),PROVENANCE_UNAVAILABLE);
        var files=new TreeSet<String>();
        for(var graph:current)files.addAll(graph.files.keySet());
        require(files.size()<=authority.readBudget().maxBatchFiles(),BUDGET_EXCEEDED);
        var receipt=authority.fileRegistry().prelockSources(gate,List.copyOf(files),skip);
        if(receipt.isEmpty())return false;
        var sources=authority.fileRegistry().sources(gate,receipt.orElseThrow(),List.copyOf(files));
        var actual=new HashMap<String,FileRegistry.CompletedSource>();
        for(var source:sources) {
            require(JdbcTranscriptAuthority.PURPOSE.equals(source.originalPurpose()),PROVENANCE_UNAVAILABLE);
            actual.put(source.file().fileId(),source);
        }
        for(var graph:current) {
            authority.requireTaskPurpose(p.owner(),text(graph.root,"id"));
            for(var member:graph.members) {
                String file=text(member,"file_id");
                var original=actual.get(file);
                var observed=graph.files.get(file);
                require(original!=null&&original.file().size()==number(observed,"size_bytes")&&original.file().sha256().equals(observed.get("sha256"))&&original.uploadCancelEpoch()==number(graph.input,"cancel_epoch"),PROVENANCE_UNAVAILABLE);
                require(j.queryForObject("SELECT COUNT(*) FROM file_reference WHERE file_id=? AND owner_kind='INPUT_BATCH_FILE' AND owner_id=? AND purpose='TRANSCRIPT_INPUT'",Long.class,file,text(graph.batch,"id"))==1,PROVENANCE_UNAVAILABLE);
            }
        }
        capacity(p);
        if(current.size()==p.graphs().size())p.completionGate=gate;
        return true;
    }
    private void capacity(Prepared p){MinutesPendingDiscovery.lock(authority.dataSource(),p.pending,MinutesPendingDiscovery.TASKS);MinutesDomainCapacity.reserve(p.pending.rows(),authority.minutesDomain().taskLimit(),256,null);}
    void requireCompletion(List<TaskSpec> specs,PreparedDomain prepared){
        issuer.require(specs,Acquisition.TASK_MUTATION,prepared);
        if(!(issuer.value(prepared) instanceof Prepared p)||p.completionGate==null||!issuer.permitsEffects(prepared))throw new DomainPreparationUnavailableException();
        // EDIT was established before this gate was retained, after the complete current graph.
        // Registry access checks issuer/holder only; re-running requirePrepared would refresh roots late.
        authority.fileRegistry().access(p.completionGate);
        require(p.graphs().stream().map(g->g.expected).toList().equals(specs),PROVENANCE_UNAVAILABLE);
    }
    public void validateInput(TaskSpec spec) {
        shape(spec);
        require(j.queryForObject("SELECT COUNT(*) FROM transcription_input i JOIN input_batch b ON b.id=i.batch_id JOIN input_batch_fingerprint_profile p ON p.batch_id=b.id WHERE i.id=? AND i.meeting_id=? AND i.kind='IMPORT' AND i.material_mode='EMPTY' AND i.material_snapshot_id IS NULL AND i.source_revision_id IS NULL AND i.model_config_version IS NULL AND i.prompt_version IS NULL AND i.parser_version IS NOT NULL AND i.manifest_schema_version=1 AND i.input_fingerprint=? AND i.base_revision=? AND i.cancel_epoch=? AND b.kind='TRANSCRIPT' AND b.meeting_id=i.meeting_id AND b.base_revision=i.base_revision AND p.profile='ama.input-batch.v1'",Long.class,spec.input().id(),spec.subject().id(),spec.inputFingerprint(),spec.businessVersion(),spec.cancelEpoch())==1,PROVENANCE_UNAVAILABLE);
    }
    public void validateOutput(TaskSpec spec,TaskResult result) {
        if(result.outcome()==Outcome.RECONCILING)throw new DomainPreparationUnavailableException();
        if(result.outcome()!=Outcome.SUCCEEDED) {
            if(result.output()!=null)throw new IllegalArgumentException("Failure cannot carry output");
            return;
        }
        if(result.output()==null||!"TRANSCRIPT_REVISION".equals(result.output().kind()))throw new IllegalArgumentException("RAW output required");
        validateInput(spec);
        require(j.queryForObject("SELECT COUNT(*) FROM transcript_revision r JOIN transcription_input i ON i.id=r.processing_input_id JOIN durable_task t ON t.id=r.producer_task_id WHERE r.id=? AND r.meeting_id=? AND r.kind='RAW' AND r.parent_id IS NULL AND i.id=? AND r.batch_id=i.batch_id AND r.input_fingerprint=i.input_fingerprint AND t.task_type=? AND t.pool=? AND t.subject_kind='MEETING' AND t.subject_id=r.meeting_id AND t.input_kind='TRANSCRIPTION_INPUT' AND t.input_id=i.id AND t.input_fingerprint=? AND t.producer_key=? AND t.business_version=? AND t.cancel_epoch=? AND t.state='RUNNING'",Long.class,result.output().id(),spec.subject().id(),spec.input().id(),spec.type(),spec.pool(),spec.inputFingerprint(),spec.producerKey(),spec.businessVersion(),spec.cancelEpoch())==1,PROVENANCE_UNAVAILABLE);
        var segments=new BoundedTranscriptMetadata(j,authority.readBudget()).select("transcript_segment","segment_id ordinal# source_file_id source_paragraph# start_ms# end_ms# speaker_id","revision_id=?","ordinal",(int)Math.min(Integer.MAX_VALUE-1,authority.readBudget().maxFactRows()),result.output().id());
        long ordinal=1;
        for(var segment:segments) {
            require(number(segment,"ordinal")==ordinal++&&segment.get("source_paragraph")!=null&&number(segment,"source_paragraph")>0&&segment.get("start_ms")==null&&segment.get("end_ms")==null&&segment.get("speaker_id")==null,PROVENANCE_UNAVAILABLE);
            require(j.queryForObject("SELECT COUNT(*) FROM input_batch_file f JOIN transcription_input i ON i.batch_id=f.batch_id WHERE i.id=? AND f.file_id=?",Long.class,spec.input().id(),text(segment,"source_file_id"))==1,PROVENANCE_UNAVAILABLE);
        }
        require(j.queryForObject("SELECT COUNT(*) FROM transcript_segment_mark WHERE revision_id=?",Long.class,result.output().id())==0,PROVENANCE_UNAVAILABLE);
    }
    public void validateExternalRequest(TaskSpec spec,ama.contract.ExternalContracts.ExternalRequest request) {
        throw new DomainPreparationUnavailableException();
    }
    private static void shape(TaskSpec spec) {
        require("TRANSCRIPT_IMPORT_V1".equals(spec.type())&&"TRANSCRIPT_IMPORT".equals(spec.pool())&&"MEETING".equals(spec.subject().kind())&&"TRANSCRIPTION_INPUT".equals(spec.input().kind())&&spec.priority()==0&&spec.maxAttempts()==3,PROVENANCE_UNAVAILABLE);
    }
}
