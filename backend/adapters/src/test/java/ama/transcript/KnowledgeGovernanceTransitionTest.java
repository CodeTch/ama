package ama.transcript;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KnowledgeGovernanceTransitionTest {
 @Test void freshActiveCanRecoverUnknownButNeverClearPendingOrTerminal(){
  assertEquals("ACTIVE",KnowledgeGovernanceTransition.next("UNKNOWN",null,-1,"ACTIVE",0));
  assertEquals("WITHDRAW_PENDING",KnowledgeGovernanceTransition.next("WITHDRAW_PENDING","ACTIVE",0,"ACTIVE",0));
  assertEquals("OBSOLETE_PENDING",KnowledgeGovernanceTransition.next("OBSOLETE_PENDING","ACTIVE",0,"ACTIVE",0));
  assertThrows(IllegalArgumentException.class,()->KnowledgeGovernanceTransition.next("WITHDRAWN","WITHDRAWN",1,"ACTIVE",2));
  assertThrows(IllegalArgumentException.class,()->KnowledgeGovernanceTransition.next("OBSOLETE","OBSOLETE",1,"ACTIVE",0));
 }
 @Test void signedTerminalRevokesEvenWithoutALocalCommandAndRetriesAreStable(){
  assertEquals("WITHDRAWN",KnowledgeGovernanceTransition.next("ACTIVE","ACTIVE",0,"WITHDRAWN",1));
  assertEquals("WITHDRAWN",KnowledgeGovernanceTransition.next("UNKNOWN",null,-1,"WITHDRAWN",1));
  assertEquals("OBSOLETE",KnowledgeGovernanceTransition.next("WITHDRAW_PENDING","ACTIVE",0,"OBSOLETE",1));
  assertEquals("WITHDRAWN",KnowledgeGovernanceTransition.next("WITHDRAWN","WITHDRAWN",1,"WITHDRAWN",1));
 }
 @Test void oldVersionsConflictingSameVersionAndSecondTerminalAreRejected(){
  assertThrows(IllegalArgumentException.class,()->KnowledgeGovernanceTransition.next("ACTIVE","ACTIVE",2,"ACTIVE",1));
  assertThrows(IllegalArgumentException.class,()->KnowledgeGovernanceTransition.next("ACTIVE","ACTIVE",0,"WITHDRAWN",0));
  assertThrows(IllegalArgumentException.class,()->KnowledgeGovernanceTransition.next("OBSOLETE","OBSOLETE",1,"WITHDRAWN",2));
  assertThrows(IllegalArgumentException.class,()->KnowledgeGovernanceTransition.next("ACTIVE",null,-1,"UNKNOWN",0));
 }
}
