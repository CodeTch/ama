package ama.storage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class Bl5SchemaPrepareTest {
 @Test void rejectsNonemptySchemaBeforeMigration(){var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);when(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()",Integer.class)).thenReturn(26);assertThrows(IllegalArgumentException.class,()->Bl5SchemaPrepare.requireEmpty(jdbc));verifyNoMoreInteractionsAfterCheck(jdbc);assertThrows(IllegalArgumentException.class,()->Bl5SchemaPrepare.schema("local;DROP"));}
 void verifyNoMoreInteractionsAfterCheck(org.springframework.jdbc.core.JdbcTemplate jdbc){verify(jdbc).queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()",Integer.class);verifyNoMoreInteractions(jdbc);}
}
