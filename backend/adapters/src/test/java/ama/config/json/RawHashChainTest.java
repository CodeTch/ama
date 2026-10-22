package ama.config.json;
import ama.config.*;import ama.minutes.contract.*;import ama.contract.MinutesContracts.*;import ama.contract.ConfigArchiveContracts.*;import ama.contract.TaskContracts.*;
import ama.canonical.*;import java.util.*;import java.nio.charset.StandardCharsets;import org.junit.jupiter.api.Test;
import static ama.config.json.ConfigBindingsTest.*;import static ama.contract.MinutesContracts.*;import static ama.contract.MinutesContracts.FailureCode.*;import static org.junit.jupiter.api.Assertions.*;
class RawHashChainTest {
 @Test void fix1DirectStoredVerifyRejectsCorruptedNestedIntrinsicConfirmation()throws Exception{
  var p=OriginGraphFix1Cases.nested(true);var t=p.taskSpecs().getLast();
  assertEquals(FINGERPRINT_MISMATCH,assertThrows(Failure.class,()->service().verifyGeneration(new GenerationFacts(OriginGraphFix1Cases.NESTED,p),t,full(),GRAPH,B,()->false)).code());
 }
 @Test void fix1OuterDraftRejectsCorruptedNestedIntrinsicConfirmation()throws Exception{
  var p=OriginGraphFix1Cases.nested(true);
  assertEquals(FINGERPRINT_MISMATCH,assertThrows(Failure.class,()->new GenerationFingerprint(service(),full(),B).compute(OriginGraphFix1Cases.draft(uid(89),OriginGraphFix1Cases.OUTER_SET),p,new Fence(14,15,2),GRAPH,()->false)).code());
 }
 @Test void fix1ReviewRejectsCorruptedNestedIntrinsicConfirmation()throws Exception{
  var p=OriginGraphFix1Cases.nested(true);var r=new ReviewFacts(MEETING,ReviewKind.SOURCE_SUPPORT,null,MIN,OutputValidation.anchor("body",0,4,()->false),List.of(uid(88)),p);
  assertEquals(FINGERPRINT_MISMATCH,assertThrows(Failure.class,()->new ResultHashProfiles(service(),full(),B).sources(r,profile(full(),ArtifactType.REVIEW_HASH),GRAPH,()->false)).code());
 }
 @Test void fix1FinalRejectsCorruptedNestedIntrinsicConfirmation()throws Exception{
  var p=OriginGraphFix1Cases.nested(true);
  assertEquals(FINGERPRINT_MISMATCH,assertThrows(Failure.class,()->new ResultHashProfiles(service(),full(),B).finalSources(new FinalFacts(FINAL,p),profile(full(),ArtifactType.FINAL_SOURCES),GRAPH,()->false)).code());
 }
 @Test void fix1ValidNestedIntrinsicChainSucceedsAcrossAllBusinessEntryPoints()throws Exception{
  var p=OriginGraphFix1Cases.nested(false);var r=new ReviewFacts(MEETING,ReviewKind.SOURCE_SUPPORT,null,MIN,OutputValidation.anchor("body",0,4,()->false),List.of(uid(88)),p);
  assertDoesNotThrow(()->service().verifyGeneration(new GenerationFacts(OriginGraphFix1Cases.NESTED,p),p.taskSpecs().getLast(),full(),GRAPH,B,()->false));
  assertDoesNotThrow(()->new GenerationFingerprint(service(),full(),B).compute(OriginGraphFix1Cases.draft(uid(89),OriginGraphFix1Cases.OUTER_SET),p,new Fence(14,15,2),GRAPH,()->false));
  assertDoesNotThrow(()->new ResultHashProfiles(service(),full(),B).sources(r,profile(full(),ArtifactType.REVIEW_HASH),GRAPH,()->false));
  assertDoesNotThrow(()->new ResultHashProfiles(service(),full(),B).finalSources(new FinalFacts(FINAL,p),profile(full(),ArtifactType.FINAL_SOURCES),GRAPH,()->false));
 }
 @Test void fix1SharedLeafDepthUsesEveryPathInEitherOriginOrdering()throws Exception{
  for(boolean reverse:List.of(false,true)){
   var p=OriginGraphFix1Cases.dag(reverse,false);var d=OriginGraphFix1Cases.draft(uid(150),uid(105));
   assertDoesNotThrow(()->new GenerationFingerprint(service(),full(),B).compute(d,p,new Fence(14,15,2),new GraphBudget(GRAPH.canonical(),2,2,10000),()->false));
   assertEquals(BUDGET_EXCEEDED,assertThrows(Failure.class,()->new GenerationFingerprint(service(),full(),B).compute(d,p,new Fence(14,15,2),new GraphBudget(GRAPH.canonical(),1,2,10000),()->false)).code());
  }
 }
 @Test void fix1ReusedNonleafDiamondDepthUsesCachedSubtreeHeight()throws Exception{
  for(boolean reverse:List.of(false,true)){
   var p=OriginGraphFix1Cases.dag(reverse,true);var d=OriginGraphFix1Cases.draft(uid(150),uid(105));
   assertDoesNotThrow(()->new GenerationFingerprint(service(),full(),B).compute(d,p,new Fence(14,15,2),new GraphBudget(GRAPH.canonical(),3,3,10000),()->false));
   assertEquals(BUDGET_EXCEEDED,assertThrows(Failure.class,()->new GenerationFingerprint(service(),full(),B).compute(d,p,new Fence(14,15,2),new GraphBudget(GRAPH.canonical(),2,3,10000),()->false)).code());
  }
 }
 @Test void fix1OriginNodeBudgetIsUniqueAndCumulativeAcrossSharedBranches()throws Exception{
  var p=OriginGraphFix1Cases.dag(false,true);var d=OriginGraphFix1Cases.draft(uid(150),uid(105));
  assertDoesNotThrow(()->new GenerationFingerprint(service(),full(),B).compute(d,p,new Fence(14,15,2),new GraphBudget(GRAPH.canonical(),3,3,10000),()->false));
  assertEquals(BUDGET_EXCEEDED,assertThrows(Failure.class,()->new GenerationFingerprint(service(),full(),B).compute(d,p,new Fence(14,15,2),new GraphBudget(GRAPH.canonical(),3,2,10000),()->false)).code());
 }
 @Test void fix1DeepRawBackedOriginsMeetExactDepthAndRejectOneMore()throws Exception{
  var p=finalStored();String previous=G;
  for(int i=0;i<8;i++){String set=uid(201+i),input=uid(301+i);p=OriginGraphFix1Cases.origins(p,set,List.of(previous),401+i);p=OriginGraphFix1Cases.store(p,input,set);previous=input;}
  var facts=OriginGraphFix1Cases.origins(p,uid(501),List.of(previous),502);var d=OriginGraphFix1Cases.draft(uid(503),uid(501));
  assertDoesNotThrow(()->new GenerationFingerprint(service(),full(),B).compute(d,facts,new Fence(14,15,2),new GraphBudget(GRAPH.canonical(),9,9,10000),()->false));
  assertEquals(BUDGET_EXCEEDED,assertThrows(Failure.class,()->new GenerationFingerprint(service(),full(),B).compute(d,facts,new Fence(14,15,2),new GraphBudget(GRAPH.canonical(),8,9,10000),()->false)).code());
 }
 @Test void fix1RawBackedCycleAndCancellationCannotReturnBusinessDigest()throws Exception{
  var p=OriginGraphFix1Cases.dag(false,true);var rows=new ArrayList<>(p.rows());rows.removeIf(r->r.table()==Table.GENERATION_INPUT&&r.string("id").equals(uid(102)));
  rows.add(OriginGraphFix1Cases.gen(uid(102),uid(105),p.taskSpecs().stream().filter(t->t.input().id().equals(uid(102))).findFirst().orElseThrow().inputFingerprint()));
  var facts=OriginGraphFix1Cases.facts(p,rows,p.configurations(),p.taskSpecs(),p.evidenceAnchors());var d=OriginGraphFix1Cases.draft(uid(150),uid(105));
  assertEquals(INPUT_INVALID,assertThrows(Failure.class,()->new GenerationFingerprint(service(),full(),B).compute(d,facts,new Fence(14,15,2),GRAPH,()->false)).code());
  assertEquals(CANCELLED,assertThrows(Failure.class,()->new GenerationFingerprint(service(),full(),B).compute(d,p,new Fence(14,15,2),GRAPH,()->true)).code());
 }
 static ArchiveReadBudget totalBudget(long bytes){return new ArchiveReadBudget(B.maxArtifactBytes(),bytes,B.maxArtifacts(),B.maxDependencies(),B.maxGraphDepth(),B.maxTokens(),B.maxJsonDepth(),B.maxNameUtf16(),B.maxStringUtf16(),B.maxStringUtf8Bytes(),B.maxAggregateScalars(),B.maxManifestBytes(),B.maxBatchFiles(),B.maxFactRows(),B.maxFactEdges());}
 @Test void fix1SharedHistoricalArchiveAcquisitionUsesOneCumulativeByteBudget()throws Exception{
  var p=OriginGraphFix1Cases.nested(false);var raw=full();long exact=raw.stream().mapToLong(a->a.bytes().length).sum()+fixture("manifest").length;
  var d=OriginGraphFix1Cases.draft(uid(89),OriginGraphFix1Cases.OUTER_SET);
  assertDoesNotThrow(()->new GenerationFingerprint(service(),raw,totalBudget(exact)).compute(d,p,new Fence(14,15,2),GRAPH,()->false));
  assertDoesNotThrow(()->new ResultHashProfiles(service(),raw,totalBudget(exact)).finalSources(new FinalFacts(FINAL,p),profile(raw,ArtifactType.FINAL_SOURCES),GRAPH,()->false));
  assertEquals(BUDGET_EXCEEDED,assertThrows(Failure.class,()->new GenerationFingerprint(service(),raw,totalBudget(exact-1)).compute(d,p,new Fence(14,15,2),GRAPH,()->false)).code());
 }
 @Test void fix1ContextArtifactsReuseTheSameIsolatedBoundedOriginal()throws Exception{
  var p=storedGeneration();var raw=full();var context=service().begin(p,raw,B,()->false);
  var input=p.rows().stream().filter(r->r.table()==Table.GENERATION_INPUT).findFirst().orElseThrow();var first=context.generation(input);var second=context.generation(input);
  assertSame(first.prompt().bytes(),second.prompt().bytes());
  for(var kind:ProfileKind.values())assertSame(first.profiles().definition(kind).bytes(),second.profiles().definition(kind).bytes());
  for(var archive:raw)archive.bytes()[0]='x';
  assertEquals(first,context.generation(input));assertDoesNotThrow(context::finish);
  assertEquals(p.configurations().getFirst(),first);
 }
 static final String G="55555555-5555-4555-8555-555555555555",R="66666666-6666-4666-8666-666666666666",S="77777777-7777-4777-8777-777777777777",C="88888888-8888-4888-8888-888888888888",T="99999999-9999-4999-8999-999999999999",X="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
 static final GraphBudget GRAPH=new GraphBudget(new CanonicalBudget(1000000,100,100000,100000,100000),30,100,10000);
 static String resource(String path)throws Exception{try(var in=RawHashChainTest.class.getResourceAsStream("/ama/config/"+path)){assertNotNull(in);return new String(in.readAllBytes(),StandardCharsets.UTF_8).strip();}}
 static List<RawArchive> full()throws Exception{
  var out=new ArrayList<RawArchive>();for(var line:resource("full/index.txt").split("\\R")){var p=line.split("[|]");try(var in=RawHashChainTest.class.getResourceAsStream("/ama/config/full/"+p[2])){out.add(new RawArchive(ArtifactType.valueOf(p[0]),p[1],in.readAllBytes()));}}return out;
 }
 static StoredProvenanceFacts beforeGeneration()throws Exception{
  var p=prerequisites();String processing=expected();var rows=new ArrayList<>(p.rows());rows.add(input(processing,new String(fixture("manifest"),StandardCharsets.UTF_8)));
  rows.add(row(Table.TRANSCRIPT_REVISION,R,MEETING,1L,"WORKING",BATCH,null,processing,"human",TIME,INPUT,null));
  rows.add(row(Table.TRANSCRIPT_SEGMENT,R,S,1L,"A",FILE,1L,null,null,null));
  rows.add(row(Table.TRANSCRIPT_CONFIRMATION,C,MEETING,R,resource("full-confirmation.sha256"),"human",TIME));
  rows.add(row(Table.TEMPLATE_REVISION,T,"00000000-0000-0000-0000-000000000090",1L,null,"synthetic default","synthetic",1L,TIME));
  rows.add(row(Table.EXCLUSION_REVISION,X,MEETING,1L,TIME));
  var task=new TaskSpec("TRANSCRIBE","TRANSCRIBE",new SubjectRef("MEETING",MEETING),H,new TypedRef("TRANSCRIPTION_INPUT",INPUT),processing,9,0,2,1);
  return new StoredProvenanceFacts(rows,p.files(),List.of(),List.of(),List.of(task),100000L);
 }
 static GenerationDraft draft(){return new GenerationDraft(G,MEETING,C,null,T,X,null,"test.generation.prompt.1","test.generation.model.1",Scope.FULL,null);}
 @Test void rawFullGenerationComputesBeforeOwnRowTaskAndOutputExist()throws Exception{
  var facts=beforeGeneration();var helper=new GenerationFingerprint(service(),full(),B);
  var result=assertDoesNotThrow(()->helper.compute(draft(),facts,new Fence(10,11,2),GRAPH,()->false));
  assertEquals(resource("full-generation.sha256"),result.fingerprint());
  assertEquals(resource("full-generation.hex"),HexFormat.of().formatHex(new CanonicalValueCodec().encode(ContractValues.envelope("ama.generation-input.v2","GENERATION_INPUT",result.manifest()),GRAPH.canonical(),()->false)));
 }
 @Test void confirmationRawChainReloadRetainsOriginalProfileAndActualContent()throws Exception{
  var facts=beforeGeneration();var raw=archives();var profile=raw.stream().filter(a->a.claimedArtifactType()==ArtifactType.CONFIRMATION_HASH).findFirst().orElseThrow();
  var owned=ArchiveBytes.capture(profile.bytes(),B.maxArtifactBytes(),B.maxTotalBytes(),()->false);var artifact=new Artifact(profile.claimedVersion(),owned.sha256(()->false),owned);
  String actual=new TranscriptConfirmationHash(service(),raw,B).hash(new ConfirmationFacts(C,facts),artifact,GRAPH,()->false);
  assertEquals(resource("full-confirmation.sha256"),actual);
 }

