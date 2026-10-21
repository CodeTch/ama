#!/usr/bin/env bash
# Run only as an explicit deployment step after package; never called by API startup.
set -euo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
: "${DATABASE_URL:?Set explicit MySQL DATABASE_URL}"
: "${DATABASE_USERNAME:?Set DATABASE_USERNAME}"
: "${DATABASE_PASSWORD:?Set DATABASE_PASSWORD through the approved secret mechanism}"
jar="$repo_root/backend/api/target/ama-api-0.1.0-SNAPSHOT-exec.jar"
if [[ ! -f "$jar" ]]; then
    printf '%s\n' 'Package backend/api first; migration requires the built application artifact.' >&2
    exit 1
fi
exec java -Dloader.main=ama.db.MigrationMain -cp "$jar" org.springframework.boot.loader.launch.PropertiesLauncher
