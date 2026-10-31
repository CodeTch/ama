package ama.storage;
import ama.db.*;
import ama.recording.*;
import ama.task.*;
import java.sql.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
/** Genuine archive authority and real JDBC; observation only counts unused PCM-manifest queries. */
class RecordingArchiveStatusQueryIT extends RecordingArchiveTaskIT {
 static final AtomicBoolean observing=new AtomicBoolean();static final AtomicInteger manifests=new AtomicInteger();
 @BeforeAll static void observeRealConnections(){
  DataSource actual=source;source=new org.springframework.jdbc.datasource.AbstractDataSource(){
   public Connection getConnection()throws SQLException{return observed(actual.getConnection());}
   public Connection getConnection(String username,String password)throws SQLException{return observed(actual.getConnection(username,password));}
  };j=new JdbcTemplate(source);
 }
 static Connection observed(Connection actual){return (Connection)Proxy.newProxyInstance(RecordingArchiveStatusQueryIT.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
  if(observing.get()&&method.getName().equals("prepareStatement")&&args!=null&&args[0] instanceof String sql&&sql.toLowerCase(Locale.ROOT).contains("from recording_chunk"))manifests.incrementAndGet();
  try{return method.invoke(actual,args);}catch(InvocationTargetException failure){throw failure.getCause();}
 });}
 @Test void authorizedArchivePollingReadsCurrentRoutingAndReceiptWithoutReloadingPcmManifest()throws Exception{
  try(var f=new Fixture()){
   var session=f.recorded();var accepted=RecordingArchiveRuntime.accept(f.queue,f.actor,session.sessionId());manifests.set(0);observing.set(true);
   try{var status=RecordingArchiveRuntime.status(f.queue,f.actor,session.sessionId());assertEquals(accepted.taskId(),status.taskId());assertFalse(status.available());}finally{observing.set(false);}
   assertEquals(0,manifests.get(),"A status observation must not rescan all acknowledged PCM proofs");
   txDelete(f);assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->RecordingArchiveRuntime.status(f.queue,f.actor,session.sessionId()));
  }
 }
}
