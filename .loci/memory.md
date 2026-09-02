# Product Direction

- LifeWise is a consumer AI life decision and execution assistant for people with limited independent-living experience, not a general Agent platform.
- Product outcome: help a user complete a real-world task through guided diagnosis, actionable steps, safety boundaries, and outcome confirmation.
- All ten scene entries now use task-appropriate guided workflows: repair troubleshooting; cooking and housework execution; health and pet safety triage; shopping, fashion, and meal-plan decisions; etiquette rehearsal; and writing draft iteration.
- Guided choices fill the composer instead of immediately sending, so users can add context; submitting locks that card's choices and outcome actions to prevent duplicate requests.
- `npm run test:ai` performs live acceptance for all ten scenes using process-only `AI_API_KEY`; the response cap matches application requests at 4096 tokens.
- Failed AI calls do not persist a user message, preventing a retry from duplicating conversation context. Guided-scene JSON parse failures show a readable recovery state instead of raw model payload.
- Failed chat cards retain a retry payload (message, scene, follow-up mode, and image URL) and reuse the same card. Scene-image polling stops after three minutes, cleans up on unmount, and exposes retry after failure or timeout.
- Live AI acceptance retries transient 429, 5xx, and network failures up to three attempts with exponential backoff. Export, writing-mode, and image quick actions are native keyboard-reachable buttons.
- Browser and Android both stream responses. Android resolves the native completion event before asynchronously removing listeners; non-streaming JSON remains only as a fallback.
- Chat accepts one request at a time and always clears loading/current-typing state plus stale typing cards in `finally`.
- Android recovery: remember the active conversation for two minutes; when an AI reply is persisted, dispatch `lifewise-conversation-updated` so an active/remounted chat reloads the saved messages.
- The chat title is editable for persisted conversations. Key icon-only controls expose accessible names and a visible keyboard focus indicator.
- Reuse the existing chat history and structured scene cards for the first version. Do not add a general workflow engine or persistent agent-state table yet.
- Relevant references: Rasa for slot filling and clarification; Dify for workflow/evaluation; Open WebUI for local model, memory, and chat interactions; Coze Studio for future agent orchestration; Home Assistant for device-state modeling.
