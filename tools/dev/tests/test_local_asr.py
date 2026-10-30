"""Boundary tests use tiny declared bytes; native inference is verified separately with the pinned real model."""
import importlib.util,json,hashlib,tempfile,unittest
from pathlib import Path
SPEC=importlib.util.spec_from_file_location('local_asr',Path(__file__).resolve().parents[2]/'providers/local_asr.py')
asr=importlib.util.module_from_spec(SPEC);SPEC.loader.exec_module(asr)
class ModelIntegrityTests(unittest.TestCase):
 def fixture(self,root):
  data=b'unit boundary bytes, not an inference model';(root/'model.bin').write_bytes(data)
  value={'schema':'ama.local-asr-model.v1','repository':'Systran/faster-whisper-small','revision':'a'*40,'engine':'faster-whisper','engineVersion':'1.2.1','device':'cpu','computeType':'int8','files':[{'name':'model.bin','sha256':hashlib.sha256(data).hexdigest(),'bytes':len(data)}]}
  manifest=root/'manifest.json';manifest.write_text(json.dumps(value));return value,manifest
 def test_declared_file_bytes_are_verified_not_just_a_ready_marker(self):
  with tempfile.TemporaryDirectory() as tmp:
   root=Path(tmp);value,manifest=self.fixture(root);proof=asr.verify_model(root,manifest);self.assertEqual(proof,hashlib.sha256(manifest.read_bytes()).hexdigest());(root/'model.bin').write_bytes(b'changed')
   with self.assertRaisesRegex(asr.AsrError,'MODEL_INTEGRITY'):asr.verify_model(root,manifest)
 def test_duplicate_fields_path_escape_and_symlink_never_load(self):
  with tempfile.TemporaryDirectory() as tmp:
   root=Path(tmp);value,manifest=self.fixture(root)
   for name in ['../model.bin','/model.bin','sub/model.bin']:
    value['files'][0]['name']=name;manifest.write_text(json.dumps(value))
    with self.assertRaises(asr.AsrError):asr.verify_model(root,manifest)
   value,manifest=self.fixture(root);(root/'model.bin').unlink();(root/'model.bin').symlink_to(manifest)
   with self.assertRaises(asr.AsrError):asr.verify_model(root,manifest)
   manifest.write_text('{"schema":"bad","schema":"ama.local-asr-model.v1"}')
   with self.assertRaises(asr.AsrError):asr.verify_model(root,manifest)
class SegmentBoundaryTests(unittest.TestCase):
 def test_real_engine_shape_is_bounded_in_utf16_and_absolute_milliseconds(self):
  state=asr.Segments(max_units=20,max_segments=2);state.add('😀 meeting',.25,1.5,offset=30)
  self.assertEqual(state.items,[{'text':'😀 meeting','startMs':30250,'endMs':31500}]);self.assertEqual(state.units,11)
  with self.assertRaisesRegex(asr.AsrError,'UTF16_CODE_UNITS_EXCEEDED'):state.add('too many characters',2,3,offset=30)
  self.assertEqual(len(state.items),1)
 def test_invalid_times_text_and_segment_overflow_fail(self):
  for start,end,text in [(2,1,'invalid'),(-1,2,'invalid'),(0,float('inf'),'invalid'),(0,1,'bad\x00text')]:
   with self.assertRaises(asr.AsrError):asr.Segments(100,2).add(text,start,end)
  state=asr.Segments(100,1);state.add('one',0,1)
  with self.assertRaisesRegex(asr.AsrError,'SEGMENTS_EXCEEDED'):state.add('two',1,2)
 def test_blank_recognition_still_consumes_the_decoded_text_budget(self):
  state=asr.Segments(4,10);state.add('  ',0,1)
  self.assertEqual(state.units,3)
  with self.assertRaisesRegex(asr.AsrError,'UTF16_CODE_UNITS_EXCEEDED'):state.add('x',1,2)

class DeclaredFormatTests(unittest.TestCase):
 def test_unknown_declared_format_is_rejected_before_decode_or_inference(self):
  with tempfile.TemporaryDirectory() as tmp:
   root=Path(tmp);_,manifest=ModelIntegrityTests().fixture(root);path=root/'source.bin';path.write_bytes(b'not media')
   with self.assertRaisesRegex(asr.AsrError,'INVALID_ARGUMENT'):
    asr.transcribe_file(path,root,manifest,100,10,format='HLS')
