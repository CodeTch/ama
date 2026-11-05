package ama.transcript;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static ama.contract.MinutesContracts.*;
class PublicationSourceContinuationTest {
 static final String MEETING="00000000-0000-4000-8000-000000000001",FINAL="00000000-0000-4000-8000-000000000002";
 MinutesGraphDiscovery.Discovery declaration(String kind,String selected,List<MinutesGraphDiscovery.Selector> selectors){return new MinutesGraphDiscovery.Discovery(MEETING,kind,selected,Map.of("owner_id","owner"),Map.of(),Map.of(),Set.of("owner"),selectors);}
 List<MinutesGraphDiscovery.Selector> history(){return List.of(new MinutesGraphDiscovery.Selector("final_confirmation","meeting_id",MEETING,""));}
 @Test void sameCompletePrivateDeclarationIsReusedWithoutNewMetadataAcquisition(){var original=declaration("FINAL",FINAL,history());assertSame(original,MinutesGraphDiscovery.publicationContinuation(original,MEETING,FINAL,Set.of("owner")));}
 @Test void anotherMeetingConfirmationKindOrActorCannotReuseTheDeclaration(){
  var original=declaration("FINAL",FINAL,history());assertThrows(Failure.class,()->MinutesGraphDiscovery.publicationContinuation(original,FINAL,FINAL,Set.of("owner")));assertThrows(Failure.class,()->MinutesGraphDiscovery.publicationContinuation(original,MEETING,MEETING,Set.of("owner")));assertThrows(Failure.class,()->MinutesGraphDiscovery.publicationContinuation(original,MEETING,FINAL,Set.of("other")));assertThrows(Failure.class,()->MinutesGraphDiscovery.publicationContinuation(declaration("READINESS",FINAL,history()),MEETING,FINAL,Set.of("owner")));
 }
 @Test void SelectedOrFilteredHistoryCannotStandInForTheCompleteConfirmationUnion(){for(var selector:List.of(new MinutesGraphDiscovery.Selector("final_confirmation","id",FINAL,""),new MinutesGraphDiscovery.Selector("final_confirmation","meeting_id",FINAL,""),new MinutesGraphDiscovery.Selector("final_confirmation","meeting_id",MEETING," AND id='selected'"))){assertThrows(Failure.class,()->MinutesGraphDiscovery.publicationContinuation(declaration("FINAL",FINAL,List.of(selector)),MEETING,FINAL,Set.of("owner")));}}
}
