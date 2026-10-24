package ama.storage;
import ama.transcript.*;
import ama.config.*;
import ama.contract.ConfigArchiveContracts.Ref;
import ama.contract.IdentityContracts.Actor;
import ama.contract.MinutesContracts.*;
import ama.contract.TaskContracts.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
class Task09GenerationPreparationIT extends Task09MinutesFixture {
 @Test void realConfirmedAndInitializedFactsProduceProspectiveExactModelRequestWithoutInputRow()throws Exception {
  confirmed();install();initialize(service(),"initial",3);var before=j.queryForMap("SELECT * FROM meeting WHERE id=?",meeting);var authority=ama.api.Task09RuntimeProbe.components(app).authority();
  Class<?> type=assertDoesNotThrow(()->Class.forName("ama.transcript.JdbcMinutesGenerationPreparation"));Object preparer=type.getConstructor(JdbcTranscriptAuthority.class,Supplier.class,String.class,Ref.class,Ref.class,TranscriptAcquisitionBudget.class).newInstance(authority,(Supplier<ConfigArchiveSource.Snapshot>)this::snapshot,pin,prompt,model,new TranscriptAcquisitionBudget(16777216,16777216,16777216,268435456,134217728,8388608,128,256,256));Object prepared;
  try{prepared=type.getMethod("prepare",Actor.class,String.class,long.class).invoke(preparer,actor(),meeting,4L);}catch(java.lang.reflect.InvocationTargetException e){throw new AssertionError("actual prospective preparation",e.getCause());}
  TaskSpec spec=(TaskSpec)prepared.getClass().getMethod("spec").invoke(prepared);byte[] body=(byte[])prepared.getClass().getMethod("requestBody",String.class).invoke(prepared,UUID.randomUUID().toString());String envelope=(String)prepared.getClass().getMethod("requestEnvelope").invoke(prepared);var verified=ama.model.ControlledMinutesProtocol.verify(body,envelope);
  assertEquals(spec.inputFingerprint(),verified.inputFingerprint());assertEquals("minutes:"+spec.input().id(),verified.logicalBusinessKey());assertEquals("MINUTES_GENERATE_V1",spec.type());assertEquals(4,spec.businessVersion());assertEquals(before,j.queryForMap("SELECT * FROM meeting WHERE id=?",meeting));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM generation_input WHERE meeting_id=?",Integer.class,meeting));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM durable_task WHERE subject_id=? AND input_kind='GENERATION_INPUT'",Integer.class,meeting));
  String content=new String(verified.payload(),java.nio.charset.StandardCharsets.UTF_8);assertTrue(content.contains("Actual original"));assertTrue(content.contains("Controlled synthetic minutes."));assertFalse(content.contains("dummy"));
  java.nio.file.Files.writeString(receipt.resolve("prospective-generation.json"),json.writeValueAsString(Map.of("meeting",meeting,"input",spec.input().id(),"producer",spec.producerKey(),"fingerprint",spec.inputFingerprint(),"requestHash",verified.logicalRequestHash(),"payloadHash",verified.payloadSha256(),"inputRows",0,"newTasks",0)));
 }
}
