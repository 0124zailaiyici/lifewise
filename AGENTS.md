# LifeWise agent notes (compact)

## Mandatory: prevent Codex/AiMaMi 413
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
- Endpoints needing `userId` must not be auth-whitelisted.

## Frontend/build lessons
- After frontend edits, run `vite build`; deploy actual `dist`, not dev server assumptions.
- If export menu not updated, likely stale `dist` or failed Actions artifact.
- Chat.vue encoding corruption (mojibake, broken identifiers) can break Vite; build locally before commit.
- Scene SVGs must match scenario (cooking/outfit/repair/etc.).

## Image generation asset hygiene
- Generated images must be copied to project dir (e.g. `uploads/food-images/v1/`), update index, verify, then delete source from temp dir to avoid C drive bloat.

## Upload 413 runbook
- App upload 413 with nginx page: align frontend max, Spring multipart (10MB file / 20MB request), and Nginx `client_max_body_size 20m`+.
- Verify server: `nginx -T | grep -n "client_max_body_size"`, `nginx -t && systemctl reload nginx`.
- Codex/AiMaMi 413 is model-router payload too large, not app upload. Shrink prompt context.

---

## Scene cards (Claude-style structured layout) -- 2026-06-10
- `renderStructured()` in Chat.vue renders JSON as warm Claude-style cards.
- Detects 9 scenes (else-if order): cooking, fashion, health, shopping, repair, housework, etiquette, pet, mealplan.
- Card header: scene icon + label + title with gradient accent.
- Tags row: difficulty/time/servings.
- Scene sections: ingredients (checklist), steps (numbered, with time/tip/warning), tools (tag list), style/color_palette (swatches), outfits/items (with icons), accessories, common_mistakes, selection_steps, severity badge, etc.
- Follow-up question chips auto-generated per scene.
- All s-* CSS in a NON-SCOPED `<style>` block (scoped styles don't penetrate v-html).

## Scene image generation -- 2026-06-10
- `generateScenePrompt(data, scene)` creates English prompts for fashion/shopping scenes (DashScope/Qwen).
- `attachSceneImage(message, prompt, scene)` triggers gen after AI response.
- `pollSceneImage(message, prompt, taskId, provider)` polls async task every 3s.
- Scene image plan in `docs/scene-image-plan.md`.

## Health scene fixes (2026-06-11)
- Backend schema: symptoms[], causes[], advice[{item,detail}], when_to_see_doctor, prevention
- Frontend renders full structured card for health queries.
- max_tokens increased to 16384 to prevent truncation.
- Prevention section: only renders if non-empty string content.

## Database: MySQL default (2026-06-12)
- application.yml default profile set to `mysql` (no more accidental H2 usage).
- MySQL80 service (root/123456), database `lifewise`.
- Start: `net start MySQL80`. If reserved: `net stop winnat` then start.

## Backend startup
- cd backend && mvn spring-boot:run (mysql profile default)
- JAVA_HOME=D:\jdk-home\jdk-17

## Frontend startup
- cd frontend && npx vite --host (port 5173)

## AI audit known issue
- Profile shows recent AI calls but without conversationId/title.
- User feedback: cannot tell which conversation triggered the call.

## Dashboard 30-day activity -- 2026-06-09/10
- Replaced scrollable bar chart with clean 4-stat card layout.
- Weekly summary table below. No overlapping.

## Font size & UI tweaks -- 2026-06-09/10
- Favorites cat-tag/date font 12px.
- KB filter: fab-style floating button.
- Favorites filter dropdown: right-aligned.
- Profile bottom tab style unified.

## Encoding hazard (PowerShell Set-Content)
- `Set-Content -NoNewline` corrupts multi-byte UTF-8 in .vue files.
- Fix: `git checkout -- file` then re-edit with proper UTF-8 write.

## Scene prompt config extract (2026-06-12)
- `AiServiceImpl.java`: `buildSystemPrompt()` now reads from config instead of hardcoded text blocks.
- New `ScenePromptProperties.java`: Spring @ConfigurationProperties bound to `scene.*` in `application.yml`.
- File reduced from 537->345 lines, 22KB->16KB. Schema changes no longer need Java recompile.
- Scene schemas in `application.yml` under `scene.schemas.*`, follow-up prompts under `scene.follow-up.*`.

## Mobile responsiveness (2026-06-12)
- `App.vue` added proper `@media` queries for mobile/tablet/desktop breakpoints.
- Desktop view (> 768px): phone-like rounded frame (32px radius) with warm shadow on gradient background.
- Tablet (481-768px): centered with shadow + side borders.
- Mobile (<= 480px): full-width, unchanged.

## Lessons: position:fixed vs position:sticky for bottom tabs (2026-06-12)
- Bottom tabs MUST use position:fixed; bottom:0; left:50%; transform:translateX(-50%) NOT position:sticky.
- sticky causes tabs to float mid-page when content is shorter than viewport (no scrolling = no "stick" trigger).
- overflow-x:clip on .page-container can interfere with touch/click events on mobile for fixed children.
- Desktop @media with overflow:hidden + order-radius on page-container creates hit-test issues for fixed tabs.
- Rule: don't put overflow props on page-container when bottom tabs are fixed children inside it.
- Favorites filter dropdown: position:absolute with left when parent uses justify-content:flex-end causes wrong popup position. Always use ight when button is on the right side.## Auto-deploy watchdog (2026-06-12)
- Server has systemd service lifewise-watchdog running /opt/lifewise/auto-deploy.sh
- Polls GitHub API every 30s for new commits on master, auto-deploys frontend
- To check: systemctl status lifewise-watchdog; tail -f /opt/lifewise/auto-deploy.log
- Service file: deploy/lifewise-watchdog.service; Script: deploy/auto-deploy.sh

