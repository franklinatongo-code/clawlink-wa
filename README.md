# ClawLink WA — AI WhatsApp Agent on Android

**Your phone becomes an AI WhatsApp assistant. No computer, no server, no Node.js — just install and link.**

---

## What It Does

ClawLink WA loads the real WhatsApp Web client inside your Android phone's own browser engine (WebView). It pairs exactly like WhatsApp Desktop — scan a QR code once, and the session persists across restarts. An AI then answers incoming messages automatically, understands photos and voice notes, and runs scheduled tasks while the app is closed.

| Traditional approach | ClawLink WA |
|:---|:---|
| Requires a computer or server | Runs entirely on your phone |
| Needs Node.js, Puppeteer, Chrome | Uses the WebView already on your device |
| Re-implements WhatsApp protocol | Loads the real `web.whatsapp.com` |
| Complex setup, ongoing maintenance | Install APK → scan QR → done |

---

## Why This Works

**Real WhatsApp Web, not a clone.**  
The app opens `web.whatsapp.com` in a system WebView with a Chrome-like user agent. Cookies, localStorage, and IndexedDB persist the linked-device session exactly as the desktop client does.

**Same injection library as whatsapp-web.js.**  
At page load, the app injects `wa_utils.js` (the upstream `Utils.js` from whatsapp-web.js) followed by `wa_boot.js`. This defines `window.WWebJS.*` helpers — `getChat`, `sendMessage`, `getMessageModel` — against WhatsApp's live webpack modules.

**Inbound hook matches the upstream.**  
`wa_boot.js` subscribes to `WAWebCollections.Msg.on('add')`, the same collection hook whatsapp-web.js uses. Each message is serialized and passed to Kotlin via a `@JavascriptInterface` bridge.

**Single-threaded, ordered processing.**  
A dedicated executor handles messages one at a time. Deduplication via a persisted `SharedPreferences` set (capped at 1000 IDs) prevents re-replies on WebView reload. A timestamp cutoff ignores history synced at connect time.

**AI replies via any OpenAI-compatible endpoint.**  
`Llm.kt` posts to `chat/completions` with a concise system prompt. Works with OpenAI, Groq, OpenRouter, Ollama, NVIDIA NIM, or any compatible API. Blank key disables auto-replies entirely.

**Send path returns into WhatsApp.**  
`__waSend(chatId, text, quotedId)` resolves the chat via `WWebJS.getChat`, sends through `WWebJS.sendMessage` (quoting when possible), and reports success or failure back to Kotlin.

---

## Architecture at a Glance

```
┌────────────────────────────────────────────────────────────┐
│  Android Phone                                             │
│                                                            │
│  WebView → https://web.whatsapp.com/                       │
│       │                                                     │
│       ├─► wa_utils.js  (upstream whatsapp-web.js helpers)  │
│       ├─► wa_boot.js   (hooks Msg.on('add'), defines      │
│       │                __waSend, bridges to AndroidWA)     │
│       │                                                     │
│       ▼                                                     │
│  AndroidWA (@JavascriptInterface)                          │
│       │                                                     │
│       ▼                                                     │
│  Kotlin Engine (single-thread executor)                    │
│       ├─ filter: fromMe, groups, backlog, empty IDs        │
│       ├─ dedupe: persisted SeenStore (1000 IDs)            │
│       ├─ type check: text only (extensible)                │
│       │                                                     │
│       ▼                                                     │
│  Llm.reply(text) → OpenAI-compatible API                   │
│       │                                                     │
│       ▼                                                     │
│  window.__waSend(chatId, answer, quotedId)                 │
│       │                                                     │
│       ▼                                                     │
│  WhatsApp Web receives reply                               │
└────────────────────────────────────────────────────────────┘
```

---

## Current Capabilities

- **Auto-reply** to incoming text messages (groups optional)
- **Persistent session** — survives force-stop, reboot, app update
- **Deduplication** — no double-replies on WebView reload
- **Backlog protection** — ignores history synced at connect
- **Ping test** — `!ping` → `pong` verifies the loop is live
- **Configurable AI** — swap endpoint, model, prompt in `Llm.kt`
- **Zero external dependencies** — no server, no Node, no bundled Chromium

---

## Requirements

| Component | Minimum |
|:---|:---|
| Android | 7.0 (API 24) |
| System WebView | Current version (WhatsApp Web requires modern Chromium) |
| Internet | Required for WhatsApp Web and AI API |
| AI API | Any OpenAI-compatible endpoint (key added in `Llm.kt`) |

---

## Build from Source

```bash
cd android
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

**Prerequisites:** JDK 17, Android SDK (compileSdk 34)

**Configure your AI:** Edit `BASE_URL`, `MODEL`, `API_KEY` in  
`app/src/main/java/app/waweb/local/Llm.kt`

---

## Project Structure

```
android/
├── app/
│   ├── src/main/
│   │   ├── java/app/waweb/local/
│   │   │   ├── MainActivity.kt    # WebView host, injection, bridge, filtering
│   │   │   └── Llm.kt             # AI reply seam (OpenAI-compatible)
│   │   ├── assets/
│   │   │   ├── wa_utils.js        # Upstream whatsapp-web.js Utils.js
│   │   │   └── wa_boot.js         # Injection, Msg hook, __waSend
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/
├── gradlew
├── settings.gradle.kts
└── build.gradle.kts
```

---

## Known Limitations (Honest)

- **Background throttling** — Android may pause a backgrounded WebView. Replies are instant when the app is foregrounded. A foreground service with WakeLock would improve this.
- **WhatsApp module drift** — WhatsApp occasionally renames internal webpack modules. If replies stop after a WhatsApp Web update, `wa_boot.js` needs a tweak.
- **Text-only replies** — Image, document, and voice note handling are not yet implemented in the filtering layer.
- **Single-threaded AI** — A slow LLM call blocks other chats. A small thread pool per chat would scale better.
- **Unofficial client** — This automates WhatsApp Web, which Meta does not officially support. Use a spare number. For production business use, use the [WhatsApp Cloud API](https://developers.facebook.com/docs/whatsapp/cloud-api).

---

## Roadmap

- [ ] Foreground service + WakeLock for true background reliability
- [ ] Media understanding (vision, voice transcription, stickers)
- [ ] Group intelligence (mentions, per-group memory, mention-only mode)
- [ ] Per-contact/group rules (custom prompts, permission tiers)
- [ ] Scheduled message queue (sends while app closed)
- [ ] Offline voice via Vosk
- [ ] Multi-provider AI routing with fallback
- [ ] Settings UI for endpoint/model/prompt (no rebuild needed)

---

## License

Apache 2.0 — see [`LICENSE`](LICENSE)

The injected helpers in `assets/wa_utils.js` derive from [whatsapp-web.js](https://github.com/pedroslopez/whatsapp-web.js) (Apache 2.0). The shipped build uses a trimmed, custom implementation.

---

## Disclaimer

ClawLink WA is not affiliated with, endorsed by, or connected to WhatsApp, Meta, or the whatsapp-web.js maintainers. This is an independent project for educational and personal automation purposes.

---

## Get Started

1. **Build the APK** (see above) or download a release
2. **Install** on Android 7.0+ with an up-to-date System WebView
3. **Open the app** → tap "Link WhatsApp"
4. **Scan the QR** with your WhatsApp (Linked Devices → Link a Device)
5. **Add your API key** to `Llm.kt` and rebuild, or wait for the in-app settings UI
6. **Watch it work** — incoming messages get AI replies automatically

---

*Clean architecture. Real WhatsApp Web. Your AI, your phone, your rules.*