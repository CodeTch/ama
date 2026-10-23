from pathlib import Path
paths=[Path('artifacts/phase-b/b-remaining/task-04')/name for name in ['write-report.py','write-qualified-report.py','report.md']]+[Path('.superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-04-report.md')]
for p in paths:
 s=p.read_text().replace('The only later source change is the integration-test fixture','The only later Java source change is the integration-test fixture').replace('Rows and accepted READY evidence remain retained.','Fixture teardown retained database rows, and accepted READY receipts remain in the evidence; current database access is qualified below.')
 p.write_text(s,newline='\n')
