from pathlib import Path
root=Path.cwd()
for p in (root/'backend/integration-tests/src/test/java/ama/storage/s3').glob('*.java'):
    before=p.read_text()
    after=before.replace('proxy.events.clear()', 'proxy.reset()').replace('proxy.events.stream()', 'proxy.snapshot().stream()').replace('proxy.events.size()', 'proxy.snapshot().size()').replace('proxy.events)', 'proxy.snapshot())')
    if before!=after:p.write_text(after)
