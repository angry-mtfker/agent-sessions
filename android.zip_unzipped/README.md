# Agent Swarm — Android app

WebView wrapper for `https://perchance.org/agent-swarm` with embedded Termux-style shell.

## What is embedded

No separate install required for basic shell:

- `TermuxBridge.java` — `@JavascriptInterface` exposing `exec(cmd, timeoutMs)` and `info()`. Runs `sh -c` via `ProcessBuilder` inside the app sandbox (toybox on-device). The web UI calls it as the `android_shell` tool.
- `LocalExecServer.java` — dependency-free `ServerSocket` on `127.0.0.1:8766` (tries 8766-8785). Endpoints: `GET /health`, `POST /exec {cmd, timeoutMs}`. Full CORS (`Access-Control-Allow-Origin: *` + OPTIONS). Lets phone browsers, Termux node, and agents reach the same shell over HTTP.
- `TermuxIntents.java` — optional handoff to the real Termux app (`com.termux.app.RUN_COMMAND`, F-Droid link, `SETUP_SH` bootstrap that installs node + writes `~/.agent-swarm/mcp-server.js` listening on `0.0.0.0:8080`).

Timeouts (all layers honor the web UI's per-tool timeout, up to 300s): bridge `exec` clamp 300s, `LocalExecServer` clamp 300s + 310s socket timeout, Termux node script reads `timeoutMs` from the request JSON (5–300s, SIGKILL on expiry). File tools (`fs_list/read/write/edit`), agent reasoning display, heartbeats, and model directory need no native changes — they run at the web/shell layer on top of `/exec`.
- `MainActivity.java` — starts the exec server, adds the JS bridge, loads the live URL.

## Build (Android Studio)

1. Open `src/android` in Android Studio.
2. Gradle sync, Run on phone or Build > Build APK.
3. Open Tools tab → Android card shows `APK embedded shell: online`.

## Termux app (optional, full Linux + local LLM)

1. Install Termux from F-Droid.
2. Tools → Android card → Copy setup script → paste in Termux.
3. `node ~/.agent-swarm/mcp-server.js` then add `http://127.0.0.1:8080` as MCP/server or use `android_shell`.
4. Local LLM: `pkg install -y llama.cpp && mkdir -p ~/models`, copy a `.gguf` in, then either `llama-server -m ~/models/qwen2-1.5b.gguf --host 0.0.0.0 --port 8080` (add as OpenAI model `http://127.0.0.1:8080/v1`) or register a CLI model with the GGUF path.

## PWA fallback

Chrome → Add to Home screen works, but `TermuxBridge` is absent — the UI falls back to `http://127.0.0.1:8080/exec` (Termux MCP server must be running with CORS).

## Files

- `app/src/main/java/com/perchance/agentswarm/MainActivity.java`
- `app/src/main/java/com/perchance/agentswarm/TermuxBridge.java`
- `app/src/main/java/com/perchance/agentswarm/LocalExecServer.java`
- `app/src/main/java/com/perchance/agentswarm/TermuxIntents.java`
- `app/src/main/AndroidManifest.xml` — INTERNET + queries for com.termux
