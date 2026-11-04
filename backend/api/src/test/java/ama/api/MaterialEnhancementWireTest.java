package ama.api;

import ama.contract.IdentityContracts.*;
import ama.transcript.JdbcMaterialEnhancements;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaterialEnhancementWireTest {
 @Test void actualMvcResponseContainsReviewChangesRatherThanJacksonNodeBeanProperties()throws Exception{
  String meeting=UUID.randomUUID().toString(),candidate=UUID.randomUUID().toString(),segment=UUID.randomUUID().toString();
  var actor=new Actor("owner",Status.ACTIVE,Set.of(Role.USER));var service=mock(JdbcMaterialEnhancements.class);var json=new ObjectMapper();
  var changes=json.createArrayNode();changes.addObject().put("segmentId",segment).put("before","招上基金").put("after","招商基金");
  var body=new LinkedHashMap<String,Object>();body.put("candidateId",candidate);body.put("sourceRevisionId",UUID.randomUUID().toString());body.put("materialSnapshotId",null);body.put("expectedRevisionToken","3");body.put("profile","ama-material-enhancement.v1");body.put("state","READY");body.put("changes",changes);body.put("canApply",true);
  when(service.read(actor,meeting,candidate)).thenReturn(body);
  var provider=new StaticListableBeanFactory(Map.of("enhancement",service)).getBeanProvider(JdbcMaterialEnhancements.class);
  var mvc=MockMvcBuilders.standaloneSetup(new MaterialEnhancementController(provider)).build();
  var response=mvc.perform(get("/api/v1/meetings/"+meeting+"/transcript/material-enhancements/"+candidate).requestAttr(SessionSecurityFilter.SESSION,new ResolvedSession(actor,"T".repeat(43),Instant.now().plusSeconds(60)))).andReturn().getResponse();
  assertEquals(200,response.getStatus());assertEquals("private, no-store",response.getHeader("Cache-Control"));
  var wire=json.readTree(response.getContentAsByteArray());assertTrue(wire.path("changes").isArray(),wire.toString());assertEquals(1,wire.path("changes").size());assertEquals("招商基金",wire.path("changes").get(0).path("after").asText());assertTrue(wire.path("materialSnapshotId").isNull());
 }
}
