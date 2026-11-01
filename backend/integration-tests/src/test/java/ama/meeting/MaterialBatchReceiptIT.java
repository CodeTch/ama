package ama.meeting;
import java.util.*;
import ama.transcript.JdbcMaterials;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MaterialBatchReceiptIT extends MaterialAuthorityGateIT {
 @Test void durableBatchVersionRejectionSurvivesReplayAndCannotAdmitChangedBytes()throws Exception{
  {
   var source=j.getDataSource();var registry=org.mockito.Mockito.mock(ama.storage.FileRegistry.class);
   var authority=org.mockito.Mockito.mock(ama.transcript.JdbcTranscriptAuthority.class,call->switch(call.getMethod().getName()){case "dataSource"->source;case "transactionRunner"->tx;case "savedAccess"->access;case "fileRegistry"->registry;default->org.mockito.Mockito.RETURNS_DEFAULTS.answer(call);});
   var materials=new JdbcMaterials(authority,org.mockito.Mockito.mock(ama.storage.FileUploads.class),null,new ama.material.MaterialParser.Budget(1024,1024,10));
   var selections=List.of(new JdbcMaterials.Selection(null,"library","document","a".repeat(64)));String key="original-version-rejection";
   String scope;
   // Use the production request digest, obtained by the same public canonical JSON profile.
   var json=new com.fasterxml.jackson.databind.ObjectMapper();var value=Map.of("expectedRevisionToken","0","items",List.of(Map.of("libraryId","library","documentId","document","version","a".repeat(64))));
   String hash=ama.material.MaterialDocumentParser.sha(ama.config.CanonicalJson.encode(ama.model.BoundedModelJson.parse(json.writeValueAsBytes(value),65536),65536,()->false));
   var digestMethod=Class.forName("ama.transcript.JdbcProcessingIntake").getDeclaredMethod("digest",String[].class);digestMethod.setAccessible(true);scope=(String)digestMethod.invoke(null,(Object)new String[]{owner.enterpriseId(),meeting,"MATERIAL_BATCH",key});
   j.update("INSERT INTO material_batch_receipt VALUES (?,?,?,?,UTC_TIMESTAMP(6))",meeting,scope,hash,"{\"rejection\":\"MATERIAL_BATCH_VERSION_REJECTED\"}");
   var error=assertThrows(ama.contract.TranscriptContracts.Failure.class,()->materials.addBatch(owner,meeting,key,0,selections,UUID.randomUUID().toString()));assertEquals("MATERIAL_BATCH_VERSION_REJECTED",error.code());
   assertEquals("IDEMPOTENCY_CONFLICT",assertThrows(ama.contract.TranscriptContracts.Failure.class,()->materials.addBatch(owner,meeting,key,1,selections,UUID.randomUUID().toString())).code());
   assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM material_reference WHERE meeting_id=?",Integer.class,meeting));
  }
 }
}
