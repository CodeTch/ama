package ama.storage;

import ama.api.Task11B2ReceiptApi;
import ama.publication.Task11Source;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Database SELECT/public-custody preflight only: no client, front, provider store or child. */
class Task11B2RejectedTransportHostPreflightIT {
 @Test void allThreeConsumedBusinessPreimagesRemainExactBeforeAnyGet()throws Exception {
  var inputs=Task11B2RuntimeFixture.inspectRejectedTransportHostPreflightInputs();var result=new LinkedHashMap<String,Object>();
  result.put("label",Task11B2RuntimeFixture.HOST_PREFLIGHT_LABEL);result.put("configurationAckSha256",Task11B2RuntimeFixture.HOST_PREFLIGHT_ACK_SHA);result.put("hostRetainedPreflightSha256",Task11B2RuntimeFixture.HOST_RETAINED_SHA);
  result.put("providerRequests",0);result.put("frontStarts",0);result.put("businessSqlWrites",0);result.put("applicationSelectBudget",37);String phase="DATABASE_IDENTITY";Throwable primary=null;
  try{
   var j=new JdbcTemplate(Task11Source.open());j.setQueryTimeout(5);
   var session=j.queryForMap("SELECT DATABASE() AS selected_schema,CURRENT_USER() AS principal,@@server_uuid AS server_uuid,@@session.time_zone AS zone,@@character_set_connection AS charset,@@session.lock_wait_timeout AS metadata_wait,@@session.innodb_lock_wait_timeout AS row_wait");
   assertEquals("ama_t11b2_d9e2fdcc611e_behavior",session.get("selected_schema"));assertEquals("t11b2_d9e2fdcc611e@%",session.get("principal"));assertEquals("1bdea6eb-cf32-11f1-8fe3-fef4aa4b3a25",session.get("server_uuid"));assertEquals("+00:00",session.get("zone"));assertEquals("utf8mb4",session.get("charset"));assertEquals(5,((Number)session.get("metadata_wait")).intValue());assertEquals(5,((Number)session.get("row_wait")).intValue());result.put("session",session);
   phase="RETAINED_REVISIONS";
   var publication=j.queryForMap("SELECT state,revision FROM publication WHERE id=?",Task11B2RejectedTransportIT.PUBLICATION);assertEquals("FAILED",publication.get("state"));assertEquals(5L,((Number)publication.get("revision")).longValue());
   assertEquals(13L,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Long.class,Task11B2RejectedTransportIT.MEETING));
   phase="CANONICAL_BUSINESS_IMAGE";var before=Task11B2ReceiptApi.business(j);result.put("businessPreimage",before);
   phase="THREE_CONSUMED_PREIMAGES";Task11B2RuntimeFixture.verifyHostBusinessPreimages(inputs,before);
   result.put("allThreeConsumedPreimagesMatch",true);result.put("allThreeConsumedFrontsAbsent",true);result.put("status","PASS");phase="COMPLETE";
  }catch(Throwable failure){primary=failure;result.put("status","FAIL_OR_SETUP_ERROR");result.put("safeFailureType",failure.getClass().getName());throw failure;}
  finally{
   result.put("lastPhase",phase);
   try{Files.writeString(inputs.evidence().resolve("rejected-transport-host-preflight.json"),Task11B2RuntimeFixture.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result)+"\n",StandardOpenOption.CREATE_NEW);}
   catch(Throwable capture){if(primary!=null)primary.addSuppressed(capture);else throw capture;}
  }
 }
}
