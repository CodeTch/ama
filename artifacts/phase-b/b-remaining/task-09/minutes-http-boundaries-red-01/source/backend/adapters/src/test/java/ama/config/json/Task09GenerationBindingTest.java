package ama.config.json;
import ama.config.*;
import ama.contract.MinutesContracts.*;
import ama.canonical.CanonicalValue;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static ama.config.json.ConfigBindingsTest.*;
import static ama.config.json.RawHashChainTest.*;
class Task09GenerationBindingTest {
 static Object accessor(Object value,String name)throws Exception {
  var method=Arrays.stream(value.getClass().getMethods()).filter(m->m.getName().equals(name)&&m.getParameterCount()==0).findFirst();
  assertTrue(method.isPresent(),"Completed validated generation must retain "+name+" from the same strict archive operation");return method.orElseThrow().invoke(value);
 }
 @Test void prospectiveBindingRetainsExactCatalogAndVerifiedDefinitionsWithoutOwnRow()throws Exception {
  var facts=beforeGeneration();assertTrue(facts.rows().stream().noneMatch(r->r.table()==Table.GENERATION_INPUT));
  var binding=service().computeGeneration(draft(),facts,new Fence(10,11,2),full(),GRAPH,B,()->false);
  assertEquals(resource("full-generation.sha256"),binding.fingerprint());
  var catalog=(Catalog)accessor(binding,"catalog");assertEquals(1,catalog.sources().size());assertEquals(S,((TranscriptLocator)catalog.sources().getFirst().fact().locator()).segmentId());
  assertInstanceOf(CanonicalValue.ObjectValue.class,accessor(binding,"promptDefinition"));assertInstanceOf(CanonicalValue.ObjectValue.class,accessor(binding,"modelDefinition"));
  assertSame(accessor(binding,"catalog"),accessor(binding,"catalog"));
 }
 @Test void storedBindingRetainsSameDefinitionsAndCatalogAfterClosureFinish()throws Exception {
  var stored=storedGeneration();var task=stored.taskSpecs().getLast();
  var draft=service().computeGeneration(draft(),beforeGeneration(),new Fence(10,11,2),full(),GRAPH,B,()->false);
  var saved=service().verifyGeneration(new GenerationFacts(G,stored),task,full(),GRAPH,B,()->false);
  for(String name:List.of("catalog","promptDefinition","modelDefinition"))assertEquals(accessor(draft,name),accessor(saved,name));
  assertEquals(draft.fingerprint(),saved.fingerprint());
 }
}
