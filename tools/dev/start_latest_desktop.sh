#!/usr/bin/env bash
set -euo pipefail
ROOT="/home/tanren/ama/.worktrees/phase-b-completion"
cd "$ROOT"
python3 tools/dev/local_app.py start
url="$(python3 - <<'PY'
import json
from pathlib import Path
state=json.loads((Path('.tools/local-app')/'state.json').read_text())
print(f"http://127.0.0.1:{state['ports']['web']}/meetings")
PY
)"
if command -v wslpath >/dev/null 2>&1 && command -v cmd.exe >/dev/null 2>&1; then
  cmd.exe /c start "" "$url" >/dev/null 2>&1 || true
elif command -v xdg-open >/dev/null 2>&1; then
  xdg-open "$url" >/dev/null 2>&1 || true
fi
echo "AMA B 阶段已启动：$url"
