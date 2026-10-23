from pathlib import Path
p=Path('backend/adapters/src/main/java/ama/transcript/JdbcTranscriptAuthority.java')
s=p.read_text();old='   revision=(String)root.get("transcript_revision_id");var row=reader.one("transcript_revision",BoundedTranscriptMetadata.REVISION,revision);'
new='''   revision=(String)root.get("transcript_revision_id");Map<String,Object> row;
   try{row=reader.one("transcript_revision",BoundedTranscriptMetadata.REVISION,revision);}
   catch(ama.contract.FileContracts.NotFound dangling){throw fail(PROVENANCE_UNAVAILABLE);}'''
assert s.count(old)==1;s=s.replace(old,new);p.write_text(s)
