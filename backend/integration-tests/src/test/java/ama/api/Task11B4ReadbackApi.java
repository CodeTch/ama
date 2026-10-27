package ama.api;

import ama.storage.*;
import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.*;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

/** Actual document controllers with a fresh TLS API; no native configure/admin RPC or Worker. */
public final class Task11B4ReadbackApi {
 @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
 @EnableAutoConfiguration(excludeName={"org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration","org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration","org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"})
 @Import({Task11B3LifecycleApi.Wiring.class,KnowledgeConfiguration.class,SecurityConfiguration.class,PublicationDiscoveryController.class,PublicationDocumentController.class,PublicationErrors.class,PublicationReadRequests.class})
 static class Server {}
 public static ConfigurableApplicationContext start(DataSource source,Task11B3LifecycleApi.Settings settings,Path pki,String password){
  var properties=new HashMap<String,Object>(Task11B3ContinuationApi.properties(settings));
  properties.put("transcript.candidates.installation","AUTHORITY_CONTROLLED");properties.put("transcript.publication.installation","AUTHORITY_CONTROLLED");properties.put("transcript.transitions.installation-version","2");
  properties.put("server.ssl.enabled",true);properties.put("server.ssl.key-store",pki.resolve("server.p12").toUri().toString());properties.put("server.ssl.key-store-type","PKCS12");properties.put("server.ssl.key-store-password",password);
  // Official-document reads need persisted publication provenance, not original-file I/O.
  // An empty catalog cannot confer native source authority; no FileConfiguration.configure call.
  var definitions=new FileBackendDefinitions(List.of(),null);
  return new SpringApplicationBuilder(Server.class).properties(properties).initializers(context->Task11B3LifecycleApi.infrastructure((GenericApplicationContext)context,source,definitions)).run("--server.address=127.0.0.1","--server.port=0","--management.server.address=127.0.0.1","--management.server.port=0","--management.endpoint.health.group.ready.include=ping","--spring.flyway.enabled=false","--spring.sql.init.mode=never","--logging.level.root=ERROR","--identity.allowed-origin=https://task11-b4.example.invalid");
 }
 private Task11B4ReadbackApi(){}
}
