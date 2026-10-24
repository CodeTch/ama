package ama.api;
import ama.transcript.ControlledKnowledgeClient;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
@Configuration(proxyBeanMethods=false)
class KnowledgeConfiguration {
 @Bean(destroyMethod="close") @ConditionalOnProperty(name="publication.client-identities")
 ControlledKnowledgeClient controlledKnowledgeClient(Environment env)throws Exception{return new ControlledKnowledgeClient(java.nio.file.Path.of(env.getRequiredProperty("publication.client-identities")),java.net.URI.create(env.getRequiredProperty("publication.origin")),env.getRequiredProperty("publication.mode"));}
}
