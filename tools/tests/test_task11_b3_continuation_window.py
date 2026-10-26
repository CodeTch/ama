"""Real B3 runner dispatch guard; synthetic public release/root; environment/launch never reached on invalid input."""
from contextlib import ExitStack
import datetime,hashlib,json,pathlib,shutil,sys,tempfile,unittest
from unittest.mock import patch
TOOLS=pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0,str(TOOLS))
import task11_b3_continuation_window as runner
class EnvironmentBoundary(Exception):pass
class SideEffectViolation(Exception):pass
class Environment(dict):
 def copy(self):raise EnvironmentBoundary()
class B3ContinuationWindowTests(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory(prefix='b3-continuation-window-guard-');self.addCleanup(self.temp.cleanup);self.root=pathlib.Path(self.temp.name);self.w=self.root/'.superpowers/sdd/2026-10-23-ama-phase-b-remaining';self.w.mkdir(parents=True);self.out=self.root/'windows';self.out.mkdir()
  raw=(runner.W/runner.ACK).read_bytes();self.cfg=json.loads(raw);(self.w/runner.ACK).write_bytes(raw)
  for pin in self.cfg['inputPins'].values():shutil.copyfile(runner.W/pin['path'],self.w/pin['path'])
  target=self.root/self.cfg['baselineRows'];target.parent.mkdir(parents=True);shutil.copyfile(runner.ROOT/self.cfg['baselineRows'],target)
  self.source=self.root/'inert.txt';self.source.write_text('inert\n');cp=self.w/self.cfg['runtimeSourceCheckpoint'];cp.write_text(json.dumps({'files':[{'path':self.source.name,'sha256':hashlib.sha256(self.source.read_bytes()).hexdigest()}]}))
  now=datetime.datetime.now(datetime.timezone.utc);self.release=dict(status='B3_CONTINUATION_RUNTIME_RELEASED_ONCE',label=runner.TUPLE[0],group=runner.TUPLE[1],selector=runner.TUPLE[2],fixtureName=runner.TUPLE[3],configurationAckSha256=runner.ACK_SHA,sourceCheckpointSha256=hashlib.sha256(cp.read_bytes()).hexdigest(),maxInvocations=1,oneGrantAuthorized=False,nativeStartAuthorized=False,sendAuthorized=False,budget=self.cfg['budget'],issuedUtc=(now-datetime.timedelta(minutes=1)).isoformat(),startBeforeUtc=(now+datetime.timedelta(minutes=5)).isoformat());self.release_path=self.w/self.cfg['runtimeReleaseFile'];self.save()
 def save(self):self.release_path.write_text(json.dumps(self.release))
 def dispatch(self,args=None):
  self.validated=False;actual=runner.validate_configuration
  def validate(cfg):
   result=actual(cfg);self.validated=True;return result
  with ExitStack() as stack:
   for key,val in [('ROOT',self.root),('W',self.w),('OUT',self.out)]:stack.enter_context(patch.object(runner,key,val))
   stack.enter_context(patch.object(sys,'argv',['task11_b3_continuation_window.py',*(args or runner.TUPLE)]));stack.enter_context(patch.object(runner.os,'environ',Environment()));stack.enter_context(patch.object(runner,'validate_configuration',validate))
   launch=stack.enter_context(patch.object(runner.shared.subprocess,'Popen',side_effect=AssertionError('must not launch')));run=stack.enter_context(patch.object(runner.shared.subprocess,'run',side_effect=AssertionError('must not launch')))
   try:runner.run_window()
   finally:
    if launch.called or run.called or (self.out/runner.TUPLE[0]).exists():raise SideEffectViolation("invalid dispatch consumed a side effect")
 def rejected(self,args=None):
  try:self.dispatch(args)
  except EnvironmentBoundary:self.fail('invalid configuration reached environment before rejection')
  except AssertionError:return
  self.fail('invalid configuration was accepted')
 def test_valid_exact_tuple_validates_before_environment(self):
  with self.assertRaises(EnvironmentBoundary):self.dispatch()
  self.assertTrue(self.validated)
 def test_missing_or_expired_release_is_rejected(self):
  self.release_path.unlink();self.rejected();self.save();self.release['startBeforeUtc']=(datetime.datetime.now(datetime.timezone.utc)-datetime.timedelta(minutes=1)).isoformat();self.save();self.rejected()
 def test_wrong_release_tuple_hash_and_expanded_effects_rejected(self):
  for key,val in [('selector','WrongIT'),('configurationAckSha256','0'*64),('sourceCheckpointSha256','0'*64),('maxInvocations',2),('oneGrantAuthorized',True),('nativeStartAuthorized',True),('sendAuthorized',True)]:
   with self.subTest(key=key):
    old=self.release[key];self.release[key]=val;self.save();self.rejected();self.release[key]=old
  self.release['budget']={**self.cfg['budget'],'grants':1};self.save();self.rejected()
 def test_changed_source_and_adopted_evidence_rejected(self):
  self.source.write_text('tampered\n');self.rejected();self.source.write_text('inert\n');(self.w/self.cfg['inputPins']['sourceReview']['path']).write_text('tampered\n');self.rejected()
 def test_consumed_second_diagnostic_window_cannot_be_dispatched(self):self.rejected(('b3-budget-diagnostic-02','it','Task11B3BudgetDiagnosticIT',runner.TUPLE[3]))
 def test_consumed_diagnostic_window_cannot_be_dispatched(self):self.rejected(('b3-budget-diagnostic-01','it','Task11B3ContinuationIT',runner.TUPLE[3]))
 def test_other_window_selector_cannot_be_dispatched(self):self.rejected(('b2-exact-retry-readback-01','it','Task11B2ExactRetryReadbackIT',runner.TUPLE[3]))
if __name__=='__main__':unittest.main()