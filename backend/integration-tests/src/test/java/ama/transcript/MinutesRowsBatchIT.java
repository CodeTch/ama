package ama.transcript;

import ama.db.TransactionRunner;
import ama.support.MysqlTestDatabase;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Real MySQL, disposable fixture only: bounded exact-key content transfer. */
class MinutesRowsBatchIT {
 private final SavedTranscriptRows.Shape shape=new SavedTranscriptRows.Shape("minutes_batch_probe",List.of("id"),List.of("id","body"),Set.of(),Set.of());
 private TranscriptAcquisitionBudget.Ledger budget(){
  var read=new ama.contract.ConfigArchiveContracts.ArchiveReadBudget(65536,1000000,100,100,16,10000,16,128,65536,65536,1000000,65536,100,1000,10000);
  return new TranscriptAcquisitionBudget(1000000,1000000,1000000,10000000,10000000,1000000,16,256,256).ledger(read,new ama.contract.MinutesContracts.CanonicalBudget(1000000,32,100000,65536,10000),()->false);
 }
 private void fixture(java.util.function.Consumer<JdbcTemplate> test){var source=MysqlTestDatabase.dataSource();new TransactionRunner(source).required(()->{var jdbc=new JdbcTemplate(source);jdbc.execute("CREATE TEMPORARY TABLE minutes_batch_probe(id VARCHAR(36) PRIMARY KEY,body LONGTEXT) CHARACTER SET utf8mb4");try{for(int i=0;i<257;i++)jdbc.update("INSERT INTO minutes_batch_probe VALUES(?,?)",String.format("%036d",i),i==256?null:"重复证据😀 "+i);test.accept(jdbc);}finally{jdbc.execute("DROP TEMPORARY TABLE minutes_batch_probe");}return null;});}
 @Test void readsAllBoundedRowsWithUnicodeNullAndStableExactKeysAcrossChunks(){fixture(j->{var rows=new SavedTranscriptRows(j,budget(),65536).read(shape,"1=1","id",257,false);assertEquals(257,rows.size());assertEquals("重复证据😀 128",rows.get(128).get("body"));assertNull(rows.get(256).get("body"));assertEquals(257,rows.stream().map(x->x.get("id")).distinct().count());});}
 @Test void changedOrMissingAdmittedRowsAreRejectedBeforeReturningAnyResult(){
  for(boolean deleted:List.of(false,true))fixture(j->{var transfer=new SavedTranscriptRows(j,budget(),65536).deferRead(shape,"1=1","id",257,false);String key=String.format("%036d",129);if(deleted)j.update("DELETE FROM minutes_batch_probe WHERE id=?",key);else j.update("UPDATE minutes_batch_probe SET body='changed' WHERE id=?",key);assertThrows(ama.contract.MinutesContracts.Failure.class,transfer::get);});
 }
 @Test void overLimitStopsAtMetadataAdmission(){fixture(j->assertThrows(ama.contract.MinutesContracts.Failure.class,()->new SavedTranscriptRows(j,budget(),65536).deferRead(shape,"1=1","id",128,false)));}
}
