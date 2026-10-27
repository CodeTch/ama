package ama.api;

import ama.meeting.*;
import ama.contract.IdentityContracts.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Task12MeetingProjectionErrorsTest {
 @Configuration @EnableWebMvc
 @ComponentScan(basePackageClasses=MeetingController.class,useDefaultFilters=false,includeFilters=@ComponentScan.Filter(type=FilterType.ANNOTATION,classes=RestControllerAdvice.class))
 static class Config {
  @Bean MeetingRepository repository(){return mock(MeetingRepository.class);}
  @Bean MeetingController controller(MeetingRepository r){return new MeetingController(new MeetingService(r),new RosterService(r),new MockEnvironment());}
  @Bean OtherController other(){return new OtherController();}
 }
 static void failActualBudget(){new WorkspaceReadBudget(new WorkspaceReadBudget.Limits(1,1,1,1,1,1,java.time.Duration.ofSeconds(5)),()->false).reserve(1,1,100,100,100,4,true);}
 @org.springframework.web.bind.annotation.RestController static class OtherController {
  @org.springframework.web.bind.annotation.GetMapping("/test/projection-other") String read(){failActualBudget();return "unreachable";}
 }
 @Test void actualBudgetFailureHasDeclared503ThroughRealAdviceDiscovery()throws Exception {
  try(var context=new AnnotationConfigWebApplicationContext()) {
   context.setServletContext(new MockServletContext());context.register(Config.class);context.refresh();var repository=context.getBean(MeetingRepository.class);
   when(repository.read(any(),anyString())).thenAnswer(i->{failActualBudget();return null;});var mvc=MockMvcBuilders.webAppContextSetup(context).build();
   var session=new ResolvedSession(new Actor("owner",Status.ACTIVE,Set.of(ama.contract.IdentityContracts.Role.USER)),"T".repeat(43),java.time.Instant.now().plusSeconds(60));
   var response=assertDoesNotThrow(()->mvc.perform(get("/api/v1/meetings/00000000-0000-0000-0000-000000000001").requestAttr(SessionSecurityFilter.SESSION,session)).andReturn().getResponse());
   assertEquals(503,response.getStatus());assertTrue(response.getContentAsString().contains("MEETING_UNAVAILABLE"));assertEquals("private, no-store",response.getHeader("Cache-Control"));Task11MeetingErrorContract.assertMatches("/api/v1/meetings/{id}","get",response);
   assertThrows(jakarta.servlet.ServletException.class,()->mvc.perform(get("/test/projection-other")));
  }
 }
}
