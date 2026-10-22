"""Independent literal design vector; imports no application code."""
import hashlib,json,pathlib,struct
e=pathlib.Path.cwd()/"artifacts/phase-b/b06-queue-seams"
value={"profile":"ama.input-batch.v1","kind":"INPUT_BATCH","data":{
 "meetingId":"11111111-1111-4111-8111-111111111111",
 "batchId":"33333333-3333-4333-8333-333333333333","kind":"TRANSCRIPT","baseRevision":9,
 "files":[
  {"ordinal":1,"fileId":"44444444-4444-4444-8444-444444444444","sha256":"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855","sizeBytes":0},
  {"ordinal":2,"fileId":"55555555-5555-4555-8555-555555555555","sha256":"559aead08264d5795d3909718cdd05abd49572e84fe55590eef31a88a08fdffd","sizeBytes":1}]}}
def encode(v):
 if v is None:return b"\\x00".decode("unicode_escape").encode("latin1")
 if v is False:return bytes([1])
 if v is True:return bytes([2])
 if type(v) is int:
  assert 0<=v<2**63
  return bytes([3])+struct.pack(">Q",v)
 if type(v) is str:
  raw=v.encode("utf-8",errors="strict");return bytes([4])+struct.pack(">Q",len(raw))+raw
 if type(v) is list:return bytes([5])+struct.pack(">Q",len(v))+b"".join(encode(x) for x in v)
 if type(v) is dict:
  entries=sorted(v.items(),key=lambda x:x[0].encode("utf-8"))
  return bytes([6])+struct.pack(">Q",len(entries))+b"".join(encode(k)+encode(x) for k,x in entries)
 raise TypeError(type(v))
raw=encode(value)
(e/"batch-v1.literal.json").write_text(json.dumps(value,ensure_ascii=False,sort_keys=True,separators=(",",":"))+"\n")
(e/"batch-v1.literal.hex").write_text(raw.hex()+"\n")
(e/"batch-v1.literal.sha256").write_text(hashlib.sha256(raw).hexdigest()+"\n")
(e/"batch-v1.literal.bytes").write_bytes(raw)
print("literal bytes="+str(len(raw))+"; sha256="+hashlib.sha256(raw).hexdigest())
