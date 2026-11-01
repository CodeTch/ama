package ama.api;
import ama.transcript.JdbcPublicationGovernance;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;

@RestController
@ConditionalOnProperty(name="publication.governance-enabled",havingValue="true")
final class KnowledgeGovernanceEventController {
 private final JdbcPublicationGovernance service;private final Environment env;
 KnowledgeGovernanceEventController(JdbcPublicationGovernance service,Environment env){this.service=service;this.env=env;}
 @PostMapping(value="/integrations/v1/knowledge/governance-events",consumes="text/plain")
 ResponseEntity<Void> event(HttpServletRequest request)throws java.io.IOException {
  if(request.getLocalPort()!=env.getProperty("local.server.port",Integer.class,-1)||!request.getParameterMap().isEmpty())throw new ama.access.MeetingAccess.AccessDeniedException();
  byte[] bytes;try(var in=request.getInputStream()){bytes=in.readNBytes(8193);}if(bytes.length>8192)throw new ama.contract.TranscriptContracts.Failure(413,"GOVERNANCE_EVENT_TOO_LARGE");
  for(byte b:bytes)if(b<33||b>126)throw new IllegalArgumentException("Exact ASCII signed event required");
  service.callback(new String(bytes,java.nio.charset.StandardCharsets.US_ASCII));return ResponseEntity.noContent().header("Cache-Control","no-store").build();
 }
}
