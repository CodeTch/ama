package ama.api;
import ama.access.MeetingAccess;import ama.contract.IdentityContracts.*;import ama.db.TransactionRunner;import ama.storage.FileRegistry;import ama.transcript.*;import java.util.*;import javax.sql.DataSource;import org.springframework.test.web.servlet.MockMvc;import org.springframework.test.web.servlet.setup.MockMvcBuilders;
/** Real controller and JDBC service. Session boundary is separately exercised by deployed CAS. */
public final class SpeechReviewHttpProbe {
 public static MockMvc create(JdbcSavedTranscripts service,DataSource source,TransactionRunner tx,MeetingAccess access,FileRegistry files){
  var runtime=new TranscriptConfiguration.Runtime(service,null,Map.of("responseBytes",33554432));
  return MockMvcBuilders.standaloneSetup(new TranscriptController(runtime,new JdbcSavedTranscriptReceipts(source,tx,access),files,tx,access,new SpeechReviewReceipts(source,tx,access))).setControllerAdvice(new TranscriptErrors()).build();
 }
 public static String sessionAttribute(){return SessionSecurityFilter.SESSION;}
}
