"""Regression checks for ownership and Linux process titles in the local launcher."""
import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import time
import tempfile
import unittest
from unittest.mock import patch

SPEC=importlib.util.spec_from_file_location('local_app',Path(__file__).resolve().parents[1]/'local_app.py')
app=importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(app)


class ProcessOwnershipTests(unittest.TestCase):
    def process(self,owned=True):
        env=os.environ.copy()
        env['AMA_LOCAL_DEV_INSTANCE']=app.OWNER if owned else 'another-checkout'
        process=subprocess.Popen([sys.executable,'-c',
            'import ctypes,time;ctypes.CDLL(None).prctl(15,b"npm run dev");time.sleep(30)'],
            env=env,start_new_session=True)
        def cleanup():
            if process.poll() is None:
                process.kill()
            process.wait(timeout=5)
        self.addCleanup(cleanup)
        for _ in range(100):
            if Path(f'/proc/{process.pid}/comm').read_text().strip()=='npm run dev':
                break
            time.sleep(.01)
        return process

    def test_space_in_npm_title_does_not_hide_owned_process(self):
        process=self.process()
        record=app.process_record(process)
        self.assertTrue(app.alive(record))
        app.stop_process(record)
        self.assertEqual(process.wait(timeout=5),-15)
        self.assertFalse(app.alive(record))

    def test_wrong_instance_and_reused_pid_are_never_stopped(self):
        foreign=self.process(owned=False)
        record=app.process_record(foreign)
        self.assertFalse(app.alive(record))
        app.stop_process(record)
        self.assertIsNone(foreign.poll())
        foreign.terminate();foreign.wait(timeout=5)
        own=self.process()
        mismatched={**app.process_record(own),'started':'0'}
        self.assertFalse(app.alive(mismatched))
        app.stop_process(mismatched)
        self.assertIsNone(own.poll())
        own.terminate();own.wait(timeout=5)


class ConfigurationIsolationTests(unittest.TestCase):
    def test_explicit_file_workers_keep_the_default_and_reject_unbounded_counts(self):
        with patch.dict(os.environ, {}, clear=True):
            self.assertEqual(16, app.file_worker_threads())
        with patch.dict(os.environ, {'AMA_LOCAL_FILE_WORKERS': '32'}):
            self.assertEqual(32, app.file_worker_threads())
        for value in ('0', '33', '-1', '32.0', ' 32', 'invalid'):
            with self.subTest(value=value), patch.dict(os.environ, {'AMA_LOCAL_FILE_WORKERS': value}):
                with self.assertRaises(ValueError):
                    app.file_worker_threads()

    def test_explicit_local_pool_maximum_is_bounded_and_independent_of_spring_environment(self):
        with patch.dict(os.environ, {'AMA_LOCAL_DB_POOL_MAXIMUM': '32', 'SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE': '64'}):
            self.assertEqual(32, app.database_pool_maximum())
            _, _, environment = app.toolchain()
            self.assertNotIn('SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE', environment)
        with patch.dict(os.environ, {}, clear=True):
            self.assertEqual(20, app.database_pool_maximum())
        for value in ('0', '65', '-1', '32.0', ' 32', 'invalid'):
            with self.subTest(value=value), patch.dict(os.environ, {'AMA_LOCAL_DB_POOL_MAXIMUM': value}):
                with self.assertRaises(ValueError):
                    app.database_pool_maximum()

    def test_changes_to_local_assembly_invalidate_running_source_signature(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary)
            (root/'tools/dev').mkdir(parents=True)
            (root/'backend/target').mkdir(parents=True)
            (root/'pom.xml').write_text('<project/>')
            source=root/'tools/dev/assembly.py';source.write_text('version=1')
            with patch.object(app,'ROOT',root):
                original=app.source_signature()
                (root/'backend/target/build.java').write_text('generated')
                self.assertEqual(original,app.source_signature())
                source.write_text('version=2')
                self.assertNotEqual(original,app.source_signature())

    def test_shell_spring_and_provider_settings_cannot_escape_into_local_runtime(self):
        foreign={'SPRING_DATASOURCE_URL':'jdbc:mysql://127.0.0.1:9/foreign',
                 'SPRING_DATASOURCE_USERNAME':'foreign', 'SPRING_DATASOURCE_PASSWORD':'test-only',
                 'SPRING_APPLICATION_JSON':'{"spring":{"datasource":{"url":"jdbc:mysql://127.0.0.1:9/foreign"}}}',
                 'MINUTES_ENABLED':'true','PUBLICATION_ENABLED':'true','FILES_BACKENDS':'foreign',
                 'S3_ENDPOINT':'https://unrelated.invalid','AMA_TEST_DATABASE_URL':'jdbc:mysql://127.0.0.1:9/old'}
        with patch.dict(os.environ,foreign):
            _,_,environment=app.toolchain()
        for name in foreign:
            self.assertFalse(name in environment,name+' must not be inherited')
        self.assertIn('PATH',environment)

    def test_start_rechecks_owned_mysql_even_when_frontend_and_api_are_alive(self):
        state={role:{'pid':index+1} for index,role in enumerate(app.SERVICE_ROLES)}
        app.configure_ports(state,None,None,None)
        state.update(databasePort='12345',sourceSignature=app.source_signature(),activePorts=dict(state['ports']))
        # Fake PIDs must never reach an OS signal, even if this regression takes a restart branch.
        with patch.object(app,'alive',return_value=True),patch.object(app,'stop_process') as stop,patch.object(app,'database',return_value='12345') as database,patch.object(app,'status'):
            app.start(state)
        database.assert_called_once_with(state)
        stop.assert_not_called()


