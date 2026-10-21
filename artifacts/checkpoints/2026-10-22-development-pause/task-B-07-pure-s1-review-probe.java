package ama.config.json;
import java.util.*;
import ama.contract.MinutesContracts.*;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.TaskContracts.*;
import ama.minutes.contract.*;
import ama.canonical.*;
import static ama.config.json.ConfigBindingsTest.*;
import static ama.config.json.RawHashChainTest.*;
import static ama.contract.MinutesContracts.*;

class B07ReviewProbe {
 static StoredProvenanceFacts facts(List<FullRow> rows,StoredProvenanceFacts p,List<GenerationConfiguration> configs,List<TaskSpec> tasks,List<EvidenceAnchor> anchors){
  return new StoredProvenanceFacts(rows,p.files(),configs,anchors,tasks,100000L);
 }
 static GenerationConfiguration config(String id,StoredProvenanceFacts p){
  var old=p.configurations().getFirst();
  return new GenerationConfiguration(id,old.prompt(),old.model(),old.profiles(),old.defaultTemplateRevisionId(),List.of());
 }
 static FullRow gen(String id,String set,String fp){return row(Table.GENERATION_INPUT,id,MEETING,C,null,T,X,set,"test.generation.prompt.1","test.generation.model.1",12L,fp,"FULL",null);}
 static TaskSpec task(String id,String fp){return new TaskSpec("MINUTES","MINUTES",new SubjectRef("MEETING",MEETING),H,new TypedRef("GENERATION_INPUT",id),fp,13,0,2,1);}
 static FullRow note(String id,String set,String input,long ordinal){return row(Table.BACKGROUND_NOTE,id,set,input,ordinal,"probe");}
 static FullRow loc(String id,String rev,String seg){return row(Table.SOURCE_LOCATOR,id,MEETING,"TRANSCRIPT",rev,seg,null,null,null,null,null,null,null);}
 static void depth()throws Exception {
  var p=matrix();String g2=uid(70),set2=uid(71);
  var d2=new GenerationDraft(g2,MEETING,C,null,T,X,SET,"test.generation.prompt.1","test.generation.model.1",Scope.FULL,null);
  var fp2=new GenerationFingerprint(service(),full(),B).compute(d2,p,new Fence(12,13,2),GRAPH,()->false).fingerprint();
  var rows=new ArrayList<>(p.rows());rows.add(gen(g2,SET,fp2));rows.add(row(Table.ELEMENT_SET,set2,MEETING,2L,TIME));
  rows.add(note(uid(72),set2,G,1L));rows.add(row(Table.BACKGROUND_NOTE_SOURCE,uid(72),LOC));
  rows.add(note(uid(73),set2,g2,2L));rows.add(row(Table.BACKGROUND_NOTE_SOURCE,uid(73),LOC));
  var configs=new ArrayList<>(p.configurations());configs.add(config(g2,p));
  var tasks=new ArrayList<>(p.taskSpecs());tasks.add(task(g2,fp2));
  var f=facts(rows,p,configs,tasks,p.evidenceAnchors());
  var tight=new GraphBudget(GRAPH.canonical(),1,100,10000);
  var out=new GenerationFingerprint(service(),full(),B).compute(new GenerationDraft(uid(74),MEETING,C,null,T,X,set2,"test.generation.prompt.1","test.generation.model.1",Scope.FULL,null),f,new Fence(14,15,2),tight,()->false);
  System.out.println("DEPTH: accepted root origins [G1,G2], G2->G1 with maxOriginDepth=1; business fingerprint="+out.fingerprint());
 }
 static void nested()throws Exception {
  var p=finalStored();String r2=uid(80),s2=uid(81),c2=uid(82),g2=uid(83),set2=uid(84),l2=uid(85),note=uid(86);
  var rows=new ArrayList<>(p.rows());rows.removeIf(r->r.table()==Table.SOURCE_LOCATOR&&r.string("id").equals(LOC));rows.add(loc(LOC,r2,s2));
  rows.add(row(Table.TRANSCRIPT_REVISION,r2,MEETING,2L,"WORKING",BATCH,R,expected(),"human",TIME,INPUT,null));
  rows.add(row(Table.TRANSCRIPT_SEGMENT,r2,s2,1L,"different exact saved body",FILE,1L,null,null,null));
  rows.add(row(Table.TRANSCRIPT_CONFIRMATION,c2,MEETING,r2,H,"human",TIME));
  rows.add(gen(g2,SET,H));
  var configs=new ArrayList<>(p.configurations());configs.add(config(g2,p));
  var provisional=facts(rows,p,configs,p.taskSpecs(),p.evidenceAnchors());
  var value=new GenerationFingerprint().data(new GenerationFacts(g2,provisional),new Fence(12,13,2),GRAPH,()->false);
  String fp=new CanonicalValueCodec().sha256(ContractValues.envelope("ama.generation-input.v2","GENERATION_INPUT",value),GRAPH.canonical(),()->false);
  rows.removeIf(r->r.table()==Table.GENERATION_INPUT&&r.string("id").equals(g2));rows.add(gen(g2,SET,fp));
  rows.add(row(Table.ELEMENT_SET,set2,MEETING,3L,TIME));rows.add(loc(l2,R,S));rows.add(note(note,set2,g2,1L));rows.add(row(Table.BACKGROUND_NOTE_SOURCE,note,l2));
  var tasks=new ArrayList<>(p.taskSpecs());tasks.add(task(g2,fp));var f=facts(rows,p,configs,tasks,p.evidenceAnchors());
  String actual=new TranscriptConfirmationHash(service(),archives(),B).hash(new ConfirmationFacts(c2,f),profile(archives(),ArtifactType.CONFIRMATION_HASH),GRAPH,()->false);
  System.out.println("NESTED: claimed C2="+H+"; independently invoking confirmation hash="+actual);
  try {service().verifyGeneration(new GenerationFacts(g2,f),task(g2,fp),full(),GRAPH,B,()->false);System.out.println("DIRECT_G2_UNEXPECTED_PASS");}
  catch(Failure e){System.out.println("DIRECT_G2: rejected "+e.code());}
  var draft=new GenerationDraft(uid(87),MEETING,C,null,T,X,set2,"test.generation.prompt.1","test.generation.model.1",Scope.FULL,null);
  var out=new GenerationFingerprint(service(),full(),B).compute(draft,f,new Fence(14,15,2),GRAPH,()->false);
  System.out.println("NESTED_OUTER: accepted inconsistent intrinsic C2 through G2; fingerprint="+out.fingerprint());
 }
 public static void main(String[] args)throws Exception{
  try{depth();}catch(Failure e){System.out.println("DEPTH rejected "+e.code());}
  try{nested();}catch(Failure e){System.out.println("NESTED rejected "+e.code());}
 }
}

