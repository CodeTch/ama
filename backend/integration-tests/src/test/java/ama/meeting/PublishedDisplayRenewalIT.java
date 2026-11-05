package ama.meeting;
import ama.access.MeetingAccess.*;
import ama.contract.IdentityContracts.*;
import ama.db.TransactionRunner;
import ama.support.MysqlTestDatabase;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/** Real MySQL evaluation of the display-only ACL/proof statement. No content capability is issued. */
class PublishedDisplayRenewalIT {
 static final String MEETING="00000000-0000-4000-8000-000000000001";
 record Fixture(JdbcTemplate j,JdbcMeetingAccess access,JdbcWorkspaceNavigationReader reader,Actor owner,Actor member){}
 void fixture(java.util.function.Consumer<Fixture> test)throws Exception{
  try(var connection=MysqlTestDatabase.dataSource().getConnection()){
   var source=new SingleConnectionDataSource(connection,true);var j=new JdbcTemplate(source);
   var tables=Map.ofEntries(
    Map.entry("meeting","id VARCHAR(36),owner_id VARCHAR(255),revision BIGINT,access_revision BIGINT,cancel_epoch BIGINT,lifecycle VARCHAR(32),governance_state VARCHAR(32),ever_published BIGINT,roster_revision_id VARCHAR(36),final_confirmation_id VARCHAR(36)"),
    Map.entry("enterprise_identity","enterprise_id VARCHAR(255),status VARCHAR(32)"),Map.entry("task_subject","subject_kind VARCHAR(32),subject_id VARCHAR(36),deleted BIGINT,business_version BIGINT,cancel_epoch BIGINT"),
    Map.entry("meeting_access_grant","meeting_id VARCHAR(36),enterprise_id VARCHAR(255),action VARCHAR(32),roster_id VARCHAR(36)"),Map.entry("roster_revision","id VARCHAR(36),meeting_id VARCHAR(36),state VARCHAR(32)"),Map.entry("roster_member","roster_id VARCHAR(36),enterprise_id VARCHAR(255),member_kind VARCHAR(32)"),
    Map.entry("material_reference","meeting_id VARCHAR(36),kind VARCHAR(32)"),Map.entry("publication","id VARCHAR(36),meeting_id VARCHAR(36),confirmation_id VARCHAR(36),state VARCHAR(32)"),
    Map.entry("external_document_mapping","publication_id VARCHAR(36),external_document_id VARCHAR(255),authority_state VARCHAR(32)"),Map.entry("publication_governance_authority","publication_id VARCHAR(36),document_id VARCHAR(255),state VARCHAR(32),verified_until DATETIME(6)"));
   try{
    for(var entry:tables.entrySet())j.execute("CREATE TEMPORARY TABLE "+entry.getKey()+"("+entry.getValue()+")");
    j.update("INSERT INTO meeting VALUES (?,'owner',1,1,0,'ACTIVE','ACTIVE',1,'roster','final')",MEETING);j.update("INSERT INTO enterprise_identity VALUES ('owner','ACTIVE'),('member','ACTIVE'),('outsider','ACTIVE')");j.update("INSERT INTO task_subject VALUES ('MEETING',?,0,1,0)",MEETING);
    j.update("INSERT INTO meeting_access_grant VALUES (?,'member','READ','roster')",MEETING);j.update("INSERT INTO roster_revision VALUES ('roster',?,'CONFIRMED')",MEETING);j.update("INSERT INTO roster_member VALUES ('roster','member','ENTERPRISE')");
    j.update("INSERT INTO publication VALUES ('publication',?,'final','SUCCEEDED')",MEETING);j.update("INSERT INTO external_document_mapping VALUES ('publication','document','ACTIVE')");j.update("INSERT INTO publication_governance_authority VALUES ('publication','document','ACTIVE',DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 5 SECOND))");
    var access=mock(JdbcMeetingAccess.class);doThrow(new AuthorityUnavailableException()).when(access).prepareExternalRead(any(),anyString());var reader=new JdbcWorkspaceNavigationReader(source,new TransactionRunner(source),access,WorkspaceReadBudget.DEFAULT);
    test.accept(new Fixture(j,access,reader,new Actor("owner",Status.ACTIVE,Set.of(Role.USER)),new Actor("member",Status.ACTIVE,Set.of(Role.USER))));
   }finally{for(String table:tables.keySet())j.execute("DROP TEMPORARY TABLE IF EXISTS "+table);}
  }
 }
 @Test void validCurrentProofRenewsOwnerAndExactMemberWithoutTakingAContentGate()throws Exception{fixture(f->{f.reader.renewAccess(f.owner,MEETING);f.reader.renewAccess(f.member,MEETING);verify(f.access,never()).prepareExternalRead(any(),anyString());verify(f.access,never()).lock(any(),anyString());assertThrows(AccessDeniedException.class,()->f.reader.renewAccess(new Actor("outsider",Status.ACTIVE,Set.of(Role.USER)),MEETING));});}
 @Test void refreshedProofRechecksCommittedAclWithoutWaitingForAnotherContentGate()throws Exception{fixture(f->{
  f.j.update("UPDATE publication_governance_authority SET verified_until=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 SECOND)");
  doAnswer(call->{f.j.update("UPDATE publication_governance_authority SET verified_until=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 5 SECOND)");return null;}).when(f.access).prepareExternalRead(f.member,MEETING);
  f.reader.renewAccess(f.member,MEETING);verify(f.access).prepareExternalRead(f.member,MEETING);verify(f.access,never()).lock(any(),anyString());
 });}
 @Test void revocationDuringProofRefreshStillDeniesDisplay()throws Exception{fixture(f->{
  f.j.update("UPDATE publication_governance_authority SET verified_until=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 SECOND)");
  doAnswer(call->{f.j.update("UPDATE publication_governance_authority SET verified_until=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 5 SECOND)");f.j.update("DELETE FROM meeting_access_grant");return null;}).when(f.access).prepareExternalRead(f.member,MEETING);
  assertThrows(AccessDeniedException.class,()->f.reader.renewAccess(f.member,MEETING));verify(f.access,never()).lock(any(),anyString());
 });}
 @Test void expiredChangedOrMissingPublicationProofRequiresTheFullExternalGate()throws Exception{
  for(String change:List.of("UPDATE publication_governance_authority SET verified_until=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 SECOND)","UPDATE publication_governance_authority SET state='WITHDRAWN'","UPDATE publication_governance_authority SET document_id='another'","DELETE FROM publication_governance_authority","UPDATE external_document_mapping SET authority_state='WITHDRAWN'","UPDATE publication SET state='FAILED'","UPDATE publication SET confirmation_id='another'","INSERT INTO material_reference VALUES ('"+MEETING+"','KNOWLEDGE')"))fixture(f->{f.j.execute(change);assertThrows(AuthorityUnavailableException.class,()->f.reader.renewAccess(f.owner,MEETING));verify(f.access).prepareExternalRead(f.owner,MEETING);});
 }
 @Test void committedIdentityRosterRootAndFenceRevocationsFailBeforeAnyExternalRead()throws Exception{
  for(String change:List.of("UPDATE enterprise_identity SET status='DISABLED' WHERE enterprise_id='member'","DELETE FROM meeting_access_grant","UPDATE roster_revision SET state='DRAFT'","DELETE FROM roster_member","UPDATE meeting SET roster_revision_id='another'","UPDATE meeting SET governance_state='WITHDRAWN'","UPDATE meeting SET lifecycle='DELETED'","UPDATE task_subject SET cancel_epoch=1","UPDATE task_subject SET deleted=1","UPDATE task_subject SET business_version=2"))fixture(f->{f.j.execute(change);assertThrows(AccessDeniedException.class,()->f.reader.renewAccess(f.member,MEETING));verify(f.access,never()).prepareExternalRead(any(),anyString());});
 }
}
