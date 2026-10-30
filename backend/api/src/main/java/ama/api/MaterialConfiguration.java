package ama.api;

import ama.material.MaterialParser;
import ama.storage.FileUploads;
import ama.transcript.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="processing.enabled",havingValue="true")
class MaterialConfiguration {
 @Bean JdbcMaterials materialService(ProcessingConfiguration.Runtime processing,FileUploads uploads,ObjectProvider<ControlledKnowledgeClient> knowledge,Environment env){if(processing.components()==null)throw new IllegalStateException("Material authority unavailable");return new JdbcMaterials(processing.components().authority(),uploads,knowledge.getIfAvailable(),new MaterialParser.Budget(env.getProperty("materials.bounds.source-bytes",Long.class,32L*1024*1024),env.getProperty("materials.bounds.utf16",Long.class,2L*1024*1024),env.getProperty("materials.bounds.units",Integer.class,10000)));}
}
