package ama.api;

import ama.contract.IdentityContracts.*;
import ama.transcript.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** HTTP normalization/dispatch only. Service substitutes do not qualify business authorization. */
class Task11SavedActivationDispatchTest {
 static final String M="12345678-1234-4234-8234-123456789abc", S="12345678-1234-4234-8234-123456789abd", I="12345678-1234-4234-8234-123456789abe", H="a".repeat(64);
 static final long REV=9007199254740993L;
 static final Actor ACTOR=new Actor("owner",Status.ACTIVE,Set.of(Role.USER));
 record Route(String verb,String path,String operation,String method,Map<String,Object> fields,List<Object> arguments,String service) {}
 static Map<String,Object> fields(Object... pairs){var result=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)result.put((String)pairs[i],pairs[i+1]);return result;}
 static Route route(String verb,String path,String operation,String method,String service,Map<String,Object> fields,Object... args){return new Route(verb,path,operation,method,fields,Arrays.asList(args),service);}
 static List<Route> routes(){var anchors=List.of(fields("evidenceId",I,"startToken","0","endToken","2"));var typed=List.of(new JdbcMinutesSavedCommands.Reanchor(I,0,2));return List.of(
  route("PUT","/body","MINUTES_BODY_SAVE","saveBody","saved",fields("sourceRevisionId",S,"title","人类标题","body","A😀","evidenceAnchors",anchors),S,"人类标题","A😀",typed),
  route("PUT","/elements/"+I,"MINUTES_ELEMENT_EDIT","saveElement","saved",fields("sourceSetId",S,"body","A😀","evidenceAnchors",anchors),S,I,"A😀",typed),
  route("PUT","/elements/"+I+"/action-fields","MINUTES_ACTION_FIELDS","saveActionFields","saved",fields("sourceSetId",S,"ownerEnterpriseId",null,"ownerDisplay","人工负责人","dueOn","2026-11-01"),S,I,new JdbcMinutesSavedCommands.ActionFields(null,"人工负责人","2026-11-01")),
  route("POST","/elements/"+I+"/open-question","MINUTES_OPEN_QUESTION","openQuestion","saved",fields("sourceSetId",S,"body","A😀","evidenceAnchors",anchors),S,I,"A😀",typed),
  route("POST","/body-restorations","MINUTES_BODY_RESTORE","restoreBody","saved",fields("sourceRevisionId",S),S),
  route("POST","/element-set-restorations","MINUTES_SET_RESTORE","restoreSet","saved",fields("sourceSetId",S),S),
  route("PUT","/settings/template","MINUTES_TEMPLATE","selectTemplate","settings",fields("templateRevisionId",S),S),
  route("PUT","/settings/exclusions","MINUTES_EXCLUSIONS","replaceExclusions","settings",fields("sourceExclusionRevisionId",S,"items",List.of(fields("locatorId",I,"category","INTERNAL_BUSINESS_DATA","reason",null))),S,List.of(new JdbcMinutesSettingsCommands.Exclusion(I,"INTERNAL_BUSINESS_DATA",null))),
  route("POST","/candidates/"+S+"/adoptions","MINUTES_ADOPT","adopt","saved",fields("inputId",I,"inputFingerprint",H),S,I,H),
  route("POST","/candidates/"+S+"/rejections","MINUTES_REJECT","reject","decisions",fields(),S),
  route("POST","/candidates/"+S+"/disposals","MINUTES_DISPOSE","dispose","decisions",fields(),S),
  route("POST","/reviews","MINUTES_REVIEW","review","reviews",fields("target",fields("kind","ELEMENT","id",S),"reviewKind","SOURCE_SUPPORT","scope","SPAN","outcome","PARTIAL","startToken","0","endToken","2"),"ELEMENT",S,"SOURCE_SUPPORT","SPAN","PARTIAL",0L,2L),
  route("POST","/reviews","MINUTES_REVIEW","review","reviews",fields("target",fields("kind","ELEMENT","id",S),"reviewKind","ACTION_FIELDS","scope",null,"outcome","CONFIRMED"),"ELEMENT",S,"ACTION_FIELDS",null,"CONFIRMED",null,null)
 );}
 static class Fixture {
  final JdbcMinutesCommandReceipts receipts=mock(JdbcMinutesCommandReceipts.class);
  final MinutesEditorRuntime editor=mock(MinutesEditorRuntime.class);
  final Map<String,Object> services=Map.of("saved",mock(JdbcMinutesSavedCommands.class),"settings",mock(JdbcMinutesSettingsCommands.class),"reviews",mock(JdbcMinutesReviews.class),"decisions",mock(JdbcMinutesDecisions.class));
  final JdbcMinutesInitialSettings initial=mock(JdbcMinutesInitialSettings.class);final JdbcMinutesGenerationCommands generation=mock(JdbcMinutesGenerationCommands.class);final JdbcMinutesQueries queries=mock(JdbcMinutesQueries.class);
  Fixture(){when(editor.saved()).thenReturn((JdbcMinutesSavedCommands)services.get("saved"));when(editor.settings()).thenReturn((JdbcMinutesSettingsCommands)services.get("settings"));when(editor.reviews()).thenReturn((JdbcMinutesReviews)services.get("reviews"));when(editor.decisions()).thenReturn((JdbcMinutesDecisions)services.get("decisions"));}
  org.springframework.mock.web.MockHttpServletResponse call(Route route,boolean installed)throws Exception {
   var runtime=new MinutesConfiguration.Runtime(installed?editor:null,null,queries,initial,generation,null,null,null,65536);
   var mvc=MockMvcBuilders.standaloneSetup(new MinutesSavedController(runtime,receipts,null,null)).setControllerAdvice(new MinutesErrors()).build();var payload=new LinkedHashMap<String,Object>(route.fields());payload.put("expectedRevisionToken",Long.toString(REV));
   return mvc.perform(MockMvcRequestBuilders.request(HttpMethod.valueOf(route.verb()),"/api/v1/meetings/"+M+"/minutes"+route.path()).requestAttr(SessionSecurityFilter.SESSION,new ResolvedSession(ACTOR,"T".repeat(43),Instant.now().plusSeconds(60))).header("Idempotency-Key","saved-activation").contentType("application/json").content(new ObjectMapper().writeValueAsBytes(payload))).andReturn().getResponse();
  }
 }
 @TestFactory Stream<DynamicTest> everyCommandPreservesNormalizedArgumentsAndRealGateOrder(){return routes().stream().map(route->DynamicTest.dynamicTest(route.operation()+" "+route.arguments(),()->{
  var f=new Fixture();var response=f.call(route,true);assertEquals(200,response.getStatus(),response.getContentAsString());assertEquals("private, no-store",response.getHeader("Cache-Control"));
  var writes=mockingDetails(f.services.get(route.service())).getInvocations().stream().filter(i->i.getMethod().getName().equals(route.method())).toList();assertEquals(1,writes.size());var invocation=writes.getFirst();var actual=Arrays.asList(invocation.getArguments());var expected=new ArrayList<Object>(List.of(ACTOR,M,"saved-activation",REV));expected.addAll(route.arguments());assertEquals(expected,actual.subList(0,actual.size()-1));assertDoesNotThrow(()->UUID.fromString((String)actual.getLast()));
  var lookup=mockingDetails(f.receipts).getInvocations().stream().filter(i->i.getMethod().getName().equals("lookup")).findFirst().orElseThrow();var gate=mockingDetails(f.editor).getInvocations().stream().filter(i->i.getMethod().getName().equals("requireCombined")).findFirst().orElseThrow();assertTrue(lookup.getSequenceNumber()<gate.getSequenceNumber()&&gate.getSequenceNumber()<invocation.getSequenceNumber());
 }));}
 @Test void oldAcknowledgmentRemainsAvailableWithoutInstallation()throws Exception {for(var route:routes()){var f=new Fixture();when(f.receipts.lookup(eq(ACTOR),eq(M),eq(route.operation()),eq("saved-activation"),eq(REV),any(String[].class))).thenReturn(Optional.of(Map.of("replayed",true)));var response=f.call(route,false);assertEquals(200,response.getStatus());assertTrue(response.getContentAsString().contains("replayed"));for(var service:f.services.values())verifyNoInteractions(service);}}
 @Test void absentOrRejectedInstallationNeverDispatchesNewEffects()throws Exception {for(var route:routes()){var f=new Fixture();assertEquals(503,f.call(route,false).getStatus());doThrow(new IllegalArgumentException("Foreign assembly")).when(f.editor).requireCombined(f.initial,f.generation,f.queries,null,null,null);assertEquals(503,f.call(route,true).getStatus());for(var service:f.services.values())verifyNoInteractions(service);}}
 @Test void currentRevisionConflictSurvivesHttpDispatch()throws Exception {var f=new Fixture();var route=routes().get(9);when(((JdbcMinutesDecisions)f.services.get("decisions")).reject(eq(ACTOR),eq(M),eq("saved-activation"),eq(REV),eq(S),anyString())).thenThrow(new ama.contract.TranscriptContracts.Failure(409,"REVISION_CONFLICT"));var response=f.call(route,true);assertEquals(409,response.getStatus());assertTrue(response.getContentAsString().contains("REVISION_CONFLICT"));}
}
