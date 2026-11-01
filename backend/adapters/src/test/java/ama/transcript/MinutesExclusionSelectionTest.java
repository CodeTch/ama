package ama.transcript;
import ama.contract.MinutesContracts.*;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class MinutesExclusionSelectionTest extends MinutesEditSourcesTest {
 final String locator=id(20);final String category="POLITICAL_SENSITIVE";
 List<JdbcMinutesSettingsCommands.Exclusion> selected(){return List.of(new JdbcMinutesSettingsCommands.Exclusion(locator,category,null));}
 @Test void currentVerifiedKnowledgeAndLocalDirectoriesCanBeExcluded(){for(var version:versions())assertDoesNotThrow(()->MinutesExclusionSelection.requireSelected(root,rows(),Map.of(locator+"/"+category,List.of(version)),selected()));}
 @Test void anUndetectedUnsavedTupleDoesNotBecomeSelectableFromItsLocatorAlone(){assertThrows(ama.contract.TranscriptContracts.Failure.class,()->MinutesExclusionSelection.requireSelected(root,rows(),Map.of(),selected()));}
 @Test void oldSavedTupleStillNeedsCurrentConfirmationAndMaterialSelection(){assertThrows(Failure.class,()->MinutesExclusionSelection.requireSelected(Map.of("transcript_confirmation_id",id(1),"material_snapshot_id",id(21)),rows(),Map.of(locator+"/"+category,List.of(versions().get(2))),selected()));}
 @Test void ambiguousOriginsRefuseAndAnEmptyReplacementCanClearStaleChoices(){assertThrows(Failure.class,()->MinutesExclusionSelection.requireSelected(root,rows(),Map.of(locator+"/"+category,versions()),selected()));assertDoesNotThrow(()->MinutesExclusionSelection.requireSelected(root,rows(),Map.of(),List.of()));}
}
