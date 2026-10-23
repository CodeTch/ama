from pathlib import Path
r=Path('/home/tanren/ama/.worktrees/phase-b-completion')
p=r/'backend/integration-tests/src/test/java/ama/storage'
original=(p/'Task05PipelineIT.java').read_text()
# Narrow fixture helpers only: no inherited test or restart invocation can run or write old receipts.
head=original[:original.index(' @Test void actualNativeHttpIntakeWorkerAndRaw')]
start=original.index(' static final ama.task.PreparationCancellation NEVER=')
end=original.index(' @Test void actualCarrierRejectsDirectForeignAndOrdinaryCompletionThenWritesOnce')
tail=original[original.index(' @AfterEach void close()'):]
fixture=(head+original[start:end]+tail).replace('class Task05PipelineIT','class Task05FixFixture')
fixture=fixture.replace('Task05NativeFixture','Task05FixNativeFixture').replace('artifacts/phase-b/b-remaining/task-05"','artifacts/phase-b/b-remaining/task-05-fix-01"')
(p/'Task05FixFixture.java').write_text(fixture)
native=(p/'Task05NativeFixture.java').read_text().replace('Task05NativeFixture','Task05FixNativeFixture')
native=native.replace('Task05RegressionResources.evidencePath()', 'evidencePath()')
native=native.replace('Task05RegressionResources.ROOT.getParent().getParent()', 'repoRoot()')
native=native.replace('    static final class OwnedRoot {', '''    static Path repoRoot() {
        Path root=Path.of("").toAbsolutePath();
        while(!Files.exists(root.resolve("tools/checks/run.py")))root=root.getParent();
        return root;
    }
    static Path evidencePath(){return repoRoot().resolve("artifacts/phase-b/b-remaining/task-05-fix-01");}
    static final class OwnedRoot {''')
(p/'Task05FixNativeFixture.java').write_text(native)
print('created only new fix01 fixture helper sources')
