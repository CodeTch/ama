package ama.task;
import javax.sql.DataSource;import java.util.function.Function;import static org.mockito.Mockito.*;
/** Test-only substitute for external engine context validation; policy preparation and holder remain real. */
public final class Task11GenerationFactContext {
 public static <T>T with(DataSource source,TaskDomainPolicy.PreparedDomain held,JdbcTaskPreparations.ExternalFacts facts,Function<ExternalOperationPort.ExecutionContext,T> work){var context=mock(ExternalOperationPort.ExecutionContext.class);try(var operations=mockStatic(JdbcExternalOperations.class)){operations.when(()->JdbcExternalOperations.contextPreparation(source,context)).thenReturn(held);operations.when(()->JdbcExternalOperations.requireContext(source,held,context)).thenReturn(facts);return work.apply(context);}}
}
