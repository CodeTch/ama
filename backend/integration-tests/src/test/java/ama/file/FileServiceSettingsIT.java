package ama.file;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FileServiceSettingsIT {
 @Test void explicitFileConcurrencyKeepsTheDefaultAndRejectsUnboundedSettings(){
  var values=new Properties();assertEquals(16,FileServiceMain.workerThreads(values));values.setProperty("workerThreads","32");assertEquals(32,FileServiceMain.workerThreads(values));
  for(String value:new String[]{"0","33","-1","32.0"," 32","invalid"}){values.setProperty("workerThreads",value);assertThrows(IllegalArgumentException.class,()->FileServiceMain.workerThreads(values),value);}
 }
}
