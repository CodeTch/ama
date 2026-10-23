import pathlib,json,hashlib
root=pathlib.Path.cwd();e=root/'artifacts/phase-b/b-remaining/task-03';before=json.loads((e/'owned-source-manifest.json').read_text());receipt={}
for name,expected in before.items():
 p=root/name;raw=p.read_bytes();assert hashlib.sha256(raw).hexdigest()==expected
 normalized=raw.decode().replace('\r\n','\n').rstrip()+'\n'
 p.write_bytes(normalized.encode());receipt[name]={'before':expected,'after':hashlib.sha256(p.read_bytes()).hexdigest(),'operation':'CRLF to LF and strip trailing EOF whitespace only'}
(e/'source-normalization.json').write_text(json.dumps(receipt,indent=2)+'\n')
