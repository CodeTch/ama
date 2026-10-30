"""Transcode a supplied real speech recording into actual import containers/codecs.

This tool never synthesizes a transcript or calls a service. Output identities refer to the
encoded original files, not to decoded PCM. Existing artifacts are never overwritten.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

FORMATS = {
    'AAC': ('aac', 'adts', False), 'FLAC': ('flac', 'flac', False), 'OGG': ('libvorbis', 'ogg', False),
    'M4V': ('aac', 'ipod', True), 'AVI': ('pcm_s16le', 'avi', True),
    'MP3': ('libmp3lame', 'mp3', False), 'WAV': ('pcm_s16le', 'wav', False),
    'M4A': ('aac', 'ipod', False), 'MP4': ('aac', 'mp4', True), 'MOV': ('aac', 'mov', True),
    'MKV': ('flac', 'matroska', True), 'WEBM': ('libopus', 'webm', False),
}


def create(source, output, formats):
    source = Path(source).resolve(strict=True)
    output = Path(output)
    if not formats or len(set(formats)) != len(formats) or any(item not in FORMATS for item in formats):
        raise ValueError('Choose explicit supported media formats')
    output.mkdir(parents=True, exist_ok=False, mode=0o700)
    items = []
    for format in formats:
        codec, muxer, video = FORMATS[format]
        path = output / ('meeting.' + format.lower())
        command = ['ffmpeg', '-nostdin', '-v', 'error', '-n', '-i', str(source)]
        if video:
            command += ['-f', 'lavfi', '-i', 'color=c=black:s=160x90:r=10', '-map', '1:v:0', '-map', '0:a:0',
                        '-c:v', 'mpeg4', '-shortest']
        else:
            command += ['-map', '0:a:0', '-vn']
        command += ['-c:a', codec, '-f', muxer, str(path)]
        subprocess.run(command, check=True, capture_output=True, timeout=120)
        with path.open('rb') as stream:
            proof = hashlib.file_digest(stream, 'sha256').hexdigest()
        items.append({'format': format, 'name': path.name, 'sha256': proof, 'bytes': path.stat().st_size,
                      'audioEncoder': codec, 'container': muxer, 'hasVideo': video})
    with source.open('rb') as stream:
        source_proof = hashlib.file_digest(stream, 'sha256').hexdigest()
    manifest = {'schema': 'ama.media-import-fixtures.v1', 'sourceSha256': source_proof, 'items': items}
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', required=True)
    parser.add_argument('--output', required=True, help='new directory; existing artifacts are retained')
    parser.add_argument('--formats', default='AAC,FLAC,OGG,M4V,AVI')
    args = parser.parse_args()
    manifest = create(args.source, args.output, args.formats.split(','))
    print(json.dumps({'created': True, 'count': len(manifest['items']), 'output': str(Path(args.output).resolve())}))


if __name__ == '__main__':
    main()
