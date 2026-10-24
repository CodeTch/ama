package ama.contract;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Detached pure facts only; none of these types authenticates storage, permission or human intent. */
public final class MinutesContracts {
    private MinutesContracts() {}
    public enum FailureCode {
        INVALID_ARGUMENT, INPUT_INVALID, INVALID_UTF8, INVALID_JSON, UNKNOWN_FIELD, INVALID_SCHEMA,
        INVALID_SCALAR, INVALID_ID, INVALID_NUMBER, INVALID_DATE, BUDGET_EXCEEDED, SOURCE_NOT_IN_INPUT,
        LOCATOR_INVALID, TARGET_INVALID, FINGERPRINT_MISMATCH, CAPABILITY_UNAVAILABLE, PROFILE_UNAVAILABLE,
        CANCELLED, READ_FAILED, PROVENANCE_UNAVAILABLE, SOURCE_CONTEXT_AMBIGUOUS, SOURCE_MEMBERSHIP_MISMATCH
    }
    /** Deliberately contains no raw input, cause, suppressed exception or writable stack trace. */
    public static final class Failure extends RuntimeException {
        private final FailureCode code;
        public Failure(FailureCode code) { super(Objects.requireNonNull(code).name(), null, false, false); this.code=code; }
        public FailureCode code() { return code; }
    }
    public static Failure fail(FailureCode code) { return new Failure(code); }
    public static void require(boolean condition, FailureCode code) { if (!condition) throw fail(code); }
    public static void check(BooleanSupplier cancelled) {
        require(cancelled!=null, FailureCode.INVALID_ARGUMENT);
        if(Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) throw fail(FailureCode.CANCELLED);
    }
    public static String uuid(String v) {
        require(v!=null && v.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
            && !v.equals("00000000-0000-0000-0000-000000000000"), FailureCode.INVALID_ID); return v;
    }
    public static String hash(String v) { require(v!=null && v.matches("[0-9a-f]{64}"),FailureCode.INVALID_ID);return v; }
    public static long uint(long v) { require(v>=0,FailureCode.INVALID_NUMBER);return v; }
    public static long positive(long v) { require(v>0,FailureCode.INVALID_NUMBER);return v; }
    public static long aliasOrdinal(String v) {
        require(v!=null && v.matches("s[1-9][0-9]{0,18}"),FailureCode.INVALID_ID);
        try { return positive(Long.parseLong(v.substring(1))); } catch(NumberFormatException e) { throw fail(FailureCode.INVALID_ID); }
    }
    public static String localKey(String v) {require(v!=null && v.matches("[A-Za-z][A-Za-z0-9_-]{0,63}"),FailureCode.INVALID_ID);return v;}
    public static String scalars(String v) {
        require(v!=null,FailureCode.INVALID_SCALAR);
        for(int i=0;i<v.length();i++) {
            char c=v.charAt(i);
            if(Character.isHighSurrogate(c)) { require(i+1<v.length() && Character.isLowSurrogate(v.charAt(++i)),FailureCode.INVALID_SCALAR); }
            else require(!Character.isLowSurrogate(c),FailureCode.INVALID_SCALAR);
        } return v;
    }
    public static String opaque(String value){scalars(value);require(!value.isEmpty(),FailureCode.INPUT_INVALID);return value;}
    public static boolean whiteSpace(int c) { return c>=9&&c<=13 || c==32 || c==0x85 || c==0xa0 || c==0x1680 || c>=0x2000&&c<=0x200a || c==0x2028 || c==0x2029 || c==0x202f || c==0x205f || c==0x3000; }
    public static String text(String v,boolean nonblank) {
        scalars(v); boolean content=false;
        for(int i=0;i<v.length();) { int c=v.codePointAt(i);i+=Character.charCount(c);
            require(!(c<32 && c!=9 && c!=10) && c!=127,FailureCode.INVALID_SCALAR);content|=!whiteSpace(c); }
        require(!nonblank || content,FailureCode.INVALID_SCHEMA);return v;
    }
    public static String date(String v) {
        require(v!=null && v.matches("[1-9][0-9]{3}-[0-9]{2}-[0-9]{2}"),FailureCode.INVALID_DATE);
        try { LocalDate.parse(v,DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)); }
        catch(java.time.DateTimeException e) { throw fail(FailureCode.INVALID_DATE); } return v;
    }
    public record CanonicalBudget(long maxEncodedBytes,int maxDepth,long maxNodes,long maxStringBytes,long maxContainerEntries) {
        public CanonicalBudget { require(maxEncodedBytes>0&&maxDepth>0&&maxNodes>0&&maxStringBytes>0&&maxContainerEntries>0,FailureCode.INVALID_ARGUMENT); }
    }
    public record ValidationBudget(long maxWireBytes,int maxDepth,long maxTokens,int maxNameUtf16,int maxStringUtf16,
            long maxStringUtf8Bytes,long maxStringScalars,long maxAggregateScalars,long maxElements,long maxDetections,
            long maxNotes,long maxRefs,long maxEvidence,long maxMemberships) {
        public ValidationBudget {require(maxWireBytes>0&&maxWireBytes<Long.MAX_VALUE&&maxDepth>0&&maxTokens>0&&maxNameUtf16>0&&maxStringUtf16>0
            &&maxStringUtf8Bytes>0&&maxStringScalars>0&&maxAggregateScalars>0&&maxElements>0&&maxDetections>0&&maxNotes>0&&maxRefs>0&&maxEvidence>0&&maxMemberships>0,FailureCode.INVALID_ARGUMENT);}
    }

    public enum Category { OVERVIEW,DISCUSSION,CONCLUSION,ACTION,RISK,OPEN_QUESTION,DISAGREEMENT }
    public enum SensitiveKind { INTERNAL_BUSINESS_DATA,POLITICAL_SENSITIVE }
    public enum Role { MEETING,BACKGROUND }
    public enum Support { PARTIAL,UNSUPPORTED }
    public enum Scope { FULL,ELEMENT }
    public record SourceIds(List<String> values) {
        public SourceIds {
            require(values!=null&&!values.isEmpty(),FailureCode.INVALID_SCHEMA);var seen=new HashSet<String>();
            for(var v:values){aliasOrdinal(v);require(seen.add(v),FailureCode.INVALID_SCHEMA);}
            var copy=new ArrayList<>(values);copy.sort(Comparator.comparingLong(MinutesContracts::aliasOrdinal));values=List.copyOf(copy);
        }
    }
    public sealed interface Locator permits TranscriptLocator,AudioLocator,PageLocator,ParagraphLocator,KnowledgeLocator {}
    public record TranscriptLocator(String transcriptRevisionId,String segmentId) implements Locator {
        public TranscriptLocator {uuid(transcriptRevisionId);uuid(segmentId);}
    }
    public record AudioLocator(String fileId,long startMs,long endMs) implements Locator {
        public AudioLocator {uuid(fileId);uint(startMs);require(startMs<endMs,FailureCode.LOCATOR_INVALID);}
    }
    public enum PageKind { PDF_PAGE,PPT_PAGE }
    public record PageLocator(PageKind kind,String fileId,long pageNo) implements Locator {
        public PageLocator {require(kind!=null,FailureCode.INVALID_ARGUMENT);uuid(fileId);positive(pageNo);}
    }
    public enum ParagraphKind { WORD_PARAGRAPH,TEXT_PARAGRAPH }
    public record ParagraphLocator(ParagraphKind kind,String fileId,long paragraphNo,String sectionPath) implements Locator {
        public ParagraphLocator {require(kind!=null,FailureCode.INVALID_ARGUMENT);uuid(fileId);positive(paragraphNo);if(sectionPath!=null)scalars(sectionPath);}
    }
    public record KnowledgeLocator(String materialRevisionId) implements Locator {public KnowledgeLocator {uuid(materialRevisionId);}}
    public sealed interface SourceVersion permits TranscriptVersion,FileVersion,KnowledgeVersion {}
    public record TranscriptVersion(String confirmationId,String revisionId,String batchId,String sourceFileId,String sourceFileSha256) implements SourceVersion {
        public TranscriptVersion {uuid(confirmationId);uuid(revisionId);uuid(batchId);uuid(sourceFileId);hash(sourceFileSha256);}
    }
    public record FileVersion(String fileId,String materialRevisionId,String parsedDocumentId,String parserVersion,String parsedTextSha256) implements SourceVersion {
        public FileVersion {
            uuid(fileId);if(materialRevisionId!=null)uuid(materialRevisionId);
            require((materialRevisionId==null)==(parsedDocumentId==null),FailureCode.INPUT_INVALID);
            require((parsedDocumentId==null)==(parserVersion==null)&&(parserVersion==null)==(parsedTextSha256==null),FailureCode.INPUT_INVALID);
            if(parsedDocumentId!=null){uuid(parsedDocumentId);opaque(parserVersion);hash(parsedTextSha256);}
        }
    }
    public record KnowledgeVersion(String materialRevisionId,String providerId,String externalId,String externalVersion,String contentFileId,
        String parsedDocumentId,String parserVersion,String parsedTextSha256) implements SourceVersion {
        public KnowledgeVersion {uuid(materialRevisionId);opaque(providerId);opaque(externalId);opaque(externalVersion);uuid(contentFileId);uuid(parsedDocumentId);opaque(parserVersion);hash(parsedTextSha256);}
    }
    public sealed interface Bounds permits TranscriptBounds,AudioBounds,DocumentBounds,KnowledgeBounds {}
    public record TranscriptBounds(String revisionId,String segmentId,String sourceFileId,Long sourceParagraph) implements Bounds {
        public TranscriptBounds {uuid(revisionId);uuid(segmentId);uuid(sourceFileId);if(sourceParagraph!=null)positive(sourceParagraph);}
    }
    public record AudioBounds(String fileId,long durationMs) implements Bounds {public AudioBounds {uuid(fileId);positive(durationMs);}}
    public record SourceUnit(long ordinal,Long pageNo,Long paragraphNo,String sectionPath) {
        public SourceUnit {positive(ordinal);if(pageNo!=null)positive(pageNo);if(paragraphNo!=null)positive(paragraphNo);if(sectionPath!=null)scalars(sectionPath);}
    }
    public static final class DocumentBounds implements Bounds {
        private final String fileId,materialRevisionId,parsedDocumentId,parserVersion,parsedTextSha256;private final List<SourceUnit> units;
        public DocumentBounds(String fileId,String materialRevisionId,String parsedDocumentId,String parserVersion,String parsedTextSha256,List<SourceUnit> units,long maxUnits){
            uuid(fileId);uuid(materialRevisionId);uuid(parsedDocumentId);opaque(parserVersion);hash(parsedTextSha256);require(maxUnits>0&&units!=null,FailureCode.INVALID_ARGUMENT);
            require(units.size()<=maxUnits,FailureCode.BUDGET_EXCEEDED);this.units=new FactCapture(maxUnits).copy(units);this.fileId=fileId;this.materialRevisionId=materialRevisionId;this.parsedDocumentId=parsedDocumentId;this.parserVersion=parserVersion;this.parsedTextSha256=parsedTextSha256;
        }
        public String fileId(){return fileId;}public String materialRevisionId(){return materialRevisionId;}public String parsedDocumentId(){return parsedDocumentId;}public String parserVersion(){return parserVersion;}public String parsedTextSha256(){return parsedTextSha256;}public List<SourceUnit> units(){return units;}
        @Override public boolean equals(Object o){return o instanceof DocumentBounds d&&fileId.equals(d.fileId)&&materialRevisionId.equals(d.materialRevisionId)&&parsedDocumentId.equals(d.parsedDocumentId)&&parserVersion.equals(d.parserVersion)&&parsedTextSha256.equals(d.parsedTextSha256)&&units.equals(d.units);}
        @Override public int hashCode(){return Objects.hash(fileId,materialRevisionId,parsedDocumentId,parserVersion,parsedTextSha256,units);}
    }
    public record KnowledgeBounds(String materialRevisionId,String providerId,String externalId,String externalVersion,String contentFileId,String sha256) implements Bounds {
        public KnowledgeBounds {uuid(materialRevisionId);opaque(providerId);opaque(externalId);opaque(externalVersion);uuid(contentFileId);hash(sha256);}
    }
    public record CatalogFact(Locator locator,Role role,SourceVersion sourceVersion,String sourceContentHash,long transcriptOrdinal,Bounds bounds) {
        public CatalogFact {require(locator!=null&&role!=null&&sourceVersion!=null&&bounds!=null,FailureCode.INVALID_ARGUMENT);hash(sourceContentHash);uint(transcriptOrdinal);}
    }
    public static final class CatalogFacts {
        private final List<CatalogFact> entries;
        public CatalogFacts(List<CatalogFact> entries,long maxEntries){require(entries!=null&&maxEntries>0,FailureCode.INVALID_ARGUMENT);require(entries.size()<=maxEntries,FailureCode.BUDGET_EXCEEDED);this.entries=new FactCapture(maxEntries).copy(entries);}
        public List<CatalogFact> entries(){return entries;}
    }
    public record CatalogSource(String sourceId,CatalogFact fact) {public CatalogSource {aliasOrdinal(sourceId);require(fact!=null,FailureCode.INVALID_ARGUMENT);}}
    public record Catalog(List<CatalogSource> sources) {
        public Catalog {sources=List.copyOf(sources);}
        public CatalogSource source(String alias) {
            long ordinal=aliasOrdinal(alias);require(ordinal<=sources.size(),FailureCode.SOURCE_NOT_IN_INPUT);
            var source=sources.get((int)ordinal-1);require(source.sourceId().equals(alias),FailureCode.SOURCE_NOT_IN_INPUT);return source;
        }
    }
    public sealed interface Target permits ElementTarget,BodyTarget {long start();long end();}
    public record ElementTarget(String elementKey,long start,long end) implements Target {
        public ElementTarget {localKey(elementKey);uint(start);require(start<end,FailureCode.TARGET_INVALID);}
    }
    public record BodyTarget(long start,long end) implements Target {public BodyTarget {uint(start);require(start<end,FailureCode.TARGET_INVALID);}}
    public record Action(String ownerMention,String dueOn) {public Action {if(ownerMention!=null)text(ownerMention,true);if(dueOn!=null)date(dueOn);}}
    public record Element(String key,String text,Action action) {public Element {localKey(key);MinutesContracts.text(text,true);}}
    public record Detection(String key,SensitiveKind category,SourceIds sourceIds) {public Detection {localKey(key);require(category!=null&&sourceIds!=null,FailureCode.INVALID_SCHEMA);}}
    public record Note(String key,String text,SourceIds sourceIds) {public Note {localKey(key);MinutesContracts.text(text,true);require(sourceIds!=null,FailureCode.INVALID_SCHEMA);}}
    public record Ref(String sourceId,Locator locator) {public Ref {aliasOrdinal(sourceId);require(locator!=null,FailureCode.INVALID_SCHEMA);}}
    public record Evidence(Target target,SourceIds sourceIds) {public Evidence {require(target!=null&&sourceIds!=null,FailureCode.INVALID_SCHEMA);}}
    public record Output(String schemaVersion,String inputFingerprint,String title,String body,Map<Category,List<Element>> elements,
        List<Detection> sensitive,List<Note> background,List<Ref> sourceRefs,List<Evidence> evidence) {
        public Output {
            require("ama.minutes-output.v1".equals(schemaVersion),FailureCode.INVALID_SCHEMA);hash(inputFingerprint);text(title,false);text(body,true);
            require(elements!=null&&elements.keySet().equals(EnumSet.allOf(Category.class)),FailureCode.INVALID_SCHEMA);
            var copy=new EnumMap<Category,List<Element>>(Category.class);
            for(var c:Category.values()){var list=List.copyOf(elements.get(c));for(var e:list)require((c==Category.ACTION)==(e.action()!=null),FailureCode.INVALID_SCHEMA);copy.put(c,list);}
            elements=Collections.unmodifiableMap(copy);sensitive=List.copyOf(sensitive);background=List.copyOf(background);sourceRefs=List.copyOf(sourceRefs);evidence=List.copyOf(evidence);
        }
    }
    public record Anchor(long start,long end,String targetTextSha256) {public Anchor {uint(start);require(start<end,FailureCode.TARGET_INVALID);hash(targetTextSha256);}}
    public record AnchoredEvidence(Evidence evidence,Anchor anchor) {public AnchoredEvidence {require(evidence!=null&&anchor!=null,FailureCode.INVALID_ARGUMENT);}}
    public record ValidatedOutput(Output output,List<AnchoredEvidence> anchors) {public ValidatedOutput {require(output!=null,FailureCode.INVALID_ARGUMENT);anchors=List.copyOf(anchors);}}
    public record ValidationContext(String inputFingerprint,String taskFingerprint,Scope scope,Catalog catalog) {
        public ValidationContext {hash(inputFingerprint);hash(taskFingerprint);require(scope!=null&&catalog!=null,FailureCode.INVALID_ARGUMENT);}
    }
    public record AcceptedAction(String ownerEnterpriseId,String ownerDisplay,String dueOn) {}
    public record ElementSupport(String key,Support support,AcceptedAction acceptedAction) {}
    public record EvidenceSupport(Target target,String sourceId,Support support) {}
    public record CreationClassification(List<ElementSupport> elements,List<EvidenceSupport> evidence) {
        public CreationClassification {elements=List.copyOf(elements);evidence=List.copyOf(evidence);}
    }

    public enum CellKind { UINT,TEXT,UUID,HASH,DATE,TIMESTAMP }
    public record ColumnSpec(String name,CellKind kind,boolean nullable) {}
    /** Fixed adopted B00-1.3 projections. Mutable material status exceptions are explicit here. */
    public enum Table {
        INPUT_BATCH("input_batch","id:UUID meeting_id:UUID kind:TEXT input_fingerprint:HASH base_revision:UINT created_at:TIMESTAMP","id","kind=TRANSCRIPT,MEDIA"),
        INPUT_BATCH_FILE("input_batch_file","batch_id:UUID ordinal:UINT file_id:UUID sha256:HASH","batch_id,ordinal",""),
        TRANSCRIPT_REVISION("transcript_revision","id:UUID meeting_id:UUID revision:UINT kind:TEXT batch_id:UUID parent_id:UUID? input_fingerprint:HASH created_by:TEXT created_at:TIMESTAMP processing_input_id:UUID producer_task_id:UUID?","id","kind=RAW,ENHANCED,WORKING,CANDIDATE"),
        TRANSCRIPT_SEGMENT("transcript_segment","revision_id:UUID segment_id:UUID ordinal:UINT body:TEXT source_file_id:UUID source_paragraph:UINT? start_ms:UINT? end_ms:UINT? speaker_id:UUID?","revision_id,segment_id",""),
        TRANSCRIPT_CONFIRMATION("transcript_confirmation","id:UUID meeting_id:UUID revision_id:UUID content_hash:HASH confirmed_by:TEXT confirmed_at:TIMESTAMP","id",""),
        TRANSCRIPT_SEGMENT_MARK("transcript_segment_mark","revision_id:UUID segment_id:UUID mark:TEXT","revision_id,segment_id,mark","mark=KEY,CONCLUSION,DISPUTE,GENERATION_FOCUS"),
        TRANSCRIPTION_INPUT("transcription_input","id:UUID meeting_id:UUID batch_id:UUID kind:TEXT source_revision_id:UUID? material_snapshot_id:UUID? material_mode:TEXT processing_config_version:TEXT parser_version:TEXT? model_config_version:TEXT? prompt_version:TEXT? manifest_schema_version:UINT config_manifest:TEXT input_fingerprint:HASH base_revision:UINT cancel_epoch:UINT created_at:TIMESTAMP","id","kind=IMPORT,ASR,ENHANCE;material_mode=EMPTY,PINNED"),
        TEMPLATE_REVISION("template_revision","id:UUID template_id:UUID revision:UINT owner_id:TEXT? name:TEXT themes:TEXT theme_count:UINT created_at:TIMESTAMP","id",""),
        GENERATION_INPUT("generation_input","id:UUID meeting_id:UUID transcript_confirmation_id:UUID material_snapshot_id:UUID? template_revision_id:UUID exclusion_revision_id:UUID element_set_id:UUID? prompt_version:TEXT model_config_version:TEXT base_revision:UINT input_fingerprint:HASH scope:TEXT target_element_id:UUID?","id","scope=FULL,ELEMENT"),
        MINUTES_REVISION("minutes_revision","id:UUID meeting_id:UUID revision:UINT parent_id:UUID? title:TEXT title_origin:TEXT body:TEXT generation_input_id:UUID? created_by:TEXT created_at:TIMESTAMP","id","title_origin=HUMAN,LINKED,AI"),
        ELEMENT_SET("element_set","id:UUID meeting_id:UUID revision:UINT created_at:TIMESTAMP","id",""),
        MINUTES_ELEMENT("minutes_element","id:UUID set_id:UUID category:TEXT ordinal:UINT body:TEXT support_state:TEXT","id","category=OVERVIEW,DISCUSSION,CONCLUSION,ACTION,RISK,OPEN_QUESTION,DISAGREEMENT;support_state=SUPPORTED,PARTIAL,UNSUPPORTED,NOT_EXTRACTED"),
        ACTION_ITEM("action_item","element_id:UUID owner_enterprise_id:TEXT? owner_display:TEXT? due_on:DATE?","element_id",""),
        EXCLUSION_REVISION("exclusion_revision","id:UUID meeting_id:UUID revision:UINT created_at:TIMESTAMP","id",""),
        SENSITIVE_EXCLUSION("sensitive_exclusion","revision_id:UUID locator_id:UUID category:TEXT reason:TEXT?","revision_id,locator_id,category","category=INTERNAL_BUSINESS_DATA,POLITICAL_SENSITIVE"),
        SOURCE_LOCATOR("source_locator","id:UUID meeting_id:UUID kind:TEXT transcript_revision_id:UUID? segment_id:UUID? file_id:UUID? material_revision_id:UUID? page_no:UINT? paragraph_no:UINT? section_path:TEXT? start_ms:UINT? end_ms:UINT?","id","kind=TRANSCRIPT,AUDIO,PDF_PAGE,PPT_PAGE,WORD_PARAGRAPH,TEXT_PARAGRAPH,KNOWLEDGE_VERSION"),
        EVIDENCE_LINK("evidence_link","id:UUID locator_id:UUID element_id:UUID? minutes_revision_id:UUID? support_state:TEXT target_anchor:TEXT","id","support_state=SUPPORTED,PARTIAL,UNSUPPORTED"),
        FINAL_CONFIRMATION("final_confirmation","id:UUID meeting_id:UUID meeting_revision:UINT info_revision_id:UUID roster_revision_id:UUID transcript_confirmation_id:UUID minutes_revision_id:UUID material_snapshot_id:UUID? element_set_id:UUID template_revision_id:UUID exclusion_revision_id:UUID source_manifest_hash:HASH input_fingerprint:HASH confirmed_by:TEXT confirmed_at:TIMESTAMP","id",""),
        MATERIAL_REFERENCE("material_reference","id:UUID meeting_id:UUID kind:TEXT provider_id:TEXT? external_id:TEXT? file_id:UUID?","id","kind=LOCAL,KNOWLEDGE"),
        MATERIAL_REVISION("material_revision","id:UUID reference_id:UUID revision:UINT external_version:TEXT? content_file_id:UUID? content_hash:HASH created_at:TIMESTAMP","id",""),
        MATERIAL_SNAPSHOT("material_snapshot","id:UUID meeting_id:UUID revision:UINT input_fingerprint:HASH applied_at:TIMESTAMP","id",""),
        MATERIAL_SNAPSHOT_ITEM("material_snapshot_item","snapshot_id:UUID material_revision_id:UUID ordinal:UINT","snapshot_id,material_revision_id",""),
        PARSED_DOCUMENT("parsed_document","id:UUID material_revision_id:UUID parser_version:TEXT text_file_id:UUID? unit_count:UINT","id",""),
        PARSED_UNIT("parsed_unit","document_id:UUID ordinal:UINT kind:TEXT page_no:UINT? paragraph_no:UINT? section_path:TEXT? body:TEXT","document_id,ordinal","kind=PAGE,PARAGRAPH,SECTION"),
        SENSITIVE_DETECTION("sensitive_detection","id:UUID set_id:UUID input_id:UUID category:TEXT ordinal:UINT","id","category=INTERNAL_BUSINESS_DATA,POLITICAL_SENSITIVE"),
        SENSITIVE_DETECTION_SOURCE("sensitive_detection_source","detection_id:UUID locator_id:UUID","detection_id,locator_id",""),
        BACKGROUND_NOTE("background_note","id:UUID set_id:UUID input_id:UUID ordinal:UINT body:TEXT","id",""),
        BACKGROUND_NOTE_SOURCE("background_note_source","note_id:UUID locator_id:UUID","note_id,locator_id",""),
        ACTION_SUGGESTION("action_suggestion","element_id:UUID input_id:UUID owner_mention:TEXT? due_on:DATE?","element_id",""),
        RESULT_REVIEW("result_review","id:UUID meeting_id:UUID review_no:UINT review_kind:TEXT element_id:UUID? minutes_revision_id:UUID? target_anchor:TEXT? target_hash:HASH source_set_hash:HASH hash_profile:TEXT outcome:TEXT reviewed_by:TEXT reviewed_at:TIMESTAMP","id","review_kind=SOURCE_SUPPORT,ACTION_FIELDS"),
        RESULT_REVIEW_EVIDENCE("result_review_evidence","review_id:UUID evidence_id:UUID","review_id,evidence_id",""),
        FINAL_RESULT_REVIEW("final_result_review","confirmation_id:UUID review_id:UUID","confirmation_id,review_id","");
        private final String sqlName;private final List<ColumnSpec> columns;private final List<String> primaryKey;private final Map<String,Set<String>> enums;
        Table(String sqlName,String columns,String primaryKey,String enumSpec){
            this.sqlName=sqlName;var specs=new ArrayList<ColumnSpec>();
            for(var column:columns.split(" ")){var pair=column.split(":");String type=pair[1];boolean nullable=type.endsWith("?");
                specs.add(new ColumnSpec(pair[0],CellKind.valueOf(nullable?type.substring(0,type.length()-1):type),nullable));}
            this.columns=List.copyOf(specs);this.primaryKey=List.of(primaryKey.split(","));
            var checks=new HashMap<String,Set<String>>();if(!enumSpec.isEmpty())for(var check:enumSpec.split(";")){var pair=check.split("=");checks.put(pair[0],Set.of(pair[1].split(",")));}this.enums=Map.copyOf(checks);
        }
        public String sqlName(){return sqlName;} public List<ColumnSpec> columns(){return columns;} public List<String> primaryKey(){return primaryKey;}
        public int index(String name){for(int i=0;i<columns.size();i++)if(columns.get(i).name().equals(name))return i;throw fail(FailureCode.INVALID_ARGUMENT);}
    }
    public sealed interface Cell permits NullCell,TextCell,UIntCell {}
    public record NullCell() implements Cell {}
    public record TextCell(String value) implements Cell {public TextCell {scalars(value);}}
    public record UIntCell(long value) implements Cell {public UIntCell {uint(value);}}
    public static Cell cell(String value){return value==null?new NullCell():new TextCell(value);}
    public static Cell cell(long value){return new UIntCell(value);}
    /** Positional cells cover every projected column in dictionary order; no caller-defined field map. */
    public record FullRow(Table table,List<Cell> cells) {
        public FullRow {
            require(table!=null&&cells!=null&&cells.size()==table.columns.size(),FailureCode.INPUT_INVALID);cells=List.copyOf(cells);
            for(int i=0;i<cells.size();i++){
                var column=table.columns.get(i);var value=cells.get(i);
                if(value instanceof NullCell){require(column.nullable(),FailureCode.INPUT_INVALID);continue;}
                if(column.kind()==CellKind.UINT){require(value instanceof UIntCell,FailureCode.INPUT_INVALID);continue;}
                require(value instanceof TextCell,FailureCode.INPUT_INVALID);String text=((TextCell)value).value();
                switch(column.kind()){
                    case UUID->uuid(text);case HASH->hash(text);case DATE->date(text);
                    case TIMESTAMP->{
                        require(text.matches("[1-9][0-9]{3}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}[.][0-9]{6}Z"),FailureCode.INPUT_INVALID);
                        try{java.time.Instant.parse(text);}catch(java.time.DateTimeException e){throw fail(FailureCode.INPUT_INVALID);}
                    }
                    default->{}
                }
                var allowed=table.enums.get(column.name());require(allowed==null||allowed.contains(text),FailureCode.INPUT_INVALID);
            }
        }
        public Cell value(String name){return cells.get(table.index(name));}
        public String string(String name){var c=value(name);require(c instanceof TextCell,FailureCode.INPUT_INVALID);return ((TextCell)c).value();}
        public String nullableString(String name){return value(name) instanceof NullCell?null:string(name);}
        public long number(String name){var c=value(name);require(c instanceof UIntCell,FailureCode.INPUT_INVALID);return ((UIntCell)c).value();}
        public Long nullableNumber(String name){return value(name) instanceof NullCell?null:number(name);}
    }
    public enum Mark { KEY,CONCLUSION,DISPUTE,GENERATION_FOCUS }
    public record ContentFile(String id,String sha256,long sizeBytes,String meetingId) {public ContentFile {uuid(id);hash(sha256);uint(sizeBytes);if(meetingId!=null)uuid(meetingId);}public ContentFile(String id,String sha256,long sizeBytes){this(id,sha256,sizeBytes,null);}}
    public enum ProfileKind { CANONICAL_CODEC,CONFIRMATION_HASH,GENERATION_HASH,ROWSET_HASH,ANCHOR,REVIEW_HASH,FINAL_SOURCES,OUTPUT_SCHEMA,VALIDATION_BUDGET,CATALOG,RENDER,EXCLUSION,SUPPORT,TITLE,CHUNK_PLAN }
    /** Capture raw arrays with ArchiveBytes.capture(maximum, remaining, cancelled) before constructing this fact. */
    public record Artifact(String version,String sha256,ConfigArchiveContracts.ArchiveBytes bytes) {
        public Artifact {scalars(version);require(!version.isEmpty(),FailureCode.PROFILE_UNAVAILABLE);hash(sha256);require(bytes!=null,FailureCode.INVALID_ARGUMENT);}
        @Override public boolean equals(Object obj){return obj instanceof Artifact a&&version.equals(a.version)&&sha256.equals(a.sha256)&&bytes.sameBytes(a.bytes);}
        @Override public int hashCode(){return Objects.hash(version,sha256,bytes.contentHashCode());}
    }
    public record Profiles(Map<ProfileKind,Artifact> definitions) {
        public Profiles {require(definitions!=null&&definitions.keySet().equals(EnumSet.allOf(ProfileKind.class)),FailureCode.PROFILE_UNAVAILABLE);definitions=Map.copyOf(definitions);}
        public Artifact definition(ProfileKind kind){return definitions.get(kind);}
    }
    public record ParserSelection(String materialRevisionId,String parserVersion) {
        public ParserSelection {uuid(materialRevisionId);scalars(parserVersion);require(!parserVersion.isEmpty(),FailureCode.CAPABILITY_UNAVAILABLE);}
    }
    /** Detached interpretation only. Raw archived-schema validation remains a separate prerequisite. */
    public record GenerationConfiguration(String inputId,Artifact prompt,Artifact model,Profiles profiles,String defaultTemplateRevisionId,List<ParserSelection> parserSelections) {
        public GenerationConfiguration {uuid(inputId);require(prompt!=null&&model!=null&&profiles!=null,FailureCode.PROFILE_UNAVAILABLE);uuid(defaultTemplateRevisionId);parserSelections=List.copyOf(parserSelections);}
    }
    public record EvidenceAnchor(String evidenceId,Anchor anchor) {public EvidenceAnchor {uuid(evidenceId);require(anchor!=null,FailureCode.INVALID_ARGUMENT);}}
    /** Caller bounds the aggregate snapshot before any defensive collection copy. */
    public record StoredProvenanceFacts(List<FullRow> rows,List<ContentFile> files,List<GenerationConfiguration> configurations,
      List<EvidenceAnchor> evidenceAnchors,List<TaskContracts.TaskSpec> taskSpecs,List<ReviewScopeFact> reviewScopes,List<StoredTask> taskFacts,long maxFacts) {
        public StoredProvenanceFacts {
            require(maxFacts>0&&rows!=null&&files!=null&&configurations!=null&&evidenceAnchors!=null&&taskSpecs!=null&&reviewScopes!=null&&taskFacts!=null,FailureCode.INVALID_ARGUMENT);
            require((long)rows.size()+files.size()+configurations.size()+evidenceAnchors.size()+taskSpecs.size()+reviewScopes.size()+taskFacts.size()<=maxFacts,FailureCode.BUDGET_EXCEEDED);
            var capture=new FactCapture(maxFacts);rows=capture.copy(rows);files=capture.copy(files);configurations=capture.copy(configurations);
            evidenceAnchors=capture.copy(evidenceAnchors);taskSpecs=capture.copy(taskSpecs);reviewScopes=capture.copy(reviewScopes);taskFacts=capture.copy(taskFacts);
        }
        public StoredProvenanceFacts(List<FullRow> rows,List<ContentFile> files,List<GenerationConfiguration> configs,List<EvidenceAnchor> anchors,long maxFacts){this(rows,files,configs,anchors,List.of(),List.of(),List.of(),maxFacts);}
        public StoredProvenanceFacts(List<FullRow> rows,List<ContentFile> files,List<GenerationConfiguration> configs,List<EvidenceAnchor> anchors,List<TaskContracts.TaskSpec> tasks,long maxFacts){this(rows,files,configs,anchors,tasks,List.of(),List.of(),maxFacts);}
        public StoredProvenanceFacts(List<FullRow> rows,List<ContentFile> files,List<GenerationConfiguration> configs,List<EvidenceAnchor> anchors,List<TaskContracts.TaskSpec> tasks,List<ReviewScopeFact> scopes,long maxFacts){this(rows,files,configs,anchors,tasks,scopes,List.of(),maxFacts);}
    }
    private static final class FactCapture {
        private final long limit;private long count;FactCapture(long limit){this.limit=limit;}
        <T>List<T> copy(List<T> source){
            require(source.size()<=limit-count,FailureCode.BUDGET_EXCEEDED);var copy=new ArrayList<T>(Math.min(source.size(),1024));
            for(var item:source){if(Thread.currentThread().isInterrupted())throw fail(FailureCode.CANCELLED);require(count<limit,FailureCode.BUDGET_EXCEEDED);require(item!=null,FailureCode.INVALID_ARGUMENT);count++;copy.add(item);}
            return Collections.unmodifiableList(copy);
        }
    }
    public sealed interface UsageKey permits EvidenceUsage,DetectionUsage,BackgroundUsage,ExclusionUsage {}
    public record EvidenceUsage(String evidenceId) implements UsageKey {public EvidenceUsage {uuid(evidenceId);}}
    public record DetectionUsage(String detectionId,String locatorId) implements UsageKey {public DetectionUsage {uuid(detectionId);uuid(locatorId);}}
    public record BackgroundUsage(String noteId,String locatorId) implements UsageKey {public BackgroundUsage {uuid(noteId);uuid(locatorId);}}
    public record ExclusionUsage(String exclusionRevisionId,String locatorId,SensitiveKind category) implements UsageKey {public ExclusionUsage {uuid(exclusionRevisionId);uuid(locatorId);require(category!=null,FailureCode.INPUT_INVALID);}}
    public sealed interface Origin permits GenerationOrigin,ConfirmationOrigin,AppliedOrigin {}
    public record GenerationOrigin(String inputId,String inputFingerprint) implements Origin {public GenerationOrigin {uuid(inputId);hash(inputFingerprint);}}
    public record ConfirmationOrigin(String confirmationId,String revisionId,String contentHash) implements Origin {public ConfirmationOrigin {uuid(confirmationId);uuid(revisionId);hash(contentHash);}}
    public record AppliedOrigin(String inputId,String inputFingerprint) implements Origin {public AppliedOrigin {uuid(inputId);hash(inputFingerprint);}}
    public record SourceUsage(UsageKey usage,String locatorId,Origin origin,Role role,SourceVersion sourceVersion,String sourceContentHash) {
        public SourceUsage {require(usage!=null&&origin!=null&&role!=null&&sourceVersion!=null,FailureCode.INPUT_INVALID);uuid(locatorId);hash(sourceContentHash);}
    }
    public record SourceGraph(List<FullRow> locatorRows,List<SourceUsage> sourceUsages) {
        public SourceGraph {locatorRows=List.copyOf(locatorRows);sourceUsages=List.copyOf(sourceUsages);}
    }
    public record GraphSelection(String meetingId,String minutesRevisionId,String elementSetId,String exclusionRevisionId,String appliedInputId) {
        public GraphSelection {uuid(meetingId);if(minutesRevisionId!=null)uuid(minutesRevisionId);if(elementSetId!=null)uuid(elementSetId);if(exclusionRevisionId!=null)uuid(exclusionRevisionId);if(appliedInputId!=null)uuid(appliedInputId);}
    }
    public record Fence(long meetingRevision,long subjectBusinessVersion,long cancelEpoch) {public Fence {uint(meetingRevision);uint(subjectBusinessVersion);uint(cancelEpoch);}}
    public record GenerationFacts(String inputId,StoredProvenanceFacts stored) {public GenerationFacts {uuid(inputId);require(stored!=null,FailureCode.INVALID_ARGUMENT);}}
    /** Actual saved revision facts; does not assert confirmation or write permission. */
    public record ConfirmationDraft(String meetingId,String revisionId,StoredProvenanceFacts stored) {
        public ConfirmationDraft {uuid(meetingId);uuid(revisionId);require(stored!=null,FailureCode.INVALID_ARGUMENT);}
    }
    public record ConfirmationFacts(String confirmationId,StoredProvenanceFacts stored) {public ConfirmationFacts {uuid(confirmationId);require(stored!=null,FailureCode.INVALID_ARGUMENT);}}
    public enum ReviewKind { SOURCE_SUPPORT,ACTION_FIELDS }
    public record ReviewFacts(String meetingId,ReviewKind kind,String elementId,String minutesRevisionId,Anchor anchor,List<String> selectedEvidenceIds,StoredProvenanceFacts stored) {
        public ReviewFacts {uuid(meetingId);require(kind!=null&&(elementId==null)!=(minutesRevisionId==null),FailureCode.INPUT_INVALID);if(elementId!=null)uuid(elementId);if(minutesRevisionId!=null)uuid(minutesRevisionId);require((kind==ReviewKind.SOURCE_SUPPORT)==(anchor!=null),FailureCode.TARGET_INVALID);selectedEvidenceIds=List.copyOf(selectedEvidenceIds);require(stored!=null,FailureCode.INVALID_ARGUMENT);}
    }
    public record FinalFacts(String confirmationId,StoredProvenanceFacts stored) {public FinalFacts {uuid(confirmationId);require(stored!=null,FailureCode.INVALID_ARGUMENT);}}

    public record GraphBudget(CanonicalBudget canonical,int maxOriginDepth,long maxOriginNodes,long maxAggregateFacts) {
        public GraphBudget {require(canonical!=null&&maxOriginDepth>0&&maxOriginNodes>0&&maxAggregateFacts>0,FailureCode.INVALID_ARGUMENT);}
    }

    public record ReviewCoverage(String reviewId,String outcome,boolean wholeTargetSupported) {}

    public record ReviewScopeFact(String reviewId,Anchor anchor) {public ReviewScopeFact {uuid(reviewId);}}

    public record StoredTask(String taskId,TaskContracts.TaskSpec spec){public StoredTask {uuid(taskId);require(spec!=null,FailureCode.INVALID_ARGUMENT);}}
    /** New input fields only, deliberately excluding its own not-yet-computed fingerprint and TaskSpec. */
    public record GenerationDraft(String inputId,String meetingId,String transcriptConfirmationId,String materialSnapshotId,
      String templateRevisionId,String exclusionRevisionId,String elementSetId,String promptVersion,String modelVersion,Scope scope,String targetElementId) {
      public GenerationDraft {uuid(inputId);uuid(meetingId);uuid(transcriptConfirmationId);if(materialSnapshotId!=null)uuid(materialSnapshotId);uuid(templateRevisionId);uuid(exclusionRevisionId);if(elementSetId!=null)uuid(elementSetId);scalars(promptVersion);scalars(modelVersion);require(scope!=null,FailureCode.INVALID_ARGUMENT);require((scope==Scope.ELEMENT)==(targetElementId!=null),FailureCode.INPUT_INVALID);if(targetElementId!=null)uuid(targetElementId);}
    }
}
