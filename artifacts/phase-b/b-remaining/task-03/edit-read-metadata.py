from pathlib import Path
p=Path('backend/adapters/src/main/java/ama/transcript/BoundedTranscriptMetadata.java')
s=p.read_text().replace('private final JdbcTemplate j;private final ArchiveReadBudget budget;private long rows,edges,bytes,points;', 'private final JdbcTemplate j;private final ArchiveReadBudget budget;private final boolean locked;private long rows,edges,bytes,points;')
s=s.replace('BoundedTranscriptMetadata(JdbcTemplate j,ArchiveReadBudget budget){this.j=j;this.budget=budget;}', 'BoundedTranscriptMetadata(JdbcTemplate j,ArchiveReadBudget budget){this(j,budget,false);}\n BoundedTranscriptMetadata(JdbcTemplate j,ArchiveReadBudget budget,boolean locked){this.j=j;this.budget=budget;this.locked=locked;}')
s=s.replace('ORDER BY "+order+" LIMIT ?) a)', 'ORDER BY "+order+" LIMIT ?"+(locked?" FOR UPDATE":"")+") a)')
p.write_text(s)
