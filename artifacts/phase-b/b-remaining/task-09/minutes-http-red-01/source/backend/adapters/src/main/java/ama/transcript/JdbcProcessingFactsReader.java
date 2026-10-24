package ama.transcript;

import ama.access.MeetingAccess;
import ama.contract.ConfigArchiveContracts.ArchiveReadBudget;
import ama.contract.IdentityContracts.Actor;
import ama.contract.MinutesContracts.*;
import ama.contract.TaskContracts.*;
import ama.db.TransactionRunner;
import ama.meeting.JdbcMeetingAccess;
import ama.task.JdbcSubjectFences;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.function.BooleanSupplier;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/**
 * Processing-only detached acquisition. Every entry owns a fresh same-DataSource transaction;
 * no acquired access handle escapes and no business row is written. Raw archive I/O and S1
 * verification belong after this transaction. Never compose this reader into an enqueue write.
 */
public final class JdbcProcessingFactsReader implements ProcessingFactsReader {
    private static final DateTimeFormatter UTC_MICROS = new DateTimeFormatterBuilder()
            .appendPattern("uuuu-MM-dd'T'HH:mm:ss").appendFraction(ChronoField.NANO_OF_SECOND,6,6,true)
            .appendLiteral('Z').toFormatter(Locale.ROOT);
    private static final String TASK_COLUMNS = "id,task_type,pool,subject_kind,subject_id,producer_key,"
            + "input_kind,input_id,input_fingerprint,business_version,priority,cancel_epoch,max_attempts";
    private final JdbcTemplate jdbc;
    private final TransactionRunner transactions;
    private final MeetingAccess access;

    public JdbcProcessingFactsReader(DataSource source) {
        Objects.requireNonNull(source);
        jdbc = new JdbcTemplate(source);
        jdbc.setQueryTimeout(5);
        transactions = new TransactionRunner(source);
        access = new JdbcMeetingAccess(source,transactions,new JdbcSubjectFences(source,Set.of("MEETING")));
    }

    JdbcProcessingFactsReader(JdbcTranscriptAuthority authority) {
        var source=authority.dataSource();jdbc=new JdbcTemplate(source);jdbc.setQueryTimeout(2);
        transactions=authority.transactionRunner();access=authority.meetingAccess();
    }

