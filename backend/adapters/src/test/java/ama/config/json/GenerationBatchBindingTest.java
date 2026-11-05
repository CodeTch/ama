package ama.config.json;
import ama.contract.MinutesContracts.*;
import ama.contract.ConfigArchiveContracts.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static ama.config.json.ConfigBindingsTest.*;
import static ama.config.json.RawHashChainTest.*;
import static ama.contract.MinutesContracts.FailureCode.*;
class GenerationBatchBindingTest {
 @Test void sharedRawClosureIsDecodedOnceButEveryStoredInputIsVerified()throws Exception{var facts=OriginGraphFix1Cases.nested(false);var tasks=facts.taskSpecs().stream().filter(t->t.input().kind().equals("GENERATION_INPUT")).toList();assertTrue(tasks.size()>1);var raw=full();long exact=raw.stream().mapToLong(a->a.bytes().length).sum()+fixture("manifest").length;var result=service().verifyGenerations(facts,tasks,raw,GRAPH,totalBudget(exact),()->false);assertEquals(tasks.size(),result.size());for(var task:tasks){var separate=service().verifyGeneration(new GenerationFacts(task.input().id(),facts),task,raw,GRAPH,B,()->false);assertEquals(separate.fingerprint(),result.get(task.input().id()).fingerprint());assertEquals(separate.manifest(),result.get(task.input().id()).manifest());}assertEquals(BUDGET_EXCEEDED,assertThrows(Failure.class,()->service().verifyGenerations(facts,tasks,raw,GRAPH,totalBudget(exact-1),()->false)).code());}
 @Test void oneCorruptedNestedInputRejectsTheWholeBatch()throws Exception{var facts=OriginGraphFix1Cases.nested(true);var tasks=facts.taskSpecs().stream().filter(t->t.input().kind().equals("GENERATION_INPUT")).toList();assertEquals(FINGERPRINT_MISMATCH,assertThrows(Failure.class,()->service().verifyGenerations(facts,tasks,full(),GRAPH,B,()->false)).code());}
 @Test void duplicateInputsAndCancellationCannotMintBindings()throws Exception{var facts=storedGeneration();var task=facts.taskSpecs().getLast();assertEquals(INPUT_INVALID,assertThrows(Failure.class,()->service().verifyGenerations(facts,List.of(task,task),full(),GRAPH,B,()->false)).code());assertEquals(CANCELLED,assertThrows(Failure.class,()->service().verifyGenerations(facts,List.of(task),full(),GRAPH,B,()->true)).code());}
 @Test void alteredOriginalArchiveStillFailsExactClosure()throws Exception{var facts=storedGeneration();var raw=new ArrayList<>(full());var first=raw.getFirst();byte[] bytes=first.bytes();bytes[0]='x';raw.set(0,new RawArchive(first.claimedArtifactType(),first.claimedVersion(),bytes));assertThrows(Failure.class,()->service().verifyGenerations(facts,List.of(facts.taskSpecs().getLast()),raw,GRAPH,B,()->false));}
}
