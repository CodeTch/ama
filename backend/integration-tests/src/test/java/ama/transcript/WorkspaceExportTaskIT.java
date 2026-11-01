package ama.transcript;

import ama.contract.TaskContracts.*;
import ama.meeting.WorkspaceReadBudget;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Bounded navigation relation fixtures, not an export producer or permission proof. */
class WorkspaceExportTaskIT {
 static final String MEETING=id(1),TASK=id(2),INPUT=id(3),BODY=id(4),OTHER=id(5),HASH="a".repeat(64);
 static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
 WorkspaceTaskScan.Metadata task(){return new WorkspaceTaskScan.Metadata(TASK,new TaskSpec("MINUTES_EXPORT_V1","MINUTES_EXPORT",new SubjectRef("MEETING",MEETING),HASH,new TypedRef("MINUTES_EXPORT_INPUT",INPUT),HASH,15,0,0,3),"SUCCEEDED",1,new WorkspaceTaskScan.Position(LocalDateTime.of(2026,11,1,0,0),TASK));}
 @Test void registeredExportTaskShapeHasItsExactPoolAndInputFamily(){
  assertDoesNotThrow(()->WorkspaceTaskScan.metadata(TASK,"MINUTES_EXPORT_V1","MINUTES_EXPORT","MEETING",MEETING,HASH,"MINUTES_EXPORT_INPUT",INPUT,HASH,15,0,0,3,"SUCCEEDED",1,LocalDateTime.of(2026,11,1,0,0)));
  assertThrows(WorkspaceReadBudget.Unavailable.class,()->WorkspaceTaskScan.metadata(TASK,"MINUTES_EXPORT_V1","MINUTES_GENERATE","MEETING",MEETING,HASH,"MINUTES_EXPORT_INPUT",INPUT,HASH,15,0,0,3,"SUCCEEDED",1,LocalDateTime.of(2026,11,1,0,0)));
 }
 Map<String,Object> input(){var m=new HashMap<String,Object>();m.putAll(Map.of("id",INPUT,"meeting_id",MEETING,"source_revision_id",BODY,"base_revision",15L,"cancel_epoch",0L,"input_fingerprint",HASH,"producer_key",HASH,"format","pdf"));m.put("final_confirmation_id",null);return m;}
 WorkspaceTaskRelations fixture(Map<String,Object> input,String sourceMeeting)throws Exception{
  var ds=mock(DataSource.class);var connection=mock(Connection.class);when(ds.getConnection()).thenReturn(connection);
  when(connection.prepareStatement(anyString())).thenAnswer(call->{String sql=call.getArgument(0);Map<String,Object> row=sql.contains(" FROM minutes_export_input ")?input:sql.contains(" FROM minutes_revision ")?Map.of("id",BODY,"meeting_id",sourceMeeting,"revision",3L):Map.of();assertFalse(row.isEmpty(),sql);var statement=mock(PreparedStatement.class);var result=mock(ResultSet.class);when(statement.executeQuery()).thenReturn(result);when(result.next()).thenReturn(true,false);when(result.getObject(anyString())).thenAnswer(get->row.get(get.getArgument(0)));return statement;});
  return new WorkspaceTaskRelations(new JdbcTemplate(ds),new WorkspaceReadBudget());
 }
 @Test void exactPersistedExportInputAndSavedSourceKeepWorkspaceNavigable()throws Exception{
  var relations=fixture(input(),MEETING);assertDoesNotThrow(()->relations.capture(List.of(task())));relations.lockAndCompare();assertEquals("/api/v1/exports/"+TASK,relations.detailPath(task()));
 }
 @Test void mismatchedExportInputOrForeignSourceCannotBecomeANavigationRelation()throws Exception{
  for(String column:List.of("meeting_id","input_fingerprint","producer_key","base_revision","cancel_epoch")){var bad=input();bad.put(column,column.endsWith("revision")||column.endsWith("epoch")?16L:OTHER);var relations=fixture(bad,MEETING);assertThrows(WorkspaceReadBudget.Unavailable.class,()->relations.capture(List.of(task())));}
  var relations=fixture(input(),OTHER);assertThrows(WorkspaceReadBudget.Unavailable.class,()->relations.capture(List.of(task())));
 }
}
