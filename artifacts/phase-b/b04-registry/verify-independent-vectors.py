"""Independent Python byte implementation, not calling any Java codec."""
import hashlib
import json
import pathlib
import struct

vectors = json.loads(pathlib.Path(__file__).with_name("managed-binding-independent-vectors.json").read_text())
for vector in vectors:
    fields = [field.encode("utf-8", errors="strict") for field in vector["fields"]]
    encoded = struct.pack(">I", len(fields)) + b"".join(struct.pack(">I", len(field)) + field for field in fields)
    assert encoded.hex() == vector["hex"]
    assert hashlib.sha256(encoded).hexdigest() == vector["sha256"]
print(f"PASS: {len(vectors)} independent counted big-endian UTF-8 golden vectors")
