from pathlib import Path
p=Path('backend/adapters/src/main/java/ama/meeting/JdbcMeetingAccess.java')
s=p.read_text();a=s.index('    final class Identities implements');b=s.index('    public JdbcMeetingAccess(',a)
s=s[:a]+'''    record IdentityStatus(String id,String status) {}
    abstract class IdentityCapture implements LockedIdentities {
        final Object transaction; final String actor; final Map<String,IdentityStatus> statuses;
        private IdentityCapture(String actor,Map<String,IdentityStatus> statuses){this.transaction=transaction();this.actor=actor;this.statuses=Map.copyOf(statuses);}
        final Set<String> ids(){return statuses.keySet();}
        final JdbcMeetingAccess issuer(){return JdbcMeetingAccess.this;}
    }
    private final class AuthorizationIdentities extends IdentityCapture {
        private AuthorizationIdentities(String actor,Map<String,IdentityStatus> statuses){super(actor,statuses);}
    }
    final class Identities extends IdentityCapture {
        final Map<String,Person> people;
        private Identities(String actor,Map<String,Person> people,Map<String,IdentityStatus> statuses){super(actor,statuses);this.people=Map.copyOf(people);}
    }
    final class Held implements Locked {
        final IdentityCapture identities; final SubjectFencePort.LockedSubject subject; final Root root;
        private Held(IdentityCapture identities,SubjectFencePort.LockedSubject subject,Root root){this.identities=identities;this.subject=subject;this.root=root;}
        JdbcMeetingAccess issuer(){return JdbcMeetingAccess.this;}
    }
'''+s[b:]
s=s.replace('return lockMeeting(lockIdentities(actor,List.of()),id);','return lockPrepared(captureIdentities(actor,List.of(),false,false,false).orElseThrow(AccessDeniedException::new),id,false).orElseThrow(AccessDeniedException::new);')
s=s.replace('captureIdentities(actor,affected,false,false)','captureIdentities(actor,affected,false,false,true)').replace('captureIdentities(actor,affected,skip,true)','captureIdentities(actor,affected,skip,true,false)')
s=s.replace('boolean skip,boolean allActive){','boolean skip,boolean allActive,boolean names){')
s=s.replace('var people=new HashMap<String,Person>();','var people=new HashMap<String,Person>();var statuses=new HashMap<String,IdentityStatus>();')
a=s.index('            var found=j.query(');b=s.index('    Held lockMeeting',a)
s=s[:a]+'''            if(names){
                var found=j.query("SELECT enterprise_id,display_name,status FROM enterprise_identity WHERE enterprise_id=? FOR UPDATE"+(skip?" SKIP LOCKED":""),
                        (r,n)->new Person(r.getString(1),r.getString(2),r.getString(3)),id);
                if(found.isEmpty()){if(skip)return Optional.empty();continue;}
                var person=found.getFirst();people.put(id,person);statuses.put(id,new IdentityStatus(person.id(),person.status()));
            } else {
                var found=j.query("SELECT enterprise_id,status FROM enterprise_identity WHERE enterprise_id=? FOR UPDATE"+(skip?" SKIP LOCKED":""),
                        (r,n)->new IdentityStatus(r.getString(1),r.getString(2)),id);
                if(found.isEmpty()){if(skip)return Optional.empty();continue;}statuses.put(id,found.getFirst());
            }
        }
        var current=statuses.get(actor.enterpriseId());
        if(current==null||!current.status().equals("ACTIVE")
                ||allActive&&(statuses.size()!=ids.size()||statuses.values().stream().anyMatch(p->!p.status().equals("ACTIVE")))){
            if(skip)return Optional.empty();throw new AccessDeniedException();
        }
        return Optional.of(names?new Identities(actor.enterpriseId(),people,statuses):new AuthorizationIdentities(actor.enterpriseId(),statuses));
    }
'''+s[b:]
s=s.replace('prepared instanceof JdbcMeetingAccess.Identities identities','prepared instanceof JdbcMeetingAccess.IdentityCapture identities').replace('held.identities.people.keySet()','held.identities.ids()').replace('private void check(Identities identities)','private void check(IdentityCapture identities)')
p.write_text(s)
p=Path('backend/adapters/src/main/java/ama/meeting/JdbcMeetingRepository.java');s=p.read_text().replace('held.identities.people.keySet()','held.identities.ids()');p.write_text(s)
