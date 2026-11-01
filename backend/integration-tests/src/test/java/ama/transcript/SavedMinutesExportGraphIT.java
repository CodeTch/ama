package ama.transcript;

import ama.contract.IdentityContracts.*;
import ama.contract.MinutesContracts.CanonicalBudget;
import ama.db.*;
import ama.export.*;
import ama.meeting.*;
import ama.storage.*;
import ama.task.*;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import static org.junit.jupiter.api.Assertions.*;

/** Replays a bounded, actual CAS-created saved graph in a fresh owned schema.
 * Source DB is read-only. No mocked authority, fabricated manifests or new company calls.
 * Output objects use fresh queue-owned keys and are retained with the isolated evidence schema. */
class SavedMinutesExportGraphIT {
 static final String MEETING=required("AMA_EXPORT_GRAPH_MEETING"),ACTOR=required("AMA_EXPORT_GRAPH_ACTOR");
 static String required(String key){return Objects.requireNonNull(System.getenv(key),key);}
 static String identifier(String value){if(!value.matches("[a-z][a-z0-9_]*"))throw new IllegalArgumentException("SQL identifier");return "`"+value+"`";}
 static class Snapshot {
  final JdbcTemplate read,write;final String schema;final Map<String,List<String>> primary=new HashMap<>();final Set<String> seen=new HashSet<>();long bytes;
  Snapshot(JdbcTemplate read,JdbcTemplate write,String schema){this.read=read;this.write=write;this.schema=schema;}
  List<String> keys(String table){return primary.computeIfAbsent(table,t->read.queryForList("SELECT column_name FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name=? AND constraint_name='PRIMARY' ORDER BY ordinal_position",String.class,t));}
  void select(String table,String where,Object...args){for(var row:read.queryForList("SELECT * FROM "+identifier(table)+" WHERE "+where+" LIMIT 10001",args))copy(table,row);}
  void copy(String table,Map<String,Object> row){
   var keys=keys(table);assertFalse(keys.isEmpty());String marker=table+keys.stream().map(k->Objects.toString(row.get(k))).toList();if(!seen.add(marker))return;assertTrue(seen.size()<=10000,"Snapshot row bound");
   for(var value:row.values())if(value instanceof String text)bytes+=text.length()*4L;assertTrue(bytes<=64L*1024*1024,"Snapshot text bound");
   var cols=List.copyOf(row.keySet());String values=String.join(",",Collections.nCopies(cols.size(),"?"));
   write.update("INSERT INTO "+identifier(table)+" ("+String.join(",",cols.stream().map(SavedMinutesExportGraphIT::identifier).toList())+") VALUES ("+values+") ON DUPLICATE KEY UPDATE "+String.join(",",cols.stream().map(c->identifier(c)+"=VALUES("+identifier(c)+")").toList()),cols.stream().map(row::get).toArray());
   // Parent FK closure is exact and bounded; identifiers originate in local schema metadata.
   var foreign=read.queryForList("SELECT constraint_name,column_name,referenced_table_name,referenced_column_name FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name=? AND referenced_table_name IS NOT NULL ORDER BY constraint_name,ordinal_position",table);
   var groups=new LinkedHashMap<String,List<Map<String,Object>>>();for(var fk:foreign)groups.computeIfAbsent((String)fk.get("constraint_name"),k->new ArrayList<>()).add(fk);
   for(var group:groups.values()){if(group.stream().anyMatch(f->row.get((String)f.get("column_name"))==null))continue;select((String)group.getFirst().get("referenced_table_name"),String.join(" AND ",group.stream().map(f->identifier((String)f.get("referenced_column_name"))+"=?").toList()),group.stream().map(f->row.get((String)f.get("column_name"))).toArray());}
  }
 }
 @Test void actualSavedGraphAuthorizesAllFormatsAndRevokesChangedSources()throws Exception{
  String url=required("AMA_EXPORT_SNAPSHOT_DATABASE_URL"),schema=url.substring(url.lastIndexOf('/')+1);assertTrue(schema.matches("ama_c_recording_[0-9a-f]{12}"));
  var admin=new DriverManagerDataSource(url.substring(0,url.lastIndexOf('/')+1)+"mysql",required("AMA_EXPORT_SNAPSHOT_DATABASE_USERNAME"),required("AMA_EXPORT_SNAPSHOT_DATABASE_PASSWORD"));
  new JdbcTemplate(admin).execute("CREATE DATABASE "+identifier(schema)+" CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
  var poolConfig=new com.zaxxer.hikari.HikariConfig();poolConfig.setDataSource(MysqlDataSources.fromEnvironment("AMA_EXPORT_SNAPSHOT_DATABASE_"));poolConfig.setMaximumPoolSize(4);poolConfig.setMinimumIdle(1);poolConfig.setConnectionTimeout(3000);try(var target=new com.zaxxer.hikari.HikariDataSource(poolConfig)){Flyway.configure().dataSource(target).locations("classpath:db/migration").load().migrate();
  var properties=new Properties();try(var in=Files.newInputStream(Path.of(required("AMA_EXPORT_GRAPH_PROPERTIES")))){properties.load(in);}
  var env=new StandardEnvironment();env.getPropertySources().addFirst(new PropertiesPropertySource("actual-installed-config",properties));
  var limits=ConfiguredMinutes.acquisition(env);var readBudget=ConfiguredMinutes.archiveBudget(env);
  try(var from=MysqlDataSources.fromEnvironment("AMA_EXPORT_SOURCE_DATABASE_").getConnection();var to=target.getConnection()){
   from.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);from.setReadOnly(true);from.setAutoCommit(false);to.setAutoCommit(false);
   var source=new SingleConnectionDataSource(from,true);var destination=new SingleConnectionDataSource(to,true);var read=new JdbcTemplate(source);var write=new JdbcTemplate(destination);
   var graph=MinutesGraphDiscovery.discover(source,MEETING,"SAVED_EFFECTIVE",null,limits,readBudget);var snapshot=new Snapshot(read,write,schema);
   write.execute("SET FOREIGN_KEY_CHECKS=0");
   try{
    snapshot.select("meeting","id=?",MEETING);
    for(var entry:graph.tables().entrySet())for(var row:entry.getValue()){var keys=snapshot.keys(entry.getKey());snapshot.select(entry.getKey(),String.join(" AND ",keys.stream().map(k->identifier(k)+"=?").toList()),keys.stream().map(row::get).toArray());}
    var publicationGraph=PublicationGraph.discover(source,MEETING,limits.ledger(readBudget,new CanonicalBudget(env.getRequiredProperty("processing.bounds.canonical-bytes",Long.class),env.getRequiredProperty("processing.bounds.canonical-depth",Integer.class),env.getRequiredProperty("processing.bounds.canonical-nodes",Long.class),env.getRequiredProperty("processing.bounds.canonical-string-utf8",Long.class),env.getRequiredProperty("processing.bounds.canonical-scalars",Long.class)),()->false));
    for(var entry:publicationGraph.rows().entrySet())for(var row:entry.getValue()){var keys=snapshot.keys(entry.getKey());snapshot.select(entry.getKey(),String.join(" AND ",keys.stream().map(k->identifier(k)+"=?").toList()),keys.stream().map(row::get).toArray());}
    for(String identity:graph.identities())snapshot.select("enterprise_identity","enterprise_id=?",identity);snapshot.select("enterprise_identity","enterprise_id=?",ACTOR);
    for(String table:List.of("meeting_access_grant","meeting_minutes_editor_guard","publication"))snapshot.select(table,"meeting_id=?",MEETING);
    snapshot.select("task_subject","subject_kind='MEETING' AND subject_id=?",MEETING);
   }finally{write.execute("SET FOREIGN_KEY_CHECKS=1");}
   to.commit();from.rollback();System.out.println("ACTUAL_GRAPH_SNAPSHOT schema="+schema+" rows="+snapshot.seen.size()+" bytesBound="+snapshot.bytes);
  }
  try(var definitions=ConfiguredBackends.read(env);var knowledge=ConfiguredPublication.read(env,readBudget,ConfiguredMinutes.indexBudget(env)).client()){
   var backends=new FileBackendCatalog(target,definitions.backends(),definitions.defaultId());backends.configure();var tx=new TransactionRunner(target);var fences=new JdbcSubjectFences(target,Set.of("MEETING"));var access=new JdbcMeetingAccess(target,tx,fences);
   String prefix="processing.bounds.";var canonical=new CanonicalBudget(env.getRequiredProperty(prefix+"canonical-bytes",Long.class),env.getRequiredProperty(prefix+"canonical-depth",Integer.class),env.getRequiredProperty(prefix+"canonical-nodes",Long.class),env.getRequiredProperty(prefix+"canonical-string-utf8",Long.class),env.getRequiredProperty(prefix+"canonical-scalars",Long.class));
   var components=JdbcTranscriptAuthority.create(target,tx,access,new TranscriptFilePurposePolicy(),backends,Map.of(),readBudget,canonical);var configured=ConfiguredMinutes.read(env,readBudget,ConfiguredMinutes.indexBudget(env));configured.preparation(components.authority());
   var saved=components.authority().installExportSource(configured.archives(),configured.pin(),limits);var validators=new HashMap<>(new MeetingEventReferences(target).validators());validators.putAll(MinutesExportRuntime.eventReferences(target));var outbox=new JdbcOutbox(target,validators);
   var queue=MinutesExportRuntime.install(target,fences,outbox,new MinutesExportRuntime.Dependencies(access,components.registry(),backends,saved));var actor=new Actor(ACTOR,Status.ACTIVE,Set.of(Role.USER));var j=new JdbcTemplate(target);String revision=j.queryForObject("SELECT minutes_revision_id FROM meeting WHERE id=?",String.class,MEETING);
   var governance=new PublicationGovernanceBinding(components.authority(),knowledge,limits);String publication=j.queryForObject("SELECT id FROM publication WHERE meeting_id=?",String.class,MEETING);var recorded=governance.prepare(MEETING,publication);assertTrue(recorded.documentId().startsWith("doc-"));assertEquals(publication,recorded.publicationId());
   var commands=new JdbcPublicationGovernance(components.authority(),knowledge,limits);
   access.installPublicationValidity();
   assertThrows(ama.access.MeetingAccess.AuthorityUnavailableException.class,()->access.require(actor,MEETING,ama.access.MeetingAccess.Action.READ),"Missing proof is unavailable and still refuses content; it is not permanent revocation");
   var origin=j.queryForMap("SELECT * FROM external_document_mapping WHERE publication_id=?",publication);
   commands.pollOnce();
   assertEquals(1L,j.queryForObject("SELECT COUNT(*) FROM publication_governance_authority WHERE publication_id=? AND verified_until>UTC_TIMESTAMP(6)",Long.class,publication),"Actual fixed-harness signed state must have been verified");
   assertEquals(origin,j.queryForMap("SELECT * FROM external_document_mapping WHERE publication_id=?",publication),"An unchanged ACTIVE authority heartbeat must not mutate immutable publication provenance");
   var prepared=saved.prepare(actor,MEETING,revision);assertFalse(prepared.document().body().isBlank());System.out.println("GENUINE_SOURCE_PREPARED source="+revision);
   // Keep genuine short-lived signed authority fresh during converter and storage work.
   commands.start();
   Path artifacts=Path.of(required("AMA_EXPORT_GRAPH_OUTPUT"));Files.createDirectories(artifacts);var converter=new MinutesExportProcess(artifacts.resolve("work"),new MinutesExportRenderer.Fonts(Path.of("/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc"),Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")),Duration.ofSeconds(30));
   var cancellation=new PreparationCancellation(){public boolean cancelled(){return false;}public AutoCloseable register(Runnable abort){return ()->{};}};var completed=new ArrayList<String>();
   for(String format:List.of("md","pdf","docx")){
    var accepted=MinutesExportRuntime.accept(queue,actor,MEETING,"genuine-"+format,revision,format);assertEquals(accepted.exportJobId(),MinutesExportRuntime.accept(queue,actor,MEETING,"genuine-"+format,revision,format).exportJobId());
    var lease=queue.claim(MinutesExportRuntime.POOL,"GENUINE_GRAPH").orElseThrow();assertEquals(accepted.exportJobId(),lease.taskId());try(var heartbeat=java.util.concurrent.Executors.newSingleThreadScheduledExecutor()){
     var renewalFailure=new java.util.concurrent.atomic.AtomicReference<Throwable>();var renewing=heartbeat.scheduleWithFixedDelay(()->{try{if(!queue.renew(lease))renewalFailure.compareAndSet(null,new IllegalStateException("Actual export lease renewal denied"));}catch(Throwable failure){renewalFailure.compareAndSet(null,failure);}},2,3,java.util.concurrent.TimeUnit.SECONDS);
     try{var result=MinutesExportRuntime.prepare(queue,lease,converter,cancellation);assertNull(renewalFailure.get(),"Worker-equivalent renewal must remain healthy");assertTrue(queue.completeWithEffects(lease,result));}finally{renewing.cancel(false);heartbeat.shutdown();}
    }
    var done=MinutesExportRuntime.status(queue,actor,lease.taskId());assertEquals("SUCCEEDED",done.state(),done.error());assertNotNull(done.fileId());completed.add(done.fileId());System.out.println("GENUINE_EXPORT_REGISTERED format="+format+" file="+done.fileId());byte[] bytes;
    try(var full=components.registry().download(actor,done.fileId(),null)){bytes=full.body().readAllBytes();assertTrue(bytes.length>10);Files.write(artifacts.resolve("actual-saved-graph."+format),bytes,StandardOpenOption.CREATE_NEW);}
    try(var range=components.registry().download(actor,done.fileId(),"bytes=0-9")){assertArrayEquals(Arrays.copyOf(bytes,10),range.body().readAllBytes());}
    if(format.equals("md"))assertEquals("# "+prepared.document().title()+"\n\n"+prepared.document().body()+"\n",new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
   }
   commands.close(); // Finish read-only polling before isolated local mutation/command cases.
   j.update("UPDATE minutes_revision SET body=CONCAT(body,' changed') WHERE id=?",revision);
   assertThrows(RuntimeException.class,()->components.registry().download(actor,completed.getFirst(),null));
   assertEquals(recorded.documentId(),governance.prepare(MEETING,publication).documentId(),"Governance metadata can withdraw a prior publication even when local source body integrity fails");
   j.update("UPDATE minutes_revision SET body=? WHERE id=?",prepared.document().body(),revision);
   j.update("UPDATE meeting SET governance_state='WITHDRAW_PENDING' WHERE id=?",MEETING);
   assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->components.registry().download(actor,completed.getFirst(),null));
   assertEquals(recorded.documentId(),governance.prepare(MEETING,publication).documentId(),"Pending governance never blocks technical reconciliation of immutable publication metadata");
   System.out.println("GENUINE_EXPORT_GRAPH formats=3 range=3 sourceMutationDenied=true governanceDenied=true");
   // Only this newly owned snapshot is reset to test durable command admission. No company call.
   j.update("UPDATE meeting SET governance_state='ACTIVE' WHERE id=?",MEETING);
   long meetingRevision=j.queryForObject("SELECT revision FROM meeting WHERE id=?",Long.class,MEETING);
   var accepted=commands.accept(actor,MEETING,publication,"graph-withdraw",meetingRevision,"WITHDRAW","保存原请求，未知结果保持拒读");
   assertEquals("PENDING",accepted.get("state"));
   assertEquals(accepted,commands.accept(actor,MEETING,publication,"graph-withdraw",meetingRevision,"WITHDRAW","保存原请求，未知结果保持拒读"));
   assertThrows(ama.contract.TranscriptContracts.Failure.class,()->commands.accept(actor,MEETING,publication,"graph-withdraw",meetingRevision,"WITHDRAW","修改理由"));
   assertThrows(ama.contract.TranscriptContracts.Failure.class,()->commands.accept(actor,MEETING,publication,"second-command",meetingRevision,"OBSOLETE","另一个请求"));
   assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->components.registry().download(actor,completed.getFirst(),null));
   assertEquals("WITHDRAW_PENDING",j.queryForObject("SELECT governance_state FROM meeting WHERE id=?",String.class,MEETING));
   assertEquals(1L,j.queryForObject("SELECT COUNT(*) FROM publication_governance_command",Long.class));
   assertEquals(accepted,commands.status(actor,MEETING,publication,(String)accepted.get("governanceJobId")));
   j.update("UPDATE enterprise_identity SET status='DISABLED' WHERE enterprise_id=?",ACTOR);
   assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->commands.status(actor,MEETING,publication,(String)accepted.get("governanceJobId")));
   assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->commands.accept(actor,MEETING,publication,"graph-withdraw",meetingRevision,"WITHDRAW","保存原请求，未知结果保持拒读"));
   System.out.println("GENUINE_GOVERNANCE_COMMAND originalReplay=true changedTupleDenied=true pendingReadDenied=true disabledOwnerDenied=true");
  }
  }
 }
}
