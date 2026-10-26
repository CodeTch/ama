"""Bounded, exclusive report custody for future Task11 windows only."""
import contextlib, fcntl, hashlib, json, os, re, time, uuid
import xml.etree.ElementTree as ET

@contextlib.contextmanager
def exclusive_runner_lock(path, metadata):
 # Keep this inode: unlinking a flock file would permit a second lock namespace.
 with path.open('a+') as handle:
  try: fcntl.flock(handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
  except BlockingIOError: raise RuntimeError('runner lock already held') from None
  identity=dict(metadata,pid=os.getpid(),runId=str(uuid.uuid4()),lockPath=str(path.resolve()),lockDevice=os.fstat(handle.fileno()).st_dev,lockInode=os.fstat(handle.fileno()).st_ino,acquiredNs=time.time_ns(),acquiredMonotonicNs=time.monotonic_ns())
  handle.seek(0);handle.truncate();json.dump(identity,handle);handle.flush();os.fsync(handle.fileno())
  try: yield identity
  finally: fcntl.flock(handle.fileno(),fcntl.LOCK_UN)

def _snapshot(paths):
 rows=[]
 for path in sorted(paths):
  before=path.stat();raw=path.read_bytes();after=path.stat()
  fields=lambda s:(s.st_dev,s.st_ino,s.st_size,s.st_mtime_ns,s.st_ctime_ns)
  if fields(before)!=fields(after) or len(raw)!=after.st_size:raise ValueError('report changed during read')
  rows.append(dict(path=str(path),device=after.st_dev,inode=after.st_ino,size=after.st_size,mtimeNs=after.st_mtime_ns,ctimeNs=after.st_ctime_ns,sha256=hashlib.sha256(raw).hexdigest(),raw=raw))
 return rows

def collect_reports(paths, log, canonical, started_ns, child_exit_monotonic_ns, timeout=5, interval=.1):
 assert timeout>0 and 0<interval<timeout
 opened=time.monotonic_ns();assert opened>=child_exit_monotonic_ns
 deadline=opened+int(timeout*1e9);prior=None;stable=0;observations=0;last_error=None; samples=[]
 while time.monotonic_ns()<deadline:
  observations+=1
  try:
   rows=_snapshot(paths());signature=[{k:v for k,v in row.items() if k!='raw'} for row in rows]
   stable=stable+1 if rows and signature==prior else 1 if rows else 0
   prior=signature
   samples=(samples+[dict(observedMonotonicNs=time.monotonic_ns(),reports=signature)])[-2:] if stable>1 else [dict(observedMonotonicNs=time.monotonic_ns(),reports=signature)]
   if stable>=2:
    trees=[ET.fromstring(row['raw']) for row in rows]
    log_raw=log.read_bytes();log_text=log_raw.decode(errors='replace')
    reason=None
    if len(rows)!=1 or trees[0].get('name')!=canonical:reason='wrong-selector-or-report-count'
    elif len(re.findall(r'Running\s+'+re.escape(canonical)+r'(?=\s|$)',log_text))!=1:reason='canonical-log-association'
    elif any(c.get('classname')!=canonical for c in trees[0].findall('testcase')):reason='wrong-case-selector'
    elif len(trees[0].findall('testcase'))!=int(trees[0].get('tests','0')) or int(trees[0].get('tests','0'))==0:reason='case-count-mismatch'
    # Re-observe exactly the bytes to be sealed. Wall-clock anomalies are not repaired.
    if _snapshot(paths())!=rows or log.read_bytes()!=log_raw:
     stable=0;prior=None;continue
    closed=time.time_ns();closed_mono=time.monotonic_ns()
    if closed_mono>=deadline:break
    if any(not started_ns<=row['mtimeNs']<=closed for row in rows):reason='outside-observed-wall-interval'
    return dict(qualified=reason is None,reason=reason,reports=rows,closedNs=closed,closedMonotonicNs=closed_mono,openedMonotonicNs=opened,stableObservations=stable,stableSamples=samples,observations=observations,logSha256=hashlib.sha256(log_raw).hexdigest())
  except (OSError,ValueError,ET.ParseError) as exc:
   last_error=type(exc).__name__;stable=0;prior=None
  remaining=(deadline-time.monotonic_ns())/1e9
  if remaining>0:time.sleep(min(interval,remaining))
 return dict(qualified=False,reason='settle-timeout',reports=[],closedNs=time.time_ns(),closedMonotonicNs=time.monotonic_ns(),openedMonotonicNs=opened,stableObservations=stable,observations=observations,lastObservationError=last_error)
