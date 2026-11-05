package ama.transcript;
import ama.db.TransactionRunner;
import ama.support.MysqlTestDatabase;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
class MinutesGenerationSelectionIT {
 @Test void prospectiveGenerationRetainsBodyPointerWithoutTreatingBodyHistoryAsAnInput(){var source=MysqlTestDatabase.dataSource();new TransactionRunner(source).required(()->{var j=new JdbcTemplate(source);String meeting="00000000-0000-4000-8000-000000000001",body="00000000-0000-4000-8000-000000000002";j.execute("CREATE TEMPORARY TABLE meeting AS SELECT * FROM meeting WHERE 1=0");try{j.update("INSERT INTO meeting(id,owner_id,revision,cancel_epoch,access_revision,stage,lifecycle,governance_state,ever_published,minutes_revision_id,needs_generation,materials_pending,created_at,updated_at) VALUES(?,'owner',1,0,1,'MINUTES','ACTIVE','ACTIVE',0,?,1,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",meeting,body);var plan=MinutesGraphDiscovery.discover(source,meeting,"CURRENT_GENERATION",null,1000);assertEquals(body,plan.root().get("minutes_revision_id"));assertFalse(plan.selectors().stream().anyMatch(s->s.table().equals("minutes_revision")));assertThrows(ama.contract.MinutesContracts.Failure.class,()->MinutesGraphDiscovery.discover(source,meeting,"SAVED_EFFECTIVE",null,1000));}finally{j.execute("DROP TEMPORARY TABLE meeting");}return null;});}
}
