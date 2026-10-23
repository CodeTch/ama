from pathlib import Path
p=Path('backend/adapters/src/main/java/ama/storage/JdbcFileRegistry.java');s=p.read_text()
start=s.index('                            var proofs = new ArrayList<Proof>();',s.index('public Download download('))
end=s.index('                            return file;',start)+len('                            return file;')
legacy=s[start:end]
old_start=s.index('                            if(references.stream().anyMatch(')
s=s[:old_start]+'                            return legacyRead(gate,references,id);'+s[end:]
s=s.replace('    @Override\n    public Download download(Actor actor, String id, String range) {','    private Download legacyDownload(Actor actor, String id, String range) {',1)
pos=s.index('    private void checkOriginal(')
s=s[:pos]+'    private FileRecord legacyRead(Gate gate,List<Reference> references,String id){\n'+legacy+'\n    }\n\n'+s[pos:]
a=s.index('    @Override public Download read(');b=s.index('    private Download open(',a)
s=s[:a]+Path('artifacts/phase-b/b-remaining/task-03/registry-read-fragment.txt').read_text()+s[b:]
s=s.replace('private FileRecord genericComplete(Gate gate,List<Reference> references,String requested){','private FileRecord genericComplete(Gate gate,List<Reference> references,String requested,PreparedRead prepared){')
s=s.replace('var proof=(Proof)prelock(gate,reference,requested);proofs.put(new ProofKey(reference,requested),proof);','''Proof proof;
                if(policy instanceof CompleteReadPolicy complete){
                    if(complete!=prepared.policy)throw new Unavailable();
                    Object domain=complete.prelockCurrent(gate,gate.meeting,reference,requested,prepared.data);
                    if(domain==null)throw new NotFound();proof=new Proof(gate,reference,requested,complete,domain);
                }else proof=(Proof)prelock(gate,reference,requested);
                proofs.put(new ProofKey(reference,requested),proof);''')
# Same current reads after locks must not consume a repeatable-read snapshot.
s=s.replace('expected_sha256,state,completed_file_id FROM upload_session WHERE id=?",', 'expected_sha256,state,completed_file_id FROM upload_session WHERE id=? FOR UPDATE",')
s=s.replace('reference_epoch,storage_type FROM file_object WHERE id=?",','reference_epoch,storage_type FROM file_object WHERE id=? FOR UPDATE",')
p.write_text(s)
