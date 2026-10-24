package ama.minutes.contract;
import ama.contract.MinutesContracts.*;
import java.util.*;
import static ama.contract.MinutesContracts.*;
public final class CreationSupport {
 public CreationClassification classify(ValidatedOutput output,Catalog catalog){
  var elements=new ArrayList<ElementSupport>();var links=new ArrayList<EvidenceSupport>();var kinds=new HashMap<String,Category>();
  for(var c:Category.values())for(var e:output.output().elements().get(c))kinds.put(e.key(),c);
  var hasEvidence=new HashSet<String>();var hasMeeting=new HashSet<String>();
  for(var e:output.output().evidence())for(var id:e.sourceIds().values()){
   var role=catalog.source(id).fact().role();Category kind=null;
   if(e.target() instanceof ElementTarget t){hasEvidence.add(t.elementKey());if(role==Role.MEETING)hasMeeting.add(t.elementKey());kind=kinds.get(t.elementKey());}
   boolean meetingClaim=kind==Category.CONCLUSION||kind==Category.ACTION;
   links.add(new EvidenceSupport(e.target(),id,meetingClaim&&role!=Role.MEETING?Support.UNSUPPORTED:Support.PARTIAL));
  }
  for(var c:Category.values())for(var e:output.output().elements().get(c)){
   boolean meetingClaim=c==Category.CONCLUSION||c==Category.ACTION;
   var state=hasEvidence.contains(e.key())&&(!meetingClaim||hasMeeting.contains(e.key()))?Support.PARTIAL:Support.UNSUPPORTED;
   elements.add(new ElementSupport(e.key(),state,c==Category.ACTION?new AcceptedAction(null,null,null):null));
  }
  return new CreationClassification(elements,links);
 }
}
