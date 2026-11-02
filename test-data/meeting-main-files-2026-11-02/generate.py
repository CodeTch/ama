"""Generate fictional, offline meeting upload samples; never overwrite a sample set."""
import argparse
import base64
import csv
import hashlib
import io
import json
from pathlib import Path
import shutil
import subprocess
import wave

from docx import Document
from docx.oxml.ns import qn
from docx.shared import Pt


TURNS = [
    ("林晨", "这是模拟会议测试。现在是二零二六年十一月二日，我们召开会议助手项目周会，讨论文件导入、转写质量和验收安排。"),
    ("陈宇", "开发进展是，文本和音频的导入入口已经准备好。接下来逐一验证不同文件格式，并核对上传后的文件顺序。"),
    ("王敏", "测试会覆盖正常文件、空文件、损坏文件和中文文件名。同一份会议内容使用不同格式导入，方便比较解析结果。"),
    ("周宁", "请保留原始文件名。失败时要显示明确原因，并允许重新选择原文件。不能把上传成功直接当成解析成功。"),
    ("林晨", "第一项决定，陈宇负责核对音频解析结果，在十一月四日十八点前提交格式兼容性报告。"),
    ("陈宇", "收到。我会重点检查采样率、声道、文件顺序，以及字幕中的时间戳，发现问题后记录文件名和复现步骤。"),
    ("王敏", "第二项任务由我负责，在十一月五日十八点前完成回归测试。验收标准包括中文可读、内容完整、顺序正确和失败原因清楚。"),
    ("周宁", "目前的风险是，只有静音或没有有效文字的文件可能无法生成转写。这类文件应提示用户检查原文件，不能生成虚构的会议内容。"),
    ("林晨", "第三项任务，周宁在十一月六日中午十二点前整理操作说明。下一次验收会议定在十一月六日下午三点。"),
    ("林晨", "会议结论是，优先保证导入和解析结果准确。本周按这三项任务推进。这些人物、任务和日期全部用于测试，会议结束。"),
]
LINES = [speaker + "：" + text for speaker, text in TURNS]
SAMPLE_LIMIT = 32 * 1024 * 1024
RATE = 16000


def run(command, **kwargs):
    return subprocess.run(command, check=True, capture_output=True, timeout=180, **kwargs)


def wav_bytes(pcm, channels=1, rate=RATE):
    target = io.BytesIO()
    with wave.open(target, "wb") as stream:
        stream.setnchannels(channels)
        stream.setsampwidth(2)
        stream.setframerate(rate)
        stream.writeframes(pcm)
    return target.getvalue()


