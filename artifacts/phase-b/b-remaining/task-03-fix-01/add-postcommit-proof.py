from pathlib import Path
p=Path('backend/integration-tests/src/test/java/ama/storage/TranscriptAuthorityReviewIT.java');s=p.read_text();old='''                assertTrue(HashBoundaryDigest.outside > 0, "real managed identity digest remains outside roots");''';new='''                // Change the exact owned route only after the read transaction physically commits.
                var outside = new org.springframework.jdbc.core.JdbcTemplate(f.trace.actual);
                f.trace.afterCommit.set(() -> outside.update(
                        "UPDATE storage_backend SET status='DISABLED' WHERE id=?", backendId));
                assertThrows(ama.contract.FileContracts.Unavailable.class,
                        () -> c.sources().open(lease, prepared.inputId(), uploaded.file()));
                assertEquals(opened, f.reads.get(), "post-commit route recheck must precede storage open");
                outside.update("UPDATE storage_backend SET status='WRITABLE' WHERE id=?", backendId);
                assertTrue(HashBoundaryDigest.outside > 0, "real managed identity digest remains outside roots");''';assert s.count(old)==1;s=s.replace(old,new);s=s.replace('''                assertEquals(0, HashBoundaryDigest.locked);
''','''                assertEquals(0, HashBoundaryDigest.locked);
                System.out.println("Task3 IR01 managed SHA-256 outside=" + HashBoundaryDigest.outside
                        + " locked=" + HashBoundaryDigest.locked + "; real draft/bind/worker read and post-commit denial checked");
''');p.write_text(s.rstrip()+'\n')