class LocalPreparationRecoveryTests(unittest.TestCase):
    def test_json_only_resource_change_rebuilds_the_actual_cached_artifact(self):
        import zipfile
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary)
            runtime=root/'.tools/local-app';runtime.mkdir(parents=True)
            resource=root/'backend/adapters/src/main/resources/parser.json';resource.parent.mkdir(parents=True)
            resource.write_text('one')
            (root/'pom.xml').write_text('<project/>')
            (root/'tools/dev/java').mkdir(parents=True)
            native=root/'backend/file-service/target/ama-file-service-0.1.0-SNAPSHOT-exec.jar';native.parent.mkdir(parents=True);native.touch()
            worker=root/'backend/worker/target/classes/ama/worker/TranscriptWorkerMain.class';worker.parent.mkdir(parents=True);worker.touch()
            jar=root/'backend/api/target/ama-api-0.1.0-SNAPSHOT-exec.jar';jar.parent.mkdir(parents=True)
            def compile_or_package(command,env,log):
                if 'package' in command:
                    with zipfile.ZipFile(jar,'w') as archive:
                        archive.writestr('BOOT-INF/classes/parser.json',resource.read_text())
            with patch.object(app,'ROOT',root),patch.object(app,'RUNTIME',runtime),patch.object(app,'run_logged',side_effect=compile_or_package):
                first=app.application(Path('/tool/bin/java'),{})
                self.assertEqual(Path(first.split(':')[1]).joinpath('parser.json').read_text(),'one')
                resource.write_text('two')
                second=app.application(Path('/tool/bin/java'),{})
                self.assertEqual(Path(second.split(':')[1]).joinpath('parser.json').read_text(),'two')

    def test_failed_preparation_can_retry_using_the_persisted_pki_password(self):
        import types
        # Use the real local state writer/reader. Only external services, build and the failing
        # provisioning boundary are substituted; no SQL/process should start in this test.
        with tempfile.TemporaryDirectory() as temporary:
            runtime=Path(temporary)
            initial={'owner':app.OWNER,'databasePassword':'test-db','rootPassword':'test-root','seedDate':'2026-10-28'}
            app.save(runtime/'state.json',initial)
            passwords=[]
            def failed_prepare(root,state,java,env,classpath):
                passwords.append(state['pkiPassword'])
                (root/'pki-complete').touch()
                raise RuntimeError('failure after completed PKI')
            ports=iter([45691,45692,45693,45694])
            helper=types.SimpleNamespace(prepare=failed_prepare,properties=lambda *_:None,port=lambda:next(ports))
            with patch.object(app,'RUNTIME',runtime),patch.object(app,'source_signature',return_value='changed'),patch.object(app,'alive',return_value=False),patch.object(app,'database',return_value='12345'),patch.object(app,'toolchain',return_value=(Path('/tool/bin/java'),Path('/tool/bin/npm'),{})),patch.object(app,'application',return_value='classpath'),patch.object(app,'run_logged'),patch.dict(sys.modules,{'local_services':helper}),patch.object(app.socket,'socket'),patch.object(app.secrets,'token_urlsafe',side_effect=['test-key-one','test-key-two']):
                for _ in range(2):
                    state=__import__('json').loads((runtime/'state.json').read_text())
                    with self.assertRaisesRegex(RuntimeError,'failure after completed PKI'):
                        app.start(state)
                persisted=__import__('json').loads((runtime/'state.json').read_text())
                self.assertEqual(persisted.get('pkiPassword'),passwords[0])
                self.assertEqual(passwords[0],passwords[1])
                self.assertEqual(persisted['filePort'],45691)
                self.assertEqual(persisted['workerHealthPort'],45692)


