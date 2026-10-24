package ama.transcript;
import ama.contract.IdentityContracts.Actor;
import ama.contract.TaskContracts.TaskLease;
import ama.storage.FileRegistry;
final class JdbcProcessingSourceReader implements ProcessingSourceReader {
 private final JdbcTranscriptAuthority authority;
 JdbcProcessingSourceReader(JdbcTranscriptAuthority authority){this.authority=authority;}
 public FileRegistry.Download open(TaskLease lease,String input,String file){return authority.worker(lease,input,file);}
 public FileRegistry.Download history(Actor actor,String meeting,String revision,String file,String range){return authority.history(actor,meeting,revision,file,range);}
}
