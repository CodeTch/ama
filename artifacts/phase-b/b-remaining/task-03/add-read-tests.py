from pathlib import Path
p=Path('backend/integration-tests/src/test/java/ama/storage/TranscriptAuthorityFixture.java');s=p.read_text();pos=s.index(' public void close()')
s=s[:pos]+''' Actor member(){
  String person="authority-member-"+id(),roster=id();
  j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",person,"member😀");
  j.update("INSERT INTO roster_revision(id,meeting_id,revision,state,confirmed_at,confirmed_by) VALUES (?,?,1,'CONFIRMED',UTC_TIMESTAMP(6),?)",roster,meeting,owner);
  j.update("INSERT INTO roster_member(roster_id,member_id,enterprise_id,display_name,member_kind) VALUES (?,?,?,?,'ENTERPRISE')",roster,id(),person,"member😀");
  j.update("INSERT INTO meeting_access_grant VALUES (?,?,'READ',?,UTC_TIMESTAMP(6))",meeting,person,roster);
  j.update("UPDATE meeting SET roster_revision_id=? WHERE id=?",roster,meeting);
  return new Actor(person,Status.ACTIVE,Set.of(Role.USER));
 }
 Actor outsider(){String person="authority-outsider-"+id();j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",person,person);return new Actor(person,Status.ACTIVE,Set.of(Role.ADMIN));}
''' + s[pos:];p.write_text(s)
p=Path('backend/integration-tests/src/test/java/ama/storage/TranscriptAuthorityIT.java');s=p.read_text();i=s.rfind('}');p.write_text(s[:i]+Path('artifacts/phase-b/b-remaining/task-03/read-adversarial-tests-fragment.txt').read_text()+s[i:])
