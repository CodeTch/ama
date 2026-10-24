package ama.config.json;
import ama.contract.MinutesContracts.*;
import ama.contract.TaskContracts.*;
import ama.minutes.contract.*;
import ama.canonical.*;
import java.util.*;
import static ama.config.json.ConfigBindingsTest.*;
import static ama.config.json.RawHashChainTest.*;
import static ama.contract.MinutesContracts.*;

/** Counterexample fixtures: saved digests are consistency setup, never independent golden values. */
final class OriginGraphFix1Cases {
 static StoredProvenanceFacts facts(StoredProvenanceFacts p,List<FullRow> rows,List<GenerationConfiguration> configs,List<TaskSpec> tasks,List<EvidenceAnchor> anchors){
  return new StoredProvenanceFacts(rows,p.files(),configs,anchors,tasks,100000L);
 }
 static GenerationConfiguration config(String id,StoredProvenanceFacts p){
  var old=p.configurations().getFirst();return new GenerationConfiguration(id,old.prompt(),old.model(),old.profiles(),old.defaultTemplateRevisionId(),List.of());
 }
 static TaskSpec task(String id,String fp){return new TaskSpec("MINUTES","MINUTES",new SubjectRef("MEETING",MEETING),H,new TypedRef("GENERATION_INPUT",id),fp,13,0,2,1);}
 static FullRow gen(String id,String set,String fp){return row(Table.GENERATION_INPUT,id,MEETING,C,null,T,X,set,"test.generation.prompt.1","test.generation.model.1",12L,fp,"FULL",null);}
 static GenerationDraft draft(String id,String set){return new GenerationDraft(id,MEETING,C,null,T,X,set,"test.generation.prompt.1","test.generation.model.1",Scope.FULL,null);}
 static FullRow locator(String id,String revision,String segment){return row(Table.SOURCE_LOCATOR,id,MEETING,"TRANSCRIPT",revision,segment,null,null,null,null,null,null,null);}
 static StoredProvenanceFacts origins(StoredProvenanceFacts p,String set,List<String> inputs,int noteStart){
  var rows=new ArrayList<>(p.rows());rows.add(row(Table.ELEMENT_SET,set,MEETING,3L,TIME));int ordinal=0;
  for(var input:inputs){String note=uid(noteStart+ordinal++);rows.add(row(Table.BACKGROUND_NOTE,note,set,input,(long)ordinal,"context"));rows.add(row(Table.BACKGROUND_NOTE_SOURCE,note,LOC));}
  return facts(p,rows,p.configurations(),p.taskSpecs(),p.evidenceAnchors());
 }
 static StoredProvenanceFacts store(StoredProvenanceFacts p,String id,String set)throws Exception{
  String fp=new GenerationFingerprint(service(),full(),B).compute(draft(id,set),p,new Fence(12,13,2),GRAPH,()->false).fingerprint();
  var rows=new ArrayList<>(p.rows());rows.add(gen(id,set,fp));var configs=new ArrayList<>(p.configurations());configs.add(config(id,p));var tasks=new ArrayList<>(p.taskSpecs());tasks.add(task(id,fp));
  return facts(p,rows,configs,tasks,p.evidenceAnchors());
 }
 static final String NESTED=uid(83),OUTER_SET=uid(84);
 static StoredProvenanceFacts nested(boolean corrupted)throws Exception{
  var p=finalStored();var rows=new ArrayList<>(p.rows());rows.removeIf(r->r.table()==Table.SOURCE_LOCATOR&&r.string("id").equals(LOC));rows.add(locator(LOC,uid(80),uid(81)));
  rows.add(row(Table.TRANSCRIPT_REVISION,uid(80),MEETING,2L,"WORKING",BATCH,R,expected(),"human",TIME,INPUT,null));
  rows.add(row(Table.TRANSCRIPT_SEGMENT,uid(80),uid(81),1L,"different exact saved body",FILE,1L,null,null,null));
  rows.add(row(Table.TRANSCRIPT_CONFIRMATION,uid(82),MEETING,uid(80),corrupted?H:"256224cda292064218be15d60eff2d93f14d45333543a486e543f97ecf6d6812","human",TIME));
  rows.add(gen(NESTED,SET,H));var configs=new ArrayList<>(p.configurations());configs.add(config(NESTED,p));
  var provisional=facts(p,rows,configs,p.taskSpecs(),p.evidenceAnchors());
  var data=new GenerationFingerprint().data(new GenerationFacts(NESTED,provisional),new Fence(12,13,2),GRAPH,()->false);
  String fp=new CanonicalValueCodec().sha256(ContractValues.envelope("ama.generation-input.v2","GENERATION_INPUT",data),GRAPH.canonical(),()->false);
  rows.removeIf(r->r.table()==Table.GENERATION_INPUT&&r.string("id").equals(NESTED));rows.add(gen(NESTED,SET,fp));
  rows.add(row(Table.ELEMENT_SET,OUTER_SET,MEETING,3L,TIME));rows.add(locator(uid(85),R,S));
  rows.add(row(Table.BACKGROUND_NOTE,uid(86),OUTER_SET,NESTED,1L,"context"));rows.add(row(Table.BACKGROUND_NOTE_SOURCE,uid(86),uid(85)));
  // Review reaches G2 through BODY; final reaches it through the saved final-set note.
  rows.removeIf(r->r.table()==Table.MINUTES_REVISION||r.table()==Table.FINAL_CONFIRMATION);
  rows.add(row(Table.MINUTES_REVISION,MIN,MEETING,1L,null,"synthetic title","AI","body",NESTED,"human",TIME));
  var a=OutputValidation.anchor("body",0,4,()->false);rows.add(row(Table.EVIDENCE_LINK,uid(88),uid(85),null,MIN,"PARTIAL",OutputValidation.anchorText(a)));
  rows.add(row(Table.FINAL_CONFIRMATION,FINAL,MEETING,12L,uid(21),uid(22),C,MIN,null,OUTER_SET,T,X,H,fp,"human",TIME));
  var tasks=new ArrayList<>(p.taskSpecs());tasks.add(task(NESTED,fp));var anchors=new ArrayList<>(p.evidenceAnchors());anchors.add(new EvidenceAnchor(uid(88),a));
  return facts(p,rows,configs,tasks,anchors);
 }
 static StoredProvenanceFacts dag(boolean reverse,boolean nonleaf)throws Exception{
  var p=origins(finalStored(),uid(101),List.of(G),110);p=store(p,uid(102),uid(101));
  String shallow=G,deep=uid(102);
  if(nonleaf){p=origins(p,uid(103),List.of(G,uid(102)),120);p=store(p,uid(104),uid(103));shallow=uid(102);deep=uid(104);}
  return origins(p,uid(105),reverse?List.of(deep,shallow):List.of(shallow,deep),130);
 }
 private OriginGraphFix1Cases(){}
}
