import pathlib,subprocess,os,uuid,json
root=pathlib.Path(__file__).resolve().parents[4]
e=root/'artifacts/phase-b/b-remaining/task-03'
restart=e/('archive-restart-'+str(uuid.uuid4()))
assert not restart.exists();restart.mkdir()
env=os.environ.copy();env['JAVA_TOOL_OPTIONS']='-Xmx64m -XX:ActiveProcessorCount=2';env['AMA_B06_RESTART_EVIDENCE']=str(restart)
args=['./mvnw','-q','-pl','backend/integration-tests','-am','-DargLine=-Xmx512m -XX:ActiveProcessorCount=2','-Dit.test=TranscriptAuthorityIT,FileRegistryUploadIT,FileRegistryRestartIT#frozenManifestNamePurposeAndOpaqueTokenRecoverAfterActualJvmDeath,ProcessingFactsReaderIT,B06QueueSeamsIT,MeetingRevisionIT,MeetingConcurrencyIT,ConfigArchiveRestartIT','-DfailIfNoTests=false','-Dsurefire.failIfNoSpecifiedTests=false','-Dfailsafe.failIfNoSpecifiedTests=false','verify']
(e/'final-launch.json').write_text(json.dumps({'argv':args,'fresh_restart_evidence':str(restart),'maven_heap_mib':512,'test_fork_heap_mib':512,'archive_child_heap_mib':64,'native_service_heap_mib':64,'registry_api_child_heap_mib':192,'active_processors':2},indent=2)+'\n')
raise SystemExit(subprocess.call(['python3',str(e/'run.py'),'authority-regressions',*args],cwd=root,env=env))