 static final String SET="bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",MIN="cccccccc-cccc-4ccc-8ccc-cccccccccccc",FINAL="dddddddd-dddd-4ddd-8ddd-dddddddddddd",LOC="eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee",EV="ffffffff-ffff-4fff-8fff-ffffffffffff",ELEMENT="00000000-0000-0000-0000-000000000012";
 static StoredProvenanceFacts storedGeneration()throws Exception{
  var p=beforeGeneration();String fingerprint=resource("full-generation.sha256");var rows=new ArrayList<>(p.rows());
  var input=row(Table.GENERATION_INPUT,G,MEETING,C,null,T,X,null,"test.generation.prompt.1","test.generation.model.1",10L,fingerprint,"FULL",null);rows.add(input);
  var tasks=new ArrayList<>(p.taskSpecs());tasks.add(new TaskSpec("MINUTES","MINUTES",new SubjectRef("MEETING",MEETING),H,new TypedRef("GENERATION_INPUT",G),fingerprint,11,0,2,1));
  var preliminary=new StoredProvenanceFacts(rows,p.files(),List.of(),List.of(),tasks,100000L);var context=service().begin(preliminary,full(),B,()->false);var config=context.generation(input);context.finish();
  return new StoredProvenanceFacts(rows,p.files(),List.of(config),List.of(),tasks,100000L);
 }
 static StoredProvenanceFacts finalStored()throws Exception{
  var p=storedGeneration();var rows=new ArrayList<>(p.rows());rows.add(row(Table.ELEMENT_SET,SET,MEETING,1L,TIME));rows.add(row(Table.MINUTES_ELEMENT,ELEMENT,SET,"CONCLUSION",1L,"A","PARTIAL"));
  rows.add(row(Table.SOURCE_LOCATOR,LOC,MEETING,"TRANSCRIPT",R,S,null,null,null,null,null,null,null));
  var anchor=OutputValidation.anchor("A",0,1,()->false);rows.add(row(Table.EVIDENCE_LINK,EV,LOC,ELEMENT,null,"PARTIAL",OutputValidation.anchorText(anchor)));
  rows.add(row(Table.MINUTES_REVISION,MIN,MEETING,1L,null,"synthetic title","AI","body",G,"human",TIME));
  rows.add(row(Table.FINAL_CONFIRMATION,FINAL,MEETING,10L,"00000000-0000-0000-0000-000000000021","00000000-0000-0000-0000-000000000022",C,MIN,null,SET,T,X,H,resource("full-generation.sha256"),"human",TIME));
  return new StoredProvenanceFacts(rows,p.files(),p.configurations(),List.of(new EvidenceAnchor(EV,anchor)),p.taskSpecs(),100000L);
 }
 static Artifact profile(List<RawArchive> raw,ArtifactType kind){
  var a=raw.stream().filter(r->r.claimedArtifactType()==kind).findFirst().orElseThrow();var copy=ArchiveBytes.capture(a.bytes(),B.maxArtifactBytes(),B.maxTotalBytes(),()->false);return new Artifact(a.claimedVersion(),copy.sha256(()->false),copy);
 }
 @Test void completeStoredGenerationAndFinalReloadHashesUseRawOriginalArchives()throws Exception{
  var stored=storedGeneration();var generation=new GenerationFingerprint(service(),full(),B);
  var task=stored.taskSpecs().stream().filter(t->t.input().kind().equals("GENERATION_INPUT")).findFirst().orElseThrow();
  var verified=assertDoesNotThrow(()->generation.verify(new GenerationFacts(G,stored),task,stored.configurations().getFirst().profiles(),GRAPH,()->false));
  assertEquals(resource("full-generation.sha256"),verified.fingerprint());
  var finalFacts=finalStored();var finalHash=assertDoesNotThrow(()->new ResultHashProfiles(service(),full(),B).finalSources(new FinalFacts(FINAL,finalFacts),profile(full(),ArtifactType.FINAL_SOURCES),GRAPH,()->false));
  assertEquals(resource("full-final.sha256"),finalHash);
 }
 @Test void exactReviewArchiveClosureAndIntrinsicConfirmationProduceIndependentHashes()throws Exception{
  var facts=finalStored();var anchor=OutputValidation.anchor("A",0,1,()->false);var review=new ReviewFacts(MEETING,ReviewKind.SOURCE_SUPPORT,ELEMENT,null,anchor,List.of(EV),facts);
  var targetRaw=full().stream().filter(a->Set.of(ArtifactType.CANONICAL_CODEC,ArtifactType.REVIEW_HASH).contains(a.claimedArtifactType())).toList();
  var target=new ResultHashProfiles(service(),targetRaw,B).target(review,profile(targetRaw,ArtifactType.REVIEW_HASH),GRAPH,()->false);
  assertEquals(resource("full-review-target.sha256"),target);
  var sourcesRaw=full().stream().filter(a->Set.of(ArtifactType.CANONICAL_CODEC,ArtifactType.REVIEW_HASH,ArtifactType.CONFIRMATION_HASH,ArtifactType.PROCESSING_CONFIG,ArtifactType.PARSER_CONFIG).contains(a.claimedArtifactType())).toList();
  var sources=new ResultHashProfiles(service(),sourcesRaw,B).sources(review,profile(sourcesRaw,ArtifactType.REVIEW_HASH),GRAPH,()->false);
  assertEquals(resource("full-review-sources.sha256"),sources);
 }

