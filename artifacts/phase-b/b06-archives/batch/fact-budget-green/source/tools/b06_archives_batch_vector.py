"""Freeze and independently verify the public batch v1 vector; no application imports."""
import hashlib
import json
from pathlib import Path
import struct
import subprocess

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "backend/core/src/test/resources/ama/transcript"
EXPECTED = "c4a558e4dc8aff8289b4ed60193efdfbc7f64f73ae642e2eec6398c473458053"
VALUE = {"profile": "ama.input-batch.v1", "kind": "INPUT_BATCH", "data": {
    "meetingId": "11111111-1111-4111-8111-111111111111",
    "batchId": "33333333-3333-4333-8333-333333333333", "kind": "TRANSCRIPT", "baseRevision": 9,
    "files": [
        {"ordinal": 1, "fileId": "44444444-4444-4444-8444-444444444444",
         "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "sizeBytes": 0},
        {"ordinal": 2, "fileId": "55555555-5555-4555-8555-555555555555",
         "sha256": "559aead08264d5795d3909718cdd05abd49572e84fe55590eef31a88a08fdffd", "sizeBytes": 1}]}}


def encode(value):
    if value is None:
        return bytes([0])
    if type(value) is bool:
        return bytes([2 if value else 1])
    if type(value) is int:
        assert 0 <= value <= 2**63 - 1
        return bytes([3]) + struct.pack(">Q", value)
    if type(value) is str:
        raw = value.encode("utf-8", errors="strict")
        return bytes([4]) + struct.pack(">Q", len(raw)) + raw
    if type(value) is list:
        return bytes([5]) + struct.pack(">Q", len(value)) + b"".join(map(encode, value))
    if type(value) is dict:
        entries = sorted(value.items(), key=lambda item: item[0].encode("utf-8"))
        return bytes([6]) + struct.pack(">Q", len(entries)) + b"".join(encode(k) + encode(v) for k, v in entries)
    raise TypeError(type(value))


def main():
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--freeze", action="store_true")
    args = parser.parse_args()
    raw = encode(VALUE)
    assert len(raw) == 734
    assert hashlib.sha256(raw).hexdigest() == EXPECTED
    payloads = {"json": (json.dumps(VALUE, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode(),
                "hex": (raw.hex() + "\n").encode(), "bytes": raw, "sha256": (EXPECTED + "\n").encode()}
    if args.freeze:
        RESOURCES.mkdir(parents=True, exist_ok=True)
        for ext, data in payloads.items():
            target = RESOURCES / ("batch-v1.literal." + ext)
            assert not target.exists(), target
            target.write_bytes(data)
    for ext, data in payloads.items():
        assert (RESOURCES / ("batch-v1.literal." + ext)).read_bytes() == data
        assert (ROOT / "artifacts/phase-b/b06-queue-seams" / ("batch-v1.literal." + ext)).read_bytes() == data
    assert encode(json.loads((RESOURCES / "batch-v1.literal.json").read_bytes())) == raw
    assert bytes.fromhex((RESOURCES / "batch-v1.literal.hex").read_text()) == raw
    result = subprocess.run(["openssl", "dgst", "-sha256", str(RESOURCES / "batch-v1.literal.bytes")],
                            capture_output=True, text=True, check=True)
    assert result.stdout.strip().split()[-1] == EXPECTED
    print("PASS: JSON/hex/bytes, independent Python and OpenSSL agree; old literal unchanged")
    print("734 bytes; sha256=" + EXPECTED)
    print(result.stdout.strip())


if __name__ == "__main__":
    main()
