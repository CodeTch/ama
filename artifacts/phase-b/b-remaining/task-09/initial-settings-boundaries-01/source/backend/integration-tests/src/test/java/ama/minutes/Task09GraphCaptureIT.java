package ama.minutes;
import ama.transcript.*;
import ama.contract.IdentityContracts.*;
import ama.contract.IdentityContracts.Role;
import ama.contract.MinutesContracts.*;
import ama.access.MeetingAccess;
import ama.db.TransactionRunner;
import ama.storage.*;
import ama.meeting.JdbcMeetingAccess;
import ama.task.JdbcSubjectFences;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class Task09GraphCaptureIT {
 static final TranscriptAcquisitionBudget L=new TranscriptAcquisitionBudget(16777216,16777216,16777216,67108864,67108864,33554432,128,256,256);
 static final CanonicalBudget C=new CanonicalBudget(16777216,32,1000000,1048576,250000);
 static final class Fixture {
  final List<String> sql=new ArrayList<>();final DataSource source;final JdbcTemplate j;final TransactionRunner tx;final JdbcTranscriptAuthority.Components components;final String meeting=UUID.randomUUID().toString(),owner="task09-capture-"+UUID.randomUUID(),body=UUID.randomUUID().toString();final Actor actor=new Actor(owner,Status.ACTIVE,Set.of(Role.USER));
  Fixture(int bodyLength){DataSource actual=Task09Source.open();source=(DataSource)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{DataSource.class},(proxy,method,args)->{Object value;try{value=method.invoke(actual,args);}catch(InvocationTargetException e){throw e.getCause();}if(value instanceof java.sql.Connection connection)return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{java.sql.Connection.class},(cp,cm,ca)->{if(cm.getName().equals("prepareStatement"))sql.add((String)ca[0]);try{return cm.invoke(connection,ca);}catch(InvocationTargetException e){throw e.getCause();}});return value;});
   j=new JdbcTemplate(source);tx=new TransactionRunner(source,10);var fences=new JdbcSubjectFences(source,Set.of("MEETING"));var access=new JdbcMeetingAccess(source,tx,fences);components=JdbcTranscriptAuthority.create(source,tx,access,(a,m,p)->{},new FileBackendCatalog(source,List.of(),null),Map.of(),ArchiveProcessingFixture.B,C);
   j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",owner,owner);j.update("INSERT INTO task_subject(subject_kind,subject_id,business_version,cancel_epoch,deleted) VALUES ('MEETING',?,0,0,0)",meeting);j.update("INSERT INTO meeting(id,owner_id,stage,created_at,updated_at) VALUES (?,?,'ENTRY',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",meeting,owner);j.update("INSERT INTO minutes_revision(id,meeting_id,revision,title,title_origin,body,created_by,created_at) VALUES (?,?,1,'title','HUMAN',?,?,UTC_TIMESTAMP(6))",body,meeting,"x".repeat(bodyLength),owner);sql.clear();
  }
 }
 static Object invoke(Object target,String name,Class<?>[] types,Object...args)throws Exception{var m=target.getClass().getDeclaredMethod(name,types);m.setAccessible(true);try{return m.invoke(target,args);}catch(InvocationTargetException e){if(e.getCause() instanceof RuntimeException r)throw r;if(e.getCause() instanceof Exception x)throw x;throw e;}}
 Object discovery(Fixture f)throws Exception{Class<?> c=Class.forName("ama.transcript.MinutesGraphDiscovery");var m=c.getDeclaredMethod("discover",DataSource.class,String.class,String.class,String.class,int.class);m.setAccessible(true);return m.invoke(null,f.source,f.meeting,"OWNER_REVISION",f.body,1000);}
 Object scope(Fixture f,FileRegistry.LockedMeeting gate)throws Exception {var methods=Arrays.stream(JdbcTranscriptAuthority.class.getDeclaredMethods()).filter(m->m.getName().equals("minutesScope")).toList();assertEquals(1,methods.size(),"same transcript issuer must own minutes scope");var m=methods.getFirst();m.setAccessible(true);return m.invoke(f.components.authority(),gate,f.actor,f.meeting);}
 Object capture(Fixture f,Object scope,FileRegistry.LockedMeeting gate,Object plan)throws Exception{Class<?> c=assertDoesNotThrow(()->Class.forName("ama.transcript.MinutesGraphCapture"));var m=Arrays.stream(c.getDeclaredMethods()).filter(x->x.getName().equals("capture")).findFirst().orElseThrow();m.setAccessible(true);try{return m.invoke(null,f.components.authority(),scope,gate,f.actor,plan,L.ledger(ArchiveProcessingFixture.B,C,()->false));}catch(InvocationTargetException e){if(e.getCause() instanceof RuntimeException x)throw x;throw e;}}
 @Test void authenticSameIssuerHolderCapturesProtectedBodyAndExactEmptySets()throws Exception {
  var f=new Fixture(20);Object plan=discovery(f);Object graph=f.tx.required(()->{var gate=f.components.registry().lock(f.actor,f.meeting,MeetingAccess.Action.EDIT);return capture(f,scope(f,gate),gate,plan);});
  @SuppressWarnings("unchecked") List<FullRow> rows=(List<FullRow>)invoke(graph,"rows",new Class[]{});assertEquals("x".repeat(20),rows.stream().filter(r->r.table()==Table.MINUTES_REVISION).findFirst().orElseThrow().string("body"));
 }
 @Test void foreignAndExpiredScopesNeverBecomeAmbientTransactionAuthority()throws Exception {
  var f=new Fixture(20);Object plan=discovery(f);Object[] held=new Object[2];f.tx.required(()->{var gate=f.components.registry().lock(f.actor,f.meeting,MeetingAccess.Action.EDIT);held[0]=scope(f,gate);held[1]=gate;assertThrows(IllegalArgumentException.class,()->capture(f,new Object(),gate,plan));return null;});
  f.tx.required(()->{assertThrows(IllegalArgumentException.class,()->capture(f,held[0],(FileRegistry.LockedMeeting)held[1],plan));return null;});
 }
 @Test void oversizedLongtextIsRejectedBeforeAnyBodySelect()throws Exception {
  var f=new Fixture(65537);Object plan=discovery(f);f.tx.required(()->{var gate=f.components.registry().lock(f.actor,f.meeting,MeetingAccess.Action.EDIT);var scope=scope(f,gate);assertThrows(ama.contract.MinutesContracts.Failure.class,()->capture(f,scope,gate,plan));return null;});
  assertTrue(f.sql.stream().noneMatch(s->s.startsWith("SELECT ")&&s.contains(" FROM minutes_revision ")&&Arrays.asList(s.substring(7,s.indexOf(" FROM ")).split(",")).contains("body")),String.join("\n",f.sql));
 }
 @Test void childSetChangedAfterDiscoveryIsRejected()throws Exception {
  var f=new Fixture(20);Object plan=discovery(f);String other=UUID.randomUUID().toString();f.j.update("INSERT INTO result_review(id,meeting_id,review_no,review_kind,minutes_revision_id,target_anchor,target_hash,source_set_hash,hash_profile,outcome,reviewed_by,reviewed_at) VALUES (?,?,1,'SOURCE_SUPPORT',?,'{}',?,?,'ama.review.v1','UNSUPPORTED',?,UTC_TIMESTAMP(6))",other,f.meeting,f.body,"a".repeat(64),"a".repeat(64),f.owner);
  f.tx.required(()->{var gate=f.components.registry().lock(f.actor,f.meeting,MeetingAccess.Action.EDIT);var scope=scope(f,gate);assertThrows(ama.contract.MinutesContracts.Failure.class,()->capture(f,scope,gate,plan));return null;});
 }
}
