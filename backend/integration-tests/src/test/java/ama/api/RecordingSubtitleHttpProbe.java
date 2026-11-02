package ama.api;
import ama.recording.*;import ama.access.MeetingAccess;import ama.db.TransactionRunner;import org.springframework.test.web.servlet.MockMvc;import org.springframework.test.web.servlet.setup.MockMvcBuilders;
public final class RecordingSubtitleHttpProbe {
 public static MockMvc create(JdbcLiveSubtitles service,RecordingService recordings,TransactionRunner tx,MeetingAccess access){return MockMvcBuilders.standaloneSetup(new RecordingSubtitleController(new RecordingSubtitleRuntime(service),recordings,tx,access)).setControllerAdvice(new RecordingErrors()).build();}
 public static String sessionAttribute(){return SessionSecurityFilter.SESSION;}
}
