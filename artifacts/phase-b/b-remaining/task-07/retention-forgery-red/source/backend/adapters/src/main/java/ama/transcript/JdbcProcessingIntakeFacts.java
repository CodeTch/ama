package ama.transcript;
import ama.contract.IdentityContracts.Actor;
import java.util.List;
import java.util.function.BooleanSupplier;
final class JdbcProcessingIntakeFacts implements ProcessingIntakeFacts {
 private final JdbcTranscriptAuthority authority;
 JdbcProcessingIntakeFacts(JdbcTranscriptAuthority authority){this.authority=authority;}
 public Draft readDraft(Actor actor,String meeting,List<String> files,BooleanSupplier cancelled){return authority.readDraft(actor,meeting,files,cancelled);}
}
