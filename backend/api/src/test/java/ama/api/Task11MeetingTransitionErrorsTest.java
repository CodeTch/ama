package ama.api;
import ama.meeting.*;
import ama.contract.IdentityContracts.*;
import ama.contract.MinutesContracts.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual MVC advice discovery and both B03 routes; repository outcome is a deliberate substitute. */
class Task11MeetingTransitionErrorsTest {
 @Configuration @EnableWebMvc
 @ComponentScan(basePackageClasses=MeetingController.class,useDefaultFilters=false,
   includeFilters=@ComponentScan.Filter(type=FilterType.ANNOTATION,classes=RestControllerAdvice.class))
 static class Config {
  @Bean OtherController other(){return new OtherController();}
  @Bean MeetingRepository repository(){return mock(MeetingRepository.class);}
  @Bean MeetingController controller(MeetingRepository r){return new MeetingController(new MeetingService(r),new RosterService(r),new MockEnvironment());}
 }
 @Test void bothMutationsMapPublicationFreezeAndUnavailablePreparationSafely()throws Exception {
  try(var context=new AnnotationConfigWebApplicationContext()){
   context.setServletContext(new MockServletContext());context.register(Config.class);context.refresh();
   var repository=context.getBean(MeetingRepository.class);var mvc=MockMvcBuilders.webAppContextSetup(context).build();
   var session=new ResolvedSession(new Actor("owner",Status.ACTIVE,Set.of(ama.contract.IdentityContracts.Role.USER)),"T".repeat(43),Instant.now().plusSeconds(60));
   for(RuntimeException failure:List.of(new Failure(FailureCode.CAPABILITY_UNAVAILABLE),new Failure(FailureCode.PROVENANCE_UNAVAILABLE),new Failure(FailureCode.BUDGET_EXCEEDED),new ama.contract.TranscriptContracts.Failure(503,"MINUTES_TRANSITION_UNAVAILABLE"),new ama.contract.TranscriptContracts.Failure(409,"REVISION_CONFLICT"))){
    doThrow(failure).when(repository).save(any(),anyString(),anyString(),any(),anyString());doThrow(failure).when(repository).confirm(any(),anyString(),anyString(),any(),anyString());
    for(boolean roster:List.of(false,true)){
     var request=roster?post("/api/v1/meetings/12345678-1234-4234-8234-123456789abc/roster-confirmations"):put("/api/v1/meetings/12345678-1234-4234-8234-123456789abc/info");
     String payload=roster?"{\"expectedRevisionToken\":\"4\"}":"{\"expectedRevisionToken\":\"4\",\"name\":\"Meeting\",\"rosterDraftIds\":[],\"externalRosterNames\":[]}";
     var response=assertDoesNotThrow(()->mvc.perform(request.requestAttr(SessionSecurityFilter.SESSION,session).header("Idempotency-Key","transition").contentType("application/json").content(payload)).andReturn().getResponse(),"Meeting transition failures must have a controlled HTTP mapping");
     Task11MeetingErrorContract.assertMatches(roster?"/api/v1/meetings/{id}/roster-confirmations":"/api/v1/meetings/{id}/info",roster?"post":"put",response);
     boolean frozen=failure instanceof Failure f&&f.code()==FailureCode.CAPABILITY_UNAVAILABLE||failure instanceof ama.contract.TranscriptContracts.Failure t&&t.status()==409;
     assertEquals(frozen?409:503,response.getStatus());assertTrue(response.getContentAsString().contains(frozen?"MEETING_CONFLICT":"MEETING_UNAVAILABLE"));assertEquals("no-store",response.getHeader("Cache-Control"));assertFalse(response.getContentAsString().contains("PROVENANCE_UNAVAILABLE"));assertFalse(response.getContentAsString().contains("MINUTES_TRANSITION_UNAVAILABLE"));
    }
   }
  }
 }
 @org.springframework.web.bind.annotation.RestController
 static class OtherController {
  @org.springframework.web.bind.annotation.GetMapping("/test/other") String read(){throw new Failure(FailureCode.CAPABILITY_UNAVAILABLE);}
 }
 @Test void scopedAdviceLeavesOtherControllersAndExistingMeetingOutcomesUnchanged()throws Exception {
  try(var context=new AnnotationConfigWebApplicationContext()){
   context.setServletContext(new MockServletContext());context.register(Config.class);context.refresh();
   var mvc=MockMvcBuilders.webAppContextSetup(context).build();var repository=context.getBean(MeetingRepository.class);
   var other=assertThrows(jakarta.servlet.ServletException.class,()->mvc.perform(get("/test/other")));
   assertInstanceOf(Failure.class,other.getCause());
   var session=new ResolvedSession(new Actor("owner",Status.ACTIVE,Set.of(ama.contract.IdentityContracts.Role.USER)),"T".repeat(43),Instant.now().plusSeconds(60));
   String id="12345678-1234-4234-8234-123456789abc";
   for(boolean roster:List.of(false,true)){
    reset(repository);when(repository.save(any(),anyString(),anyString(),any(),anyString())).thenReturn(new ama.contract.MeetingContracts.Mutation(id,5));when(repository.confirm(any(),anyString(),anyString(),any(),anyString())).thenReturn(new ama.contract.MeetingContracts.Mutation(id,5));
    var request=roster?post("/api/v1/meetings/"+id+"/roster-confirmations"):put("/api/v1/meetings/"+id+"/info");
    String payload=roster?"{\"expectedRevisionToken\":\"4\"}":"{\"expectedRevisionToken\":\"4\",\"name\":\"Meeting\",\"rosterDraftIds\":[],\"externalRosterNames\":[]}";
    request.requestAttr(SessionSecurityFilter.SESSION,session).header("Idempotency-Key","controls").contentType("application/json").content(payload);
    var accepted=mvc.perform(request).andReturn().getResponse();assertEquals(200,accepted.getStatus());assertTrue(accepted.getContentAsString().contains("\"revisionToken\":\"5\""));
    doThrow(new ama.contract.MeetingContracts.Conflict()).when(repository).save(any(),anyString(),anyString(),any(),anyString());doThrow(new ama.contract.MeetingContracts.Conflict()).when(repository).confirm(any(),anyString(),anyString(),any(),anyString());
    var conflict=mvc.perform(request).andReturn().getResponse();assertEquals(409,conflict.getStatus());assertTrue(conflict.getContentAsString().contains("MEETING_CONFLICT"));
   }
  }
 }

}
