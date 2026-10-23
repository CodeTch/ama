from pathlib import Path
p=Path('backend/integration-tests/src/test/java/ama/storage/TranscriptAuthorityReviewIT.java');s=p.read_text();fragment=Path('artifacts/phase-b/b-remaining/task-03-fix-01/multiscope-tests.fragment').read_text(encoding='utf-8-sig');marker='    /** Test-only JCA observer';assert s.count(marker)==1;p.write_text(s.replace(marker,fragment+'\n'+marker))
