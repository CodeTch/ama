package ama.storage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
class Bl5StorageLifetimeTest {
 @Test void contextCloseMustNotCloseBorrowedAdapter()throws Exception {
  var owned=mock(FileStoragePort.class,withSettings().extraInterfaces(AutoCloseable.class));var binding=new ManagedBackendId.S3("http://127.0.0.1:12345","us-east-1","test-data","test-control",true,1024);
  var raw=new FileBackendDefinitions(List.of(new FileBackendCatalog.Backend(binding.id(),"s3",1,"WRITABLE",owned,binding)),binding.id());
  try(var context=new org.springframework.context.support.GenericApplicationContext()){context.registerBean(FileBackendDefinitions.class,()->raw);context.refresh();}
  verify((AutoCloseable)owned).close();clearInvocations(owned);
  var borrowed=new Bl5BorrowedStorage(owned);var shared=new FileBackendDefinitions(List.of(new FileBackendCatalog.Backend(binding.id(),"s3",1,"WRITABLE",borrowed,binding)),binding.id());
  for(int i=0;i<2;i++){try(var context=new org.springframework.context.support.GenericApplicationContext()){context.registerBean(FileBackendDefinitions.class,()->shared);context.refresh();}borrowed.list(binding.id(),"ns","prefix",null,1);}
  verify((AutoCloseable)owned,never()).close();verify(owned,times(2)).list(binding.id(),"ns","prefix",null,1);
  ((AutoCloseable)owned).close();verify((AutoCloseable)owned).close();
 }
}
