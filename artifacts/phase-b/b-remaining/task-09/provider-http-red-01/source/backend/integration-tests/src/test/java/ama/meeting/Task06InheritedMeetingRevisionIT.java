package ama.meeting;

import ama.api.ApiApplication;
import ama.access.MeetingAccess;
import ama.access.MeetingAccess.AccessDeniedException;
import ama.contract.IdentityContracts.*;
import ama.contract.MeetingContracts.*;
import ama.contract.OutboxContracts.*;
import ama.contract.TaskContracts.SubjectRef;
import ama.db.TransactionRunner;
import ama.task.*;
import ama.db.SchemaMigrator;
import ama.support.MysqlTestDatabase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Actual HTTP, shared identity sessions and deployed MySQL schema; no fixture resets. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Task06InheritedMeetingRevisionIT {
    ConfigurableApplicationContext app;
    JdbcTemplate j;
    ObjectMapper json = new ObjectMapper();
    String owner, member, token, memberToken;
    final String csrf="B".repeat(43), origin="https://meetings.example.invalid";
    @BeforeAll void start() {
        var source=MysqlTestDatabase.dataSource();
        assertEquals(14,new JdbcTemplate(source).queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1",Integer.class));
        j=new JdbcTemplate(source);
        app=startApp();
    }
    ConfigurableApplicationContext startApp(String... extra){
        var options=new ArrayList<>(List.of("--server.address=127.0.0.1","--server.port=0",
                "--management.server.port=0","--logging.level.root=ERROR",
                "--spring.datasource.url="+System.getenv("AMA_TEST_DATABASE_URL"),
                "--spring.datasource.username="+System.getenv("AMA_TEST_DATABASE_USERNAME"),
                "--spring.datasource.password="+System.getenv("AMA_TEST_DATABASE_PASSWORD"),"--identity.allowed-origin="+origin));
        options.addAll(List.of(extra));return new SpringApplicationBuilder(ApiApplication.class).run(options.toArray(String[]::new));
    }
    @AfterAll void stop(){if(app!=null)app.close();}
    @BeforeEach void actors() throws Exception {
        owner="task06-fix-meeting-owner-"+UUID.randomUUID(); member="task06-fix-meeting-member-"+UUID.randomUUID();
        token=identity(owner);memberToken=identity(member);
    }
    String identity(String id) throws Exception {
        j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",id,"姓名-"+id);
        byte[] secret=new byte[32];new java.security.SecureRandom().nextBytes(secret);
        String t=Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(t.getBytes(StandardCharsets.US_ASCII)));
        j.update("INSERT INTO identity_session(token_hash,enterprise_id,session_generation,csrf_token,issued_at,expires_at) VALUES (?,?,0,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)+INTERVAL 1 HOUR)",hash,id,csrf);
        return t;
    }
    HttpResponse<String> call(String method,String path,String body,String key,String credential) throws Exception {
        var r=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+app.getEnvironment().getProperty("local.server.port")+"/api/v1/meetings"+path))
                .header("Cookie","__Host-AMA_SESSION="+credential).header("X-CSRF-Token",csrf).header("Origin",origin).header("Content-Type","application/json");
        if(key!=null)r.header("Idempotency-Key",key);
        return HttpClient.newHttpClient().send(r.method(method,HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode body(HttpResponse<String> r)throws Exception{return json.readTree(r.body());}
    String create()throws Exception{
        var r=call("POST","","{}",UUID.randomUUID().toString(),token);assertEquals(201,r.statusCode(),r.body());return body(r).get("id").asText();
    }
    String info(long revision,String name,List<String> ids)throws Exception{
        var b=new LinkedHashMap<String,Object>(); b.put("expectedRevision",revision);b.put("name",name);b.put("category",null);b.put("summary",null);b.put("occurredOn",null);b.put("rosterDraftIds",ids);
        return json.writeValueAsString(b);
    }
    @Test void sameKeyDifferentPayloadConflicts()throws Exception{
        String id=create(), key=UUID.randomUUID().toString();
        assertEquals(200,call("PUT","/"+id+"/info",info(0,"first",List.of()),key,token).statusCode());
        assertEquals(409,call("PUT","/"+id+"/info",info(0,"different",List.of()),key,token).statusCode());
    }
    @Test void staleRevisionPreservesCurrentValue()throws Exception{
        String id=create();
        assertEquals(200,call("PUT","/"+id+"/info",info(0,"current",List.of()),"save-one",token).statusCode());
        var stale=call("PUT","/"+id+"/info",info(0,"stale",List.of()),"save-two",token);
        assertEquals(409,stale.statusCode());
        DeclaredErrorResponse.assertMatches("/api/v1/meetings/{id}/info","put",stale);
        assertEquals("current",body(call("GET","/"+id,"",null,token)).get("name").asText());
    }
    @Test void draftRosterDoesNotGrantAccess()throws Exception{
        String id=create();
        assertEquals(200,call("PUT","/"+id+"/info",info(0,"saved",List.of(member)),"draft",token).statusCode());
        assertEquals(403,call("GET","/"+id,"",null,memberToken).statusCode());
        assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM meeting_access_grant WHERE meeting_id=?",Integer.class,id));
    }
    @Test void emptyDraftAndUnknownDateRemainEmpty()throws Exception{
        String id=create(); var saved=body(call("GET","/"+id,"",null,token));
        assertTrue(saved.get("name").isNull());assertTrue(saved.get("occurredOn").isNull());assertEquals(0,saved.get("revision").asLong());
        assertEquals(id,j.queryForObject("SELECT number_search FROM meeting_search_projection WHERE meeting_id=?",String.class,id));
        assertEquals(0L,j.queryForObject("SELECT business_version FROM task_subject WHERE subject_kind='MEETING' AND subject_id=?",Long.class,id));
    }
    @Test void longUnicodeValuesRoundTripWithoutTruncation()throws Exception{
        String id=create(),value="长名称😀".repeat(20000);
        String payload=info(0,value,List.of()).replace("\"summary\":null","\"summary\":"+json.writeValueAsString(value));
        assertEquals(200,call("PUT","/"+id+"/info",payload,"long",token).statusCode());
        var saved=body(call("GET","/"+id,"",null,token));assertEquals(value,saved.get("name").asText());assertEquals(value,saved.get("summary").asText());
    }
    @Test void confirmationChangesGrantsAndProjectionOnlyExplicitly()throws Exception{
        String id=create();
        assertEquals(200,call("PUT","/"+id+"/info",info(0,"saved",List.of(member)),"draft",token).statusCode());
        assertEquals(200,call("POST","/"+id+"/roster-confirmations","{\"expectedRevision\":1}","confirm",token).statusCode());
        var seen=body(call("GET","/"+id,"",null,memberToken));assertEquals(1,seen.get("confirmedRoster").size());
        assertTrue(!seen.has("rosterDraft")||seen.get("rosterDraft").isNull());
        assertTrue(body(call("GET","/"+id,"",null,token)).get("rosterDraft").isNull(),"copied confirmation supersedes historical draft");
        String confirmed=j.queryForObject("SELECT roster_revision_id FROM meeting WHERE id=?",String.class,id);
        assertEquals(200,call("PUT","/"+id+"/info",info(2,"new",List.of()),"remove-draft",token).statusCode());
        assertEquals(200,call("GET","/"+id,"",null,memberToken).statusCode());
        assertEquals(confirmed,j.queryForObject("SELECT roster_revision_id FROM meeting_search_projection WHERE meeting_id=?",String.class,id));
        assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM meeting_person_search WHERE meeting_id=?",Integer.class,id));
        assertEquals(200,call("POST","/"+id+"/roster-confirmations","{\"expectedRevision\":3}","remove-confirm",token).statusCode());
        assertEquals(403,call("GET","/"+id,"",null,memberToken).statusCode());
        assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM meeting_person_search WHERE meeting_id=?",Integer.class,id));
        assertEquals(4L,j.queryForObject("SELECT meeting_revision FROM meeting_search_projection WHERE meeting_id=?",Long.class,id));
        assertEquals(4L,j.queryForObject("SELECT business_version FROM task_subject WHERE subject_id=?",Long.class,id));
        assertEquals(2L,j.queryForObject("SELECT access_revision FROM meeting WHERE id=?",Long.class,id));
        var replay=call("POST","/"+id+"/roster-confirmations","{\"expectedRevision\":1}","confirm",token);
        assertEquals(200,replay.statusCode());assertEquals(2,body(replay).get("revision").asLong());
    }
    @Test void participantsCannotWriteAndPlatformAdminCannotRead()throws Exception{
        String id=create();
        call("PUT","/"+id+"/info",info(0,"saved",List.of(member)),"d",token);
        assertEquals(200,call("POST","/"+id+"/roster-confirmations","{\"expectedRevision\":1}","c",token).statusCode());
        var forbidden=call("PUT","/"+id+"/info",info(2,"hijack",List.of()),"m",memberToken);
        assertEquals(403,forbidden.statusCode());
        DeclaredErrorResponse.assertMatches("/api/v1/meetings/{id}/info","put",forbidden);
        assertEquals(403,call("POST","/"+id+"/roster-confirmations","{\"expectedRevision\":2}","m",memberToken).statusCode());
        String admin="admin-"+UUID.randomUUID(),adminToken=identity(admin);
        j.update("INSERT INTO role_assignment VALUES (?,'PLATFORM_ADMIN',UTC_TIMESTAMP(6))",admin);
        assertEquals(403,call("GET","/"+id,"",null,adminToken).statusCode());
    }
    @Test void inactiveIdentityCannotGainGrant()throws Exception{
        String id=create();call("PUT","/"+id+"/info",info(0,"saved",List.of(member)),"d",token);
        j.update("UPDATE enterprise_identity SET status='DISABLED' WHERE enterprise_id=?",member);
        assertEquals(409,call("POST","/"+id+"/roster-confirmations","{\"expectedRevision\":1}","c",token).statusCode());
        assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM meeting_access_grant WHERE meeting_id=?",Integer.class,id));
        assertEquals(1L,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Long.class,id));
    }
    @Test void concurrentCreateSameKeyProducesOneAtomicAggregate()throws Exception{
        String key=UUID.randomUUID().toString();
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var gate=new java.util.concurrent.CountDownLatch(1);
            var a=pool.submit(()->{gate.await();return call("POST","","{}",key,token);});
            var b=pool.submit(()->{gate.await();return call("POST","","{}",key,token);});gate.countDown();
            var ar=a.get();var br=b.get();assertEquals(201,ar.statusCode());assertEquals(201,br.statusCode());assertEquals(body(ar),body(br));
            String id=body(ar).get("id").asText();
            assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM meeting WHERE owner_id=?",Integer.class,owner));
            assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_id=?",Integer.class,id));
            assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM task_outbox WHERE subject_id=?",Integer.class,id));
            assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM task_subject WHERE subject_id=?",Integer.class,id));
        }
    }
    @Test void unavailableCategoryDictionaryDoesNotBlockEmptyDraft()throws Exception{
        String id=create();String payload=info(0,"name",List.of()).replace("\"category\":null","\"category\":\"unapproved\"");
        var unavailable=call("PUT","/"+id+"/info",payload,"category",token);
        assertEquals(503,unavailable.statusCode());
        DeclaredErrorResponse.assertMatches("/api/v1/meetings/{id}/info","put",unavailable);
        assertEquals(0L,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Long.class,id));
    }
    Actor actor(String id){return new Actor(id,Status.ACTIVE,Set.of(Role.USER));}
    SaveInfo command(long revision,String name,List<String> people){return new SaveInfo(revision,name,null,null,null,people,List.of());}
    @Test void staleActorAndReplaysRecheckActiveIdentity()throws Exception{
        var service=app.getBean(MeetingService.class);var access=app.getBean(MeetingAccess.class);Actor old=actor(owner);
        var original=service.create(old,"create-key","trace");
        j.update("UPDATE enterprise_identity SET status='DISABLED' WHERE enterprise_id=?",owner);
        assertThrows(AccessDeniedException.class,()->service.create(old,"create-key","trace"));
        assertThrows(AccessDeniedException.class,()->service.save(old,original.id(),"save",command(0,"blocked",List.of()),"trace"));
        assertThrows(AccessDeniedException.class,()->access.require(old,original.id(),MeetingAccess.Action.READ));
        assertEquals(0L,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Long.class,original.id()));
    }
    @Test void failedAuditRollsBackAndSameKeyCanRetry()throws Exception{
        var service=app.getBean(MeetingService.class);Actor a=actor(owner);
        assertThrows(org.springframework.dao.DataAccessException.class,()->service.create(a,"rollback-create","x".repeat(256)));
        assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM meeting WHERE owner_id=?",Integer.class,owner));
        assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE actor_id=?",Integer.class,owner));
        var created=service.create(a,"rollback-create","trace");
        assertThrows(org.springframework.dao.DataAccessException.class,()->service.save(a,created.id(),"rollback-save",command(0,"rolled-back",List.of(member)),"x".repeat(256)));
        assertEquals(0L,j.queryForObject("SELECT business_version FROM task_subject WHERE subject_id=?",Long.class,created.id()));
        assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM meeting_info_revision WHERE meeting_id=?",Integer.class,created.id()));
        assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM roster_revision WHERE meeting_id=?",Integer.class,created.id()));
        assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM task_outbox WHERE subject_id=?",Integer.class,created.id()));
        assertEquals(1,service.save(a,created.id(),"rollback-save",command(0,"rolled-back",List.of(member)),"trace").revision());
    }
    void failureDuringFinalOutboxFlushRollsBackWholeConfirmation()throws Exception{
        var service=app.getBean(MeetingService.class);var roster=app.getBean(RosterService.class);Actor a=actor(owner);
        String id=service.create(a,"c","trace").id();service.save(a,id,"d",command(0,"saved",List.of(member)),"trace");
        long next=j.queryForObject("SELECT next_id FROM task_outbox_sequence WHERE id=1",Long.class);
        try{
            // Actual final UPDATE fails in MySQL when Java's next sequence overflows; no fake outbox adapter.
            j.update("UPDATE task_outbox_sequence SET next_id=? WHERE id=1",Long.MAX_VALUE);
            assertThrows(org.springframework.dao.DataAccessException.class,()->roster.confirm(a,id,"confirm",new ConfirmRoster(1L),"trace"));
        }finally{j.update("UPDATE task_outbox_sequence SET next_id=? WHERE id=1",next);}
        assertEquals(1L,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Long.class,id));
        assertEquals(1L,j.queryForObject("SELECT business_version FROM task_subject WHERE subject_id=?",Long.class,id));
        assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM meeting_access_grant WHERE meeting_id=?",Integer.class,id));
        assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM meeting_person_search WHERE meeting_id=?",Integer.class,id));
        assertEquals(2,j.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_id=?",Integer.class,id));
        assertEquals(2,j.queryForObject("SELECT COUNT(*) FROM task_outbox WHERE subject_id=?",Integer.class,id));
        assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM roster_revision WHERE meeting_id=? AND state='CONFIRMED'",Integer.class,id));
        assertEquals(2,roster.confirm(a,id,"confirm",new ConfirmRoster(1L),"trace").revision());
    }
    @Test void exactCaseAndTrailingSpaceIdsAreNotInterchangeable()throws Exception{
        String exact="Exact-"+UUID.randomUUID(),lower=exact.toLowerCase(Locale.ROOT),space=exact+" ";
        String exactToken=identity(exact),lowerToken=identity(lower),spaceToken=identity(space),id=create();
        call("PUT","/"+id+"/info",info(0,"saved",List.of(exact)),"d",token);
        assertEquals(200,call("POST","/"+id+"/roster-confirmations","{\"expectedRevision\":1}","c",token).statusCode());
        assertEquals(200,call("GET","/"+id,"",null,exactToken).statusCode());
        assertEquals(403,call("GET","/"+id,"",null,lowerToken).statusCode());
        assertEquals(403,call("GET","/"+id,"",null,spaceToken).statusCode());
    }
    @Test void forgedCrossMeetingAndNonMemberGrantsFailClosed()throws Exception{
        String first=create(),second=create();
        call("PUT","/"+first+"/info",info(0,"first",List.of(member)),"d",token);
        call("POST","/"+first+"/roster-confirmations","{\"expectedRevision\":1}","c",token);
        String foreign=j.queryForObject("SELECT roster_revision_id FROM meeting WHERE id=?",String.class,first);
        j.update("INSERT INTO meeting_access_grant VALUES (?,?,'READ',?,UTC_TIMESTAMP(6))",second,member,foreign);
        assertEquals(403,call("GET","/"+second,"",null,memberToken).statusCode());
        j.update("UPDATE meeting SET roster_revision_id=? WHERE id=?",foreign,second);
        assertEquals(403,call("GET","/"+second,"",null,memberToken).statusCode());
        String stranger="stranger-"+UUID.randomUUID(),strangerToken=identity(stranger);
        j.update("INSERT INTO meeting_access_grant VALUES (?,?,'READ',?,UTC_TIMESTAMP(6))",first,stranger,foreign);
        assertEquals(403,call("GET","/"+first,"",null,strangerToken).statusCode());
    }
    @Test void externalNamesAreSearchableOnlyAfterConfirmationAndNeverAuthorize()throws Exception{
        String id=create();String payload=info(0,"name",List.of()).replace("}",",\"externalRosterNames\":[\"访客-姓名\"]}");
        assertEquals(200,call("PUT","/"+id+"/info",payload,"d",token).statusCode());
        assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM meeting_person_search WHERE meeting_id=?",Integer.class,id));
        assertEquals(200,call("POST","/"+id+"/roster-confirmations","{\"expectedRevision\":1}","c",token).statusCode());
        assertEquals("访客-姓名",j.queryForObject("SELECT name_search FROM meeting_person_search WHERE meeting_id=?",String.class,id));
        assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM meeting_access_grant WHERE meeting_id=?",Integer.class,id));
    }
    @Test void publishedAndGovernedMeetingsRejectMutations()throws Exception{
        for(String state:List.of("PUBLISHED","WITHDRAWN","OBSOLETE","DELETING")){
            String id=create();
            if(state.equals("PUBLISHED"))j.update("UPDATE meeting SET stage='PUBLISHED',ever_published=1 WHERE id=?",id);
            else if(state.equals("DELETING"))j.update("UPDATE meeting SET lifecycle=? WHERE id=?",state,id);
            else j.update("UPDATE meeting SET governance_state=? WHERE id=?",state,id);
            int status=call("PUT","/"+id+"/info",info(0,"forbidden",List.of()),"d",token).statusCode();
            assertEquals(state.equals("PUBLISHED")?409:403,status);
            assertEquals(0L,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Long.class,id));
        }
    }
    @Test void concurrentDifferentBodiesAndCasHaveSingleWinner()throws Exception{
        for(boolean sameKey:List.of(true,false)){
            String id=create();
            try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
                var gate=new java.util.concurrent.CountDownLatch(1);
                var a=pool.submit(()->{gate.await();return call("PUT","/"+id+"/info",info(0,"one",List.of()),"k1",token);});
                var b=pool.submit(()->{gate.await();return call("PUT","/"+id+"/info",info(0,"two",List.of()),sameKey?"k1":"k2",token);});gate.countDown();
                assertEquals(List.of(200,409),java.util.stream.Stream.of(a.get().statusCode(),b.get().statusCode()).sorted().toList());
                assertEquals(1L,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Long.class,id));
                assertEquals(2,j.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_id=?",Integer.class,id));
            }
        }
    }
    @Test void accessCapabilitiesAreBoundToIssuerAndTransaction()throws Exception{
        String id=create();var access=app.getBean(MeetingAccess.class);var tx=app.getBean(TransactionRunner.class);Actor a=actor(owner);
        var held=tx.required(()->{var h=access.lock(a,id);access.require(h,MeetingAccess.Action.EDIT);return h;});
        assertThrows(IllegalStateException.class,()->access.require(held,MeetingAccess.Action.READ));
        tx.required(()->{
            assertThrows(IllegalArgumentException.class,()->access.require(held,MeetingAccess.Action.READ));
            assertThrows(IllegalArgumentException.class,()->access.require(new MeetingAccess.Locked(){},MeetingAccess.Action.READ));
            assertThrows(IllegalStateException.class,()->access.require(a,id,MeetingAccess.Action.READ));
            return null;
        });
    }
    @Test void actualDomainValidatorsRejectWrongVersionSubjectAndMeaning()throws Exception{
        String id=create(),other=create();var tx=app.getBean(TransactionRunner.class);var access=app.getBean(MeetingAccess.class);var outbox=app.getBean(JdbcOutbox.class);
        String info=j.queryForObject("SELECT info_revision_id FROM meeting WHERE id=?",String.class,id);
        for(var event:List.of(new EventDraft("MEETING_INFO_SAVED",null,new SubjectRef("MEETING",id),99,new Reference("MEETING_INFO",info)),
                new EventDraft("MEETING_CREATED",null,new SubjectRef("MEETING",other),0,new Reference("MEETING_INFO",info)),
                new EventDraft("MEETING_ROSTER_CONFIRMED",null,new SubjectRef("MEETING",id),0,new Reference("MEETING_INFO",info)))){
            assertThrows(IllegalArgumentException.class,()->tx.required(()->{access.lock(actor(owner),id);outbox.append(event);outbox.flush();return null;}));
        }
        assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM task_outbox WHERE subject_id=?",Integer.class,id));
    }
    @Test void malformedUnknownAndIncompletePayloadsAreRejectedWithoutMutation()throws Exception{
        String id=create();
        for(String payload:List.of("{}","{\"expectedRevision\":0,\"rosterDraftIds\":[],\"ownerId\":\"forged\"}","{\"expectedRevision\":0.1,\"rosterDraftIds\":[]}","{\"expectedRevision\":0,\"expectedRevision\":1,\"rosterDraftIds\":[]}"))
            assertEquals(400,call("PUT","/"+id+"/info",payload,UUID.randomUUID().toString(),token).statusCode());
        assertEquals(400,call("POST","","{\"ownerId\":\"forged\"}","forged",token).statusCode());
        assertEquals(400,call("PUT","/"+id+"/info",info(0,"saved",List.of()),null,token).statusCode());
        assertEquals(0L,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Long.class,id));
    }
    @Test void prelockedAccessCannotBeReusedAfterRootOrIdentityChanges()throws Exception{
        String id=create();var access=app.getBean(MeetingAccess.class);var tx=app.getBean(TransactionRunner.class);
        tx.required(()->{
            var held=access.lock(actor(owner),id);
            new JdbcTemplate(app.getBean(javax.sql.DataSource.class)).update("UPDATE meeting SET governance_state='WITHDRAWN' WHERE id=?",id);
            assertThrows(AccessDeniedException.class,()->access.require(held,MeetingAccess.Action.EDIT));
            return null;
        });
    }
    @Test void repeatedAccessPrelockAfterRootIsRejected()throws Exception{
        String id=create();var access=app.getBean(MeetingAccess.class);var tx=app.getBean(TransactionRunner.class);
        tx.required(()->{
            access.lock(actor(owner),id);
            assertThrows(IllegalStateException.class,()->access.lock(actor(member),id));
            return null;
        });
    }
    @Test void invalidUnicodeDoesNotCollapseDistinctRequestDigests()throws Exception{
        String id=create();String malformed=info(0,"placeholder",List.of()).replace("placeholder","\\uD800");
        assertEquals(400,call("PUT","/"+id+"/info",malformed,"unicode",token).statusCode());
        assertEquals(0L,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Long.class,id));
    }
    @Test void replayMismatchStillRequiresCurrentlyActiveActor()throws Exception{
        var service=app.getBean(MeetingService.class);Actor a=actor(owner);String id=service.create(a,"create","trace").id();
        service.save(a,id,"save",command(0,"one",List.of()),"trace");
        j.update("UPDATE enterprise_identity SET status='DISABLED' WHERE enterprise_id=?",owner);
        assertThrows(AccessDeniedException.class,()->service.save(a,id,"save",command(0,"two",List.of()),"trace"));
    }
    @Test void emptyRosterConfirmsBeforeAnyOtherInformation()throws Exception{
        String id=create();var r=call("POST","/"+id+"/roster-confirmations","{\"expectedRevision\":0}","empty",token);
        assertEquals(200,r.statusCode());assertEquals(1,body(r).get("revision").asLong());
        var saved=body(call("GET","/"+id,"",null,token));assertTrue(saved.get("name").isNull());assertTrue(saved.get("category").isNull());
        assertEquals(0,saved.get("confirmedRoster").size());assertTrue(saved.get("rosterDraft").isNull());
        assertEquals(409,call("POST","/"+id+"/roster-confirmations","{\"expectedRevision\":1}","another",token).statusCode());
    }
    @Test void saveReplayReturnsOriginalRevisionWithoutReplacingCurrentSavedValue()throws Exception{
        String id=create(),original=info(0,"original",List.of());
        call("PUT","/"+id+"/info",original,"first",token);
        call("PUT","/"+id+"/info",info(1,"latest",List.of()),"second",token);
        var replay=call("PUT","/"+id+"/info",original,"first",token);assertEquals(200,replay.statusCode());assertEquals(1,body(replay).get("revision").asLong());
        assertEquals("latest",body(call("GET","/"+id,"",null,token)).get("name").asText());
        assertEquals(3,j.queryForObject("SELECT COUNT(*) FROM task_outbox WHERE subject_id=?",Integer.class,id));
    }
    @Test void legacyTimestampedAclNeverGrantsAnyMeetingAction()throws Exception{
        String id=create();
        for(String action:List.of("READ","EDIT","MANAGE"))j.update("INSERT INTO meeting_acl VALUES (?,?,?,UTC_TIMESTAMP(6))",id,member,action);
        assertEquals(403,call("GET","/"+id,"",null,memberToken).statusCode());
        assertEquals(403,call("PUT","/"+id+"/info",info(0,"hijack",List.of()),"legacy",memberToken).statusCode());
        assertEquals(403,call("POST","/"+id+"/roster-confirmations","{\"expectedRevision\":0}","legacy",memberToken).statusCode());
    }
    @Test void configuredTransportBudgetRejectsWholeRequestWithoutTruncation()throws Exception{
        var previous=app;
        try(var limited=startApp("--meeting.http.max-request-bytes=1024")){
            app=limited;String id=create();
            var tooLarge=call("PUT","/"+id+"/info",info(0,"x".repeat(2048),List.of()),"large",token);
            assertEquals(413,tooLarge.statusCode());
            DeclaredErrorResponse.assertMatches("/api/v1/meetings/{id}/info","put",tooLarge);
            assertEquals(0L,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Long.class,id));
            assertEquals(200,call("PUT","/"+id+"/info",info(0,"short",List.of()),"large",token).statusCode());
        }finally{app=previous;}
    }
    @Test void prelockedCapabilityCannotBePresentedToAnotherIssuer()throws Exception{
        String id=create();var original=app.getBean(MeetingAccess.class);var tx=app.getBean(TransactionRunner.class);
        var source=app.getBean(javax.sql.DataSource.class);
        var foreign=new JdbcMeetingAccess(source,tx,app.getBean(JdbcSubjectFences.class));
        tx.required(()->{var held=original.lock(actor(owner),id);assertThrows(IllegalArgumentException.class,()->foreign.require(held,MeetingAccess.Action.READ));return null;});
    }
}
