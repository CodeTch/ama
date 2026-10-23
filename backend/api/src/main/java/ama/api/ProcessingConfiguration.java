package ama.api;
import ama.access.MeetingAccess;
import ama.audit.JdbcAuditAdapter;
import ama.config.*;
import ama.config.file.ControlledConfigArchiveSource;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.MinutesContracts.CanonicalBudget;
import ama.db.TransactionRunner;
import ama.storage.*;
import ama.task.*;
import ama.transcript.*;
import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import static ama.contract.ProcessingContracts.*;
@Configuration(proxyBeanMethods=false) class ProcessingConfiguration {
    record Runtime(JdbcTranscriptAuthority.Components components,JdbcProcessingIntake.NewIntake preparation,Map<String,Object> budgets,java.util.function.BooleanSupplier available) {
    }
    @Bean Runtime processingRuntime(DataSource source,TransactionRunner tx,MeetingAccess access,FilePurposePolicy purposes, FileBackendCatalog backends,Map<String,FileReferencePolicy> references,Environment env) {
        if(!env.getProperty("processing.enabled",Boolean.class,false))return unavailableRuntime();
        try {
            return configured(source,tx,access,purposes,backends,references,env);
        }
        catch(IllegalArgumentException|IllegalStateException|ama.contract.MinutesContracts.Failure invalid) {
            return unavailableRuntime();
        }
    }
    private Runtime unavailableRuntime() {
        return new Runtime(null,(a,m,r)-> {
            throw unavailable();
        },Map.of(),()->false);
    }
    private Runtime configured(DataSource source,TransactionRunner tx,MeetingAccess access,FilePurposePolicy purposes,FileBackendCatalog backends,Map<String,FileReferencePolicy> references,Environment env) {
        // Every enabled deployment supplies positive, independently named resource bounds.
        ArchiveReadBudget budget=new ArchiveReadBudget(n(env,"artifact-bytes"),n(env,"archive-total-bytes"),i(env,"artifacts"),n(env,"dependencies"),i(env,"graph-depth"),n(env,"json-tokens"),i(env,"json-depth"),i(env,"name-utf16"),i(env,"string-utf16"),n(env,"string-utf8"),n(env,"aggregate-scalars"),n(env,"manifest-bytes"),i(env,"batch-files"),n(env,"fact-rows"),n(env,"fact-edges"));
        CanonicalBudget canonical=new CanonicalBudget(n(env,"canonical-bytes"),i(env,"canonical-depth"),n(env,"canonical-nodes"),n(env,"canonical-string-utf8"),n(env,"canonical-scalars"));
        var indexBudget=new ConfigArchiveIndex.IndexBudget(n(env,"index-bytes"),i(env,"index-entries"),n(env,"release-bytes"),i(env,"index-generations"));
        long aggregate=n(env,"total-source-bytes");
        if(budget.maxBatchFiles()>64||budget.maxManifestBytes()>65536)throw new IllegalArgumentException("Processing bounds exceed protocol envelope");
        var components=JdbcTranscriptAuthority.create(source,tx,access,purposes,backends,references,budget,canonical);
        var effective=new LinkedHashMap<String,Object>();
        effective.put("maxFiles",budget.maxBatchFiles());
        effective.put("maxTotalSourceBytesToken",Long.toString(aggregate));
        effective.put("maxManifestBytesToken",Long.toString(budget.maxManifestBytes()));
        effective.put("maxRequestBytes",65536);
        effective.put("maxPageSize",50);
        effective.put("maxPartPageSize",500);
        JdbcProcessingIntake.NewIntake prepare=(actor,meeting,request)-> {
            if(request.expectedRevision()==Long.MAX_VALUE-1)throw new Failure(409,"REVISION_EXHAUSTED");
            if(request.orderedFileIds().size()>budget.maxBatchFiles())throw new Failure(413,"REQUEST_TOO_LARGE");
            var draft=components.intake().readDraft(actor,meeting,request.orderedFileIds(),()->false);
            if(draft.revision()!=request.expectedRevision())throw conflict();
            long size=0;
            for(var file:draft.orderedSources()) {
                if(file.file().size()>aggregate-size)throw new Failure(413,"REQUEST_TOO_LARGE");
                size+=file.file().size();
            }
            Path root;
            String pin,version,sha;
            try {
                root=Path.of(env.getRequiredProperty("processing.archive-root"));
                pin=env.getRequiredProperty("processing.index-sha");
                version=env.getRequiredProperty("processing.config-version");
                sha=env.getRequiredProperty("processing.config-sha");
            }
            catch(IllegalStateException failure) {
                throw unavailable();
            }
            var snapshot=new ControlledConfigArchiveSource().open(root,pin,indexBudget,budget,()->false);
            return components.authority().prepare(draft,UUID.randomUUID().toString(),UUID.randomUUID().toString(),snapshot,new Ref(ArtifactType.PROCESSING_CONFIG,version,sha),()->false);
        };
        return new Runtime(components,prepare,Collections.unmodifiableMap(effective),()-> {
            try {
                var snapshot=new ControlledConfigArchiveSource().open(Path.of(env.getRequiredProperty("processing.archive-root")),env.getRequiredProperty("processing.index-sha"),indexBudget,budget,()->false);
                var ref=new Ref(ArtifactType.PROCESSING_CONFIG,env.getRequiredProperty("processing.config-version"),env.getRequiredProperty("processing.config-sha"));
                snapshot.loadClosure(List.of(ref),budget,0,()->false);
                if(!backends.hasHistoricalRoute())return false;
                return new org.springframework.jdbc.core.JdbcTemplate(source).queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='input_batch_fingerprint_profile'",Long.class)==1;
            }
            catch(ama.contract.MinutesContracts.Failure|ama.contract.FileContracts.Unavailable|IllegalStateException failure) {
                return false;
            }
        });
    }
    @Bean JdbcTaskQueue processingQueue(DataSource source,JdbcSubjectFences fences,JdbcOutbox outbox,Runtime runtime) {
        return new JdbcTaskQueue(source,fences,outbox,runtime.components()==null?Map.of():Map.of("TRANSCRIPT_IMPORT_V1",runtime.components().authority().taskPolicy()));
    }
    @Bean JdbcProcessingIntake.NewIntake newIntakeBranch(Runtime runtime) {
        return runtime.preparation();
    }
    @Bean JdbcProcessingIntake processingIntake(DataSource source,TransactionRunner tx,MeetingAccess access,Runtime runtime,JdbcSubjectFences fences,JdbcTaskQueue queue,JdbcOutbox outbox,JdbcProcessingIntake.NewIntake preparation) {
        return new JdbcProcessingIntake(source,tx,access,runtime.components(),fences,queue,outbox,new JdbcAuditAdapter(source),preparation);
    }
    @Bean JdbcProcessingDiscovery processingDiscovery(DataSource source,TransactionRunner tx,MeetingAccess access,FilePurposePolicy purposes,FileBackendCatalog backends,FileRegistry registry,Runtime runtime,Environment env) {
        return new JdbcProcessingDiscovery(source,tx,access,purposes,backends,runtime.components()==null?null:registry,runtime.budgets(),runtime.available(),env.getProperty("files.part-size",Long.class,8L*1024*1024));
    }
    private static long n(Environment env,String key) {
        long value=env.getRequiredProperty("processing.bounds."+key,Long.class);
        if(value<=0)throw new IllegalArgumentException("Positive processing bound required");
        return value;
    }
    private static int i(Environment env,String key) {
        return Math.toIntExact(n(env,key));
    }
}
