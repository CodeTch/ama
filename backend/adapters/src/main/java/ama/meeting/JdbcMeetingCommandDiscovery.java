package ama.meeting;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.Actor;
import ama.contract.WorkspaceContracts.*;
import ama.db.TransactionRunner;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Read-only durable association lookup. Original producer retry remains the command ACK authority. */
public final class JdbcMeetingCommandDiscovery {
 private final TransactionRunner tx;private final JdbcMeetingAccess access;private final JdbcTemplate jdbc;private final WorkspaceReadBudget.Limits limits;
 private record Receipt(String actor,String resource,String operation,String keyHash,String requestHash,String state,String result,int status){}
 private record Result(String meeting,String result,String revision){}
 public JdbcMeetingCommandDiscovery(DataSource source,TransactionRunner tx,JdbcMeetingAccess access,WorkspaceReadBudget.Limits limits){access.requireProjectionSource(source);this.tx=tx;this.access=access;this.limits=limits;jdbc=new JdbcTemplate(source);jdbc.setQueryTimeout(5);}
 public CommandLookup lookup(Actor actor,String operation,String resource,String key){
  if(TransactionSynchronizationManager.isActualTransactionActive())throw unavailable();
  if(operation==null||!Set.of("CREATE","SAVE_INFO","CONFIRM_ROSTER","UPLOAD_BEGIN").contains(operation))throw new IllegalArgumentException("Invalid command operation");
  boolean create=operation.equals("CREATE");if(create){if(!"meetings".equals(resource))throw new IllegalArgumentException("Invalid resource");}else ama.contract.OutboxContracts.uuid(resource);
  WorkspaceText.check(key,4096,true);WorkspaceText.check(actor.enterpriseId(),1020,true);
  var budget=new WorkspaceReadBudget(limits,()->Thread.currentThread().isInterrupted());// Reserve both hashes before either allocation: scope <= 1020+36+14+4096+16 bytes;
  // key <= 4096+4 bytes. Separate 8KiB allowances include encoding/buffer copies.
  budget.reserve(1,4,8192,8192,8192,4,false);budget.reserve(1,1,8192,8192,8192,4,false);
  String scope=MeetingDigests.fields(actor.enterpriseId(),resource,operation,key),keyHash=MeetingDigests.fields(key);
  try {
   if(!create)return tx.required(()->authorized(actor,operation,resource,scope,keyHash,null,budget));
   // Meeting ID is not known until the exact actor-scoped accepted receipt has been discovered.
   var discovered=tx.required(()->{budget.reserve(2,4,2048,1024,1024,4,false);access.tryLockIdentities(actor,List.of(),false).orElseThrow(MeetingAccess.AccessDeniedException::new);var receipt=receipt(actor,operation,resource,scope,keyHash,budget);if(receipt==null)return null;return new AbstractMap.SimpleImmutableEntry<>(receipt,result(operation,receipt.result(),budget));});
   if(discovered==null){budget.reserve(1,1,128,128,128,4,true);return new CommandLookup("ABSENT",null,null);}
   return tx.required(()->authorized(actor,operation,discovered.getValue().meeting(),scope,keyHash,discovered,budget));
  }catch(org.springframework.dao.DataAccessException|org.springframework.transaction.TransactionException error){throw new WorkspaceReadBudget.Unavailable(error);}
 }
 private CommandLookup authorized(Actor actor,String operation,String meeting,String scope,String keyHash,Map.Entry<Receipt,Result> expected,WorkspaceReadBudget budget){
  budget.reserve(12,60,6144,2304,2816,4,false);var gate=(JdbcMeetingAccess.Held)access.lock(actor,meeting);access.require(gate,MeetingAccess.Action.EDIT);var root=gate.root;
  if(!root.owner().equals(actor.enterpriseId()))throw new MeetingAccess.AccessDeniedException();
  var receipt=receipt(actor,operation,operation.equals("CREATE")?"meetings":meeting,scope,keyHash,budget);
  if(expected!=null&&!Objects.equals(expected.getKey(),receipt))throw unavailable();
  CommandAssociation association=null;
  if(receipt!=null){var result=result(operation,receipt.result(),budget);if(!meeting.equals(result.meeting())||expected!=null&&!expected.getValue().equals(result))throw unavailable();association=new CommandAssociation(operation,meeting,operation.equals("UPLOAD_BEGIN")?"UPLOAD_SESSION":operation.equals("CONFIRM_ROSTER")?"ROSTER_REVISION":"INFO_REVISION",result.result(),result.revision(),receipt.status());}
  if(root.revision()<0||root.revision()==Long.MAX_VALUE||root.accessRevision()<0||root.accessRevision()==Long.MAX_VALUE||!MeetingListScan.STAGES.contains(root.stage()))throw unavailable();
  budget.reserve(3,16,2048,2048,2048,4,true);budget.check();return new CommandLookup(association==null?"ABSENT":"FOUND",association,new CommandObservation(meeting,Long.toString(root.revision()),Long.toString(root.accessRevision()),root.stage()));
 }
 private Receipt receipt(Actor actor,String operation,String resource,String scope,String keyHash,WorkspaceReadBudget budget){
  // Two varchar(255) UTF8 fields, fixed ASCII hashes/enums/UUID and JDBC/application copies.
  budget.reserve(1,8,2560,1024,1536,4,false);
  var rows=jdbc.query("SELECT actor_id,resource_id,operation,key_hash,request_hash,state,result_id,http_status FROM idempotency_record WHERE scope_hash=?",(r,n)->new Receipt(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getInt(8)),scope);
  budget.check();if(rows.isEmpty())return null;if(rows.size()!=1)throw unavailable();var r=rows.getFirst();
  if(!actor.enterpriseId().equals(r.actor())||!resource.equals(r.resource())||!operation.equals(r.operation())||!keyHash.equals(r.keyHash())||!"SUCCEEDED".equals(r.state())||r.requestHash()==null||!r.requestHash().matches("[0-9a-f]{64}")||r.status()!=(Set.of("CREATE","UPLOAD_BEGIN").contains(operation)?201:200))throw unavailable();
  validId(r.result());if(operation.equals("CREATE")&&!MeetingDigests.fields("meeting-create-v1").equals(r.requestHash()))throw unavailable();return r;
 }
 private Result result(String operation,String id,WorkspaceReadBudget budget){
  validId(id);budget.reserve(1,3,128,128,128,4,false);
  String table=operation.equals("UPLOAD_BEGIN")?"upload_session":operation.equals("CONFIRM_ROSTER")?"roster_revision":"meeting_info_revision";
  var rows=jdbc.query("SELECT meeting_id"+(operation.equals("UPLOAD_BEGIN")?"":operation.equals("CONFIRM_ROSTER")?",revision,state":",revision")+" FROM "+table+" WHERE id=?",(r,n)->{String meeting=r.getString(1);validId(meeting);if(operation.equals("UPLOAD_BEGIN"))return new Result(meeting,id,null);long revision=r.getLong(2);if(r.wasNull()||revision<0||revision==Long.MAX_VALUE||operation.equals("CREATE")&&revision!=0||operation.equals("CONFIRM_ROSTER")&&!"CONFIRMED".equals(r.getString(3)))throw unavailable();return new Result(meeting,id,Long.toString(revision));},id);
  budget.check();if(rows.size()!=1)throw unavailable();return rows.getFirst();
 }
 private static void validId(String value){try{ama.contract.OutboxContracts.uuid(value);}catch(IllegalArgumentException error){throw unavailable();}}
 private static WorkspaceReadBudget.Unavailable unavailable(){return new WorkspaceReadBudget.Unavailable();}
}