 @Test void pinnedCanonicalBudgetAlsoProtectsTemplateRowsetBeforeItsDigestIsHidden()throws Exception{
  var raw=full();var old=raw.stream().filter(a->a.claimedArtifactType()==ArtifactType.VALIDATION_BUDGET).findFirst().orElseThrow();
  String oldSha=ArchiveBytes.capture(old.bytes(),100000,100000,()->false).sha256(()->false);
  String altered=new String(old.bytes(),StandardCharsets.UTF_8).replace("\"maxStringBytes\":100000","\"maxStringBytes\":64").replace("test.minutes-budget.1","test.minutes-budget.tight.1");
  byte[] tight=altered.getBytes(StandardCharsets.UTF_8);String newSha=ArchiveBytes.capture(tight,100000,100000,()->false).sha256(()->false);
  var changed=new ArrayList<RawArchive>();
  for(var a:raw){
   if(a==old){changed.add(new RawArchive(a.claimedArtifactType(),"test.minutes-budget.tight.1",tight));continue;}
   if(a.claimedArtifactType()==ArtifactType.PROMPT_CONFIG||a.claimedArtifactType()==ArtifactType.MODEL_CONFIG){
    String version=a.claimedVersion().replace(".1",".tight.1");
    String value=new String(a.bytes(),StandardCharsets.UTF_8).replace(oldSha,newSha).replace("test.minutes-budget.1","test.minutes-budget.tight.1").replace(a.claimedVersion(),version);
    changed.add(new RawArchive(a.claimedArtifactType(),version,value.getBytes(StandardCharsets.UTF_8)));
   }else changed.add(a);
  }
  var p=beforeGeneration();var rows=new ArrayList<>(p.rows());rows.removeIf(r->r.table()==Table.TEMPLATE_REVISION);rows.add(row(Table.TEMPLATE_REVISION,T,"00000000-0000-0000-0000-000000000090",1L,null,"x".repeat(65),"synthetic",1L,TIME));
  var facts=new StoredProvenanceFacts(rows,p.files(),List.of(),List.of(),p.taskSpecs(),100000L);var draft=new GenerationDraft(G,MEETING,C,null,T,X,null,"test.generation.prompt.tight.1","test.generation.model.tight.1",Scope.FULL,null);
  assertEquals(BUDGET_EXCEEDED,assertThrows(Failure.class,()->new GenerationFingerprint(service(),changed,B).compute(draft,facts,new Fence(10,11,2),GRAPH,()->false)).code());
 }

