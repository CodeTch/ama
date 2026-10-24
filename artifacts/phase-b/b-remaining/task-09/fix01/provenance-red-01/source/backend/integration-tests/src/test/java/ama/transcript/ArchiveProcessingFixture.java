package ama.transcript;

import ama.config.ConfigBindings;
import ama.config.json.StrictConfigArchiveReader;
import ama.config.json.StrictProcessingManifestReader;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.IdentityContracts.*;
import ama.contract.MinutesContracts.*;
import ama.db.SchemaMigrator;
import ama.support.MysqlTestDatabase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static ama.contract.MinutesContracts.*;

/** Controlled metadata only: no upload qualification, worker, or production producer claim. */
public final class ArchiveProcessingFixture {
 public static final ArchiveReadBudget B=new ArchiveReadBudget(65536,1048576,16,32,8,200000,32,255,65536,262144,1048576,65536,32,250000,500000);
 public static final String VERSION="b06.controlled.import-txt.1",SHA=hash("A"),TIME="2026-10-23T01:02:03.123456Z";
 public final DataSource source; public final JdbcTemplate j;
 public final String owner="b06-facts-"+UUID.randomUUID(),meeting=id(),batch=id(),input=id(),file=id(),task=id(),backend="B06_FACTS_"+id();
 public final Actor actor=new Actor(owner,Status.ACTIVE,Set.of(ama.contract.IdentityContracts.Role.USER));
 public final List<RawArchive> archives=archives();
 public final String manifest,fingerprint;
 public ArchiveProcessingFixture(){this(MysqlTestDatabase.dataSource());}
 public ArchiveProcessingFixture(DataSource source){
  this.source=source;j=new JdbcTemplate(source);new SchemaMigrator(source).migrate();
  j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",owner,owner);
  j.update("INSERT INTO task_subject(subject_kind,subject_id,business_version,cancel_epoch,deleted) VALUES ('MEETING',?,20,5,0)",meeting);
  j.update("INSERT INTO meeting(id,owner_id,revision,cancel_epoch,stage,created_at,updated_at) VALUES (?,?,20,5,'TRANSCRIPT_PENDING',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",meeting,owner);
  j.update("INSERT INTO storage_backend(id,storage_type,status,config_revision) VALUES (?,'local','READ_ONLY',1)",backend);
  j.update("INSERT INTO file_object(id,meeting_id,backend_id,storage_type,namespace,object_key,display_name,size_bytes,sha256,state,created_at) VALUES (?,?,?,'local','CONTROLLED',?,'A.txt',1,?,'AVAILABLE','2026-10-23 01:02:03.123456')",file,meeting,backend,file,SHA);
  j.update("INSERT INTO input_batch(id,meeting_id,kind,input_fingerprint,base_revision,created_at) VALUES (?,?,'TRANSCRIPT',?,7,'2026-10-23 01:02:03.123456')",batch,meeting,hash(batch));
  j.update("INSERT INTO input_batch_file(batch_id,ordinal,file_id,sha256) VALUES (?,1,?,?)",batch,file,SHA);
  manifest=json(map("base",map("cancelEpoch",2,"meetingRevision",7,"subjectBusinessVersion",9),"batch",map("files",List.of(map("fileId",file,"ordinal",1,"sha256",SHA,"sizeBytes",1)),"id",batch,"kind","TRANSCRIPT"),"configs",map("model",null,"parser",ref(archives.get(1)),"processing",ref(archives.get(3)),"profiles",List.of(map("kind","CANONICAL_CODEC","ref",ref(archives.get(0))),map("kind","CONFIRMATION_HASH","ref",ref(archives.get(2)))),"prompt",null),"inputId",input,"kind","IMPORT","materials",map("items",List.of(),"mode","EMPTY"),"meetingId",meeting,"profile","ama.transcription-input.v1","schemaVersion",1,"sourceRevisionId",null,"steps",List.of(map("config",ref(archives.get(3)),"kind","IMPORT_TXT","model",null,"ordinal",1,"parentInput",null,"parser",ref(archives.get(1)),"priorOutput",null,"prompt",null,"source",map("id",batch,"kind","INPUT_BATCH")))));
  var facts=new StoredProvenanceFacts(List.of(row(Table.INPUT_BATCH,batch,meeting,"TRANSCRIPT",hash(batch),7L,TIME),row(Table.INPUT_BATCH_FILE,batch,1L,file,SHA)),List.of(new ContentFile(file,SHA,1,meeting)),List.of(),List.of(),B.maxFactRows());
  fingerprint=service().computeProcessing(manifest.getBytes(StandardCharsets.UTF_8),facts,archives,B,()->false).fingerprint();
  j.update("INSERT INTO transcription_input(id,meeting_id,batch_id,kind,material_mode,processing_config_version,parser_version,manifest_schema_version,config_manifest,input_fingerprint,base_revision,cancel_epoch,created_at) VALUES (?,?,?,'IMPORT','EMPTY',?,'ama.strict-utf8-lines.v1',1,?,?,7,2,'2026-10-23 01:02:03.123456')",input,meeting,batch,VERSION,manifest,fingerprint);
  addTask(task,input,fingerprint,9,2);
 }
 public void addTask(String id,String inputId,String fingerprint,long version,long epoch){j.update("INSERT INTO durable_task(id,task_type,pool,subject_kind,subject_id,producer_key,input_kind,input_id,input_fingerprint,business_version,cancel_epoch,priority,state,attempt,max_attempts,available_at,created_at) VALUES (?,'B06_CONTROLLED_METADATA','B06_FACTS','MEETING',?,?,'TRANSCRIPTION_INPUT',?,?,?,?,-3,'READY',0,3,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",id,meeting,hash(id),inputId,fingerprint,version,epoch);}
 public String revision(String producer){String revision=id();j.update("INSERT INTO transcript_revision(id,meeting_id,revision,kind,batch_id,input_fingerprint,created_by,created_at,processing_input_id,producer_task_id) VALUES (?,?,8,'RAW',?,?,?,'2026-10-23 01:02:03.123456',?,?)",revision,meeting,batch,fingerprint,owner,input,producer);return revision;}
 public static ConfigBindings service(){return new ConfigBindings(new StrictConfigArchiveReader(),new StrictProcessingManifestReader());}
 public static String id(){return UUID.randomUUID().toString();}
 public static String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new AssertionError(e);}}
 public static Map<String,Object> map(Object... pairs){var m=new TreeMap<String,Object>();for(int n=0;n<pairs.length;n+=2)m.put((String)pairs[n],pairs[n+1]);return m;}
 static String json(Object v){try{return new ObjectMapper().writeValueAsString(v);}catch(Exception e){throw new AssertionError(e);}}
 static Map<String,Object> ref(RawArchive a){return map("artifactType",a.claimedArtifactType().name(),"sha256",hash(new String(a.bytes(),StandardCharsets.UTF_8)),"version",a.claimedVersion());}
 static RawArchive archive(ArtifactType t,String version,String schema,Object definition,List<Object> dependencies){return new RawArchive(t,version,json(map("archiveSchema","ama.config-archive.v1","artifactType",t.name(),"definition",definition,"definitionSchema",schema,"dependencies",dependencies,"version",version)).getBytes(StandardCharsets.UTF_8));}
 static List<RawArchive> archives(){
  var codec=archive(ArtifactType.CANONICAL_CODEC,"ama.canonical-value.v1","ama.profile-contract.v1",map("contract","ama.canonical-value.v1"),List.of());
  var parser=archive(ArtifactType.PARSER_CONFIG,"ama.strict-utf8-lines.v1","ama.txt-parser-config.v1",map("contract","ama.strict-utf8-lines.v1"),List.of());
  var confirmation=archive(ArtifactType.CONFIRMATION_HASH,"ama.transcript-confirmation.v1","ama.profile-contract.v1",map("contract","ama.transcript-confirmation.v1"),List.of(map("ref",ref(codec),"slot","CANONICAL_CODEC")));
  var processing=archive(ArtifactType.PROCESSING_CONFIG,VERSION,"ama.import-txt-config.v1",map("canonicalBudget",map("maxContainerEntries",250000,"maxDepth",32,"maxEncodedBytes",16777216,"maxNodes",1000000,"maxStringBytes",2097152),"contract","ama.import-txt.v1","manifestSchemaVersion",1,"parseBudget",map("maxSegments",2048,"maxSourceBytes",65536,"maxUtf16CodeUnits",65536)),List.of(map("ref",ref(codec),"slot","CANONICAL_CODEC"),map("ref",ref(confirmation),"slot","CONFIRMATION_HASH"),map("ref",ref(parser),"slot","PARSER_CONFIG")));
  return List.of(codec,parser,confirmation,processing);
 }
 static FullRow row(Table t,Object... v){var cells=new ArrayList<Cell>();for(var x:v)cells.add(x==null?new NullCell():x instanceof Number n?cell(n.longValue()):cell((String)x));return new FullRow(t,cells);}
 public static FullRow input(StoredProvenanceFacts facts){return facts.rows().stream().filter(r->r.table()==Table.TRANSCRIPTION_INPUT).findFirst().orElseThrow();}
}