def synthesize():
    """Use the installed Windows Chinese voice, with no online speech service."""
    payload = base64.b64encode(json.dumps(
        [speaker + "说。" + text for speaker, text in TURNS], ensure_ascii=False
    ).encode("utf-8")).decode("ascii")
    script = """
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Speech
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
try {
    $synth.SelectVoice('Microsoft Huihui Desktop')
    $synth.Rate = 1
    $items = ConvertFrom-Json -InputObject ([Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('PAYLOAD')))
    foreach ($item in $items) {
        $stream = New-Object System.IO.MemoryStream
        try {
            $synth.SetOutputToWaveStream($stream)
            $synth.Speak($item)
            $synth.SetOutputToNull()
            [Console]::WriteLine([Convert]::ToBase64String($stream.ToArray()))
        } finally { $stream.Dispose() }
    }
} finally { $synth.Dispose() }
""".replace("PAYLOAD", payload)
    encoded = base64.b64encode(script.encode("utf-16-le")).decode("ascii")
    completed = run(["powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded])
    chunks = completed.stdout.decode("utf-8-sig").splitlines()
    if len(chunks) != len(TURNS):
        raise RuntimeError("Speech synthesis did not return every meeting turn")
    pcm_parts = []
    for index, chunk in enumerate(chunks, 1):
        source = base64.b64decode(chunk, validate=True)
        pcm = run(["ffmpeg", "-nostdin", "-v", "error", "-i", "pipe:0", "-vn", "-ar", str(RATE),
                   "-ac", "1", "-c:a", "pcm_s16le", "-f", "s16le", "pipe:1"], input=source).stdout
        if len(pcm) < RATE:
            raise RuntimeError("Speech turn is unexpectedly short")
        pcm_parts.append(pcm)
        print(f"Synthesized meeting turn {index}/{len(TURNS)}", flush=True)
    return pcm_parts


def timestamp(milliseconds, comma=False):
    seconds, fraction = divmod(milliseconds, 1000)
    minutes, seconds = divmod(seconds, 60)
    hours, minutes = divmod(minutes, 60)
    separator = "," if comma else "."
    return f"{hours:02}:{minutes:02}:{seconds:02}{separator}{fraction:03}"


def create(output):
    output = output.resolve()
    if (output / "files").exists() or (output / "manifest.json").exists():
        raise FileExistsError("Choose a new output directory; existing samples are retained")
    for executable in ("ffmpeg", "ffprobe", "powershell.exe"):
        if not shutil.which(executable):
            raise RuntimeError(f"Required executable is unavailable: {executable}")
    files = output / "files"
    files.mkdir(parents=True)
    entries = []

    def add(relative, data, kind, scenario, expected, **extra):
        path = files / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open("xb") as stream:
            stream.write(data)
        item = {"path": "files/" + relative, "kind": kind, "format": path.suffix[1:].upper(),
                "scenario": scenario, "expected": expected, "bytes": len(data),
                "sha256": hashlib.sha256(data).hexdigest(), **extra}
        entries.append(item)
        return path

    chunks = synthesize()
    silence = bytes(RATE)  # Half a second of mono PCM16.
    pcm = bytearray(silence)
    cues = []
    for line, chunk in zip(LINES, chunks):
        start = len(pcm) * 1000 // (RATE * 2)
        pcm.extend(chunk)
        end = len(pcm) * 1000 // (RATE * 2)
        cues.append({"text": line, "startMs": start, "endMs": end})
        pcm.extend(silence)
    duration = len(pcm) / (RATE * 2)
    source = add("01_audio/meeting.zh-CN.wav", wav_bytes(pcm), "audio", "正常格式", "应可解析有效语音",
                 codec="pcm_s16le", sampleRate=RATE, channels=1, durationSeconds=duration)
    formats = {
        "mp3": ("libmp3lame", "mp3", "mp3"),
        "m4a": ("aac", "ipod", "aac"),
        "aac": ("aac", "adts", "aac"),
        "flac": ("flac", "flac", "flac"),
        "ogg": ("libvorbis", "ogg", "vorbis"),
        "webm": ("libopus", "webm", "opus"),
    }
    for extension, (encoder, muxer, codec) in formats.items():
        # Seekable output lets every muxer finalize duration and seek metadata.
        target = output / (".encoded-" + extension)
        run(["ffmpeg", "-nostdin", "-v", "error", "-n", "-i", str(source), "-map", "0:a:0",
             "-vn", "-c:a", encoder, "-f", muxer, str(target)])
        encoded = target.read_bytes()
        target.unlink()
        add("01_audio/meeting.zh-CN." + extension, encoded, "audio", "正常格式", "应可解析有效语音", codec=codec)
        print(f"Encoded {extension.upper()}", flush=True)

    body = "\n".join(LINES) + "\n"
    add("02_text/meeting.zh-CN.txt", body.encode("utf-8"), "text", "正常格式", "应提取10段会议发言", encoding="UTF-8")
    document = Document()
    document.core_properties.title = "会议助手项目周会（虚构测试数据）"
    document.core_properties.author = "AMA synthetic test fixtures"
    style = document.styles["Normal"]
    style.font.name = "Microsoft YaHei"
    style.font.size = Pt(11)
    style.element.rPr.rFonts.set(qn("w:eastAsia"), "Microsoft YaHei")
    for speaker, text in TURNS:
        paragraph = document.add_paragraph()
        paragraph.add_run(speaker + "：").bold = True
        paragraph.add_run(text)
    docx = io.BytesIO()
    document.save(docx)
    add("02_text/meeting.zh-CN.docx", docx.getvalue(), "text", "正常格式", "应提取与TXT一致的10段发言")
    srt = "\n\n".join(f"{i}\n{timestamp(c['startMs'], True)} --> {timestamp(c['endMs'], True)}\n{c['text']}"
                        for i, c in enumerate(cues, 1)) + "\n"
    vtt = "WEBVTT\n\n" + "\n\n".join(
        f"{timestamp(c['startMs'])} --> {timestamp(c['endMs'])}\n{c['text']}" for c in cues) + "\n"
    add("02_text/meeting.zh-CN.srt", srt.encode("utf-8"), "text", "正常格式", "应提取10段发言并保留起止时间", encoding="UTF-8")
    add("02_text/meeting.zh-CN.vtt", vtt.encode("utf-8"), "text", "正常格式", "应提取10段发言并保留起止时间", encoding="UTF-8")

    add("03_variants/中文 文件名_UTF8_BOM_CRLF.txt", body.replace("\n", "\r\n").encode("utf-8-sig"),
        "text", "中文空格文件名、UTF-8 BOM、Windows换行", "应提取与标准TXT一致的10段发言", encoding="UTF-8 BOM")
    unicode_text = "林晨：中英混排 API v2.0，金额 ¥12,345.67，比例 99.9%，括号（测试）。\n王敏：Emoji 😀✅，英文 Meeting test，编号 AMA-2026-001。\n"
    add("03_variants/unicode_emoji.txt", unicode_text.encode("utf-8"), "text", "中英混排、特殊符号、Emoji", "应完整保留文字及Unicode字符", encoding="UTF-8")
    stereo = run(["ffmpeg", "-nostdin", "-v", "error", "-i", str(source), "-ar", "48000", "-ac", "2",
                  "-c:a", "pcm_s16le", "-f", "s16le", "pipe:1"]).stdout
    add("03_variants/stereo_48khz.wav", wav_bytes(stereo, 2, 48000), "audio", "48kHz双声道", "应识别与标准音频相同的会议内容", codec="pcm_s16le", sampleRate=48000, channels=2)
    add("03_variants/中文 文件名_大写扩展名.MP3", (files / "01_audio/meeting.zh-CN.mp3").read_bytes(),
        "audio", "中文空格文件名、大写扩展名、相同字节重命名", "应识别MP3；原始字节哈希应与标准MP3一致", codec="mp3")
    multiline_srt = srt.replace("：", "：\n").replace("\n", "\r\n")
    add("03_variants/multiline_BOM_CRLF.srt", multiline_srt.encode("utf-8-sig"), "text",
        "多行字幕、BOM、CRLF", "应提取10条字幕，保留字幕内换行", encoding="UTF-8 BOM")
    metadata_vtt = "WEBVTT AMA test\n\nNOTE 这条备注不应进入转写\n\n" + "\n\n".join(
        f"cue-{i}\n{timestamp(c['startMs'])} --> {timestamp(c['endMs'])} align:start size:80%\n{c['text']}"
        for i, c in enumerate(cues, 1)) + "\n"
    add("03_variants/metadata_settings.vtt", metadata_vtt.encode("utf-8"), "text", "VTT备注、cue标识和显示设置", "应提取10段发言，忽略备注和显示设置", encoding="UTF-8")

    split = cues[5]["startMs"] * RATE * 2 // 1000
    split -= split % 2
    for index, content in enumerate((pcm[:split], pcm[split:]), 1):
        add(f"04_multi_file/{index:02}_part{index}.wav", wav_bytes(content), "audio", "两段录音顺序",
            "仅选择本目录的两段WAV，先01后02；按顺序形成完整会议", codec="pcm_s16le", group="audio-parts", order=index)
        subset = LINES[:5] if index == 1 else LINES[5:]
        add(f"04_multi_file/{index:02}_part{index}.txt", ("\n".join(subset) + "\n").encode("utf-8"),
            "text", "两个文本文件顺序", "仅选择本目录的两段TXT，先01后02；合并后应与标准TXT一致", group="text-parts", order=index)

    negatives = [
        ("empty.txt", b"", "text", "空文本", "NO_TEXT", "解析阶段"),
        ("invalid_utf8.txt", b"\xff\xfe\xfa\x80", "text", "非法UTF-8", "INVALID_UTF8", "解析阶段"),
        ("utf16le.txt", body.encode("utf-16"), "text", "UTF-16 LE文本", "INVALID_UTF8", "解析阶段；当前TXT只支持UTF-8"),
        ("text_disguised_as_audio.mp3", b"This is plain text, not an MP3 recording.\n", "audio", "文本伪装MP3", "INVALID_MEDIA", "解析阶段"),
        ("corrupted.docx", b"PK\x03\x04broken-office-container", "text", "损坏DOCX", "INVALID_TEXT", "解析阶段"),
        ("end_before_start.srt", "1\n00:00:05,000 --> 00:00:02,000\n测试\n".encode("utf-8"), "text", "字幕结束早于开始", "INVALID_TEXT", "解析阶段"),
        ("missing_header.vtt", "00:00:01.000 --> 00:00:02.000\n测试\n".encode("utf-8"), "text", "缺少WEBVTT头", "INVALID_TEXT", "解析阶段"),
        ("no_cues.vtt", "WEBVTT\n\nNOTE 只有备注，没有发言\n".encode("utf-8"), "text", "VTT没有有效字幕", "NO_TEXT", "解析阶段"),
        ("silence_5s.wav", wav_bytes(bytes(RATE * 2 * 5)), "audio", "只有静音", "不得虚构会议内容；具体失败码需观察实际ASR", "解析阶段"),
        ("unsupported.md", "# 不支持的主文件扩展名\n这是文件选择校验样本。\n".encode("utf-8"), "text", "不支持的主文件格式", "应拒绝加入队列", "选择/拖入阶段"),
        ("over_32MiB_by_1byte.txt", b"A" * (SAMPLE_LIMIT + 1), "text", "超过32MiB限制1字节", "应拒绝上传", "浏览器校验阶段"),
    ]
    for name, data, kind, scenario, expected, stage in negatives:
        add("05_negative/" + name, data, kind, scenario, expected, stage=stage)

    manifest = {"schema": "ama.meeting-main-file-samples.v1", "createdDate": "2026-11-02",
                "synthetic": True, "voice": "Microsoft Huihui Desktop, zh-CN, rate=1",
                "source": "Offline TTS of fictional meeting text; one synthetic voice for all speaker labels",
                "projectLimits": {"maxFileBytes": SAMPLE_LIMIT, "maxFilesPerBatch": 16},
                "standardFormats": ["MP3", "WAV", "M4A", "AAC", "FLAC", "OGG", "WEBM", "TXT", "DOCX", "SRT", "VTT"],
                "durationSeconds": duration, "expectedCues": cues,
                "expectedActions": [
                    {"owner": "陈宇", "task": "提交格式兼容性报告", "due": "2026-11-04T18:00:00+08:00"},
                    {"owner": "王敏", "task": "完成回归测试", "due": "2026-11-05T18:00:00+08:00"},
                    {"owner": "周宁", "task": "整理操作说明", "due": "2026-11-06T12:00:00+08:00"}],
                "nextMeeting": "2026-11-06T15:00:00+08:00", "samples": entries}
    (output / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    with (output / "samples.csv").open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=["path", "kind", "format", "scenario", "expected", "bytes", "sha256"], extrasaction="ignore")
        writer.writeheader()
        writer.writerows(entries)
    (output / "SHA256SUMS").write_text("".join(f"{e['sha256']}  {e['path']}\n" for e in entries), encoding="utf-8")
    (output / "README.txt").write_text(f"""会议主文件上传测试包（2026-11-02）

所有人物、发言、任务和日期均为虚构测试数据。音频由本机离线中文TTS合成，
不是实际会议录音。所有人物标签共用一个合成声音，不用于验证真实多说话人分离。

本包共有 {len(entries)} 个上传样本，标准格式11种：
音频：MP3 / WAV / M4A / AAC / FLAC / OGG / WEBM
文本：TXT / DOCX / SRT / VTT
标准音频时长约 {duration:.1f} 秒，同一来源；标准文本均包含同一场会议的10段发言。
SRT/VTT时间轴根据合成音频分段实际长度生成，供时间戳校验。

目录用途：
  files/01_audio       7种有效音频容器/编码
  files/02_text        4种有效文本/文档/字幕
  files/03_variants    6个编码、换行、Unicode、文件名和音频参数变体
  files/04_multi_file  2段WAV和2段TXT，供添加、调整顺序、移除、恢复测试
  files/05_negative    11个异常或边界样本；预期结果见samples.csv和manifest.json

建议操作：
1. 先上传02_text/meeting.zh-CN.txt，核对10段发言、中文、人名、任务及日期。
2. 对其余10种标准格式分别新建会议或新导入批次，比较同一会议内容。
   多个格式来自同一会议；同时导入这些格式可能让会议内容重复。
3. 多文件顺序测试只选择04_multi_file里的两段WAV，或只选择两段TXT。
   先按01、02导入；另一次反向选择或移动顺序，核对最终段落顺序。
4. 逐一测试03_variants，再单独测试05_negative，观察错误提示和重试/移除行为。
   静音文件不应产生虚构会议内容；具体ASR表现需在实际服务上验证。
5. 当前代码限制每批最多16个文件、每个不超过32MiB。
   over_32MiB_by_1byte.txt大小精确为33,554,433字节，用于验证超限拦截。
   验证数量上限时，可从01_audio、02_text、03_variants合计选择17个正常样本。

内容核对要点（示例预期，不要求ASR逐字一致）：
  陈宇：2026-11-04 18:00 前提交格式兼容性报告。
  王敏：2026-11-05 18:00 前完成回归测试。
  周宁：2026-11-06 12:00 前整理操作说明。
  下次验收会议：2026-11-06 15:00。以上时间均按Asia/Shanghai解释。

配套文件：
  samples.csv       Excel可打开的清单，含场景、预期、字节数及SHA-256
  manifest.json     完整清单、标准字幕时间轴、模拟任务预期
  SHA256SUMS        32个上传样本的原始字节校验值
  validation.json   运行verify.py后产生的文件级校验结果
  generate.py       离线生成器（Python 3.11+、python-docx、ffmpeg及Windows中文TTS）
  verify.py         校验器（从项目根目录执行；依赖python-docx、ffmpeg、ffprobe）

项目根目录下重新校验：
  python3 test-data/meeting-main-files-2026-11-02/verify.py

重新生成请指定新目录，生成器拒绝覆盖既有样本：
  python3 test-data/meeting-main-files-2026-11-02/generate.py --output test-data/meeting-main-files-regenerated

验证范围：文件哈希、真实音频容器/编码和可解码性、字幕解析、DOCX/TXT内容及异常样本。
本包未自动上传到应用，也不声称已验证完整UI、后端转写或纪要生成链路。
""", encoding="utf-8-sig")
    print(json.dumps({"output": str(output), "samples": len(entries), "durationSeconds": duration}, ensure_ascii=False))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parent)
    create(parser.parse_args().output)
