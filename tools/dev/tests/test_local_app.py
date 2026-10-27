"""Regression checks for ownership and Linux process titles in the local launcher."""
import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import time
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
        state={'frontend':{'pid':1},'backend':{'pid':2},'databasePort':'12345'}
        with patch.object(app,'alive',return_value=True),patch.object(app,'database',return_value='12345') as database,patch.object(app,'status'):
            app.start(state)
        database.assert_called_once_with(state)


if __name__=='__main__':
    unittest.main()
