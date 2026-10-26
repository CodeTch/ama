package ama.transcript;
import java.util.*;
import ama.contract.TaskContracts.TaskLease;
import ama.storage.*;
import static org.junit.jupiter.api.Assertions.*;
/** Actual service.prepare with independently constructed production authority/read budgets.
 * No final-field mutation, fake lease, alternative read implementation, or registry installation. */
public final class Task11Fix02WorkerBudgetProbe {
 public static JdbcPublicationService constrained(JdbcPublicationService base,FileBackendCatalog backends,String dimension,long limit)throws Exception {
  var a=base.authority;var archive=a.readBudget();var canonical=a.canonicalBudget();
  if(dimension.equals("maxStringBytes"))canonical=replace(canonical,dimension,limit);else archive=replace(archive,dimension,limit);
  var components=JdbcTranscriptAuthority.create(a.dataSource(),a.transactionRunner(),a.savedAccess(),new TranscriptFilePurposePolicy(),backends,Map.of(),archive,canonical);
  return new JdbcPublicationService(components.authority(),base.archives,base.pin,base.limits,null,base.queue,base.outbox,base.client);
 }
 @SuppressWarnings("unchecked") private static <T> T replace(T record,String field,long value)throws Exception {var components=record.getClass().getRecordComponents();Class<?>[] types=new Class<?>[components.length];Object[] args=new Object[components.length];boolean found=false;for(int i=0;i<components.length;i++){var c=components[i];types[i]=c.getType();args[i]=c.getAccessor().invoke(record);if(c.getName().equals(field)){found=true;if(c.getType()==int.class)args[i]=Math.toIntExact(value);else args[i]=value;}}assertTrue(found);return (T)record.getClass().getDeclaredConstructor(types).newInstance(args);}
}
