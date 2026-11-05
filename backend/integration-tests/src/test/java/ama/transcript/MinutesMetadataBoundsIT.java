package ama.transcript;
import ama.support.MysqlTestDatabase;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
class MinutesMetadataBoundsIT {
 @Test void taskAndPublicationMetadataReservationsCoverActualMigratedStringColumns(){var j=new JdbcTemplate(MysqlTestDatabase.dataSource());for(String table:List.of("durable_task","task_attempt","external_operation","external_execution","publication_submission","external_document_mapping","publication_attempt")){var rows=j.queryForList("SELECT COLUMN_NAME,CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND DATA_TYPE IN ('char','varchar')",table);assertFalse(rows.isEmpty());for(var row:rows){String column=(String)row.get("COLUMN_NAME");assertTrue(MinutesMetadataBounds.scalars(column)>=((Number)row.get("CHARACTER_MAXIMUM_LENGTH")).longValue(),table+"."+column);}}}
}
