package ama.transcript;
import ama.contract.ConfigArchiveContracts.ArchiveReadBudget;
import ama.contract.IdentityContracts.Actor;
import ama.contract.MinutesContracts.StoredProvenanceFacts;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.sql.DataSource;
/** Compile-only RED entry point. */
public final class JdbcProcessingFactsReader implements ProcessingFactsReader {
 public JdbcProcessingFactsReader(DataSource source) {}
 public StoredProvenanceFacts readBatch(Actor actor,String meetingId,String batchId,ArchiveReadBudget budget,BooleanSupplier cancel){return new StoredProvenanceFacts(List.of(),List.of(),List.of(),List.of(),budget.maxFactRows());}
 public StoredProvenanceFacts readStored(Actor actor,String meetingId,String inputId,ArchiveReadBudget budget,BooleanSupplier cancel){return readBatch(actor,meetingId,inputId,budget,cancel);}
}
