package ama.localdev;

import ama.api.SessionCookies;
import ama.audit.JdbcAuditAdapter;
import ama.contract.AuditContracts.AuditEvent;
import ama.contract.IdentityContracts.IssuedSession;
import ama.db.TransactionRunner;
import ama.identity.SessionPort;
import jakarta.servlet.http.*;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.*;

/** This controller is imported only by LocalApplication, on its separate development classpath. */
@RestController
public class LocalLogin {
    private final JdbcTemplate jdbc;
    private final DataSource source;
    private final Environment environment;
    private final SessionPort sessions;
    private final String origin;
    private static final SecureRandom RANDOM=new SecureRandom();

    public LocalLogin(DataSource source,Environment environment,SessionPort sessions) {
        this.source=source;this.jdbc=new JdbcTemplate(source);this.environment=environment;this.sessions=sessions;
        origin=environment.getRequiredProperty("identity.allowed-origin");
        if(!Set.of("http://127.0.0.1:5173","http://localhost:5173").contains(origin))throw new IllegalStateException("Loopback development Origin required");
    }

    @Bean @Order(0) SecurityFilterChain localLoginSecurity(HttpSecurity http)throws Exception {
        return http.securityMatcher("/api/dev/**").csrf(c->c.disable())
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(c->c.disable()).authorizeHttpRequests(a->a.anyRequest().permitAll()).build();
    }

    private boolean loopback(HttpServletRequest request) {
        try{return request.getLocalPort()==environment.getProperty("local.server.port",Integer.class,-1)
            &&InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress();}
        catch(Exception invalid){return false;}
    }

    @GetMapping(value="/api/dev/login",produces="text/html;charset=UTF-8")
    ResponseEntity<String> page(HttpServletRequest request) {
        if(!loopback(request))return ResponseEntity.status(403).body("仅限本地开发环境");
        return html(200,"""
            <!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>本地开发登录 · 智能会议助手</title><style>
            *{box-sizing:border-box}body{font:16px system-ui,sans-serif;background:#f3f5f9;color:#182239;margin:0;min-height:100vh;display:grid;place-items:center;padding:24px}
            main{background:white;border:1px solid #dce2ed;border-radius:16px;padding:36px;width:min(100%,460px);box-shadow:0 12px 40px #1822390c}
            h1{font-size:26px;margin:10px 0 16px}p{color:#607086;line-height:1.7}.badge{font-size:13px;color:#385cb0;background:#edf2ff;padding:5px 9px;border-radius:6px}
            label{display:block;margin:20px 0 8px}select,input,button{font:inherit;width:100%;padding:12px;border:1px solid #cbd4e3;border-radius:8px}button{margin-top:24px;background:#244b98;color:white;border:0;cursor:pointer}
            a{color:#244b98}small{display:block;line-height:1.7;color:#607086;margin-top:20px}</style>
            <main><span class="badge">仅限本地开发</span><h1>登录智能会议助手</h1><p>使用独立本地数据库。示例账号与会议用于开发验证。</p>
            <form method="post" action="/api/dev/login"><label for="username">开发账号</label><select id="username" name="username">
            <option value="dev-owner">dev-owner · 开发负责人</option><option value="dev-member">dev-member · 开发参会人</option><option value="dev-viewer">dev-viewer · 开发访客</option></select>
            <label for="password">密码（任意填写，可留空）</label><input id="password" name="password" type="password" autocomplete="off" placeholder="不校验密码，可直接登录">
            <button type="submit">登录本地环境</button></form><small>本地开发登录不校验密码，选择账号即可登录。<br>本页面仅由专用开发启动器提供。</small></main></html>
            """);
    }

    @PostMapping(value="/api/dev/login",consumes="application/x-www-form-urlencoded")
    ResponseEntity<String> login(@RequestParam String username,HttpServletRequest request) throws Exception {
        if(!loopback(request)||Collections.list(request.getHeaders("Origin")).size()!=1||!origin.equals(request.getHeader("Origin")))
            return html(403,"登录来源无效。请从本地开发页面登录。");
        if(username.length()>255||!LocalApplication.USERS.containsKey(username))
            return html(401,"开发账号不存在。<a href=\"/api/dev/login\">返回登录</a>");
        if(request.getCookies()!=null)for(var cookie:request.getCookies())
            if(SessionCookies.NAME.equals(cookie.getName()))sessions.revoke(cookie.getValue(),UUID.randomUUID().toString());
        String token=random(),csrf=random();Instant now=Instant.now(),expires=now.plus(Duration.ofHours(24));
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
        new TransactionRunner(source).required(()->{
            Long generation=jdbc.queryForObject("SELECT session_generation FROM enterprise_identity WHERE enterprise_id=? AND status='ACTIVE' FOR UPDATE",Long.class,username);
            jdbc.update("INSERT INTO identity_session(token_hash,enterprise_id,session_generation,csrf_token,issued_at,expires_at) VALUES (?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)+INTERVAL 24 HOUR)",hash,username,generation,csrf);
            new JdbcAuditAdapter(source).append(new AuditEvent(username,"LOCAL_DEV_LOGIN",username,generation,"SUCCESS",UUID.randomUUID().toString(),now));
            return null;
        });
        return ResponseEntity.status(303).location(URI.create(origin+"/meetings"))
            .header("Cache-Control","no-store").header("Set-Cookie",SessionCookies.issue(new IssuedSession(token,csrf,expires),now)).build();
    }

    private static String random(){byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    private ResponseEntity<String> html(int status,String body){return ResponseEntity.status(status)
        .header("Cache-Control","no-store").header("Content-Type","text/html;charset=UTF-8")
        .header("Content-Security-Policy","default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'")
        .body(body);}
}
