"""Check sample bytes and parsability; does not upload or claim end-to-end results."""
import argparse
from array import array
import csv
import hashlib
import importlib.util
import io
import json
import math
from pathlib import Path
import subprocess
import sys
import wave
import zipfile

from docx import Document


PROJECT = Path(__file__).resolve().parents[2]
CODECS = {"MP3": "mp3", "WAV": "pcm_s16le", "M4A": "aac", "AAC": "aac",
          "FLAC": "flac", "OGG": "vorbis", "WEBM": "opus"}
CONTAINERS = {"MP3": "mp3", "WAV": "wav", "M4A": "mov", "AAC": "aac",
              "FLAC": "flac", "OGG": "ogg", "WEBM": "webm"}


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def command(arguments, check=True):
    return subprocess.run(arguments, check=check, capture_output=True, timeout=120)


def wave_pcm(path):
    with wave.open(str(path), "rb") as stream:
        require(stream.getparams()[:3] == (1, 2, 16000), f"Unexpected PCM parameters: {path}")
        return stream.readframes(stream.getnframes())


def verify(root):
    root = root.resolve()
    manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
    spec = importlib.util.spec_from_file_location("ama_sample_asr", PROJECT / "tools/providers/local_asr.py")
    asr = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(asr)
    results = []
    entries = manifest["samples"]
    paths = [entry["path"] for entry in entries]
    require(len(paths) == len(set(paths)) == 32, "Expected 32 unique upload samples")
    actual_paths = {str(path.relative_to(root)) for path in (root / "files").rglob("*") if path.is_file()}
    require(set(paths) == actual_paths, "Sample inventory does not match filesystem")
    require({e["format"] for e in entries if "/01_audio/" in e["path"]} == set(CODECS), "Missing audio format")
    require({e["format"] for e in entries if "/02_text/" in e["path"]} == {"TXT", "DOCX", "SRT", "VTT"}, "Missing text format")
    reference_text = (root / "files/02_text/meeting.zh-CN.txt").read_text(encoding="utf-8")
    reference_cues = manifest["expectedCues"]
    require(reference_text.splitlines() == [c["text"] for c in reference_cues], "Reference text/cues differ")
    require(len(reference_cues) == 10, "Expected 10 meeting turns")
    source_pcm = wave_pcm(root / "files/01_audio/meeting.zh-CN.wav")
    source_duration = len(source_pcm) / 32000
    for entry in entries:
        path = root / entry["path"]
        data = path.read_bytes()
        require(len(data) == entry["bytes"], f"Size mismatch: {path}")
        require(hashlib.sha256(data).hexdigest() == entry["sha256"], f"Hash mismatch: {path}")
        observed = {"path": entry["path"], "passed": True, "checks": ["size", "sha256"]}
        negative = "/05_negative/" in entry["path"]
        format = entry["format"]
        if entry["kind"] == "audio" and path.name != "text_disguised_as_audio.mp3":
            probe = json.loads(command(["ffprobe", "-v", "error", "-show_format", "-show_streams", "-of", "json", str(path)]).stdout)
            streams = probe["streams"]
            require(len(streams) == 1 and streams[0]["codec_type"] == "audio", f"Expected audio only: {path}")
            stream = streams[0]
            require(stream["codec_name"] == CODECS[format], f"Wrong actual codec: {path}")
            require(CONTAINERS[format] in probe["format"]["format_name"].split(","), f"Wrong container: {path}")
            decoded = command(["ffmpeg", "-nostdin", "-v", "error", "-xerror", "-i", str(path), "-map", "0:a:0", "-vn",
                               "-ac", "1", "-ar", "16000", "-c:a", "pcm_s16le", "-f", "s16le", "pipe:1"]).stdout
            decoded_duration = len(decoded) / 32000
            samples = array("h", decoded)
            if sys.byteorder != "little":
                samples.byteswap()
            require(len(samples) > 0, f"No decoded PCM: {path}")
            rms = math.sqrt(sum(value * value for value in samples) / len(samples))
            if negative:
                require(rms == 0 and decoded_duration == 5, "Silence sample is not five seconds of silence")
                observed["checks"].append("exactly_5_seconds_silence")
            else:
                require(rms > 100, f"Expected audible speech signal: {path}")
                if "/04_multi_file/" not in entry["path"]:
                    require(abs(decoded_duration - source_duration) < 0.25, f"Transcode duration differs: {path}")
                for key, field in (("sampleRate", "sample_rate"), ("channels", "channels")):
                    if key in entry:
                        require(int(stream[field]) == entry[key], f"Wrong audio parameter: {path}, {key}")
            observed.update(codec=stream["codec_name"], container=probe["format"]["format_name"],
                            sampleRate=int(stream["sample_rate"]), channels=stream["channels"],
                            decodedDurationSeconds=decoded_duration, pcmRms=round(rms, 2))
            observed["checks"].extend(["actual_container_and_codec", "full_audio_decode", "pcm_signal"])
        elif format in ("SRT", "VTT"):
            if negative:
                try:
                    asr.subtitle_file(path, format, 100000, 1000)
                except asr.AsrError as error:
                    code = str(error)
                    require(code == entry["expected"], f"Unexpected subtitle error: {path}, {code}")
                    observed["observedError"] = code
                else:
                    raise AssertionError(f"Malformed subtitle was accepted: {path}")
            else:
                parsed = asr.subtitle_file(path, format, 100000, 1000)
                expected = [dict(cue) for cue in reference_cues]
                if path.name == "multiline_BOM_CRLF.srt":
                    for cue in expected:
                        cue["text"] = cue["text"].replace("：", "：\n")
                require(parsed["segments"] == expected, f"Subtitle text/timing mismatch: {path}")
                observed.update(cues=len(parsed["segments"]), durationMs=parsed["durationMs"])
            observed["checks"].append("project_subtitle_parser")
        elif format == "DOCX":
            if negative:
                require(not zipfile.is_zipfile(path), "Corrupted DOCX should be invalid")
                observed["checks"].append("intentionally_invalid_office_container")
            else:
                with zipfile.ZipFile(path) as archive:
                    require(archive.testzip() is None, "DOCX ZIP integrity failed")
                paragraphs = [p.text for p in Document(path).paragraphs]
                require(paragraphs == reference_text.splitlines(), "DOCX content mismatch")
                observed["checks"].extend(["zip_integrity", "docx_readback", "reference_text_match"])
        elif path.name == "text_disguised_as_audio.mp3":
            probe = command(["ffprobe", "-v", "error", str(path)], check=False)
            require(probe.returncode != 0, "Fake MP3 was unexpectedly accepted")
            observed["checks"].append("ffprobe_rejects_fake_mp3")
        elif format == "TXT":
            if path.name in ("invalid_utf8.txt", "utf16le.txt"):
                try:
                    data.decode("utf-8", errors="strict")
                except UnicodeDecodeError:
                    observed["checks"].append("strict_utf8_rejection")
                else:
                    raise AssertionError("Invalid encoding was accepted")
            elif path.name == "empty.txt":
                require(data == b"", "Expected zero-byte text")
            elif path.name == "over_32MiB_by_1byte.txt":
                require(len(data) == 32 * 1024 * 1024 + 1, "Wrong boundary size")
                observed["checks"].append("exact_upload_limit_plus_one_byte")
            else:
                text = data.decode("utf-8-sig").replace("\r\n", "\n")
                if "/02_text/" in entry["path"] or path.name.startswith("中文 文件名_UTF8"):
                    require(text == reference_text, "TXT content mismatch")
                if path.name == "unicode_emoji.txt":
                    require("😀✅" in text and "¥12,345.67" in text, "Unicode content mismatch")
                observed["checks"].append("utf8_readback")
        elif format == "MD":
            require("MD" not in manifest["standardFormats"], "Unexpected extension support")
            observed["checks"].append("unsupported_extension_sample")
        results.append(observed)

    joined_text = "".join((root / f"files/04_multi_file/{i:02}_part{i}.txt").read_text(encoding="utf-8") for i in (1, 2))
    require(joined_text == reference_text, "Multipart text does not reconstruct meeting")
    joined_pcm = b"".join(wave_pcm(root / f"files/04_multi_file/{i:02}_part{i}.wav") for i in (1, 2))
    require(joined_pcm == source_pcm, "Multipart audio does not reconstruct original PCM")
    require((root / "files/03_variants/中文 文件名_大写扩展名.MP3").read_bytes()
            == (root / "files/01_audio/meeting.zh-CN.mp3").read_bytes(), "Rename fixture changed original bytes")
    with (root / "samples.csv").open(encoding="utf-8-sig", newline="") as stream:
        rows = list(csv.DictReader(stream))
    require(len(rows) == len(entries), "CSV count differs")
    for row, entry in zip(rows, entries):
        require(row["path"] == entry["path"] and row["sha256"] == entry["sha256"], "CSV inventory mismatch")
    sums = "".join(f"{entry['sha256']}  {entry['path']}\n" for entry in entries)
    require((root / "SHA256SUMS").read_text(encoding="utf-8") == sums, "Checksum inventory mismatch")
    report = {"schema": "ama.meeting-main-file-validation.v1", "passed": True, "sampleCount": len(entries),
              "standardAudioCount": 7, "standardTextCount": 4, "durationSeconds": source_duration,
              "multipartTextReconstructsReference": True, "multipartAudioReconstructsOriginalPcm": True,
              "boundaryFileBytes": 33554433,
              "scope": "File-level verification; no app upload, ASR recognition or end-to-end claims", "samples": results}
    (root / "validation.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"passed": True, "samples": len(entries), "standardFormats": 11,
                      "durationSeconds": source_duration, "report": str(root / "validation.json")}, ensure_ascii=False))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent)
    verify(parser.parse_args().root)
