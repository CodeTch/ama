package ama.transcript;
import ama.storage.*;
import java.util.List;
/** Constructed only by the shared production authority assembly. */
final class InputBatchFileReferencePolicy implements CompleteReadPolicy {
 private final JdbcTranscriptAuthority authority;
 InputBatchFileReferencePolicy(JdbcTranscriptAuthority authority){this.authority=authority;}
 public Object prelock(FileRegistry.LockedMeeting gate,String meeting,FileRegistry.Reference ref,String file){throw new ama.contract.FileContracts.Unavailable();}
 public Object prelock(FileRegistry.LockedMeeting gate,String meeting,FileRegistry.Reference ref,String file,FileRegistry.ReferenceContext context){return authority.prelock(gate,meeting,ref,file,context);}
 public void validate(Object domain,String meeting,FileRegistry.Reference ref,FileRegistry.FileRecord file,String purpose){authority.validate(domain,meeting,ref,file,purpose);}
 public ReadPreparation prepareRead(ama.contract.IdentityContracts.Actor actor,String meeting,FileRegistry.ReadRequest request){return authority.prepareRead(actor,meeting,request);}
 public void authorizeRead(FileRegistry.LockedMeeting gate,String meeting,FileRegistry.ReadRequest request){authority.authorizeRead(gate,meeting,request);}
 public Object prelockCurrent(FileRegistry.LockedMeeting gate,String meeting,FileRegistry.Reference ref,String file,ReadPreparation prepared){return authority.prelockCurrent(gate,meeting,ref,file,prepared);}
 public ReadPlan prelockRead(FileRegistry.LockedMeeting gate,String meeting,FileRegistry.ReadRequest request,ReadPreparation prepared){return authority.prelockRead(gate,meeting,request,prepared);}
 public List<ReadMember> readMembers(Object domain,FileRegistry.Reference ref,String file){return authority.readMembers(domain,ref,file);}
 public void prelockReadTasks(List<Object> scopes){authority.prelockReadTasks(scopes);}
 public void finishRead(Object domain,List<FileRegistry.CompletedSource> sources){authority.finishRead(domain,sources);}
}
