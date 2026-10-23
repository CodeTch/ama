from pathlib import Path
p=Path('backend/integration-tests/src/test/java/ama/storage/Task04ProbeSource.java');s=p.read_text().replace('    volatile String insertedFile;', '''    final java.util.Set<Thread> rootsHeld=java.util.concurrent.ConcurrentHashMap.newKeySet();
    final java.util.List<String> lockTrace=new java.util.concurrent.CopyOnWriteArrayList<>();
    volatile String insertedFile;''')
s=s.replace('''                            if (method.getName().equals("prepareStatement")''','''                            if(method.getName().equals("close"))rootsHeld.remove(Thread.currentThread());
                            if (method.getName().equals("prepareStatement")''')
s=s.replace('''                                return Proxy.newProxyInstance(
                                        getClass().getClassLoader(),
                                        new Class[] {PreparedStatement.class},''','''                                var parameters=new java.util.HashMap<Integer,Object>();
                                return Proxy.newProxyInstance(
                                        getClass().getClassLoader(),
                                        new Class[] {PreparedStatement.class},''')
s=s.replace('''                                            if (sql.startsWith("INSERT INTO file_object")''','''                                            if(m.getName().startsWith("set")&&a!=null&&a.length>1&&a[0] instanceof Integer index)parameters.put(index,a[1]);
                                            if(m.getName().startsWith("execute")&&sql.contains("FOR UPDATE")) {
                                                if(sql.contains("FROM task_subject WHERE")||sql.contains("FROM meeting WHERE"))rootsHeld.add(Thread.currentThread());
                                                for(String table:java.util.List.of("enterprise_identity","task_subject","meeting","transcription_input","input_batch","input_batch_fingerprint_profile","input_batch_file","upload_session","file_object","durable_task","task_attempt"))if(sql.contains("FROM "+table+" WHERE")) {
                                                    Object id=parameters.get(table.equals("task_subject")?2:1);
                                                    lockTrace.add(Thread.currentThread().getName()+" "+table+" "+id);
                                                }
                                            }
                                            if (sql.startsWith("INSERT INTO file_object")''');p.write_text(s)
p=Path('backend/integration-tests/src/test/java/ama/storage/ProcessingIntakeHttpIT.java');s=p.read_text();at='    @AfterEach void close() throws Exception {'
new=r'''    @Test void actualCanonicalAndManagedHashesStayOutsideMeetingRoots() throws Exception {
        String file=upload("observed\n"),payload=json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(file)));
        String provider="TASK04_"+UUID.randomUUID();Task04HashObserver.inside.set(0);Task04HashObserver.outside.set(0);Task04HashObserver.held=()->source.rootsHeld.contains(Thread.currentThread());
        java.security.Security.insertProviderAt(new Task04HashObserver(provider),1);
        try {
            var accepted=call("POST","/meetings/"+meeting+"/transcript-imports",payload,"observe");assertEquals(202,accepted.statusCode(),accepted.body());String task=body(accepted).get("taskId").asText();ownedTasks.add(task);
            assertEquals(202,call("POST","/meetings/"+meeting+"/transcript-imports",payload,"observe").statusCode());
            assertEquals(200,call("GET","/meetings/"+meeting+"/processing-inputs/"+body(accepted).get("inputId").asText(),"",null).statusCode());
            assertEquals(200,call("GET","/meetings/"+meeting+"/processing-tasks","",null).statusCode());
            var queue=app.getBean(ama.task.JdbcTaskQueue.class);var lease=queue.claim("TRANSCRIPT_IMPORT","TASK04_OBSERVED_WORKER").orElseThrow();assertEquals(task,lease.taskId());assertTrue(queue.renew(lease));assertTrue(queue.complete(lease,new ama.contract.TaskContracts.TaskResult(ama.contract.TaskContracts.Outcome.FAILED,null,"PROCESSING_INPUT_INVALID")));
            assertTrue(Task04HashObserver.outside.get()>0);assertEquals(0,Task04HashObserver.inside.get());
        }finally{java.security.Security.removeProvider(provider);Task04HashObserver.held=()->false;}
    }
'''
s=s.replace(at,new+at).replace('if(app!=null)app.close();if(storage!=null)storage.close();','''if(meeting!=null&&source!=null) {
            Files.writeString(receipts.resolve(meeting+"-locks.json"),json.writeValueAsString(source.lockTrace));
            Files.writeString(receipts.resolve(meeting+"-hashes.json"),json.writeValueAsString(Map.of("inside",Task04HashObserver.inside.get(),"outside",Task04HashObserver.outside.get())));
        }
        if(app!=null)app.close();if(storage!=null)storage.close();''');p.write_text(s)
