"""Actual loopback CAS/API/WebSocket load transport. Synthetic PCM is not speech quality evidence."""
from __future__ import annotations

import asyncio
from contextlib import closing
import hashlib
import http.client
import json
import math
import os
from pathlib import Path
import secrets
import struct
import time
import urllib.parse
import uuid

ROOT=Path(__file__).resolve().parents[2]

def configured_pool_maximum(state,api_env):
    # local_app writes the explicit local setting into api.properties. Its
    # isolation boundary deliberately removes inherited Spring environment.
    value=state.get('databasePoolMaximum',20)
    if type(value) is not int or not 1<=value<=64:raise ValueError('Invalid owned local pool configuration')
    return value

def pcm_bytes(run,actor,seq,samples):
    if type(samples) is not int or not 1<=samples<=32000:raise ValueError("Bounded PCM required")
    return hashlib.shake_256(f"ama.load-pcm.v1:{run}:{actor}:{seq}".encode()).digest(samples*2)

def encode_frame(part,epoch,seq,start,pcm):
    if any(type(v) is not int or v<minimum or v>2**63-1 for v,minimum in [(epoch,1),(seq,1),(start,0)]) or not isinstance(pcm,bytes) or not 2<=len(pcm)<=64000 or len(pcm)%2:raise ValueError("Bounded recording frame required")
    return b"AMA1"+uuid.UUID(part).bytes+struct.pack(">QQQI",epoch,seq,start,len(pcm)//2)+hashlib.sha256(pcm).digest()+pcm

class HttpFailure(Exception):
    def __init__(self,status,method,path):
        super().__init__(f"HTTP {status}: {method} {path}");self.status=status;self.method=method;self.path=path

class Http:
    def __init__(self,origin,api=None):
        self.origin=origin;self.api=api or origin;self.cookie=None;self.csrf=None;self.actor=None
    def connection(self,origin=None,timeout=20):
        url=urllib.parse.urlsplit(origin or self.api)
        if url.scheme!="http" or url.hostname!="127.0.0.1" or url.path not in ("","/") or url.username or url.query or url.fragment:raise ValueError("Explicit loopback harness/API origin required")
        return http.client.HTTPConnection(url.hostname,url.port,timeout=timeout)
    def request(self,method,path,body=None,*,expected=200,headers=None,origin=None):
        h={"Origin":self.origin,"Accept":"application/json"}
        if self.cookie:h["Cookie"]=self.cookie
        if self.csrf:h["X-CSRF-Token"]=self.csrf
        if isinstance(body,(dict,list)):
            body=json.dumps(body,separators=(",",":")).encode();h["Content-Type"]="application/json"
        if headers:h.update(headers)
        with closing(self.connection(origin)) as conn:
            conn.request(method,path,body=body,headers=h);response=conn.getresponse();raw=response.read(1048577)
            if len(raw)>1048576:raise ValueError("Bounded HTTP response required")
            if response.status!=expected:
                failure=HttpFailure(response.status,method,path)
                try:
                    value=json.loads(raw);error=value.get("error") if isinstance(value,dict) else None
                    code=value.get("code") if isinstance(value,dict) else None
                    if code is None and isinstance(error,dict):code=error.get("code")
                    failure.business_code=code if isinstance(code,str) and 0<len(code)<=64 and all(c in "ABCDEFGHIJKLMNOPQRSTUVWXYZ_0123456789" for c in code) else None
                except (ValueError,TypeError):failure.business_code=None
                if response.status==401 and self.actor:failure.clock_diagnostic=self.clock_diagnostic()
                raise failure
            return response.getheaders(),json.loads(raw) if raw and "application/json" in response.getheader("Content-Type","") else raw
    def login(self,username,password):
        self.actor=username
        headers,_=self.request("POST","/auth/cas/login",urllib.parse.urlencode({"username":username,"password":password}).encode(),expected=303,headers={"Content-Type":"application/x-www-form-urlencoded"},origin=self.origin)
        cookies=[v.split(";",1)[0] for k,v in headers if k.lower()=="set-cookie" and v.startswith("__Host-AMA_SESSION=")]
        if len(cookies)!=1:raise ValueError("Real CAS did not issue an AMA session")
        self.cookie=cookies[0];_,session=self.request("GET","/api/v1/session")
        if session.get("actor",{}).get("enterpriseId")!=username:raise ValueError("CAS identity mismatch")
        self.csrf=session["csrfToken"]
    def clock_diagnostic(self):
        """Owned test oracle: timestamps/status only; never emits session/CSRF tokens or database secrets."""
        import datetime,re,subprocess,sys
        if not self.actor or not re.fullmatch(r"load-[0-9a-f]{12}-[rb][0-9]{3}",self.actor):return None
        sys.path.insert(0,str(ROOT/"tools/dev"));import local_app
        state=json.loads((local_app.RUNTIME/"state.json").read_text())
        if state.get("owner")!=local_app.OWNER:return {"error":"OWNER_CHANGED"}
        sql="SELECT UTC_TIMESTAMP(6),i.status,i.session_generation,s.session_generation,s.issued_at,s.expires_at,s.revoked_at IS NOT NULL,s.issued_at>UTC_TIMESTAMP(6),TIMESTAMPDIFF(MICROSECOND,UTC_TIMESTAMP(6),s.issued_at) FROM identity_session s JOIN enterprise_identity i ON i.enterprise_id=s.enterprise_id WHERE s.enterprise_id='"+self.actor+"' ORDER BY s.issued_at DESC LIMIT 2"
        env=os.environ.copy();env["MYSQL_PWD"]=state["databasePassword"]
        started=datetime.datetime.now(datetime.timezone.utc).isoformat()
        result=subprocess.run(["docker","exec","-e","MYSQL_PWD",local_app.NAME,"mysql","--user=ama_local","--database=ama_local_dev","-N","-B","-e",sql],env=env,capture_output=True,text=True,timeout=10)
        return dict(actor=self.actor,clientUtc=started,rows=result.stdout.splitlines() if result.returncode==0 else [],oracleSucceeded=result.returncode==0)
    def json(self,method,path,body=None,**kw):return self.request(method,path,body,**kw)[1]
    def verify_archive(self,session,proofs):
        path=f"/api/v1/recordings/{session['sessionId']}/audio";whole=hashlib.sha256();verified=0;samples=sum(p["samples"] for p in proofs);size=44+samples*2
        with closing(self.connection(timeout=120)) as conn:
            conn.request("GET",path,headers={"Cookie":self.cookie,"Origin":self.origin});response=conn.getresponse()
            if response.status!=200 or response.getheader("Content-Length")!=str(size):raise ValueError("Archive unavailable or wrong size")
            header=response.read(44);whole.update(header)
            if len(header)!=44 or header[:4]!=b"RIFF" or header[8:16]!=b"WAVEfmt " or header[36:40]!=b"data" or struct.unpack_from("<I",header,24)[0]!=16000 or struct.unpack_from("<I",header,40)[0]!=samples*2:raise ValueError("Wrong canonical archive header")
            for proof in proofs:
                pcm=response.read(proof["samples"]*2);whole.update(pcm)
                if len(pcm)!=proof["samples"]*2 or hashlib.sha256(pcm).hexdigest()!=proof["sha256"]:raise ValueError("Acknowledged PCM differs from archive")
                verified+=1
            if response.read(1):raise ValueError("Archive has extra bytes")
        return {"verifiedChunks":verified,"hashMatched":True,"wavSha256":whole.hexdigest(),"wavBytes":size}

class OwnedApiFault:
    """Exact owned process/binary/environment only. Private environment never leaves this object."""
    def __init__(self):self.saved=None;self.events=[]
    def stop(self):
        import sys,signal
        sys.path.insert(0,str(ROOT/"tools/dev"));import local_app
        state=json.loads((local_app.RUNTIME/"state.json").read_text());role=state["backend"]
        if state.get("owner")!=local_app.OWNER or not local_app.alive(role):raise ValueError("Owned API process required")
        pid=role["pid"];args=Path(f"/proc/{pid}/cmdline").read_bytes().rstrip(b"\0").split(b"\0");env=Path(f"/proc/{pid}/environ").read_bytes().rstrip(b"\0").split(b"\0")
        self.saved=([x.decode() for x in args],dict(x.decode().split("=",1) for x in env));os.killpg(pid,signal.SIGKILL)
        self.events.append(dict(kind="API_SIGKILL",monotonic=time.monotonic(),pid=pid))
        deadline=time.monotonic()+5
        while local_app.alive(role) and time.monotonic()<deadline:time.sleep(.05)
        if local_app.alive(role):raise ValueError("Owned API did not stop")
    def restore(self):
        if self.saved is None:return
        import sys
        sys.path.insert(0,str(ROOT/"tools/dev"));import local_app
        state=json.loads((local_app.RUNTIME/"state.json").read_text())
        if state.get("owner")!=local_app.OWNER:raise ValueError("Runtime owner changed")
        if not local_app.alive(state["backend"]):
            state["backend"]=local_app.launch(*self.saved,"backend.log");local_app.save(local_app.RUNTIME/"state.json",state)
        self.saved=None;self.events.append(dict(kind="API_RESTARTED_SAME_BINARY",monotonic=time.monotonic(),pid=state["backend"]["pid"]))

class Transport:
    def __init__(self,scenario,output,origin="http://127.0.0.1:5174",api="http://127.0.0.1:8182",harness="http://127.0.0.1:8792",api_fault=False):
        self.scenario=scenario;self.output=Path(output);self.origin=origin;self.api=api;self.harness=harness;self.run=secrets.token_hex(6);self.api_fault=api_fault
        self.http_slots=asyncio.Semaphore(16);self.audit=None;self.fault=OwnedApiFault();self.resources=[];self.events=[];self.ready=asyncio.Event();self.epoch=0
    def event(self,kind,**fields):
        value={"kind":kind,"monotonic":time.monotonic(),**fields}
        if self.audit:self.audit.write(json.dumps(value,separators=(",",":"))+"\n");self.audit.flush()
    def freeze_worker(self):
        import shutil,sys
        sys.path.insert(0,str(ROOT/"tools/dev"));import local_app
        state=json.loads((local_app.RUNTIME/"state.json").read_text());role=state["worker"]
        if state.get("owner")!=local_app.OWNER or not local_app.alive(role):raise ValueError("Owned running worker required")
        pid=role["pid"];args=[v.decode() for v in Path(f"/proc/{pid}/cmdline").read_bytes().rstrip(b"\0").split(b"\0")];env=dict(v.decode().split("=",1) for v in Path(f"/proc/{pid}/environ").read_bytes().rstrip(b"\0").split(b"\0"))
        position=args.index("-cp")+1;entries=args[position].split(":");classes=Path(entries[0]).resolve()
        if not classes.is_relative_to(ROOT) or not(classes/"ama/worker/TranscriptWorkerMain.class").is_file():raise ValueError("Owned worker class directory required")
        frozen=self.output.resolve()/"worker-classes";shutil.copytree(classes,frozen);digest=hashlib.sha256()
        for path in sorted(frozen.rglob("*")):
            if path.is_file():digest.update(str(path.relative_to(frozen)).encode());digest.update(path.read_bytes())
        # The API's content-addressed exploded JAR is already immutable. Snapshot the worker's
        # target/classes so later Maven compilation cannot alter a live load's class loading.
        args[position]=":".join([str(frozen),*entries[1:]]);local_app.stop_process(role)
        state["worker"]=local_app.launch(args,env,"worker.log");local_app.save(local_app.RUNTIME/"state.json",state)
        self.execution=dict(amaDeployedSourceSignature=state["sourceSignature"],workerClassesSha256=digest.hexdigest(),workerPid=state["worker"]["pid"],backendPid=state["backend"]["pid"])
        api_env=dict(v.decode().split("=",1) for v in Path(f"/proc/{state['backend']['pid']}/environ").read_bytes().rstrip(b"\0").split(b"\0"))
        self.execution["apiPoolMaximumConfigured"]=configured_pool_maximum(state,api_env)
        self.execution["apiPoolConnectionTimeoutMillis"]=1000
        driver=hashlib.sha256();sources=self.output/"driver-sources";sources.mkdir()
        for name in ("transport.py","report.py","recording_load.py","requirements.txt"):
            raw=(ROOT/"tools/load"/name).read_bytes();(sources/name).write_bytes(raw);driver.update(name.encode());driver.update(raw)
        scenario=json.dumps(self.scenario,sort_keys=True,separators=(",",":")).encode();(sources/"scenario.json").write_bytes(scenario)
        self.execution.update(loadDriverSha256=driver.hexdigest(),scenarioSha256=hashlib.sha256(scenario).hexdigest())
        (self.output/"execution.json").write_text(json.dumps(self.execution,indent=2))
    def freeze_file_service(self):
        import shutil,sys
        sys.path.insert(0,str(ROOT/"tools/dev"));import local_app
        state=json.loads((local_app.RUNTIME/"state.json").read_text());role=state["files"]
        if state.get("owner")!=local_app.OWNER or not local_app.alive(role):raise ValueError("Owned file process required")
        pid=role["pid"];args=[v.decode() for v in Path(f"/proc/{pid}/cmdline").read_bytes().rstrip(b"\0").split(b"\0")];env=dict(v.decode().split("=",1) for v in Path(f"/proc/{pid}/environ").read_bytes().rstrip(b"\0").split(b"\0"))
        position=args.index("-jar")+1;jar=Path(args[position]).resolve()
        if not jar.is_relative_to(ROOT) or not jar.is_file():raise ValueError("Owned immutable file executable required")
        frozen=self.output/"file-service-exec.jar";shutil.copyfile(jar,frozen);frozen.chmod(0o600);digest=hashlib.sha256(frozen.read_bytes()).hexdigest();args[position]=str(frozen)
        local_app.stop_process(role);state["files"]=local_app.launch(args,env,"files.log");local_app.save(local_app.RUNTIME/"state.json",state)
        self.execution.update(fileExecutableSha256=digest,filePid=state["files"]["pid"],fileWorkerThreadsConfigured=state.get("fileWorkerThreads",16),fileHeapMaximumMiB=192 if state.get("fileWorkerThreads",16)>16 else 96)
        (self.output/"execution.json").write_text(json.dumps(self.execution,indent=2))
    async def http(self,client,method,path,body=None,**kw):
        async with self.http_slots:return await asyncio.to_thread(client.json,method,path,body,**kw)
    async def setup(self,index,recorder):
        username=f"load-{self.run}-{'r' if recorder else 'b'}{index:03d}";password=secrets.token_urlsafe(24)
        admin=Http(self.harness)
        await self.http(admin,"POST","/admin/api/cas/users",dict(username=username,password=password,displayName=f"协议负载 {index:03d}",department="本地协议验证",attributes={"fixture":"ama.recording-load.v2"}),expected=201)
        client=Http(self.origin,self.api)
        async with self.http_slots:await asyncio.to_thread(client.login,username,password)
        meeting=await self.http(client,"POST","/api/v1/meetings",{},expected=201,headers={"Idempotency-Key":str(uuid.uuid4())})
        value=dict(recorderId=username,meetingId=meeting["id"],errors=0,transientErrors=0,sentChunks=0,ackedChunks=0,verifiedChunks=0,sampleCount=0,hashMatched=False,latenciesMs=[],queueAgesMs=[],observedSeconds=0,proofs=[],client=client)
        if recorder:
            session=await self.http(client,"POST",f"/api/v1/meetings/{meeting['id']}/recordings",expected=201)
            value.update(sessionId=session["sessionId"],session=session,collectorId=str(uuid.uuid4()))
        else:value.update(userId=username,requests=0,initialPhaseSeconds=(index-self.scenario["recorders"])/max(1,self.scenario["totalUsers"]-self.scenario["recorders"])*15)
        self.event("CAS_USER_READY",actor=username,meetingId=meeting["id"],recorder=recorder);return value
    async def socket(self,r):
        from websockets.asyncio.client import connect
        client=r["client"];path=f"/api/v1/recordings/{r['sessionId']}"
        current=await self.http(client,"GET",path)
        claimed=await self.http(client,"POST",path+"/writer-lease",dict(expectedEpoch=current["writerEpoch"],collectorId=r["collectorId"],requestId=str(uuid.uuid4())))
        if claimed["sessionId"]!=r["sessionId"] or claimed["partId"]!=r["session"]["partId"]:raise ValueError("Recording scope changed")
        r["session"]=claimed;r["renewed"]=time.monotonic()
        ws=await connect(self.api.replace("http:","ws:")+path+"/stream",origin=self.origin,subprotocols=["ama.recording.v1","ama.csrf."+client.csrf],additional_headers={"Cookie":client.cookie},proxy=None,compression=None,open_timeout=15,close_timeout=2,max_size=8192,max_queue=2)
        try:
            if ws.subprotocol!="ama.recording.v1":raise ValueError("Recording protocol not negotiated")
            await ws.send(json.dumps(dict(type="HELLO",collectorId=r["collectorId"],writerEpoch=claimed["writerEpoch"])))
            ready=json.loads(await asyncio.wait_for(ws.recv(),15))
            if ready.get("type")=="ERROR" and ready.get("code") in ("RECORDING_UNAVAILABLE","RATE_LIMITED","RETRY_LATER","HELLO_REQUIRED"):
                failure=ConnectionError("Temporary recording handshake unavailable");failure.server_code=ready["code"];raise failure
            if ready!={"type":"READY","sessionId":r["sessionId"],"partId":claimed["partId"],"writerEpoch":claimed["writerEpoch"]}:raise ValueError("Wrong READY scope")
            return ws
        except BaseException:await ws.close();raise
    async def prime(self,r):
        from websockets.exceptions import InvalidStatus
        deadline=time.monotonic()+90
        while True:
            try:r["_socket"]=await self.socket(r);return
            except (OSError,TimeoutError,HttpFailure) as failure:
                if isinstance(failure,HttpFailure) and failure.status not in (409,429,500,502,503,504):raise
                r["transientErrors"]+=1;self.event("CONNECTION_START_TRANSIENT",actor=r["recorderId"],status=getattr(failure,"status",None),businessCode=getattr(failure,"business_code",None),path=getattr(failure,"path",None),errorType=type(failure).__name__)
                if time.monotonic()>=deadline:raise
                await asyncio.sleep(.5)
            except InvalidStatus as failure:
                status=failure.response.status_code
                if status not in (429,500,502,503,504):raise
                r["transientErrors"]+=1;self.event("CONNECTION_START_TRANSIENT",actor=r["recorderId"],status=status,errorType=type(failure).__name__)
                if time.monotonic()>=deadline:raise
                await asyncio.sleep(.5)
    async def produce(self,r,queue):
        duration=self.scenario["durationSeconds"];step=self.scenario["chunkMs"]/1000;count=math.ceil(duration/step);position=0
        try:
            for index in range(count):
                due=self.epoch+index*step
                await asyncio.sleep(max(0,due-time.monotonic()))
                samples=min(int(step*16000),duration*16000-position);pcm=pcm_bytes(self.run,r["recorderId"],index+1,samples)
                proof=dict(seq=index+1,start=position,samples=samples,sha256=hashlib.sha256(pcm).hexdigest());r["proofs"].append(proof);position+=samples
                queue.put_nowait((proof,pcm,due));r["sentChunks"]+=1;r["sampleCount"]+=samples
            await asyncio.sleep(max(0,self.epoch+duration-time.monotonic()));r["observedSeconds"]=time.monotonic()-self.epoch
            await queue.put(None)
        except BaseException:
            # Bounded producer failure is visible; a consumer may not hang and then claim full duration.
            r["errors"]+=1
            try:queue.put_nowait(None)
            except asyncio.QueueFull:pass
            raise
    async def record(self,r):
        await self.ready.wait();queue=asyncio.Queue(maxsize=300);producer=asyncio.create_task(self.produce(r,queue));ws=r.pop("_socket",None);disconnected=False
        try:
            while True:
                item=await queue.get()
                if item is None:break
                proof,pcm,due=item;started=time.monotonic();deadline=started+90
                while True:
                    try:
                        if ws is None:ws=await self.socket(r)
                        session=r["session"];path=f"/api/v1/recordings/{r['sessionId']}"
                        if time.monotonic()-r["renewed"]>=10:
                            renewed=await self.http(r["client"],"POST",path+"/writer-lease/renew",dict(expectedEpoch=session["writerEpoch"],collectorId=r["collectorId"]))
                            if renewed["writerEpoch"]!=session["writerEpoch"]:raise ValueError("Renewal scope changed")
                            r["renewed"]=time.monotonic()
                        frame=encode_frame(session["partId"],session["writerEpoch"],proof["seq"],proof["start"],pcm)
                        await ws.send(frame)
                        if not disconnected and proof["seq"]==max(2,math.ceil(self.scenario["durationSeconds"]*1000/self.scenario["chunkMs"]/3)):
                            disconnected=True;self.event("DROP_ACK_DISCONNECT",actor=r["recorderId"],seq=proof["seq"]);await ws.close();ws=None;raise ConnectionError("Planned lost ACK")
                        ack=json.loads(await asyncio.wait_for(ws.recv(),15))
                        if ack!={"type":"ACK","sessionId":r["sessionId"],"partId":session["partId"],"writerEpoch":session["writerEpoch"],"highestContiguousSeq":proof["seq"],"missingSeqs":[]}:
                            failure=ValueError("Wrong ACK scope or sequence")
                            code=ack.get("code");failure.server_code=code if isinstance(code,str) and len(code)<=64 and all(c in "ABCDEFGHIJKLMNOPQRSTUVWXYZ_" for c in code) else None
                            raise failure
                        latency=(time.monotonic()-due)*1000;r["latenciesMs"].append(latency);r["queueAgesMs"].append((started-due)*1000);r["ackedChunks"]+=1
                        self.event("PCM_ACK",actor=r["recorderId"],sessionId=r["sessionId"],partId=session["partId"],writerEpoch=session["writerEpoch"],latencyMs=latency,**proof);break
                    except (OSError,TimeoutError,HttpFailure,ValueError) as failure:
                        r["transientErrors"]+=1;self.event("TRANSIENT",actor=r["recorderId"],seq=proof["seq"],errorType=type(failure).__name__,status=getattr(failure,"status",None),businessCode=getattr(failure,"business_code",None),serverCode=getattr(failure,"server_code",None))
                        if ws:await ws.close();ws=None
                        if time.monotonic()>=deadline:raise
                        await asyncio.sleep(.5)
                    except Exception as failure:
                        # websockets' own connection errors are lazily imported with the test dependency.
                        from websockets.exceptions import ConnectionClosed,InvalidHandshake
                        if not isinstance(failure,(ConnectionClosed,InvalidHandshake)):raise
                        r["transientErrors"]+=1;self.event("TRANSIENT",actor=r["recorderId"],seq=proof["seq"],errorType=type(failure).__name__)
                        if ws:await ws.close();ws=None
                        if time.monotonic()>=deadline:raise
                        await asyncio.sleep(.5)
            await producer
            if ws:await ws.close();ws=None
            path=f"/api/v1/recordings/{r['sessionId']}";finish=await self.finish(r)
            r["archiveTaskId"]=finish["archiveTaskId"];until=time.monotonic()+1800
            while True:
                try:status=await self.http(r["client"],"GET",path+"/archive")
                except (OSError,TimeoutError,HttpFailure) as failure:
                    if isinstance(failure,HttpFailure) and failure.status not in (429,500,502,503,504):raise
                    r["transientErrors"]+=1;self.event("ARCHIVE_STATUS_TRANSIENT",actor=r["recorderId"],status=getattr(failure,"status",None),errorType=type(failure).__name__)
                    if time.monotonic()>until:raise
                    await asyncio.sleep(1);continue
                if status["available"]:break
                if status["state"] not in ("READY","RUNNING") or time.monotonic()>until:raise ValueError("Archive did not complete")
                await asyncio.sleep(2)
            async with self.http_slots:result=await asyncio.to_thread(r["client"].verify_archive,r["session"],r["proofs"])
            r.update(result);self.event("ARCHIVE_VERIFIED",actor=r["recorderId"],sessionId=r["sessionId"],**result)
        except Exception as failure:
            r["errors"]+=1;r["errorType"]=type(failure).__name__;r["failedStatus"]=getattr(failure,"status",None);r["failedPath"]=getattr(failure,"path",None)
            self.event("RECORDING_FAILED",actor=r["recorderId"],errorType=type(failure).__name__,status=r["failedStatus"],businessCode=getattr(failure,"business_code",None),path=r["failedPath"],clockDiagnostic=getattr(failure,"clock_diagnostic",None))
        finally:
            if ws:await ws.close()
            if not producer.done():producer.cancel()
            await asyncio.gather(producer,return_exceptions=True)
    async def finish(self,r):
        """Reconcile exact sealed PCM on uncertain replies; never manufacture archive availability."""
        path=f"/api/v1/recordings/{r['sessionId']}";deadline=time.monotonic()+180;client=r["client"]
        while True:
            try:
                manifest=await self.http(client,"GET",path+"/manifest");current=await self.http(client,"GET",path)
                expected=r["session"]
                if any(current.get(k)!=expected.get(k) for k in ("sessionId","partId","backendId")):raise ValueError("Final recording scope changed")
                parts=manifest.get("parts");correct=dict(partId=expected["partId"],highestContiguousSeq=r["ackedChunks"],highestReceivedSeq=r["ackedChunks"],sampleCount=r["sampleCount"])
                if manifest.get("sessionId")!=r["sessionId"] or parts!=[correct]:raise ValueError("Final persisted manifest differs from acknowledged PCM")
                r["session"]=current
                if not manifest.get("finished"):
                    try:await self.http(client,"POST",path+"/writer-lease/renew",dict(expectedEpoch=current["writerEpoch"],collectorId=r["collectorId"]))
                    except HttpFailure as failure:
                        if failure.status!=409:raise
                        current=await self.http(client,"GET",path)
                        r["session"]=await self.http(client,"POST",path+"/writer-lease",dict(expectedEpoch=current["writerEpoch"],collectorId=r["collectorId"],requestId=str(uuid.uuid4())))
                return await self.http(client,"POST",path+"/finish",{expected["partId"]:r["ackedChunks"]},headers={"X-Expected-Epoch":str(r["session"]["writerEpoch"]),"X-Collector-Id":r["collectorId"]})
            except (OSError,TimeoutError,HttpFailure) as failure:
                if isinstance(failure,HttpFailure) and failure.status not in (409,429,500,502,503,504):raise
                r["transientErrors"]+=1;self.event("FINISH_TRANSIENT",actor=r["recorderId"],status=getattr(failure,"status",None),businessCode=getattr(failure,"business_code",None),path=getattr(failure,"path",None),errorType=type(failure).__name__)
                if time.monotonic()>=deadline:raise
                await asyncio.sleep(.5)
    async def background(self,b):
        await self.ready.wait();client=b["client"];end=self.epoch+self.scenario["durationSeconds"]
        await asyncio.sleep(min(b.get("initialPhaseSeconds",0),max(0,end-time.monotonic())))
        while True:
            for path in ("/api/v1/meetings?limit=10",f"/api/v1/meetings/{b['meetingId']}"):
                started=time.monotonic()
                while True:
                    try:
                        await self.http(client,"GET",path);b["requests"]+=1;b["latenciesMs"].append((time.monotonic()-started)*1000);break
                    except Exception as failure:
                        b["transientErrors"]+=1;self.event("BACKGROUND_TRANSIENT",actor=b["userId"],errorType=type(failure).__name__)
                        if time.monotonic()-started>=90:b["errors"]+=1;break
                        await asyncio.sleep(.5)
            if time.monotonic()>=end:break
            await asyncio.sleep(min(15,max(0,end-time.monotonic())))
        b["observedSeconds"]=time.monotonic()-self.epoch
    async def inject(self):
        if not self.api_fault:return
        await self.ready.wait();await asyncio.sleep(self.scenario["durationSeconds"]/2)
        try:
            await asyncio.to_thread(self.fault.stop);self.event("API_SIGKILL")
            await asyncio.sleep(6)
        finally:await asyncio.to_thread(self.fault.restore);self.event("API_RESTARTED_SAME_BINARY")
    async def sample_resources(self):
        import resource
        previous={};ticks=os.sysconf("SC_CLK_TCK");page=os.sysconf("SC_PAGE_SIZE")
        while True:
            state=json.loads((ROOT/".tools/local-app/state.json").read_text());now=time.monotonic();roles={name:value["pid"] for name,value in state.items() if isinstance(value,dict) and type(value.get("pid")) is int};roles["load-client"]=os.getpid()
            used=[];missing=[]
            for name,pid in roles.items():
                try:
                    fields=Path(f"/proc/{pid}/stat").read_text().rsplit(")",1)[1].split();cpu=int(fields[11])+int(fields[12]);identity=(pid,fields[19]);old=previous.get(name);percent=(cpu-old[1])/ticks/(now-old[2])*100 if old and old[0]==identity else None;previous[name]=(identity,cpu,now)
                    used.append(dict(role=name,pid=pid,rssBytes=int(fields[21])*page,cpuPercent=percent))
                except (OSError,ValueError,IndexError):missing.append(name)
            value=dict(monotonic=now,roles=used,missingRoles=missing,loadMaxRssBytes=resource.getrusage(resource.RUSAGE_SELF).ru_maxrss*1024)
            self.resources.append(value);self.event("RESOURCES",**value);await asyncio.sleep(5)
    async def run_all(self):
        self.output=self.output.resolve()
        if not self.output.is_relative_to(ROOT) or self.output==ROOT:raise ValueError("New output inside this checkout required")
        self.output.mkdir(parents=True,exist_ok=False);os.chmod(self.output,0o700)
        self.audit=(self.output/"events.ndjson").open("x");os.chmod(self.output/"events.ndjson",0o600)
        s=self.scenario
        if not(1<=s["recorders"]<=100 and s["recorders"]<=s["totalUsers"]<=300 and 1<=s["durationSeconds"]<=7200 and s["chunkMs"]==2000):raise ValueError("Bounded local recording scenario required")
        recordings=[];background=[];tasks=[];resource_task=None;fatal=None
        try:
            await asyncio.to_thread(self.freeze_worker)
            await asyncio.to_thread(self.freeze_file_service)
            # Bound CAS admission too. No credentials or cookies are written to evidence.
            for index in range(s["totalUsers"]):
                r=await self.setup(index,index<s["recorders"])
                (recordings if index<s["recorders"] else background).append(r)
                if (index+1)%25==0:print(f"CAS/API setup {index+1}/{s['totalUsers']}",flush=True)
            await asyncio.gather(*(self.prime(r) for r in recordings))
            resource_task=asyncio.create_task(self.sample_resources())
            self.epoch=time.monotonic();self.ready.set();self.event("START",recorders=len(recordings),backgroundUsers=len(background),scenario=s)
            tasks=[asyncio.create_task(self.record(r)) for r in recordings]+[asyncio.create_task(self.background(b)) for b in background]+[asyncio.create_task(self.inject())]
            while any(not task.done() for task in tasks):
                await asyncio.sleep(5)
                failed=[task for task in tasks if task.done() and not task.cancelled() and task.exception() is not None]
                if failed:
                    fatal=type(failed[0].exception()).__name__;self.event("LOAD_CONTROLLER_FAILED",errorType=fatal);break
                status=dict(elapsedSeconds=time.monotonic()-self.epoch,recorders=len(recordings),acknowledgedChunks=sum(r["ackedChunks"] for r in recordings),verifiedArchives=sum(r["hashMatched"] for r in recordings),errors=sum(r["errors"] for r in recordings)+sum(b["errors"] for b in background))
                (self.output/"progress.json").write_text(json.dumps(status));print(json.dumps(status),flush=True)
            if fatal is None:await asyncio.gather(*tasks)
        except Exception as failure:
            fatal=type(failure).__name__;self.event("LOAD_SETUP_FAILED",errorType=fatal,status=getattr(failure,"status",None),businessCode=getattr(failure,"business_code",None),method=getattr(failure,"method",None),path=getattr(failure,"path",None),clockDiagnostic=getattr(failure,"clock_diagnostic",None))
        finally:
            for task in tasks:
                if not task.done():task.cancel()
            await asyncio.gather(*tasks,return_exceptions=True);await asyncio.to_thread(self.fault.restore)
            for r in recordings:
                socket=r.pop("_socket",None)
                if socket:await socket.close()
            if resource_task:
                resource_task.cancel();await asyncio.gather(resource_task,return_exceptions=True)
            self.audit.close()
            clean=lambda rows:[{k:v for k,v in r.items() if k not in ("client","collectorId")} for r in rows]
            (self.output/"observations.json").write_text(json.dumps(dict(recordings=clean(recordings),background=clean(background),faultEvents=self.fault.events,resources=self.resources),indent=2));os.chmod(self.output/"observations.json",0o600)
        from .report import qualify_transport
        report=qualify_transport(s,recordings,background);report.update(evidenceMode="ACTUAL_LOOPBACK_CAS_WEBSOCKET_FILE_MYSQL",runId=self.run,sourceEvidence=str(self.output/"observations.json"),apiFaultEvents=self.fault.events,modelExecution="DISABLED_NOT_INSTALLED")
        report["execution"]=getattr(self,"execution",None)
        if fatal:report["passed"]=False;report["reasons"].append("LOAD_CONTROLLER_"+fatal)
        report["backgroundTransientErrorCounts"]=sum(b["transientErrors"] for b in background)
        report["resourcePeaks"]={"rssBytes":max((sum(r["rssBytes"] for r in sample["roles"]) for sample in self.resources),default=None),"cpuPercent":max((sum(r["cpuPercent"] or 0 for r in sample["roles"]) for sample in self.resources),default=None),"scope":"owned AMA processes and load client; MySQL container/kernel cache not included","samples":len(self.resources)}
        if self.api_fault and len(self.fault.events)!=2:report["passed"]=False;report["reasons"].append("API_FAULT_NOT_COMPLETED")
        return report
