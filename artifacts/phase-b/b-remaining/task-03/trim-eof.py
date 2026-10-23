from pathlib import Path
for name in ['backend/adapters/src/main/java/ama/storage/JdbcFileRegistry.java','backend/core/src/main/java/ama/storage/FileRegistry.java']:
 p=Path(name);p.write_text(p.read_text().rstrip()+'\n')
