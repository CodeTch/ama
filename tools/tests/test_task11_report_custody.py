import importlib.util, json, pathlib, subprocess, sys, tempfile, threading, time, unittest
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
import task11_report_custody as c

class CustodyTests(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=pathlib.Path(self.temp.name)
  self.report=self.root/'TEST-example.xml';self.log=self.root/'run.log';self.log.write_text('Running example.Case\n')
  self.started=time.time_ns();self.child=time.monotonic_ns()
 def wire(self,suite='example.Case',tag='case'):
  return f'<testsuite name="{suite}" tests="1" failures="0" errors="0" skipped="0"><testcase classname="{suite}" name="{tag}"/></testsuite>'
 def collect(self,**kw):
  return c.collect_reports(lambda:list(self.root.glob('TEST-*.xml')),self.log,'example.Case',self.started,self.child,timeout=.25,interval=.015,**kw)
 def test_late_stable_report_uses_observed_collection_close(self):
  writer=threading.Thread(target=lambda:(time.sleep(.045),self.report.write_text(self.wire())))
  writer.start();result=self.collect();writer.join()
  self.assertTrue(result['qualified']);self.assertGreater(result['closedMonotonicNs'],self.child)
  self.assertGreaterEqual(result['closedNs'],result['reports'][0]['mtimeNs']);self.assertGreaterEqual(result['stableObservations'],2)
 def test_old_report_residue_rejected(self):
  self.report.write_text(self.wire());import os;os.utime(self.report,ns=(self.started-100,self.started-100))
  result=self.collect();self.assertFalse(result['qualified']);self.assertIn('outside-observed-wall-interval',result['reason'])
 def test_wrong_selector_rejected(self):
  self.report.write_text(self.wire('other.Case'));result=self.collect()
  self.assertFalse(result['qualified']);self.assertIn('selector',result['reason'])
 def test_continuously_changing_report_rejected(self):
  stop=threading.Event()
  def mutate():
   n=0
   while not stop.is_set():
    self.report.write_text(self.wire(tag=str(n)));n+=1;time.sleep(.003)
  writer=threading.Thread(target=mutate);writer.start()
  try:result=self.collect()
  finally:stop.set();writer.join()
  self.assertFalse(result['qualified']);self.assertEqual('settle-timeout',result['reason'])
 def test_missing_report_times_out(self):
  result=self.collect();self.assertFalse(result['qualified']);self.assertEqual('settle-timeout',result['reason'])
 def test_competing_lock_rejected(self):
  lock=self.root/'runner.lock'
  with c.exclusive_runner_lock(lock,{'selector':'example.Case'}) as identity:
   script="import sys;sys.path.insert(0,sys.argv[1]);import task11_report_custody as c;from pathlib import Path;ctx=c.exclusive_runner_lock(Path(sys.argv[2]),{});ctx.__enter__()"
   proc=subprocess.run([sys.executable,'-c',script,str(pathlib.Path(c.__file__).parent),str(lock)],capture_output=True,text=True)
   self.assertNotEqual(0,proc.returncode);self.assertIn('runner lock already held',proc.stderr);self.assertEqual(identity['pid'],__import__('os').getpid())
  with c.exclusive_runner_lock(lock,{}) as following:self.assertNotEqual(identity['runId'],following['runId'])

if __name__=='__main__':unittest.main(verbosity=2)
