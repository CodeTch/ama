package ama.api;

import ama.identity.SessionPort;
import ama.task.JdbcTaskQueue;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Uses the actual filter to issue its private, freshly resolvable read identity. */
public final class RecordingArchiveSecurityFixture {
 private RecordingArchiveSecurityFixture(){}
 public static MockMvc create(JdbcTaskQueue queue,SessionPort sessions){
  return MockMvcBuilders.standaloneSetup(new RecordingArchiveController(queue))
   .defaultRequest(get("/").with(request->{request.setLocalPort(80);return request;}))
   .addFilters(new SessionSecurityFilter(sessions,new MockEnvironment().withProperty("local.server.port","80")))
   .build();
 }
}
