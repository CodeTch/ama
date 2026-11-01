package ama.transcript;

import ama.contract.OutboxContracts.*;
import ama.contract.TaskContracts.SubjectRef;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Actual reference validator against owned metadata fixtures; not a full initial-command producer. */
class PersonalTemplateInitialEventIT extends PersonalTemplateDiscoveryIT {
 @Test void initialReferenceAcceptsCurrentOwnedPersonalRevisionAndRejectsDeletedOrForeignLifecycle(){
  var first=templates.create(actor,UUID.randomUUID().toString(),"事件模板",List.of("决定"));
  var chosen=templates.update(actor,first.id(),UUID.randomUUID().toString(),1,"事件模板第二版",List.of("决定","行动"));
  var j=new JdbcTemplate(source);String exclusion=UUID.randomUUID().toString();
  j.update("INSERT INTO exclusion_revision(id,meeting_id,revision,created_at) VALUES (?,?,1,UTC_TIMESTAMP(6))",exclusion,meeting);
  j.update("UPDATE meeting SET template_revision_id=?,exclusion_revision_id=?,revision=1,needs_generation=1 WHERE id=?",chosen.revisionId(),exclusion,meeting);
  j.update("UPDATE task_subject SET business_version=1 WHERE subject_id=? AND subject_kind='MEETING'",meeting);
  var event=new EventDraft("MINUTES_SETTINGS_INITIALIZED",null,new SubjectRef("MEETING",meeting),1,new Reference("EXCLUSION_REVISION",exclusion));
  var validator=new MinutesEventReferences(source).validators().get("EXCLUSION_REVISION");
  assertDoesNotThrow(()->validator.validate(event));
  String foreign="template-event-"+UUID.randomUUID();
  j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",foreign,foreign);
  j.update("UPDATE personal_template_state SET owner_id=? WHERE template_id=?",foreign,chosen.id());
  assertThrows(IllegalArgumentException.class,()->validator.validate(event));
  j.update("UPDATE personal_template_state SET owner_id=?,deleted_at=UTC_TIMESTAMP(6) WHERE template_id=?",actor.enterpriseId(),chosen.id());
  assertThrows(IllegalArgumentException.class,()->validator.validate(event));
 }
}
