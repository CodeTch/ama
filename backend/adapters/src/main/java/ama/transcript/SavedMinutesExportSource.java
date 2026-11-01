package ama.transcript;

import ama.access.MeetingAccess;
import ama.config.ConfigArchiveSource;
import ama.contract.IdentityContracts.Actor;
import ama.contract.MinutesContracts.Table;
import ama.contract.TranscriptContracts.Failure;
import ama.export.MinutesExportDocument;
import ama.storage.FileRegistry;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.function.Supplier;

/** Export sources are genuinely saved graphs; caller text or candidate IDs never authorize output. */
public final class SavedMinutesExportSource {
 private static final List<String> FROZEN=List.of("info_revision_id","roster_revision_id","transcript_confirmation_id","minutes_revision_id","material_snapshot_id","element_set_id","template_revision_id","exclusion_revision_id");
 private final JdbcTranscriptAuthority authority;private final JdbcMinutesFactsReader reader;private final TranscriptAcquisitionBudget limits;
 public SavedMinutesExportSource(JdbcTranscriptAuthority authority,Supplier<ConfigArchiveSource.Snapshot> archives,String pin,TranscriptAcquisitionBudget limits){this.authority=Objects.requireNonNull(authority);this.limits=Objects.requireNonNull(limits);reader=new JdbcMinutesFactsReader(authority,archives,pin,limits);}
 public final class Prepared {
  private final Actor actor;private final String meeting,revision;private final JdbcMinutesFactsReader.Snapshot snapshot;private final MinutesExportDocument document;private final String hash;
  private Prepared(Actor actor,String meeting,String revision,JdbcMinutesFactsReader.Snapshot snapshot,MinutesExportDocument document){this.actor=actor;this.meeting=meeting;this.revision=revision;this.snapshot=snapshot;this.document=document;this.hash=contentHash(document);}
  private SavedMinutesExportSource issuer(){return SavedMinutesExportSource.this;}
  public String meetingId(){return meeting;}public String sourceRevision(){return revision;}public String sourceHash(){return hash;}public MinutesExportDocument document(){return document;}
  public long meetingRevision(){return number(snapshot.observation(),"revision");}public long accessRevision(){return number(snapshot.observation(),"access_revision");}public long cancelEpoch(){return number(snapshot.observation(),"cancel_epoch");}
  public String finalConfirmation(){return (String)snapshot.observation().get("final_confirmation_id");}
 }
 public Prepared prepare(Actor actor,String meeting,String sourceRevision){
  ama.contract.MinutesContracts.uuid(sourceRevision);var snapshot=reader.readCurrent(actor,meeting);var graph=snapshot.graph();
  var fixed=graph.discovery().tables().getOrDefault("final_confirmation",List.of()).stream().filter(row->Objects.equals(row.get("id"),snapshot.observation().get("final_confirmation_id"))).toList();
  boolean candidate=graph.auxiliary().getOrDefault("generation_candidate",List.of()).stream().anyMatch(row->sourceRevision.equals(row.get("minutes_revision_id")));
  checkSelection(snapshot.observation(),sourceRevision,candidate,fixed.size()==1?fixed.getFirst():null);
  var body=JdbcMinutesFactsReader.one(graph,Table.MINUTES_REVISION,sourceRevision);
  return new Prepared(actor,meeting,sourceRevision,snapshot,new MinutesExportDocument(body.string("title"),body.string("body")));
 }
 /** Within the caller transaction, before any export/file/task rows: re-capture the full saved graph. */
 public FileRegistry.LockedMeeting lock(Prepared prepared,Actor actor,String meeting){
  var gate=lockRoot(prepared,actor,meeting);
  recheck(prepared,gate);return gate;
 }
 public FileRegistry.LockedMeeting lockRoot(Prepared prepared,Actor actor,String meeting){require(prepared,actor,meeting);return JdbcMinutesFactsReader.lockGraph(authority,actor,meeting,prepared.snapshot.graph().discovery(),MeetingAccess.Action.READ);}
 /** Metadata may be discovered before queue identities; it is never a source-read grant. */
 public final class Metadata {
  private final MinutesGraphDiscovery.Discovery discovery;
  private Metadata(MinutesGraphDiscovery.Discovery discovery){this.discovery=discovery;}
  public List<String> identities(){return List.copyOf(discovery.identities());}
  private SavedMinutesExportSource issuer(){return SavedMinutesExportSource.this;}
 }
 public final class Captured {
  private final MinutesGraphCapture.Graph graph;private final Object holder;
  private Captured(MinutesGraphCapture.Graph graph){this.graph=graph;holder=org.springframework.transaction.support.TransactionSynchronizationManager.getResource(authority.dataSource());}
  private SavedMinutesExportSource issuer(){return SavedMinutesExportSource.this;}
 }
 public Metadata discover(String meeting){return new Metadata(MinutesGraphDiscovery.discover(authority.dataSource(),meeting,"SAVED_EFFECTIVE",null,limits,authority.readBudget()));}
 /** Called after authentic identities/root, before export input/artifact and source file/task locks. */
 public Captured capture(Actor actor,FileRegistry.LockedMeeting gate,Metadata metadata,Runnable controls){
  if(metadata==null||metadata.issuer()!=this)throw new IllegalArgumentException("Foreign export discovery");
  var scope=authority.minutesReadScope(gate,actor,metadata.discovery.meeting());var budget=limits.ledger(authority.readBudget(),authority.canonicalBudget(),()->false);
  return new Captured(MinutesGraphCapture.capturePublicationCurrent(authority,scope,gate,actor,metadata.discovery,budget,controls,null));
 }
 public void requireCaptured(Prepared prepared,Captured captured){
  if(prepared==null||prepared.issuer()!=this||captured==null||captured.issuer()!=this||captured.holder==null||captured.holder!=org.springframework.transaction.support.TransactionSynchronizationManager.getResource(authority.dataSource()))throw new IllegalArgumentException("Foreign export capture");
  if(!prepared.snapshot.graph().equals(captured.graph))throw new Failure(409,"EXPORT_SOURCE_CHANGED");
 }
 /** Only an actual registry READ gate for this actor/root can recheck a detached prepared source. */
 public void recheck(Prepared prepared,FileRegistry.LockedMeeting gate){
  recheck(prepared,gate,null);
 }
 public void recheck(Prepared prepared,FileRegistry.LockedMeeting gate,Runnable controls){
  require(prepared,prepared.actor,prepared.meeting);var scope=authority.minutesReadScope(gate,prepared.actor,prepared.meeting);
  var budget=limits.ledger(authority.readBudget(),authority.canonicalBudget(),()->false);
  var fresh=MinutesGraphCapture.capturePublicationCurrent(authority,scope,gate,prepared.actor,prepared.snapshot.graph().discovery(),budget,controls,null);
  if(!fresh.equals(prepared.snapshot.graph()))throw new Failure(409,"EXPORT_SOURCE_CHANGED");
 }
 public void requireSource(javax.sql.DataSource source){if(authority.dataSource()!=source)throw new IllegalArgumentException("Shared export source required");}
 public void requireAuthority(JdbcTranscriptAuthority expected){if(authority!=expected||expected.exportSource()!=this)throw new IllegalArgumentException("Genuine shared export source required");}
 public FileRegistry.AcquiredSources recheckRead(Prepared prepared,FileRegistry.LockedMeeting gate,List<String> files,Runnable controls,Runnable tasks){
  require(prepared,prepared.actor,prepared.meeting);var scope=authority.minutesReadScope(gate,prepared.actor,prepared.meeting);
  var budget=limits.ledger(authority.readBudget(),authority.canonicalBudget(),()->false);
  var fresh=MinutesGraphCapture.captureExportRead(authority,scope,gate,prepared.actor,prepared.snapshot.graph().discovery(),budget,controls,tasks,files);
  if(!fresh.graph().equals(prepared.snapshot.graph()))throw new Failure(409,"EXPORT_SOURCE_CHANGED");return fresh.sources();
 }
 private void require(Prepared prepared,Actor actor,String meeting){if(prepared==null||prepared.issuer()!=this||!prepared.actor.enterpriseId().equals(actor.enterpriseId())||!prepared.meeting.equals(meeting))throw new IllegalArgumentException("Foreign export preparation");}
 static void checkSelection(Map<String,Object> root,String sourceRevision,boolean candidate,Map<String,Object> frozen){
  if(!"ACTIVE".equals(root.get("lifecycle"))||!"ACTIVE".equals(root.get("governance_state")))throw new MeetingAccess.AccessDeniedException();
  if(candidate||!sourceRevision.equals(root.get("minutes_revision_id")))throw new Failure(409,"EXPORT_SOURCE_CHANGED");
  boolean published=number(root,"ever_published")!=0;
  if(published){
   if(!"PUBLISHED".equals(root.get("stage"))||frozen==null||!Objects.equals(root.get("final_confirmation_id"),frozen.get("id"))||number(root,"revision")!=number(frozen,"meeting_revision"))throw new Failure(409,"EXPORT_SOURCE_CHANGED");
   for(String field:FROZEN)if(!Objects.equals(root.get(field),frozen.get(field)))throw new Failure(409,"EXPORT_SOURCE_CHANGED");
  }else if("PUBLISHED".equals(root.get("stage")))throw new Failure(409,"EXPORT_SOURCE_CHANGED");
 }
 private static long number(Map<String,Object> row,String key){Object value=row.get(key);if(!(value instanceof Number number)||number.longValue()<0)throw new Failure(503,"EXPORT_SOURCE_UNAVAILABLE");return number.longValue();}
 private static String contentHash(MinutesExportDocument document){
  try{var hash=MessageDigest.getInstance("SHA-256");hash.update("ama.saved-minutes-export.v1\n".getBytes(StandardCharsets.US_ASCII));for(String text:List.of(document.title(),document.body())){byte[] bytes=text.getBytes(StandardCharsets.UTF_8);hash.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());hash.update(bytes);}return HexFormat.of().formatHex(hash.digest());}
  catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
 }
}
