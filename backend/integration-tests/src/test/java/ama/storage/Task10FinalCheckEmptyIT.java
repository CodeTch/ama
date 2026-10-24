package ama.storage;
import java.util.*;import ama.transcript.*;import static org.junit.jupiter.api.Assertions.*;
class Task10FinalCheckEmptyIT extends Task09PipelineFixture {
 @Override javax.sql.DataSource createSource(){return ama.minutes.Task10Source.open();}
 @org.junit.jupiter.api.Test void genuineEmptyCurrentIsNotMissingCapability()throws Exception {var queries=new JdbcMinutesSavedQueries(ama.api.Task09RuntimeProbe.components(app).authority(),()->new ama.config.file.ControlledConfigArchiveSource().open(archive,pin,new ama.config.ConfigArchiveIndex.IndexBudget(4194304,4096,67108864,128),B,()->false),pin,new TranscriptAcquisitionBudget(16777216,16777216,16777216,268435456,134217728,8388608,128,256,256),isolated());var ready=queries.readiness(actor(),meeting);assertEquals(false,ready.get("businessReady"));assertTrue(((List<?>)ready.get("blockers")).contains("BODY_REQUIRED"));assertTrue(((List<?>)ready.get("blockers")).contains("ELEMENT_SET_REQUIRED"));assertFalse(((List<?>)ready.get("blockers")).contains("CORRECTION_UNAVAILABLE"));java.nio.file.Files.writeString(receipt.resolve("legitimate-empty-readiness.json"),json.writeValueAsString(ready));}
}
