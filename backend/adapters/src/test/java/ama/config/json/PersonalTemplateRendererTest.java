package ama.config.json;

import ama.canonical.CanonicalValue;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.MinutesContracts.*;
import ama.minutes.*;
import ama.transcript.ControlledMinutesRenderer;
import java.util.*;
import org.junit.jupiter.api.Test;
import static ama.config.json.ConfigBindingsTest.*;
import static ama.config.json.RawHashChainTest.*;
import static ama.config.WireValues.*;
import static org.junit.jupiter.api.Assertions.*;

class PersonalTemplateRendererTest {
 record Fixture(List<RawArchive> archives,StoredProvenanceFacts facts,GenerationDraft draft){}
 Fixture fixture(List<String> themes,boolean personal)throws Exception{
  var selected=personal?ControlledPersonalMinutesArchives.enable(ControlledMinutesArchives.build("CONTROLLED_INSTALLATION",T,"personal-render-test.1","Controlled synthetic response.",Task09ControlledArchivesTest.C,Task09ControlledArchivesTest.V)):ControlledMinutesArchives.build("CONTROLLED_INSTALLATION",T,"personal-render-test.1","Controlled synthetic response.",Task09ControlledArchivesTest.C,Task09ControlledArchivesTest.V);
  var raw=new LinkedHashMap<Identity,RawArchive>();for(var entry:archives())raw.put(entry.claimedIdentity(),entry);for(var entry:selected){var old=raw.put(entry.claimedIdentity(),entry);if(old!=null)assertArrayEquals(old.bytes(),entry.bytes());}
  var source=beforeGeneration();var rows=new ArrayList<>(source.rows());rows.removeIf(r->r.table()==Table.TEMPLATE_REVISION);rows.add(row(Table.TEMPLATE_REVISION,T,"00000000-0000-0000-0000-000000000090",1L,"human","个人模板",new PersonalTemplateDocument(themes).json(),(long)themes.size(),TIME));
  var facts=new StoredProvenanceFacts(rows,source.files(),List.of(),source.evidenceAnchors(),source.taskSpecs(),100000L);
  var draft=new GenerationDraft(G,MEETING,C,null,T,X,null,"personal-render-test.1.prompt","personal-render-test.1.model",Scope.FULL,null);
  return new Fixture(List.copyOf(raw.values()),facts,draft);
 }
 @Test void boundPersonalThemesReachModelPayloadAndChangingThemChangesTheInputFingerprint()throws Exception{
  String previous=null;
  for(var themes:List.of(List.of("结论😀"),List.of("背景","讨论","结论","行动","风险","问题","分歧","后续"))){
   var f=fixture(themes,true);var binding=service().computeGeneration(f.draft(),f.facts(),new Fence(10,11,2),f.archives(),GRAPH,B,()->false);
   var output=ControlledMinutesRenderer.render(f.facts().rows(),f.draft(),binding);
   var payload=fields(output,"protocolVersion","modelIdentity","parameters","instructionText","inputManifest","transcript","template","pinnedElements","exclusions");assertEquals("ama.controlled-minutes-http.v2",text(payload.get("protocolVersion")));
   assertEquals(themes,array(fields(payload.get("template"),"revisionId","name","themes").get("themes")).stream().map(ama.config.WireValues::text).toList());
   if(previous!=null)assertNotEquals(previous,binding.fingerprint());previous=binding.fingerprint();
  }
 }
 @Test void legacyRendererDoesNotSilentlyAcquirePersonalTemplateCapability()throws Exception{
  var f=fixture(List.of("个人主题"),false);var binding=service().computeGeneration(f.draft(),f.facts(),new Fence(10,11,2),f.archives(),GRAPH,B,()->false);
  assertThrows(RuntimeException.class,()->ControlledMinutesRenderer.render(f.facts().rows(),f.draft(),binding));
 }
}
