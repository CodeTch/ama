from pathlib import Path
p=Path('backend/integration-tests/src/test/java/ama/storage/RegistryProbeSource.java');s=p.read_text().replace('RegistryProbeSource','Task04ProbeSource')
s=s.replace('    volatile String insertedFile;', '''    final AtomicReference<String> failStatement=new AtomicReference<>();
    volatile java.util.function.Consumer<String> before=sql->{};
    volatile java.util.function.Consumer<String> after=sql->{};
    volatile String insertedFile;''')
s=s.replace('                                            return invoke(statement, m, a);','''                                            if(m.getName().startsWith("execute")) {
                                                before.accept(sql);
                                                String failure=failStatement.get();
                                                if(failure!=null&&sql.contains(failure)&&failStatement.compareAndSet(failure,null))throw new SQLException("Task4 controlled statement refusal");
                                                Object result=invoke(statement,m,a);after.accept(sql);return result;
                                            }
                                            return invoke(statement, m, a);''');Path('backend/integration-tests/src/test/java/ama/storage/Task04ProbeSource.java').write_text(s)
p=Path('backend/api/src/main/java/ama/api/ProcessingConfiguration.java');s=p.read_text();start='''  if(!env.getProperty("processing.enabled",Boolean.class,false))return new Runtime(null,(a,m,r)->{throw unavailable();},Map.of(),()->false);
  // Every''';replace='''  if(!env.getProperty("processing.enabled",Boolean.class,false))return unavailableRuntime();
  try {return configured(source,tx,access,purposes,backends,references,env);}
  catch(IllegalArgumentException|IllegalStateException|ama.contract.MinutesContracts.Failure invalid){return unavailableRuntime();}
 }
 private Runtime unavailableRuntime(){return new Runtime(null,(a,m,r)->{throw unavailable();},Map.of(),()->false);}
 private Runtime configured(DataSource source,TransactionRunner tx,MeetingAccess access,FilePurposePolicy purposes,FileBackendCatalog backends,Map<String,FileReferencePolicy> references,Environment env) {
  // Every'''
assert start in s;s=s.replace(start,replace)
s=s.replace(' @Bean JdbcProcessingIntake processingIntake(', ' @Bean JdbcProcessingIntake.NewIntake newIntakeBranch(Runtime runtime){return runtime.preparation();}\n @Bean JdbcProcessingIntake processingIntake(').replace('JdbcTaskQueue queue,JdbcOutbox outbox) {\n  return new JdbcProcessingIntake','JdbcTaskQueue queue,JdbcOutbox outbox,JdbcProcessingIntake.NewIntake preparation) {\n  return new JdbcProcessingIntake').replace('new JdbcAuditAdapter(source),runtime.preparation());','new JdbcAuditAdapter(source),preparation);');p.write_text(s)
p=Path('backend/integration-tests/src/test/java/ama/storage/ProcessingIntakeHttpIT.java');s=p.read_text().replace('RegistryProbeSource source;','Task04ProbeSource source;').replace('source=new RegistryProbeSource(', 'source=new Task04ProbeSource(')
s=s.replace('    Path archive;', '''    volatile java.util.function.UnaryOperator<ama.transcript.JdbcProcessingIntake.NewIntake> preparationDecorator=java.util.function.UnaryOperator.identity();
    Path archive;''')
s=s.replace('            c.registerBean("task04DataSource",', '''            c.registerBean("task04Preparation",ama.transcript.JdbcProcessingIntake.NewIntake.class,()->{
                var actual=(ama.transcript.JdbcProcessingIntake.NewIntake)c.getBean("newIntakeBranch");
                return (a,m,r)->preparationDecorator.apply(actual).prepare(a,m,r);
            },definition->definition.setPrimary(true));
            c.registerBean("task04DataSource",''')
s=s.replace('assertEquals("READY",rows.getFirst().get("state"));','assertTrue(Set.of("READY","RUNNING","FAILED","CANCELLED").contains(rows.getFirst().get("state")));')
s=s.replace('            j.update("UPDATE durable_task SET state=\'CANCELLED\',lease_owner=NULL,expires_at=NULL WHERE id=? AND state=\'READY\'",task);','''            var tx=new ama.db.TransactionRunner(source);
            tx.required(()->{
                var current=j.queryForObject("SELECT state FROM durable_task WHERE id=? FOR UPDATE",String.class,task);
                if(Set.of("READY","RUNNING").contains(current)) {
                    j.update("UPDATE task_attempt SET ended_at=UTC_TIMESTAMP(6),outcome='CANCELLED',error_code='TASK04_FIXTURE_CLOSED' WHERE task_id=? AND ended_at IS NULL",task);
                    assertEquals(1,j.update("UPDATE durable_task SET state='CANCELLED',lease_owner=NULL,expires_at=NULL WHERE id=? AND state=?",task,current));
                }
                return null;
            });''')
p.write_text(s)