class WorkerLifecycleTests(unittest.TestCase):
    def health_port_start(self,port):
        from contextlib import ExitStack
        with tempfile.TemporaryDirectory() as temporary,ExitStack() as patches:
            runtime=Path(temporary);configuration=runtime/'worker.properties';configuration.write_text('qualification-only');configuration.chmod(0o600)
            state={role:{'pid':i+1} for i,role in enumerate(app.SERVICE_ROLES)};state.update(sourceSignature='same',databasePort='12345',workerHealthPort=port)
            patches.enter_context(patch.object(app,'RUNTIME',runtime));patches.enter_context(patch.object(app,'source_signature',return_value='same'))
            patches.enter_context(patch.object(app,'alive',side_effect=lambda record:record is not state['worker']));patches.enter_context(patch.object(app,'database',return_value='12345'))
            patches.enter_context(patch.object(app,'toolchain',return_value=(Path('/tool/bin/java'),Path('/tool/bin/npm'),{})));patches.enter_context(patch.object(app,'application',return_value='qualification'))
            launch=patches.enter_context(patch.object(app,'launch',side_effect=RuntimeError('Reached exact owned launch')))
            try:app.start_worker(state)
            finally:self.last_launch_called=launch.called

    def test_closed_health_connection_time_wait_is_not_an_unrelated_listener(self):
        import socket
        server=socket.socket();server.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1);server.bind(('127.0.0.1',0));port=server.getsockname()[1];server.listen(1)
        client=socket.create_connection(('127.0.0.1',port));accepted,_=server.accept();accepted.shutdown(socket.SHUT_WR);self.assertEqual(client.recv(1),b'');client.close();accepted.close();server.close()
        with self.assertRaisesRegex(RuntimeError,'Reached exact owned launch'):self.health_port_start(port)
        self.assertTrue(self.last_launch_called)

    def test_real_listener_is_still_refused_and_never_stopped(self):
        import socket
        with socket.socket() as server:
            server.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1);server.bind(('127.0.0.1',0));server.listen(1);port=server.getsockname()[1]
            with self.assertRaisesRegex(RuntimeError,'health port occupied'):self.health_port_start(port)
            self.assertFalse(self.last_launch_called)
            with socket.create_connection(('127.0.0.1',port)) as client:
                accepted,_=server.accept();accepted.close()

    def test_component_start_cannot_mix_changed_source_with_the_running_api(self):
        state={role:{'pid':i+1} for i,role in enumerate(app.SERVICE_ROLES)}
        state['sourceSignature']='old'
        with patch.object(app,'source_signature',return_value='new'),patch.object(app,'alive',side_effect=lambda r:r is not state['worker']),patch.object(app,'launch') as launch:
            with self.assertRaisesRegex(RuntimeError,'source changed'):
                app.start_worker(state)
        launch.assert_not_called()


if __name__=='__main__':
    unittest.main()

class PortIsolationTests(unittest.TestCase):
    def test_explicit_ports_are_retained_and_default_is_compatible(self):
        state={}
        self.assertEqual(app.configure_ports(state,None,None,None),(5173,8082,8083))
        self.assertEqual(app.configure_ports(state,5174,8182,8183),(5174,8182,8183))
        self.assertEqual(app.configure_ports(state,None,None,None),(5174,8182,8183))
        self.assertEqual(app.ORIGIN,'http://127.0.0.1:5174')
    def test_invalid_or_overlapping_ports_do_not_mutate_configuration(self):
        state={}
        for ports in [(0,8182,8183),(5174,65536,8183),(5174,5174,8183)]:
            with self.assertRaises(ValueError):app.configure_ports(state,*ports)
        self.assertEqual(state,{})
