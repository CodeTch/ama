    @Override public LockedReference prelock(LockedMeeting held,Reference reference,String file,ReferenceContext context){
        var gate=gate(held);uuid(file);if(gate.filesStarted)throw new IllegalStateException("Domain prelock cannot follow sources");
        if(!(policies.get(reference.kind()) instanceof CompleteReadPolicy policy))throw new Unavailable();
        Object domain=policy.prelock(gate,gate.meeting,reference,file,context);
        if(domain==null)throw new NotFound();return new Proof(gate,reference,file,policy,domain);
    }
    @Override public Download read(Actor actor,String meeting,ReadRequest request,String range){
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Read owns its authorization transaction");
        if(sourceBudget==null||request==null||!(policies.get(request.referenceKind()) instanceof CompleteReadPolicy policy))throw new Unavailable();
        FileRecord pinned=tx.required(()->{
            var gate=(Gate)lock(actor,meeting,MeetingAccess.Action.READ);
            var plan=policy.prelockRead(gate,meeting,request);
            if(plan==null||plan.context()==null||plan.completeMembers().isEmpty()||plan.completeMembers().size()>sourceBudget.maxBatchFiles())throw new Unavailable();
            var proofs=new ArrayList<Proof>();var unique=new HashSet<CompleteReadPolicy.ReadMember>();
            for(var member:plan.completeMembers()){
                if(!member.reference().kind().equals(request.referenceKind())||!unique.add(member))throw new Unavailable();
                var proof=(Proof)prelock(gate,member.reference(),member.fileId(),plan.context());
                if(proof.policy!=policy||proof.domain!=plan.context())throw new Unavailable();proofs.add(proof);
            }
            return completeRead(gate,proofs,plan.requestedFileId());
        });
        return open(pinned,range);
    }
    private Download open(FileRecord pinned,String range){
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Storage open must follow commit");
        long[] interval=range(range,pinned.size());
        return new Download(pinned,backends.require(pinned.key().backendId(),false).storage().read(pinned.key(),interval[0],interval[1]),interval[0],interval[1]);
    }
    private record ProofKey(Reference reference,String file) {}
    private FileRecord genericComplete(Gate gate,List<Reference> references,String requested){
        var proofs=new LinkedHashMap<ProofKey,Proof>();CompleteReadPolicy provider=null;
        for(var reference:references){
            var policy=policies.get(reference.kind());if(policy==null||proofs.containsKey(new ProofKey(reference,requested)))continue;
            try {
                var proof=(Proof)prelock(gate,reference,requested);proofs.put(new ProofKey(reference,requested),proof);
                if(policy instanceof CompleteReadPolicy complete){
                    if(provider!=null&&provider!=complete)throw new Unavailable();provider=complete;
                    if(!(proof.domain instanceof ReferenceContext context))throw new Unavailable();
                    var members=complete.readMembers(proof.domain,reference,requested);
                    if(members==null||members.isEmpty()||members.size()>sourceBudget.maxBatchFiles())throw new Unavailable();
                    for(var member:members){
                        if(policies.get(member.reference().kind())!=complete)throw new Unavailable();
                        var key=new ProofKey(member.reference(),member.fileId());if(proofs.containsKey(key))continue;
                        var added=(Proof)prelock(gate,member.reference(),member.fileId(),context);
                        if(added.domain!=proof.domain)throw new Unavailable();proofs.put(key,added);
                    }
                }
            } catch(NotFound denied){/* independently valid current references can still qualify */}
            if(proofs.size()>sourceBudget.maxFactRows())throw new Unavailable();
        }
        return completeRead(gate,new ArrayList<>(proofs.values()),requested);
    }
    private FileRecord completeRead(Gate gate,List<Proof> proofs,String requested){
        if(proofs.isEmpty())throw new NotFound();uuid(requested);
        CompleteReadPolicy provider=null;var domains=new IdentityHashMap<Object,List<Proof>>();
        var fileIds=new TreeSet<String>();
        for(var p:proofs){
            proof(p);fileIds.add(p.file);
            if(p.policy instanceof CompleteReadPolicy complete){
                if(provider!=null&&provider!=complete)throw new Unavailable();provider=complete;
                domains.computeIfAbsent(p.domain,k->new ArrayList<>()).add(p);
            }
        }
        var receipt=prelockSources(gate,List.copyOf(fileIds),false).orElseThrow(NotFound::new);
        var actual=sources(gate,receipt,List.copyOf(fileIds));var files=new HashMap<String,CompletedSource>();for(var a:actual)files.put(a.file().fileId(),a);
        boolean readable=false;var valid=new ArrayList<Object>();
        for(var p:proofs)if(!(p.policy instanceof CompleteReadPolicy)){
            try{validateReadReference(p,files.get(p.file));if(p.file.equals(requested))readable=true;}catch(NotFound denied){/* legacy alternative */}
        }
        for(var e:domains.entrySet()){
            try{for(var p:e.getValue())validateReadReference(p,files.get(p.file));valid.add(e.getKey());}catch(NotFound denied){/* this complete scope does not authorize */}
        }
        if(!valid.isEmpty()){
            provider.prelockReadTasks(List.copyOf(valid));
            // The provider has acquired its entire task union. No task discovery may follow an attempt.
            for(var scope:valid){
                var members=domains.get(scope);var scopedIds=new HashSet<String>();for(var p:members)scopedIds.add(p.file);
                try{
                    provider.finishRead(scope,actual.stream().filter(a->scopedIds.contains(a.file().fileId())).toList());
                    if(scopedIds.contains(requested))readable=true;
                }catch(NotFound denied){/* independently finalized alternative may still qualify */}
            }
        }
        if(!readable||!files.containsKey(requested))throw new NotFound();return displayName(files.get(requested).file());
    }
    private void validateReadReference(Proof p,CompletedSource actual){
        if(actual==null||!p.reference.purpose().equals(actual.originalPurpose())
                ||j.queryForObject("SELECT COUNT(*) FROM file_reference WHERE file_id=? AND owner_kind=? AND owner_id=? AND purpose=?",Integer.class,p.file,p.reference.kind(),p.reference.ownerId(),p.reference.purpose())!=1)throw new NotFound();
        p.policy.validate(p.domain,p.gate.meeting,p.reference,actual.file(),actual.originalPurpose());
    }
    private FileRecord displayName(FileRecord file){
        // Only an authorized browser/worker result needs a display name; source authentication never does.
        var state=new SourceAcquisition();measure("file_object",file.fileId(),List.of("display_name"),false,state,0);
        String name=j.query("SELECT display_name FROM file_object WHERE id=?",rs->{
            if(!rs.next())throw new NotFound();
            try(var reader=rs.getCharacterStream(1)){
                var text=new StringBuilder();char[] chunk=new char[Math.min(1024,sourceBudget.maxStringUtf16())];
                for(int n;(n=reader.read(chunk))!=-1;){state.check();if(n>sourceBudget.maxStringUtf16()-text.length())throw new Unavailable();text.append(chunk,0,n);}return text.toString();
            }catch(java.io.IOException failure){throw new Unavailable();}
        },file.fileId());
        return new FileRecord(file.fileId(),file.meetingId(),file.key(),file.size(),file.sha256(),file.state(),file.locationRevision(),file.referenceEpoch(),name);
    }
