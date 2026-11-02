package ama.api;
import ama.recording.*;import ama.access.MeetingAccess;import ama.db.TransactionRunner;import ama.storage.*;import org.springframework.test.web.servlet.MockMvc;import org.springframework.test.web.servlet.setup.MockMvcBuilders;
public final class RecordingTranscriptHttpProbe {
 public static MockMvc create(JdbcRecordingTranscriptSources service,RecordingService recordings,MeetingAccess access,TransactionRunner tx){return MockMvcBuilders.standaloneSetup(new RecordingTranscriptController(new RecordingTranscriptRuntime(service),recordings,access,tx)).setControllerAdvice(new RecordingErrors()).build();}
 public static MockMvc uploads(FileUploads uploads,FileRegistry registry){return MockMvcBuilders.standaloneSetup(new FileController(uploads,registry,new org.springframework.mock.env.MockEnvironment())).setControllerAdvice(new FileErrors()).build();}
 public static String sessionAttribute(){return SessionSecurityFilter.SESSION;}
}