    @Override public StoredProvenanceFacts readBatch(Actor actor,String meetingId,String batchId,
                                                    ArchiveReadBudget budget,BooleanSupplier cancelled) {
        return read(actor,meetingId,batchId,false,budget,cancelled,null);
    }
    @Override public StoredProvenanceFacts readStored(Actor actor,String meetingId,String inputId,
                                                     ArchiveReadBudget budget,BooleanSupplier cancelled) {
        return read(actor,meetingId,inputId,true,budget,cancelled,null);
    }
    StoredProvenanceFacts readWorker(Actor actor,TaskLease lease,ArchiveReadBudget budget,BooleanSupplier cancelled){
        return read(actor,lease.spec().subject().id(),lease.spec().input().id(),true,budget,cancelled,lease);
    }
    private StoredProvenanceFacts read(Actor actor,String meetingId,String id,boolean stored,
                                       ArchiveReadBudget budget,BooleanSupplier cancelled,TaskLease lease) {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Processing facts require a standalone transaction");
        require(actor!=null&&budget!=null&&cancelled!=null,INVALID_ARGUMENT);
        uuid(meetingId); uuid(id);
        var state = new Acquisition(budget,cancelled);
        try {
            var result = transactions.required(() -> {
                // The existing access adapter reads display_name LONGTEXT. Bound that exact,
                // already-held identity first, without changing shared access behavior.
                jdbc.query("SELECT OCTET_LENGTH(display_name),CHAR_LENGTH(display_name),OCTET_LENGTH(CONVERT(display_name USING utf16)) DIV 2 FROM enterprise_identity WHERE enterprise_id=? FOR UPDATE",
                        rs -> {state.check();if(rs.next())state.textSize(rs.getLong(1),rs.getLong(2),rs.getLong(3));return null;},actor.enterpriseId());
                var held = access.lock(actor,meetingId);
                access.require(held,MeetingAccess.Action.EDIT);
                state.check();
                var rows = new ArrayList<FullRow>();
                FullRow input = null;
                String batchId = id;
                ManifestSize manifestSize = null;
                if (stored) {
                    manifestSize = manifestSize(id,meetingId,state);
                    var inputs = rows(Table.TRANSCRIPTION_INPUT,"id=? AND meeting_id=?","id",state,manifestSize,id,meetingId);
                    require(inputs.size()==1,PROVENANCE_UNAVAILABLE);
                    input = inputs.getFirst(); batchId = input.string("batch_id");rows.add(input);
                }
                var batches = stored
                        ? rows(Table.INPUT_BATCH,"id=?","id",state,null,batchId)
                        : rows(Table.INPUT_BATCH,"id=? AND meeting_id=?","id",state,null,batchId,meetingId);
                require(batches.size()==1,PROVENANCE_UNAVAILABLE);rows.addAll(batches);
                var members = rows(Table.INPUT_BATCH_FILE,"batch_id=?","ordinal",state,null,batchId);
                rows.addAll(members);
                var revisions = stored
                        ? rows(Table.TRANSCRIPT_REVISION,"processing_input_id=?","id",state,null,id)
                        : List.<FullRow>of();
                rows.addAll(revisions);
                // Discover the complete domain set before file locks; all file locks precede tasks.
                var fileIds = new TreeSet<String>();
                for (var member:members) {state.check();fileIds.add(member.string("file_id"));}
                var files = files(fileIds,state);
                var producers = new TreeSet<String>();
                for (var revision:revisions) {
                    state.check();String producer=revision.nullableString("producer_task_id");
                    if(producer!=null)producers.add(producer);
                }
                var tasks = stored ? tasks(id,producers,state) : List.<StoredTask>of();
                if(lease!=null){
                    require(tasks.size()==1&&tasks.getFirst().taskId().equals(lease.taskId())&&tasks.getFirst().spec().equals(lease.spec()),PROVENANCE_UNAVAILABLE);
                    require(jdbc.queryForObject("SELECT (state='RUNNING' AND attempt=? AND lease_owner=? AND expires_at>UTC_TIMESTAMP(6)) FROM durable_task WHERE id=? FOR UPDATE",Long.class,lease.attempt(),lease.leaseOwner(),lease.taskId())==1,PROVENANCE_UNAVAILABLE);
                    var attempts=jdbc.queryForList("SELECT owner,ended_at FROM task_attempt WHERE task_id=? AND attempt=? FOR UPDATE",lease.taskId(),lease.attempt());
                    require(attempts.size()==1&&lease.leaseOwner().equals(attempts.getFirst().get("owner"))&&attempts.getFirst().get("ended_at")==null,PROVENANCE_UNAVAILABLE);
                    require(jdbc.queryForObject("SELECT expires_at>UTC_TIMESTAMP(6) FROM durable_task WHERE id=?",Long.class,lease.taskId())==1,PROVENANCE_UNAVAILABLE);
                    require(jdbc.queryForObject("SELECT (owner_id=? AND revision=? AND cancel_epoch=? AND ever_published=0 AND stage<>'PUBLISHED' AND lifecycle='ACTIVE') FROM meeting WHERE id=?",Long.class,actor.enterpriseId(),lease.spec().businessVersion(),lease.spec().cancelEpoch(),lease.spec().subject().id())==1,PROVENANCE_UNAVAILABLE);
                }
                state.check();
                // count was reserved before every DTO, row-list growth and final owned copy.
                var facts = new StoredProvenanceFacts(rows,files,List.of(),List.of(),List.of(),List.of(),tasks,budget.maxFactRows());
                state.check();return facts;
            });
            state.check();return result;
        } catch (DataAccessException failure) {
            state.check();throw fail(READ_FAILED);
        }
    }

