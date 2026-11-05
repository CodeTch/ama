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
 @Bean(destroyMethod="close") @ConditionalOnProperty(name="processing.material-enhancement-origin") ama.model.HttpMaterialEnhancement materialEnhancementModel(Environment env){return new ama.model.HttpMaterialEnhancement(java.net.URI.create(env.getRequiredProperty("processing.material-enhancement-origin")));}
 @Bean @ConditionalOnProperty(name="processing.material-enhancement-origin") JdbcMaterialEnhancements materialEnhancements(ProcessingConfiguration.Runtime processing,TranscriptConfiguration.Runtime transcript,ama.model.HttpMaterialEnhancement model){if(processing.components()==null||transcript.service()==null)throw new IllegalStateException("Material enhancement requires saved transcript authority");return new JdbcMaterialEnhancements(processing.components().authority(),transcript.service(),model);}
 @Bean(destroyMethod="close") @ConditionalOnProperty(name="processing.material-enhancement-origin") JdbcRecordingProcessing recordingProcessing(ProcessingConfiguration.Runtime processing,RecordingTranscriptRuntime recording,JdbcProcessingIntake intake,TranscriptConfiguration.Runtime transcript,JdbcMaterialEnhancements enhancements){
  if(recording.service()==null)throw new IllegalStateException("Recording processing requires archive sources");
  var service=new JdbcRecordingProcessing(processing.components().authority(),recording.service(),intake,transcript.service(),enhancements);service.start();return service;
 }
 @Bean(destroyMethod="close") @ConditionalOnProperty(name="processing.material-enhancement-origin") OfflineMaterialProcessing offlineProcessing(ProcessingConfiguration.Runtime runtime,JdbcMaterialEnhancements enhancements,JdbcProcessingIntake intake,JdbcRecordingProcessing recording){var service=new OfflineMaterialProcessing(runtime.components().authority(),enhancements,intake,recording);service.start();return service;}
 @Bean JdbcMaterials materialService(ProcessingConfiguration.Runtime processing,FileUploads uploads,ObjectProvider<ControlledKnowledgeClient> knowledge,Environment env){if(processing.components()==null)throw new IllegalStateException("Material authority unavailable");return new JdbcMaterials(processing.components().authority(),uploads,knowledge.getIfAvailable(),new MaterialParser.Budget(env.getProperty("materials.bounds.source-bytes",Long.class,32L*1024*1024),env.getProperty("materials.bounds.utf16",Long.class,2L*1024*1024),env.getProperty("materials.bounds.units",Integer.class,10000)));}
}
