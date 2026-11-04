package ama.api;
import ama.contract.IdentityContracts.ResolvedSession;
import ama.transcript.PublicationDestinationPage;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.WebAsyncTask;
@RestController
final class PublicationLibraryController {
 private final MinutesConfiguration.Runtime runtime;private final PublicationReadRequests reads;
 PublicationLibraryController(MinutesConfiguration.Runtime runtime,PublicationReadRequests reads){this.runtime=runtime;this.reads=reads;}
 @GetMapping("/api/v1/knowledge-documents") WebAsyncTask<ResponseEntity<byte[]>> records(HttpServletRequest request){
  PublicationDestinationPage.path(request.getRequestURI());if(request.getParameterMap().entrySet().stream().anyMatch(e->!java.util.Set.of("view","cursor").contains(e.getKey())||e.getValue().length!=1))throw new IllegalArgumentException("Invalid record page");
  String view=request.getParameter("view")==null?"read":request.getParameter("view"),cursor=request.getParameter("cursor");if(runtime.publications()==null)throw PublicationReadRequests.unavailable();var actor=((ResolvedSession)request.getAttribute(SessionSecurityFilter.SESSION)).actor();
  return reads.start(cancel->{var value=runtime.publications().libraryRecords(actor,view,cursor,cancel);return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).header("Cache-Control","private, no-store").body(TranscriptWire.response(value,262144));});
 }
}