 @Test void storedTaskIdentityFactsCanSupplyCompleteHistoricalGenerationFence()throws Exception{
  var p=storedGeneration();var task=p.taskSpecs().stream().filter(t->t.input().kind().equals("GENERATION_INPUT")).findFirst().orElseThrow();
  var specs=p.taskSpecs().stream().filter(t->!t.input().kind().equals("GENERATION_INPUT")).toList();
  var facts=new StoredProvenanceFacts(p.rows(),p.files(),p.configurations(),p.evidenceAnchors(),specs,List.of(),List.of(new StoredTask("00000000-0000-0000-0000-000000000041",task)),100000L);
  var result=assertDoesNotThrow(()->service().verifyGeneration(new GenerationFacts(G,facts),task,full(),GRAPH,B,()->false));
  assertEquals(resource("full-generation.sha256"),result.fingerprint());assertEquals(100000,result.outputBudget().maxWireBytes());
 }

 static String uid(int n){return "00000000-0000-0000-0000-%012d".formatted(n);}
 static StoredProvenanceFacts matrix()throws Exception{
  var p=finalStored();var rows=new ArrayList<>(p.rows());var a=OutputValidation.anchor("A",0,1,()->false);var anchors=new ArrayList<>(p.evidenceAnchors());
  var bodyAnchor=OutputValidation.anchor("body",0,4,()->false);rows.add(row(Table.EVIDENCE_LINK,uid(40),LOC,null,MIN,"PARTIAL",OutputValidation.anchorText(bodyAnchor)));anchors.add(new EvidenceAnchor(uid(40),bodyAnchor));
  rows.add(row(Table.MINUTES_ELEMENT,uid(41),SET,"ACTION",1L,"A","PARTIAL"));
  rows.add(row(Table.ACTION_SUGGESTION,uid(41),G,null,null));rows.add(row(Table.ACTION_ITEM,uid(41),null,null,null));
  rows.add(row(Table.EVIDENCE_LINK,uid(42),LOC,uid(41),null,"PARTIAL",OutputValidation.anchorText(a)));anchors.add(new EvidenceAnchor(uid(42),a));
  rows.add(row(Table.BACKGROUND_NOTE,uid(43),SET,G,1L,"context"));rows.add(row(Table.BACKGROUND_NOTE_SOURCE,uid(43),LOC));
  int n=44;for(var label:SensitiveKind.values()){rows.add(row(Table.SENSITIVE_DETECTION,uid(n),SET,G,label.name(),1L));rows.add(row(Table.SENSITIVE_DETECTION_SOURCE,uid(n++),LOC));}
  return new StoredProvenanceFacts(rows,p.files(),p.configurations(),anchors,p.taskSpecs(),100000L);
 }
 @Test void actualBodyActionDetectionBackgroundAndIntrinsicMatrixMatchesFullIndependentFinal()throws Exception{
  var p=matrix();var graph=new StoredSourceGraph().derive(p,new GraphSelection(MEETING,MIN,SET,X,G),GRAPH,()->false);
  assertEquals(1,graph.locatorRows().size());assertEquals(6,graph.sourceUsages().size());
  assertEquals(5,graph.sourceUsages().stream().filter(u->u.origin() instanceof GenerationOrigin).count());
  assertEquals(1,graph.sourceUsages().stream().filter(u->u.origin() instanceof ConfirmationOrigin).count());
  assertEquals(resource("full-final-matrix.sha256"),new ResultHashProfiles(service(),full(),B).finalSources(new FinalFacts(FINAL,p),profile(full(),ArtifactType.FINAL_SOURCES),GRAPH,()->false));
 }
 @Test void pinnedSetResolvesAllActualHistoricalOriginsAndMatchesIndependentBytes()throws Exception{
  var p=matrix();var d=new GenerationDraft(uid(50),MEETING,C,null,T,X,SET,"test.generation.prompt.1","test.generation.model.1",Scope.FULL,null);
  var computed=new GenerationFingerprint(service(),full(),B).compute(d,p,new Fence(12,13,2),GRAPH,()->false);
  assertEquals(resource("full-generation-pinned.sha256"),computed.fingerprint());
  assertEquals(resource("full-generation-pinned.hex"),HexFormat.of().formatHex(new CanonicalValueCodec().encode(ContractValues.envelope("ama.generation-input.v2","GENERATION_INPUT",computed.manifest()),GRAPH.canonical(),()->false)));
  var tasks=new ArrayList<>(p.taskSpecs());var prior=tasks.stream().filter(t->t.input().id().equals(G)).findFirst().orElseThrow();
  tasks.add(new TaskSpec(prior.type(),prior.pool(),prior.subject(),prior.producerKey(),prior.input(),prior.inputFingerprint(),99,0,prior.cancelEpoch(),1));
  var disagree=new StoredProvenanceFacts(p.rows(),p.files(),p.configurations(),p.evidenceAnchors(),tasks,100000L);
  assertEquals(INPUT_INVALID,assertThrows(Failure.class,()->new GenerationFingerprint(service(),full(),B).compute(d,disagree,new Fence(12,13,2),GRAPH,()->false)).code());
 }
 @Test void presentContradictorySuggestionNeverFallsBackToIntrinsicTranscript()throws Exception{
  var p=matrix();var rows=new ArrayList<>(p.rows());rows.removeIf(r->r.table()==Table.ACTION_SUGGESTION);rows.add(row(Table.ACTION_SUGGESTION,uid(41),uid(999),null,null));
  var invalid=new StoredProvenanceFacts(rows,p.files(),p.configurations(),p.evidenceAnchors(),p.taskSpecs(),100000L);
  assertEquals(SOURCE_MEMBERSHIP_MISMATCH,assertThrows(Failure.class,()->new StoredSourceGraph().derive(invalid,new GraphSelection(MEETING,MIN,SET,null,null),GRAPH,()->false)).code());
 }
 @Test void newExclusionRevisionComputesIndependentlyAndAppliedOriginRequiresExactBodyInput()throws Exception{
  var p=finalStored();var rows=new ArrayList<>(p.rows());rows.add(row(Table.EXCLUSION_REVISION,uid(51),MEETING,2L,TIME));
  for(var label:SensitiveKind.values())rows.add(row(Table.SENSITIVE_EXCLUSION,uid(51),LOC,label.name(),"saved reason"));
  var prereq=new StoredProvenanceFacts(rows,p.files(),p.configurations(),p.evidenceAnchors(),p.taskSpecs(),100000L);
  var d=new GenerationDraft(uid(52),MEETING,C,null,T,uid(51),null,"test.generation.prompt.1","test.generation.model.1",Scope.FULL,null);
  var computed=new GenerationFingerprint(service(),full(),B).compute(d,prereq,new Fence(12,13,2),GRAPH,()->false);
  assertEquals(resource("full-generation-excluded.sha256"),computed.fingerprint());
  assertEquals(resource("full-generation-excluded.hex"),HexFormat.of().formatHex(new CanonicalValueCodec().encode(ContractValues.envelope("ama.generation-input.v2","GENERATION_INPUT",computed.manifest()),GRAPH.canonical(),()->false)));
  rows.add(row(Table.GENERATION_INPUT,uid(52),MEETING,C,null,T,uid(51),null,"test.generation.prompt.1","test.generation.model.1",12L,computed.fingerprint(),"FULL",null));
  rows.removeIf(r->r.table()==Table.MINUTES_REVISION||r.table()==Table.FINAL_CONFIRMATION);
  rows.add(row(Table.MINUTES_REVISION,MIN,MEETING,2L,null,"synthetic title","AI","body",uid(52),"human",TIME));
  var tasks=new ArrayList<>(p.taskSpecs());tasks.add(new TaskSpec("MINUTES","MINUTES",new SubjectRef("MEETING",MEETING),H,new TypedRef("GENERATION_INPUT",uid(52)),computed.fingerprint(),13,0,2,1));
  var interim=new StoredProvenanceFacts(rows,p.files(),p.configurations(),p.evidenceAnchors(),tasks,100000L);var context=service().begin(interim,full(),B,()->false);
  var config=context.generation(rows.stream().filter(r->r.table()==Table.GENERATION_INPUT&&r.string("id").equals(uid(52))).findFirst().orElseThrow());context.finish();
  var configs=new ArrayList<>(p.configurations());configs.add(config);var facts=new StoredProvenanceFacts(rows,p.files(),configs,p.evidenceAnchors(),tasks,100000L);
  var graph=new StoredSourceGraph().derive(facts,new GraphSelection(MEETING,MIN,SET,uid(51),uid(52)),GRAPH,()->false);
  assertEquals(2,graph.sourceUsages().stream().filter(u->u.origin() instanceof AppliedOrigin a&&a.inputId().equals(uid(52))).count());
  assertEquals(SOURCE_MEMBERSHIP_MISMATCH,assertThrows(Failure.class,()->new StoredSourceGraph().derive(facts,new GraphSelection(MEETING,MIN,SET,X,G),GRAPH,()->false)).code());
  assertEquals(computed.fingerprint(),service().verifyGeneration(new GenerationFacts(uid(52),facts),tasks.getLast(),full(),GRAPH,B,()->false).fingerprint());
 }
 @Test void selectedReviewUsesSavedScopeAndExactFrozenMemberships()throws Exception{
  var p=finalStored();var rows=new ArrayList<>(p.rows());var a=OutputValidation.anchor("A",0,1,()->false);
  rows.add(row(Table.RESULT_REVIEW,uid(53),MEETING,1L,"SOURCE_SUPPORT",ELEMENT,null,OutputValidation.anchorText(a),resource("full-review-target.sha256"),resource("full-review-sources.sha256"),"ama.result-review.v2","PARTIAL","human",TIME));
  rows.add(row(Table.RESULT_REVIEW_EVIDENCE,uid(53),EV));rows.add(row(Table.FINAL_RESULT_REVIEW,FINAL,uid(53)));
  var f=new StoredProvenanceFacts(rows,p.files(),p.configurations(),p.evidenceAnchors(),p.taskSpecs(),List.of(new ReviewScopeFact(uid(53),a)),100000L);
  assertEquals(resource("full-final-review.sha256"),new ResultHashProfiles(service(),full(),B).finalSources(new FinalFacts(FINAL,f),profile(full(),ArtifactType.FINAL_SOURCES),GRAPH,()->false));
  rows.removeIf(r->r.table()==Table.RESULT_REVIEW_EVIDENCE);
  var bad=new StoredProvenanceFacts(rows,p.files(),p.configurations(),p.evidenceAnchors(),p.taskSpecs(),List.of(new ReviewScopeFact(uid(53),a)),100000L);
  assertEquals(SOURCE_MEMBERSHIP_MISMATCH,assertThrows(Failure.class,()->new ResultHashProfiles(service(),full(),B).finalSources(new FinalFacts(FINAL,bad),profile(full(),ArtifactType.FINAL_SOURCES),GRAPH,()->false)).code());
 }

