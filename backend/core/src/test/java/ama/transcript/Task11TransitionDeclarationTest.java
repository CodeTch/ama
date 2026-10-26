package ama.transcript;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class Task11TransitionDeclarationTest {
 @Test void internalControlledDeclarationIsCompatibleButDoesNotClaimAbsence(){var declared=new SavedTranscriptTransitions("AUTHORITY_CONTROLLED","AUTHORITY_CONTROLLED","2",List.of(),List.of());assertDoesNotThrow(declared::requireControlledPublication);assertThrows(RuntimeException.class,declared::requireAbsent);}
 @Test void legacyAbsenceRemainsADeclarationAndUnexpectedProvidersAreDenied(){var old=new SavedTranscriptTransitions("DISABLED_NOT_INSTALLED","DISABLED_NOT_INSTALLED","1",List.of(),List.of());assertDoesNotThrow(old::requireAbsent);assertEquals("DISABLED_NOT_INSTALLED",old.observation().get("publicationInstallation"));for(var declared:List.of(new SavedTranscriptTransitions("AUTHORITY_CONTROLLED","DISABLED_NOT_INSTALLED","2",List.of(),List.of()),new SavedTranscriptTransitions("AUTHORITY_CONTROLLED","AUTHORITY_CONTROLLED","2",List.of(),List.of("unknown"))))assertThrows(RuntimeException.class,declared::requireControlledPublication);}
}