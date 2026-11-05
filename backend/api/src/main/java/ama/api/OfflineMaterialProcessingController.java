package ama.api;

import ama.contract.IdentityContracts.ResolvedSession;
import ama.transcript.OfflineMaterialProcessing;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@RestController
@ConditionalOnProperty(name="processing.material-enhancement-origin")
@RequestMapping("/api/v1/meetings/{meeting}")
final class OfflineMaterialProcessingController {
 private final OfflineMaterialProcessing service;
 OfflineMaterialProcessingController(OfflineMaterialProcessing service){this.service=service;}
 @GetMapping("/material-adjustments/latest") java.util.Map<String,Object> latest(@PathVariable String meeting,HttpServletRequest request){return service.adjustment(((ResolvedSession)request.getAttribute(SessionSecurityFilter.SESSION)).actor(),meeting);}
 @PostMapping("/material-adjustments/{id}/retry") java.util.Map<String,Boolean> retry(@PathVariable String meeting,@PathVariable String id,HttpServletRequest request){service.retryAdjustment(((ResolvedSession)request.getAttribute(SessionSecurityFilter.SESSION)).actor(),meeting,id);return java.util.Map.of("accepted",true);}
 @PostMapping("/processing-tasks/{task}/enhancement/{action:retry|cancel}") java.util.Map<String,Boolean> command(@PathVariable String meeting,@PathVariable String task,@PathVariable String action,HttpServletRequest request){
  service.command(((ResolvedSession)request.getAttribute(SessionSecurityFilter.SESSION)).actor(),meeting,task,action.equals("cancel"));
  return java.util.Map.of("accepted",true);
 }
}
