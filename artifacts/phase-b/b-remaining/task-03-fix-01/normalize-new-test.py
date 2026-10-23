from pathlib import Path
p=Path('backend/integration-tests/src/test/java/ama/storage/TranscriptAuthorityReviewIT.java');p.write_text(p.read_text(encoding='utf-8-sig'))
