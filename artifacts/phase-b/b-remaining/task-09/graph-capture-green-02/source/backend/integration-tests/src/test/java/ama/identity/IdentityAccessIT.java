package ama.identity;

import ama.api.ApiApplication;
import ama.contract.IdentityContracts.*;
import ama.db.SchemaMigrator;
import ama.support.MysqlTestDatabase;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Prerequisite only: no synthetic meeting tables or legacy ACL authorization. Final ACL belongs to V008. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IdentityAccessIT {
    private ConfigurableApplicationContext first, second;
    private JdbcTemplate jdbc;
    private static final String TOKEN = "A".repeat(43), CSRF = "B".repeat(43);
    private static final String ORIGIN = "https://meetings.example.invalid";
    @BeforeAll void startNodes() {
        var source = MysqlTestDatabase.reset();
        new SchemaMigrator(source).migrate();
        jdbc = new JdbcTemplate(source);
        first = startNode(true); second = startNode(true);
    }
    @AfterAll void stopNodes() {
        if (second != null) second.close();
        if (first != null) first.close();
    }
    @BeforeEach void seedIdentities() throws Exception {
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("DELETE FROM identity_session");
        jdbc.update("DELETE FROM role_assignment");
        jdbc.update("DELETE FROM enterprise_identity");
        jdbc.update("DELETE FROM department");
        jdbc.update("INSERT INTO department VALUES ('dept-1','研发',NULL,UTC_TIMESTAMP(6)),('dept-2','财务',NULL,UTC_TIMESTAMP(6))");
        for (String id : List.of("employee-1","employee-2","Employee-1","employee-1 ","admin-1")) {
            jdbc.update("INSERT INTO enterprise_identity(enterprise_id,display_name,department_id,status,session_generation,verified_at) VALUES (?,'王明',?,'ACTIVE',0,'2026-10-01')",
                    id, id.equals("employee-2") ? "dept-2" : "dept-1");
            jdbc.update("INSERT INTO role_assignment VALUES (?,?,UTC_TIMESTAMP(6))", id, id.equals("admin-1") ? "PLATFORM_ADMIN" : "USER");
        }
        seedSession(TOKEN,"employee-1");
    }
    private void seedSession(String token,String actor) throws Exception {
        var hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
        jdbc.update("INSERT INTO identity_session(token_hash,enterprise_id,session_generation,csrf_token,issued_at,expires_at,revoked_at) SELECT ?,enterprise_id,session_generation,?,UTC_TIMESTAMP(6),DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 HOUR),NULL FROM enterprise_identity WHERE enterprise_id=?",hash,CSRF,actor);
    }
    private ConfigurableApplicationContext startNode(boolean controlled) {
        var builder = controlled ? new SpringApplicationBuilder(ApiApplication.class,ControlledIdentityConfiguration.class).profiles("test") : new SpringApplicationBuilder(ApiApplication.class);
        return builder.run("--server.address=127.0.0.1","--server.port=0","--management.server.port=0","--logging.level.root=ERROR",
                "--spring.datasource.url="+System.getenv("AMA_TEST_DATABASE_URL"),"--spring.datasource.username="+System.getenv("AMA_TEST_DATABASE_USERNAME"),
                "--spring.datasource.password="+System.getenv("AMA_TEST_DATABASE_PASSWORD"),"--identity.allowed-origin="+ORIGIN);
    }
    private HttpResponse<String> request(ConfigurableApplicationContext node,String path,String method,String token,String csrf,String origin) throws Exception {
        return requestPort(Integer.parseInt(node.getEnvironment().getProperty("local.server.port")),path,method,token,csrf,origin);
    }
    private HttpResponse<String> requestPort(int port,String path,String method,String token,String csrf,String origin) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path));
        if(token!=null)b.header("Cookie","__Host-AMA_SESSION="+token);
        if(csrf!=null)b.header("X-CSRF-Token",csrf);
        if(origin!=null)b.header("Origin",origin);
        return HttpClient.newHttpClient().send(b.method(method,HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void anonymousSessionHasSafeBusinessError() throws Exception {
        var r=request(first,"/api/v1/session","GET",null,null,null);
        assertEquals(401,r.statusCode()); assertTrue(r.body().contains("UNAUTHENTICATED")); assertTrue(r.body().contains("traceId"));
        assertEquals("no-store",r.headers().firstValue("Cache-Control").orElseThrow());
    }
    @Test void sessionRevocationWorksOnBothNodes() throws Exception {
        for(var node:List.of(first,second))assertEquals(200,request(node,"/api/v1/session","GET",TOKEN,null,null).statusCode());
        var logout=request(first,"/api/v1/session/logout","POST",TOKEN,CSRF,ORIGIN); assertEquals(204,logout.statusCode());
        String cookie=logout.headers().firstValue("Set-Cookie").orElseThrow();
        for(String flag:List.of("Max-Age=0","Secure","HttpOnly","SameSite=Lax","Path=/"))assertTrue(cookie.contains(flag));
        for(var node:List.of(first,second))assertEquals(401,request(node,"/api/v1/session","GET",TOKEN,null,null).statusCode());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action='SESSION_REVOKE'",Integer.class));
    }
    @Test void disableThenReenableNeverRevivesOldGeneration() throws Exception {
        assertEquals(200,request(first,"/api/v1/session","GET",TOKEN,null,null).statusCode());
        var lifecycle=first.getBean(IdentityLifecyclePort.class); var t=Instant.parse("2026-10-21T16:00:00.123456999Z");
        lifecycle.setStatus("employee-1",Status.DISABLED,t,"disable-trace");
        for(var node:List.of(first,second))assertEquals(401,request(node,"/api/v1/session","GET",TOKEN,null,null).statusCode());
        lifecycle.setStatus("employee-1",Status.ACTIVE,t.plusSeconds(1),"enable-trace");
        assertEquals(2L,jdbc.queryForObject("SELECT session_generation FROM enterprise_identity WHERE enterprise_id='employee-1'",Long.class));
        for(var node:List.of(first,second))assertEquals(401,request(node,"/api/v1/session","GET",TOKEN,null,null).statusCode());
        seedSession("F".repeat(43),"employee-1"); assertEquals(200,request(second,"/api/v1/session","GET","F".repeat(43),null,null).statusCode());
        assertEquals("2026-10-21 16:00:01.123456",jdbc.queryForObject("SELECT CAST(verified_at AS CHAR) FROM enterprise_identity WHERE enterprise_id='employee-1'",String.class));
        assertEquals(List.of("SYSTEM_IDENTITY_SYNC", "SYSTEM_IDENTITY_SYNC"),
                jdbc.queryForList("SELECT actor_id FROM audit_event WHERE action='IDENTITY_STATUS' ORDER BY id", String.class),
                "Server lifecycle verification must not impersonate the affected employee as its actor");
    }
    @Test void logoutAllOnlyAdvancesAuthenticatedIdentity() throws Exception {
        seedSession("D".repeat(43),"employee-2"); seedSession("E".repeat(43),"employee-1");
        assertEquals(204,request(first,"/api/v1/session/logout-all","POST",TOKEN,CSRF,ORIGIN).statusCode());
        for(var node:List.of(first,second)){
            assertEquals(401,request(node,"/api/v1/session","GET",TOKEN,null,null).statusCode());
            assertEquals(401,request(node,"/api/v1/session","GET","E".repeat(43),null,null).statusCode());
            assertEquals(200,request(node,"/api/v1/session","GET","D".repeat(43),null,null).statusCode());
        }
    }
    @Test void expiredAndForgedCookiesCannotAuthenticate() throws Exception {
        jdbc.update("UPDATE identity_session SET issued_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 2 HOUR),expires_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 SECOND)");
        assertEquals(401,request(second,"/api/v1/session","GET",TOKEN,null,null).statusCode());
        assertEquals(401,request(first,"/api/v1/session","GET","C".repeat(43),null,null).statusCode());
    }
    @Test void exactStableIdsAndRolesNeverComeFromBrowserClaims() throws Exception {
        String variant="C".repeat(43); seedSession(variant,"employee-1 ");
        var r=request(first,"/api/v1/session?enterpriseId=admin-1&role=PLATFORM_ADMIN","GET",variant,null,null);
        assertEquals(200,r.statusCode()); assertTrue(r.body().contains("\"enterpriseId\":\"employee-1 \""));
        assertFalse(r.body().contains("PLATFORM_ADMIN")); assertFalse(r.body().contains(variant)); assertTrue(r.body().contains(CSRF));
        assertEquals(5,jdbc.queryForObject("SELECT COUNT(*) FROM enterprise_identity",Integer.class));
    }
    @Test void writesRequireBothExactOriginAndSessionCsrf() throws Exception {
        for(String origin:new String[]{"","https://attacker.invalid",ORIGIN+".attacker.invalid","null"})
            assertEquals(403,request(first,"/api/v1/session/logout","POST",TOKEN,CSRF,origin.isEmpty()?null:origin).statusCode());
        assertEquals(403,request(first,"/api/v1/session/logout","POST",TOKEN,null,ORIGIN).statusCode());
        assertEquals(403,request(first,"/api/v1/session/logout","POST",TOKEN,"wrong",ORIGIN).statusCode());
        assertEquals(200,request(second,"/api/v1/session","GET",TOKEN,null,null).statusCode());
    }
    @Test void ambiguousOriginHeadersCannotRevokeSession() throws Exception {
        int port=Integer.parseInt(first.getEnvironment().getProperty("local.server.port"));
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/session/logout"))
                .header("Cookie","__Host-AMA_SESSION="+TOKEN).header("Origin",ORIGIN).header("Origin","https://attacker.invalid")
                .header("X-CSRF-Token",CSRF).POST(HttpRequest.BodyPublishers.noBody()).build();
        assertEquals(403,HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(200,request(second,"/api/v1/session","GET",TOKEN,null,null).statusCode());
    }
    @Test void ambiguousCsrfHeadersCannotRevokeSession() throws Exception {
        int port=Integer.parseInt(first.getEnvironment().getProperty("local.server.port"));
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/session/logout"))
                .header("Cookie","__Host-AMA_SESSION="+TOKEN).header("Origin",ORIGIN)
                .header("X-CSRF-Token",CSRF).header("X-CSRF-Token","wrong").POST(HttpRequest.BodyPublishers.noBody()).build();
        assertEquals(403,HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString()).statusCode());
    }
    @Test void directoryKeepsSameNamesDistinctAndPagesByExactId() throws Exception {
        var r=request(first,"/api/v1/directory/people?query=%E7%8E%8B%E6%98%8E&limit=10","GET",TOKEN,null,null);
        assertEquals(200,r.statusCode()); for(String s:List.of("employee-1","employee-2","研发","财务"))assertTrue(r.body().contains(s));
        var d=first.getBean(DirectoryPort.class); var page=d.search("王明",null,1);
        assertEquals(1,page.items().size()); assertNotNull(page.nextCursor());
        assertNotEquals(page.items().getFirst().stableId(),d.search("王明",page.nextCursor(),1).items().getFirst().stableId());
        assertTrue(d.search("%",null,100).items().isEmpty());
        for(String q:List.of("limit=0","limit=101","cursor=bad!"))
            assertEquals(400,request(first,"/api/v1/directory/people?"+q,"GET",TOKEN,null,null).statusCode());
    }
    @Test void managementListenerRejectsBusinessSessionEvenWithCookie() throws Exception {
        int management=Integer.parseInt(second.getEnvironment().getProperty("local.management.port"));
        assertEquals(403,requestPort(management,"/api/v1/session","GET",TOKEN,null,null).statusCode());
        assertEquals(403,request(first,"/internal/health/live","GET",TOKEN,null,null).statusCode());
        assertEquals(200,requestPort(management,"/internal/health/live","GET",TOKEN,null,null).statusCode());
    }
    @Test void productionDirectoryAndIssuerRemainUnavailable() throws Exception {
        try(var app=startNode(false)){
            assertThrows(ProviderUnavailableException.class,()->app.getBean(SessionIssuer.class).issueForVerifiedIdentity("employee-1",java.time.Duration.ofHours(1)));
            var r=request(app,"/api/v1/directory/people","GET",TOKEN,null,null);
            assertEquals(503,r.statusCode()); assertTrue(r.body().contains("IDENTITY_PROVIDER_UNAVAILABLE")); assertTrue(r.body().contains("traceId"));
        }
    }
    @Test void revocationRollsBackWhenAuditCannotBeStored() throws Exception {
        var sessions=first.getBean(SessionPort.class);
        assertThrows(org.springframework.dao.DataAccessException.class,()->sessions.revoke(TOKEN,"x".repeat(256)));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM identity_session WHERE revoked_at IS NOT NULL",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM audit_event",Integer.class));
        assertEquals(200,request(second,"/api/v1/session","GET",TOKEN,null,null).statusCode());
    }
    @Test void generationAndDisableRollBackWithTheirAudit() {
        assertThrows(org.springframework.dao.DataAccessException.class,()->first.getBean(SessionPort.class).revokeAll("employee-1","x".repeat(256)));
        assertThrows(org.springframework.dao.DataAccessException.class,()->first.getBean(IdentityLifecyclePort.class).setStatus("employee-1",Status.DISABLED,Instant.parse("2026-10-21T16:00:00Z"),"x".repeat(256)));
        assertEquals(0L,jdbc.queryForObject("SELECT session_generation FROM enterprise_identity WHERE enterprise_id='employee-1'",Long.class));
        assertEquals("ACTIVE",jdbc.queryForObject("SELECT status FROM enterprise_identity WHERE enterprise_id='employee-1'",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM audit_event",Integer.class));
    }
    @Test void concurrentGlobalRevocationsSerializeWithoutLosingGeneration() throws Exception {
        try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            var one=workers.submit(()->{start.await();first.getBean(SessionPort.class).revokeAll("employee-1","trace-one");return null;});
            var two=workers.submit(()->{start.await();second.getBean(SessionPort.class).revokeAll("employee-1","trace-two");return null;});
            start.countDown(); one.get(5,java.util.concurrent.TimeUnit.SECONDS); two.get(5,java.util.concurrent.TimeUnit.SECONDS);
        }
        assertEquals(2L,jdbc.queryForObject("SELECT session_generation FROM enterprise_identity WHERE enterprise_id='employee-1'",Long.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action='SESSION_REVOKE_ALL'",Integer.class));
        assertEquals(401,request(second,"/api/v1/session","GET",TOKEN,null,null).statusCode());
    }
    @Test void restartingNodeCannotRestoreRevokedSession() throws Exception {
        assertEquals(204,request(first,"/api/v1/session/logout","POST",TOKEN,CSRF,ORIGIN).statusCode());
        second.close(); second=startNode(true);
        assertEquals(401,request(second,"/api/v1/session","GET",TOKEN,null,null).statusCode());
    }
    @Test void legacyAdminFlagNeverBecomesPlatformPrivilege() throws Exception {
        jdbc.update("UPDATE enterprise_identity SET platform_admin=TRUE WHERE enterprise_id='employee-1'");
        for(var node:List.of(first,second)) assertFalse(request(node,"/api/v1/session","GET",TOKEN,null,null).body().contains("PLATFORM_ADMIN"));
        jdbc.update("UPDATE role_assignment SET role='PLATFORM_ADMIN' WHERE enterprise_id='employee-1'");
        for(var node:List.of(first,second)) assertTrue(request(node,"/api/v1/session","GET",TOKEN,null,null).body().contains("PLATFORM_ADMIN"));
    }
    @Test void browserIdentityHeadersAndBasicCredentialsNeverAuthenticate() throws Exception {
        int port=Integer.parseInt(first.getEnvironment().getProperty("local.server.port"));
        var attack=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/session"))
                .header("X-Enterprise-Id","admin-1").header("X-Role","PLATFORM_ADMIN").header("Authorization","Basic YWRtaW46cGFzc3dvcmQ=").GET().build();
        assertEquals(401,HttpClient.newHttpClient().send(attack,HttpResponse.BodyHandlers.ofString()).statusCode());
    }
    @TestConfiguration(proxyBeanMethods=false) @Profile("test")
    static class ControlledIdentityConfiguration {
        @Bean @Primary DirectoryPort controlledDirectory(javax.sql.DataSource source){return new JdbcDirectoryProjection(source);}
    }
}
