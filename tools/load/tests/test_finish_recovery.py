import asyncio
import http.server
import json
import tempfile
import threading
import unittest
from tools.load.transport import Http,Transport

class FinishRecoveryTest(unittest.TestCase):
    def test_lost_finish_response_reuses_the_sealed_manifest_and_original_archive(self):
        state={"finished":False,"finishes":0,"renewals":0,"claims":0,"bodies":[]}
        session=dict(sessionId="session",partId="part",writerEpoch=2,backendId="backend")
        class Handler(http.server.BaseHTTPRequestHandler):
            def log_message(self,*args):pass
            def respond(self,code,value):
                raw=json.dumps(value).encode();self.send_response(code);self.send_header("Content-Type","application/json");self.send_header("Content-Length",str(len(raw)));self.end_headers();self.wfile.write(raw)
            def do_GET(self):
                if self.path.endswith("/manifest"):self.respond(200,dict(sessionId="session",paused=False,finished=state["finished"],parts=[dict(partId="part",highestContiguousSeq=2,highestReceivedSeq=2,sampleCount=64000)]))
                else:self.respond(200,session)
            def do_POST(self):
                body=json.loads(self.rfile.read(int(self.headers["Content-Length"])) or b"{}");state["bodies"].append((self.path,body))
                if self.path.endswith("/renew"):state["renewals"]+=1;self.respond(200,session)
                elif self.path.endswith("/writer-lease"):state["claims"]+=1;self.respond(500,{})
                else:
                    state["finishes"]+=1;state["finished"]=True
                    self.respond(503,dict(error="lost local admission response")) if state["finishes"]==1 else self.respond(200,dict(sessionId="session",finalSequence=2,archiveTaskId="original-archive"))
        with http.server.ThreadingHTTPServer(("127.0.0.1",0),Handler) as server,tempfile.TemporaryDirectory() as folder:
            thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
            try:
                origin=f"http://127.0.0.1:{server.server_port}";transport=Transport(dict(recorders=1,totalUsers=1,chunkMs=2000,durationSeconds=4),folder,origin,origin)
                recorder=dict(recorderId="fixture",sessionId="session",session=session,collectorId="collector",ackedChunks=2,sampleCount=64000,transientErrors=0,client=Http(origin))
                result=asyncio.run(transport.finish(recorder));self.assertEqual("original-archive",result["archiveTaskId"]);self.assertEqual(2,state["finishes"]);self.assertEqual(0,state["claims"]);self.assertEqual(1,recorder["transientErrors"])
                self.assertEqual([{"part":2},{"part":2}],[body for path,body in state["bodies"] if path.endswith("/finish")])
            finally:server.shutdown();thread.join(2)
