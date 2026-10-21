# Phase A application shell

From the repository root in Linux/WSL:

```sh
. /home/tanren/ama/.worktrees/phase-a/.tools/env.sh
npm ci --ignore-scripts
npm --prefix web ci --ignore-scripts
npm --prefix web exec playwright install chromium
npm --prefix web run dev
```

Node/npm pins match the root: 22.23.4 / 10.9.9. Open `/meetings` on Vite's printed local URL. Deployment of the built SPA must serve `web/dist/index.html` for client routes such as `/meetings`; this task does not publish a deployment. The test server binds loopback 127.0.0.2:4275 to avoid the current WSL mirrored-network black hole on unused 127.0.0.1 ports. Test proxy exclusions contain exact loopback addresses because wildcard NO_PROXY patterns are not portable.

```sh
python3 tools/checks/run.py web client.test.ts
python3 tools/checks/run.py e2e shell.spec.ts
python3 tools/checks/run.py visual shell.spec.ts
python3 tools/checks/run.py all
npm --prefix web run typecheck
npm --prefix web run build
npm --prefix web run check:api
```

`all` includes every delivered Python test directory, web unit tests, OpenAPI lint/bundle and Java unit tests; browser/visual modes run explicitly. Every registered test mode rejects missing matches, zero executed cases, skipped required cases and failures. Load, corporate contracts and external integration modes remain pending.

`request<T>(path, init?)` sends cookie credentials, preserves caller headers, defaults JSON Accept, handles empty responses, normalizes HTTP/network/malformed-response errors, and preserves cancellation. It does not display backend error bodies. Business consumers should use same-origin paths and show `ApiError.message` to users. The current shell makes no API call because only internal management health endpoints exist. It never turns management liveness into an employee login or meeting-list success.

Generate types using `npm --prefix web run generate:api`; `check:api` regenerates into an isolated temporary directory and compares bytes, including before the generated file is tracked. Generated types cover only the approved current health contract, including external health.yaml references.

The original references and exact environment are indexed in [prototype-index](../docs/acceptance/a/prototype-index.md). Playwright 1.58.2 pins Chromium/headless revision 1208. For visual reproduction use Ubuntu 24.04, the recorded CJK/font packages, locale/timezone and scale in provenance.json. Normal tests consume committed prototype-derived baselines. They never write baselines. `npm --prefix web run capture:prototype -- --refresh-reference` is an explicit review action that renders the unchanged original HTML only; it never captures the Vue application as a reference.

Runtime states: no company identity, directory, meeting records, uploads, recording, transcription, generation or publishing is fabricated. Import/recording buttons explain their unavailable service and do not access files/microphone or create meetings. Those business flows belong to later phases.
