"""Exact newly-owned process group cleanup; no DB/network or retained process capability."""
import contextlib,json,os,pathlib,signal,subprocess,sys,tempfile,time,unittest
from unittest.mock import Mock,patch
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
import task11_b3_window as runner
class CleanupTests(unittest.TestCase):
 def child(self):
  child=Mock();child.pid=987654;child.poll.return_value=0;child.wait.return_value=0;return child
 def run_mock(self,child,members,kill):
  with patch.object(runner,'live_group_members',side_effect=members,create=True),patch.object(runner.os,'killpg',side_effect=kill),patch.object(runner,'GROUP_TERM_SECONDS',.01,create=True),patch.object(runner,'GROUP_KILL_SECONDS',.01,create=True),patch.object(runner.time,'sleep',return_value=None):return runner.stop_owned_group(child)
 def test_exited_leader_still_terminates_its_live_descendants(self):
  state=[{'pid':987655,'startTicks':'123','state':'S'}];sent=[]
  def kill(group,sig):self.assertEqual(987654,group);sent.append(sig);state.clear()
  custody=self.run_mock(self.child(),lambda group:list(state),kill);self.assertEqual([signal.SIGTERM],sent);self.assertTrue(custody['quiescent']);self.assertEqual([],custody['remainingLiveMembers'])
 def test_empty_group_is_confirmed_without_signalling(self):
  sent=[];custody=self.run_mock(self.child(),lambda group:[],lambda group,sig:sent.append(sig));self.assertEqual([],sent);self.assertTrue(custody['quiescent'])
 def test_term_ignoring_descendant_gets_kill_after_leader_exited(self):
  state=[{'pid':987655,'startTicks':'123','state':'S'}];sent=[]
  def kill(group,sig):sent.append(sig);self.assertEqual(987654,group);state.clear() if sig==signal.SIGKILL else None
  custody=self.run_mock(self.child(),lambda group:list(state),kill);self.assertEqual([signal.SIGTERM,signal.SIGKILL],sent);self.assertTrue(custody['quiescent'])
 def test_signal_exit_race_is_confirmed(self):
  state=[{'pid':987655,'startTicks':'123','state':'S'}]
  def kill(group,sig):state.clear();raise ProcessLookupError()
  custody=self.run_mock(self.child(),lambda group:list(state),kill);self.assertTrue(custody['quiescent'])
 def test_unconfirmed_group_fails_with_honest_custody(self):
  with self.assertRaises(RuntimeError) as caught:self.run_mock(self.child(),lambda group:[{'pid':987655,'startTicks':'123','state':'S'}],lambda group,sig:None)
  self.assertFalse(caught.exception.custody['quiescent']);self.assertEqual(1,len(caught.exception.custody['remainingLiveMembers']))
 def test_interrupt_during_term_does_not_skip_owned_group_cleanup(self):
  state=[{'pid':987655,'startTicks':'123','state':'S'}];sent=[]
  def kill(group,sig):
   sent.append(sig)
   if sig==signal.SIGTERM:raise KeyboardInterrupt()
   state.clear()
  with self.assertRaises(KeyboardInterrupt) as caught:self.run_mock(self.child(),lambda group:list(state),kill)
  self.assertEqual([signal.SIGTERM,signal.SIGKILL],sent);self.assertTrue(caught.exception.custody['quiescent'])
 def test_live_leader_with_foreign_group_is_never_signalled(self):
  child=self.child();child.poll.return_value=None;signals=[]
  with patch.object(runner.os,'getpgid',return_value=child.pid+1):
   with self.assertRaises(AssertionError):self.run_mock(child,lambda group:[],lambda group,sig:signals.append(sig))
  self.assertEqual([],signals)
 def test_actual_main_installs_term_exception_and_restores_handler(self):
  previous=signal.getsignal(signal.SIGTERM);seen=[]
  def run(lock):
   try:signal.raise_signal(signal.SIGTERM)
   finally:seen.append('producer-finally')
  with patch.object(runner,'exclusive_runner_lock',return_value=contextlib.nullcontext({})),patch.object(runner,'run_window',side_effect=run):
   with self.assertRaises(InterruptedError):runner.main()
  self.assertEqual(['producer-finally'],seen);self.assertEqual(previous,signal.getsignal(signal.SIGTERM))
 def observation_interrupt(self,after_term,kind):
  state=[{'pid':987655,'startTicks':'123','state':'S'}];sent=[];raised=False
  def members(group):
   nonlocal raised
   if not raised and (not after_term or signal.SIGTERM in sent):raised=True;raise kind()
   return list(state)
  def kill(group,sig):sent.append(sig);state.clear() if sig==signal.SIGKILL else None
  with self.assertRaises(kind) as caught:self.run_mock(self.child(),members,kill)
  self.assertEqual([signal.SIGTERM,signal.SIGKILL],sent);self.assertTrue(caught.exception.custody['quiescent'])
 def test_first_inventory_interrupt_is_delayed_until_group_is_gone(self):
  for kind in (KeyboardInterrupt,InterruptedError):
   with self.subTest(kind=kind.__name__):self.observation_interrupt(False,kind)
 def test_post_term_inventory_interrupt_still_reaches_kill(self):
  for kind in (KeyboardInterrupt,InterruptedError):
   with self.subTest(kind=kind.__name__):self.observation_interrupt(True,kind)
 def test_wait_sleep_interrupt_still_reaches_kill_and_rethrows(self):
  state=[{'pid':987655,'startTicks':'123','state':'S'}];sent=[]
  def kill(group,sig):sent.append(sig);state.clear() if sig==signal.SIGKILL else None
  with patch.object(runner.time,'sleep',side_effect=InterruptedError()):
   with self.assertRaises(InterruptedError) as caught:
    # Do not use run_mock, whose sleep patch would hide this exact interruption.
    with patch.object(runner,'live_group_members',side_effect=lambda group:list(state)),patch.object(runner.os,'killpg',side_effect=kill),patch.object(runner,'GROUP_TERM_SECONDS',.01),patch.object(runner,'GROUP_KILL_SECONDS',.01):runner.stop_owned_group(self.child())
  self.assertEqual([signal.SIGTERM,signal.SIGKILL],sent);self.assertTrue(caught.exception.custody['quiescent'])
 def test_reap_interrupt_has_confirmed_group_custody_and_is_rethrown(self):
  child=self.child();child.wait.side_effect=[InterruptedError(),0]
  with self.assertRaises(InterruptedError) as caught:self.run_mock(child,lambda group:[],lambda group,sig:None)
  self.assertTrue(caught.exception.custody['quiescent'])
 def test_real_term_signal_during_inventory_is_deferred_and_handler_restored(self):
  previous=signal.signal(signal.SIGTERM,runner.stop_signal);state=[{'pid':987655,'startTicks':'123','state':'S'}];sent=[];raised=False
  def members(group):
   nonlocal raised
   if not raised:raised=True;signal.raise_signal(signal.SIGTERM)
   return list(state)
  def kill(group,sig):sent.append(sig);state.clear() if sig==signal.SIGKILL else None
  try:
   with self.assertRaises(InterruptedError) as caught:self.run_mock(self.child(),members,kill)
   self.assertEqual([signal.SIGTERM,signal.SIGKILL],sent);self.assertTrue(caught.exception.custody['quiescent']);self.assertIs(runner.stop_signal,signal.getsignal(signal.SIGTERM))
  finally:signal.signal(signal.SIGTERM,previous)
 def test_leader_identity_interrupt_retries_before_signalling_owned_group(self):
  child=self.child();alive=True;child.poll.side_effect=lambda:None if alive else 0
  def reap(timeout):
   nonlocal alive
   alive=False;return 0
  child.wait.side_effect=reap;state=[{'pid':987655,'startTicks':'123','state':'S'}];sent=[]
  def kill(group,sig):sent.append(sig);state.clear() if sig==signal.SIGKILL else None
  with patch.object(runner.os,'getpgid',side_effect=[InterruptedError(),child.pid]):
   with self.assertRaises(InterruptedError) as caught:self.run_mock(child,lambda group:list(state),kill)
  self.assertEqual([signal.SIGTERM,signal.SIGKILL],sent);self.assertTrue(caught.exception.custody['quiescent'])
 def test_leader_exit_between_poll_and_group_identity_keeps_descendant_cleanup(self):
  child=self.child();checks=0
  def poll():
   nonlocal checks
   checks+=1;return None if checks==1 else 0
  child.poll.side_effect=poll;state=[{'pid':987655,'startTicks':'123','state':'S'}];sent=[]
  def kill(group,sig):sent.append(sig);state.clear()
  with patch.object(runner.os,'getpgid',side_effect=ProcessLookupError()):custody=self.run_mock(child,lambda group:list(state),kill)
  self.assertEqual([signal.SIGTERM],sent);self.assertTrue(custody['quiescent'])
 def isolated_group(self,leader_exits_first):
  with tempfile.TemporaryDirectory(prefix='b3-owned-cleanup-') as temp:
   ready=pathlib.Path(temp)/'ready.json'
   descendant="import os,signal,time; signal.signal(signal.SIGTERM,signal.SIG_IGN); print(os.getpid(),flush=True); time.sleep(60)"
   program="import pathlib,subprocess,sys,time,json; c=subprocess.Popen([sys.executable,'-c',sys.argv[1]],stdout=subprocess.PIPE,text=True); pid=int(c.stdout.readline()); pathlib.Path(sys.argv[2]).write_text(json.dumps({'pid':pid})); time.sleep(60)"
   child=subprocess.Popen([sys.executable,'-c',program,descendant,str(ready)],start_new_session=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
   try:
    deadline=time.monotonic()+3
    while not ready.exists() and time.monotonic()<deadline:time.sleep(.01)
    self.assertTrue(ready.exists());pid=json.loads(ready.read_text())['pid']
    if leader_exits_first:child.terminate();child.wait(timeout=2)
    with patch.object(runner,'GROUP_TERM_SECONDS',.1,create=True),patch.object(runner,'GROUP_KILL_SECONDS',2,create=True):custody=runner.stop_owned_group(child)
    proc=pathlib.Path('/proc')/str(pid)/'stat';live=proc.exists() and proc.read_text().split(') ',1)[1].split()[0] not in ('Z','X');self.assertFalse(live,'Owned descendant survived even though group leader exited');self.assertTrue(custody['quiescent']);self.assertIsNotNone(child.poll())
   finally:
    with contextlib.suppress(ProcessLookupError):os.killpg(child.pid,signal.SIGKILL)
    child.wait(timeout=3)
 def test_real_isolated_group_leader_already_exited(self):self.isolated_group(True)
 def test_real_isolated_group_leader_exits_on_term(self):self.isolated_group(False)
if __name__=='__main__':unittest.main()