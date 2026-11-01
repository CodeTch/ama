package ama.api;
import ama.transcript.*;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="publication.governance-enabled",havingValue="true")
class GovernanceConfiguration {
 @Bean(initMethod="start",destroyMethod="close")
 JdbcPublicationGovernance publicationGovernance(ProcessingConfiguration.Runtime processing,ControlledKnowledgeClient client,Environment env){
  if(processing.components()==null||!env.getRequiredProperty("publication.enabled",Boolean.class))throw new IllegalStateException("Genuine publication governance installation required");
  return new JdbcPublicationGovernance(processing.components().authority(),client,ConfiguredMinutes.acquisition(env));
 }
 @Bean(initMethod="start",destroyMethod="close")
 JdbcMaterialAuthority materialAuthority(ProcessingConfiguration.Runtime processing,ControlledKnowledgeClient client,JdbcPublicationGovernance governance){return new JdbcMaterialAuthority(processing.components().authority(),client,governance);}

}
