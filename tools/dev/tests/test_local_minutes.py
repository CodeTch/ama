"""Real local provider credentials/store survive preparation and preserve ACL edits."""
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import sqlite3
from unittest.mock import patch
import unittest

DEV=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(DEV))
import local_minutes


class KnowledgePreparationTests(unittest.TestCase):
    def test_preparation_reuses_identity_keys_store_and_does_not_restore_revoked_grants(self):
        with tempfile.TemporaryDirectory() as temporary:
            runtime=Path(temporary).resolve()
            state={'owner':'test-checkout','knowledgePort':45693}
            first=local_minutes.prepare_knowledge(runtime,state)
            root=runtime/'knowledge'
            paths=['application.json','bearer','delegation.key','assertion.key','provider.json']
            pins={name:hashlib.sha256((root/name).read_bytes()).hexdigest() for name in paths}
            provider=local_minutes.open_knowledge(json.loads((root/'provider.json').read_text()))
            try:
                self.assertTrue(provider.grant('dev-owner','controlled-library','controlled-root')['can_publish'])
                self.assertTrue(provider.grant('dev-member','controlled-library','controlled-root',True)['can_read'])
                provider.administer('dev-member','controlled-library','controlled-root',False,False)
            finally:provider.db.close()
            self.assertEqual(local_minutes.prepare_knowledge(runtime,state),first)
            self.assertEqual({name:hashlib.sha256((root/name).read_bytes()).hexdigest() for name in paths},pins)
            provider=local_minutes.open_knowledge(json.loads((root/'provider.json').read_text()))
            try:
                with self.assertRaises(Exception):provider.grant('dev-member','controlled-library','controlled-root',True)
            finally:provider.db.close()
            for name in paths:self.assertEqual((root/name).stat().st_mode&0o777,0o600)

    def test_interrupted_store_bootstrap_retries_without_publishing_a_partial_store(self):
        with tempfile.TemporaryDirectory() as temporary:
            runtime=Path(temporary).resolve()
            state={'owner':'test-retry','knowledgePort':45694}
            actual_open=local_minutes.open_knowledge
            attempts=[]
            def interrupted(config,initialize=False):
                attempts.append(initialize)
                if len(attempts)==1:
                    connection=sqlite3.connect(config['store'])
                    connection.execute('CREATE TABLE interrupted_bootstrap(id INTEGER)')
                    connection.close()
                    raise RuntimeError('interrupted after store creation')
                return actual_open(config,initialize)
            with patch.object(local_minutes,'open_knowledge',side_effect=interrupted):
                with self.assertRaisesRegex(RuntimeError,'interrupted after store creation'):
                    local_minutes.prepare_knowledge(runtime,state)
                root=runtime/'knowledge'
                pins={name:(root/name).read_bytes() for name in ['delegation.key','assertion.key','bearer','application.json']}
                self.assertFalse((root/'store.sqlite').exists(),'An unfinished bootstrap must never occupy the published store')
                local_minutes.prepare_knowledge(runtime,state)
            self.assertEqual(attempts,[True,True])
            self.assertEqual({name:(root/name).read_bytes() for name in pins},pins)
            provider=actual_open(json.loads((root/'provider.json').read_text()))
            try:self.assertTrue(provider.grant('dev-owner','controlled-library','controlled-root')['can_publish'])
            finally:provider.db.close()


if __name__=='__main__':unittest.main()


class MediaEnvironmentTests(unittest.TestCase):
    def test_only_owned_installed_runtime_is_inherited_by_full_and_worker_start(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary);environment={'AMA_LOCAL_ASR_RUNTIME':'/foreign/runtime'}
            with patch.object(local_minutes,'ROOT',root):
                self.assertFalse(local_minutes.asr_environment(environment));self.assertNotIn('AMA_LOCAL_ASR_RUNTIME',environment)
                config=root/'.tools/local-models/asr-runtime.json';config.parent.mkdir(parents=True);config.write_text('{}')
                self.assertTrue(local_minutes.asr_environment(environment));self.assertEqual(environment['AMA_LOCAL_ASR_RUNTIME'],str(config))
    def test_symlink_runtime_never_enables_the_media_profile(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary);config=root/'.tools/local-models/asr-runtime.json';config.parent.mkdir(parents=True);outside=root/'other.json';outside.write_text('{}');config.symlink_to(outside)
            with patch.object(local_minutes,'ROOT',root):
                with self.assertRaises(RuntimeError):local_minutes.asr_environment({})


class PersonalTemplatePreparationTests(unittest.TestCase):
    def test_fresh_personal_release_is_appended_after_document_and_material_profiles(self):
        import subprocess
        with tempfile.TemporaryDirectory() as temporary:
            runtime=Path(temporary).resolve()
            state={'owner':'template-preparation','modelPort':47091,'knowledgePort':46969,
                   'minutesTemplateId':'00000000-0000-4000-8000-000000000001',
                   'minutesTemplateRevisionId':'00000000-0000-4000-8000-000000000002'}
            phases={'ama.localdev.LocalMinutesSetup':'a'*64,'ama.localdev.LocalDocumentSetup':'b'*64,
                    'ama.localdev.LocalMaterialSetup':'c'*64,'ama.localdev.LocalPersonalTemplatesSetup':'d'*64,'ama.localdev.LocalRegionMinutesSetup':'e'*64}
            calls=[]
            def run(command,**options):
                calls.append(command)
                pin=phases[command[4]]
                return subprocess.CompletedProcess(command,0,json.dumps({'minutes.index-sha':pin,'processing.index-sha':pin}), '')
            with patch.object(local_minutes.subprocess,'run',side_effect=run),patch.object(local_minutes,'prepare_knowledge',return_value={}),patch.object(local_minutes,'asr_environment',return_value=False):
                values,_=local_minutes.prepare(runtime,state,Path('/owned/java'),{},'owned-classpath')
            self.assertEqual([command[4] for command in calls],list(phases))
            self.assertEqual(calls[-2][-2:],[phases['ama.localdev.LocalMaterialSetup'],state['minutesTemplateRevisionId']])
            self.assertEqual(values['minutes.index-sha'],'e'*64)
            self.assertEqual(values['processing.index-sha'],'e'*64)
            self.assertEqual(values['processing.bounds.dependencies'],256)


class SensitiveScenarioPreparationTests(unittest.TestCase):
    def test_sensitive_scenario_is_an_explicit_new_release_not_a_rewrite_of_retained_archives(self):
        import subprocess
        with tempfile.TemporaryDirectory() as temporary:
            state={'owner':'sensitive-preparation','modelPort':47091,'knowledgePort':46969,
                   'minutesTemplateId':'00000000-0000-4000-8000-000000000001',
                   'minutesTemplateRevisionId':'00000000-0000-4000-8000-000000000002','minutesScenario':'sensitive'}
            calls=[]
            def run(command,**options):
                calls.append(command)
                return subprocess.CompletedProcess(command,0,json.dumps({'minutes.index-sha':'e'*64,'processing.index-sha':'e'*64}),'')
            with patch.object(local_minutes.subprocess,'run',side_effect=run),patch.object(local_minutes,'prepare_knowledge',return_value={}),patch.object(local_minutes,'asr_environment',return_value=False):
                local_minutes.prepare(Path(temporary).resolve(),state,Path('/owned/java'),{},'owned-classpath')
            self.assertEqual(calls[-1][4],'ama.localdev.LocalRegionMinutesSetup')
            self.assertEqual(calls[-1][-1],'SENSITIVE_SCENARIO')
            self.assertEqual(calls[-1][-3:-1],['e'*64,state['minutesTemplateRevisionId']])