    private record ManifestSize(long bytes,long characters,long utf16Units) {}
    private ManifestSize manifestSize(String id,String meeting,Acquisition state) {
        return jdbc.query("SELECT OCTET_LENGTH(config_manifest),CHAR_LENGTH(config_manifest),OCTET_LENGTH(CONVERT(config_manifest USING utf16)) DIV 2 FROM transcription_input WHERE id=? AND meeting_id=? LIMIT 1 FOR UPDATE",rs -> {
            state.check();require(rs.next(),PROVENANCE_UNAVAILABLE);
            long bytes=rs.getLong(1),characters=rs.getLong(2),utf16Units=rs.getLong(3);
            state.manifestSize(bytes,characters,utf16Units);
            return new ManifestSize(bytes,characters,utf16Units);
        },id,meeting);
    }
    private List<FullRow> rows(Table table,String predicate,String order,Acquisition state,
                               ManifestSize manifest,Object... arguments) {
        String projection=String.join(",",table.columns().stream().map(ColumnSpec::name).toList());
        long limit=state.remaining();
        if(table==Table.INPUT_BATCH_FILE)limit=Math.min(limit,(long)state.budget.maxBatchFiles()-state.members);
        Object[] parameters=Arrays.copyOf(arguments,arguments.length+1);parameters[arguments.length]=oneBeyond(limit);
        return jdbc.query("SELECT "+projection+" FROM "+table.sqlName()+" WHERE "+predicate+" ORDER BY "+order+" LIMIT ? FOR UPDATE",rs -> {
            var result=new ArrayList<FullRow>();
            while(rs.next()) {
                state.reserve(table.columns().stream().filter(c->c.kind()==CellKind.UUID).count());
                if(table==Table.INPUT_BATCH_FILE){require(state.members<state.budget.maxBatchFiles(),BUDGET_EXCEEDED);state.members++;}
                var cells=new ArrayList<Cell>(table.columns().size());
                for(var column:table.columns()) {
                    state.check();String name=column.name();
                    if(name.equals("config_manifest"))cells.add(cell(manifest(rs,manifest,state)));
                    else if(column.kind()==CellKind.UINT) {
                        var number=rs.getBigDecimal(name);
                        if(number==null)cells.add(new NullCell());
                        else {try{cells.add(cell(number.longValueExact()));}catch(ArithmeticException failure){throw fail(INPUT_INVALID);}}
                    } else if(column.kind()==CellKind.TIMESTAMP) {
                        var timestamp=rs.getObject(name,LocalDateTime.class);
                        cells.add(timestamp==null?new NullCell():cell(state.text(UTC_MICROS.format(timestamp))));
                    } else {
                        String value=rs.getString(name);cells.add(value==null?new NullCell():cell(state.text(value)));
                    }
                }
                result.add(new FullRow(table,cells));
            }
            state.check();return result;
        },parameters);
    }
    private String manifest(ResultSet rs,ManifestSize expected,Acquisition state) throws SQLException {
        require(expected!=null,READ_FAILED);
        // Metadata was measured under the same retained row lock before this SELECT transferred
        // any LONGTEXT. Never getString(config_manifest), even after that bounded gate.
        try(var reader=rs.getCharacterStream("config_manifest")) {
            require(reader!=null,READ_FAILED);
            var text=new StringBuilder((int)Math.min(expected.utf16Units(),1024));
            char[] chunk=new char[(int)Math.min(state.budget.maxStringUtf16(),1024)];
            for(int count;(count=reader.read(chunk))!=-1;) {
                state.check();require(count<=state.budget.maxStringUtf16()-text.length(),BUDGET_EXCEEDED);
                text.append(chunk,0,count);
            }
            state.check();String result=text.toString();scalars(result);
            require(result.length()==expected.utf16Units()&&result.codePointCount(0,result.length())==expected.characters()&&utf8Length(result)==expected.bytes(),READ_FAILED);
            return result;
        } catch(IOException failure) {state.check();throw fail(READ_FAILED);}
    }
    private List<ContentFile> files(SortedSet<String> ids,Acquisition state) {
        if(ids.isEmpty())return List.of();
        var parameters=new ArrayList<Object>(ids);parameters.add(oneBeyond(state.remaining()));
        var result=jdbc.query("SELECT id,meeting_id,sha256,size_bytes FROM file_object WHERE id IN ("+placeholders(ids.size())+") ORDER BY id LIMIT ? FOR UPDATE",rs -> {
            var values=new ArrayList<ContentFile>();
            while(rs.next()) {
                state.reserve(2);
                String id=state.text(rs.getString("id")),meeting=rs.getString("meeting_id"),sha=state.text(rs.getString("sha256"));
                if(meeting!=null)meeting=state.text(meeting);
                long size;try{size=rs.getBigDecimal("size_bytes").longValueExact();}catch(ArithmeticException failure){throw fail(INPUT_INVALID);}
                values.add(new ContentFile(id,sha,size,meeting));
            }
            state.check();return values;
        },parameters.toArray());
        require(result.size()==ids.size(),PROVENANCE_UNAVAILABLE);return result;
    }
    private List<StoredTask> tasks(String input,SortedSet<String> producers,Acquisition state) {
        var parameters=new ArrayList<Object>();parameters.add(input);parameters.addAll(producers);parameters.add(oneBeyond(state.remaining()));
        String producerPredicate=producers.isEmpty()?"":" OR id IN ("+placeholders(producers.size())+")";
        return jdbc.query("SELECT "+TASK_COLUMNS+" FROM durable_task WHERE (input_kind='TRANSCRIPTION_INPUT' AND input_id=?)"+producerPredicate+" ORDER BY id LIMIT ? FOR UPDATE",rs -> {
            var result=new ArrayList<StoredTask>();
            while(rs.next()) {
                state.reserve(3);
                try {
                    var spec=new TaskSpec(state.text(rs.getString("task_type")),state.text(rs.getString("pool")),
                            new SubjectRef(state.text(rs.getString("subject_kind")),state.text(rs.getString("subject_id"))),
                            state.text(rs.getString("producer_key")),new TypedRef(state.text(rs.getString("input_kind")),state.text(rs.getString("input_id"))),
                            state.text(rs.getString("input_fingerprint")),rs.getBigDecimal("business_version").longValueExact(),rs.getInt("priority"),
                            rs.getBigDecimal("cancel_epoch").longValueExact(),rs.getBigDecimal("max_attempts").intValueExact());
                    result.add(new StoredTask(state.text(rs.getString("id")),spec));
                } catch(IllegalArgumentException|ArithmeticException failure) {throw fail(INPUT_INVALID);}
            }
            state.check();return result;
        },parameters.toArray());
    }
    /** Saturation preserves a valid MySQL LIMIT when a caller explicitly grants Long.MAX_VALUE. */
    private static long oneBeyond(long remaining){return remaining==Long.MAX_VALUE?Long.MAX_VALUE:remaining+1;}
    private static String placeholders(int count){return String.join(",",Collections.nCopies(count,"?"));}
    private static long utf8Length(String value) {
        long bytes=0;for(int i=0;i<value.length();) {int point=value.codePointAt(i);i+=Character.charCount(point);bytes+=point<128?1:point<2048?2:point<65536?3:4;}return bytes;
    }
    private static final class Acquisition {
        final ArchiveReadBudget budget;final BooleanSupplier cancelled;long count,edges,scalars;int members;
        Acquisition(ArchiveReadBudget budget,BooleanSupplier cancelled) {
            this.budget=budget;this.cancelled=cancelled;
            // A large legal budget is not a capacity request; lists grow only for actual rows.
            check();
        }
        void check(){ama.contract.MinutesContracts.check(cancelled);}
        long remaining(){check();return budget.maxFactRows()-count;}
        void reserve(long edgeCount){check();require(count<budget.maxFactRows()&&count<Integer.MAX_VALUE-8L&&edgeCount<=budget.maxFactEdges()-edges,BUDGET_EXCEEDED);count++;edges+=edgeCount;}
        // MySQL CHAR_LENGTH is a scalar count. A supplementary scalar occupies two UTF16
        // units, measured exactly by locked OCTET_LENGTH(CONVERT(value USING utf16)) DIV 2.
        void textSize(long bytes,long characters,long utf16Units){check();require(bytes>=0&&characters>=0&&utf16Units>=0&&bytes<=budget.maxStringUtf8Bytes()&&utf16Units<=budget.maxStringUtf16()&&characters<=budget.maxAggregateScalars()-scalars,BUDGET_EXCEEDED);scalars+=characters;}
        void manifestSize(long bytes,long characters,long utf16Units){require(bytes<=budget.maxManifestBytes()&&bytes<=budget.maxTotalBytes(),BUDGET_EXCEEDED);textSize(bytes,characters,utf16Units);}
        String text(String value){require(value!=null,INPUT_INVALID);scalars(value);textSize(utf8Length(value),value.codePointCount(0,value.length()),value.length());return value;}
    }
}
