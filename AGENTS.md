# LifeWise agent notes (compact)

## 🚨 Mandatory: prevent Codex/AiMaMi 413
- If error URL looks like `127.0.0.1:*/codex/router/v1/responses`, root cause is Codex/AiMaMi model-router payload too large, NOT LifeWise app Nginx upload.
- Never paste full AGENTS.md, big logs, build outputs, directory dumps, DB data, images, zips, jars, or generated assets into chat/context.
- Never run broad recursive reads/searches over whole repo unless excluding: `.git`, `.codex`, `node_modules`, `dist`, `target`, `uploads`, `data`, `*.log`, `*.jar`, `*.zip`, images, PDFs.
- Before debugging anything after a 413, first shrink context: keep AGENTS.md compact, rely on `.codexignore`, read only exact source files needed.
- This mistake has forced many session restarts. Treat context-size hygiene as a blocking rule.
## Diagnose before fixing
- If error page says `nginx`, check active Nginx config first (`nginx -T`), not just app code.
- Code change flow: edit source -> build -> stop old process -> start new process -> verify running code -> deploy.
- Verify runtime, not only source: Windows `netstat -ano | findstr :8080` + `wmic process where "processid=PID" get commandline`; Linux `ps aux | grep java | grep lifewise`.
- Avoid recursive searches over `node_modules`, `dist`, DB, large logs, uploads, generated images; can trigger Codex/AiMaMi 413.

## Deployment/runtime
- Server Nginx: `/etc/nginx/sites-enabled/lifewise`; frontend: `/opt/lifewise/frontend/`; backend config: `/opt/lifewise/application-cloud.properties`.
- Before backend deploy: backup `data/lifewise.mv.db`; kill old `lifewise-backend` processes; check port 8082 conflicts.
- Also check hidden systemd service: `systemctl list-units --type=service | grep lifewise`; old `/app/lifewise` service may restart old JAR. Stop+disable if needed.
- GitHub Actions backend may fail; frontend build is separated. Frontend artifacts:
  - full: `https://nightly.link/0124zailaiyici/lifewise/workflows/deploy/master/lifewise-deploy.zip`
  - frontend: `https://nightly.link/0124zailaiyici/lifewise/workflows/deploy/master/lifewise-frontend.zip`
- Server Node may be too old; Vite needs Node 18+. Use NodeSource 20; if `libnode-dev` overwrite error, remove `libnode-dev` first.

## AI/API cost safety
- Default provider must be Qwen/DashScope, not DeepSeek. Never hardcode provider in old/new backend paths.
- If Qwen key missing, return clear error; never silently fall back to DeepSeek.
- `deploy/start.sh` must export `AI_DASHSCOPE_KEY` / `ai.dashscope-api-key`.
- Verify no DeepSeek traffic after changes; leaked keys must be reset immediately; keep keys in env vars only.
- Image generation default off; context rounds default 5.

## Auth/data rules
- User data, conversations, KB, cache are private per user; DB reset loses all.
- Endpoints needing `userId` must not be auth-whitelisted. If `Missing request attribute 'userId'`, inspect `JwtAuthFilter` whitelist.

## Frontend/build lessons
- After frontend edits, run `vite build`; deploy actual `dist`, not dev server assumptions.
- If export menu not updated, likely stale `dist` or failed Actions artifact.
- Chat.vue encoding corruption (`��`, broken identifiers) can break Vite; build locally before commit.
- Scene SVGs must match scenario (cooking/outfit/repair/etc.).

## Upload 413 runbook
- App upload 413 with nginx page: align frontend max, Spring multipart (`10MB` file / `20MB` request), and Nginx `client_max_body_size 20m`+.
- Verify server: `nginx -T | grep -n "client_max_body_size"`, `nginx -t && systemctl reload nginx`, then upload >1MB.
- Codex/AiMaMi 413 to `127.0.0.1:*/codex/router/v1/responses` is model-router payload too large, not LifeWise app upload. Reduce prompt/context: compress AGENTS.md, ignore large files, avoid pasting logs/binaries.

