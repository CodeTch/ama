#!/usr/bin/env bash
set -euo pipefail
cd /home/tanren/ama/.worktrees/phase-b-completion
python3 tools/dev/local_app.py start
url=http://127.0.0.1:5174/meetings
if command -v cmd.exe >/dev/null 2>&1; then cmd.exe /c start "" "$url" >/dev/null 2>&1 || true; elif command -v xdg-open >/dev/null 2>&1; then xdg-open "$url" >/dev/null 2>&1 || true; fi
echo "AMA B阶段已启动：$url"