 @Test void savedManualChildUsesExactBodyMarksAndInheritedOriginalProcessingProfile()throws Exception{
  var p=beforeGeneration();var rows=new ArrayList<>(p.rows());
  rows.add(row(Table.TRANSCRIPT_REVISION,uid(54),MEETING,2L,"WORKING",BATCH,R,expected(),"human",TIME,INPUT,null));
  rows.add(row(Table.TRANSCRIPT_SEGMENT,uid(54),uid(55),1L,"manual\n",FILE,1L,null,null,null));
  for(var mark:Mark.values())rows.add(row(Table.TRANSCRIPT_SEGMENT_MARK,uid(54),uid(55),mark.name()));
  rows.add(row(Table.TRANSCRIPT_CONFIRMATION,uid(56),MEETING,uid(54),resource("full-manual-confirmation.sha256"),"human",TIME));
  var facts=new StoredProvenanceFacts(rows,p.files(),List.of(),List.of(),p.taskSpecs(),100000L);
  assertEquals(resource("full-manual-confirmation.sha256"),new TranscriptConfirmationHash(service(),archives(),B).hash(new ConfirmationFacts(uid(56),facts),profile(archives(),ArtifactType.CONFIRMATION_HASH),GRAPH,()->false));
 }
 @Test void unrelatedCurrentConfigFactsAndQueryOrderCannotChangeHistoricalFinalHash()throws Exception{
  var p=finalStored();var rows=new ArrayList<>(p.rows());Collections.reverse(rows);
  byte[] body="synthetic changed current prompt".getBytes(StandardCharsets.UTF_8);String sha=ArchiveBytes.capture(body,100000,100000,()->false).sha256(()->false);
  var configs=new ArrayList<>(p.configurations());var old=configs.getFirst();
  configs.add(new GenerationConfiguration(uid(57),new Artifact("test.changed-current.2",sha,ArchiveBytes.capture(body,B.maxArtifactBytes(),B.maxTotalBytes(),()->false)),old.model(),old.profiles(),T,List.of()));
  var f=new StoredProvenanceFacts(rows,p.files(),configs,p.evidenceAnchors(),p.taskSpecs(),100000L);var raw=new ArrayList<>(full());Collections.reverse(raw);
  assertEquals(resource("full-final.sha256"),new ResultHashProfiles(service(),raw,B).finalSources(new FinalFacts(FINAL,f),profile(raw,ArtifactType.FINAL_SOURCES),GRAPH,()->false));
 }
}
