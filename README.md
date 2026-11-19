# 🚀 ClawLink WA — AI WhatsApp Auto-Reply on Android

<p align="center">
<b>Install → Scan QR → Your AI answers your WhatsApp chats automatically.</b>
</p>

<p align="center">
<a href="https://github.com/franklinatongo-code/clawlink-wa/releases/latest"><img src="https://img.shields.io/github/v/release/franklinatongo-code/clawlink-wa?label=Latest%20Release&color=25D366&style=for-the-badge" alt="Latest Release"/></a>
<a href="#"><img src="https://img.shields.io/badge/Android-7%2B-3DDC84?logo=android&style=for-the-badge" alt="Android"/></a>
<a href="#"><img src="https://img.shields.io/badge/WhatsApp%20Web-Automation-25D366?logo=whatsapp&style=for-the-badge" alt="WhatsApp Web Automation"/></a>
<a href="#"><img src="https://img.shields.io/badge/AI%20Auto--Reply-Yes-7C4DFF?style=for-the-badge" alt="AI Auto Reply"/></a>
<a href="#"><img src="https://img.shields.io/badge/No%20Server-Yes-555&style=for-the-badge" alt="No Server"/></a>
<a href="#"><img src="https://img.shields.io/badge/License-Apache%202.0-green?style=for-the-badge" alt="License"/></a>
<a href="https://github.com/franklinatongo-code/clawlink-wa/releases/latest/download/ClawLink-WA-1.0.apk"><img src="https://img.shields.io/badge/Download%20APK-24MB-blue?style=for-the-badge" alt="Download APK"/></a>
</p>

---

### What it does

**ClawLink WA turns your Android phone into an AI WhatsApp auto-reply machine.** It loads the real WhatsApp Web client inside your phone's own browser (WebView), pairs via QR just like WhatsApp Desktop, and lets an AI answer incoming chats for you — **24/7, hands-free.**

No computer. No Node.js. No self-hosted server. No re-inventing the WhatsApp protocol. Just install the APK on your phone, link your number once, and let the AI do the talking.

<p align="center">
<img src="screenshots/01-home.png" width="260" alt="Home screen showing link status, chats, messages, AI replies"/>
<img src="screenshots/02-tools.png" width="260" alt="Tools screen with automation settings"/>
</p>

---

## ✨ Features

- 🤖 **AI auto-reply** — your AI answers incoming WhatsApp chats automatically
- 🔗 **Real QR linking** — pairs as a genuine WhatsApp linked device
- 💾 **Persistent session** — survives app restarts and phone reboots; no re-scanning
- 💬 **Chats & AI replies view** — see conversations and what the AI said
- 🌐 **Live WhatsApp Web** — open the real web client inside the app
- 🔄 **Switch account** — cleanly clears the session for a fresh QR
- 🛠 **Tools** — automation, quiet hours, away/welcome replies, cooldowns, auto-react emoji
- 📱 **Single APK** — ~24 MB, installs on any Android 7.0+ phone

---

## 🚀 Get started in 60 seconds

**1. Download**

Get the first working build from **Releases**:

→ [Download ClawLink-WA-1.0.apk](https://github.com/franklinatongo-code/clawlink-wa/releases/latest/download/ClawLink-WA-1.0.apk)

**2. Install**
Enable "Install unknown apps" for your browser or file manager, then open the APK.  
Or from a computer:

```bash
adb install ClawLink-WA-1.0.apk
```

**3. Link WhatsApp**
Open ClawLink WA → **Link WhatsApp** → on your phone go to **WhatsApp → Linked devices → Link a device** → scan the QR shown in the app.

**4. Configure your AI**
Enter your AI endpoint/key (OpenAI-compatible or NVIDIA NIM). Your AI now answers incoming chats automatically.

---

## 🔨 Build from source

The full Android project is in [`android/`](android). You need JDK 17 and the Android SDK (compileSdk 35).

```bash
cd android
./gradlew assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk
```

To point the AI at your own endpoint, edit the three constants at the top of
[`Llm.kt`](android/app/src/main/java/app/waweb/local/Llm.kt) (`BASE_URL`, `MODEL`, `API_KEY`), then rebuild.

> The source module builds as `app.waweb.local`; the published v1.0 APK was packaged from an earlier build as `com.example`.

---

## 🧠 How it works

ClawLink WA does **not** re-implement WhatsApp. It loads the real `web.whatsapp.com` inside Android's system WebView — the same browser already on your phone — and injects a tiny JavaScript helper that talks back to the app.

```
┌─────────────────────────────────────────────────────────┐
│  Your phone                                             │
│                                                         │
│  WhatsApp Web (real client, inside WebView)             │
│  ┌─────────────┐                                       │
│  │  New msg ✉  │ ──►  Msg.on('add')                    │
│  └─────────────┘      (wa_boot.js)                      │
│                                                         │
│  AndroidWA  (@JavascriptInterface)                      │
│     inbound JSON ──────────────────────────► Kotlin     │
│                                                         │
│  Kotlin engine                                          │
│     filter → dedupe → seen → typing ON                  │
│                                                         │
│  Your AI (OpenAI-compatible / NVIDIA NIM)               │
│     writes the reply                                    │
│                                                         │
│  Kotlin engine                                          │
│     typing OFF → __waReply() → WhatsApp                 │
│                                                         │
│  ┌─────────────┐                                       │
│  │ Sent ✅     │ ◄────── window.WWebJS.sendMessage      │
│  └─────────────┘                                       │
└─────────────────────────────────────────────────────────┘
```

1. **Pair** — scan the QR with WhatsApp → Linked devices → Link a device
2. **Listen** — `wa_boot.js` hooks `Msg.on('add')` and forwards new messages through `AndroidWA`
3. **Think** — Kotlin calls `Llm.reply(chatId, text)`; your AI writes the words
4. **Send** — `__waSend()` (the shipped build exposes it as `__waReply`) sends the reply back into WhatsApp, optionally quoting the original message

> [!quote] Nothing is templated
> The AI returns the reply text, and that exact text is what gets sent.

### The details, step by step

**1. The shell — a WebView that pretends to be Chrome**
One full-screen `WebView` loads `https://web.whatsapp.com/`. JavaScript, DOM storage, the database API, and third-party cookies are enabled — that storage *is* where the linked-device session lives. File access is disabled. The user-agent is the system one with `"; wv"` and `"Version/4.0 "` stripped, because WhatsApp serves a cut-down "download the app" page to a stock WebView UA (Chrome gets the real client).

**2. Origin lock — the bridge only talks to WhatsApp**
`WebViewClient.shouldOverrideUrlLoading` cancels every navigation whose host is not `web.whatsapp.com`, `*.whatsapp.com`, or `*.whatsapp.net`. So the `@JavascriptInterface` bridge (`AndroidWA`) is only ever exposed to WhatsApp's own origin — not to arbitrary pages the WebView might wander onto.

**3. Injection — two files, in order**
When the page finishes loading on `web.whatsapp.com`, the app evaluates `assets/wa_utils.js` and then `assets/wa_boot.js`:
- `wa_utils.js` is the upstream `whatsapp-web.js` `Utils.js`, unmodified. It defines `__loadWWebUtils()`.
- `wa_boot.js` waits for WhatsApp's internal webpack loader (`window.require`) and the `WAWebCollections` module to exist, calls `__loadWWebUtils()`, and only then proceeds. That defines the whole `window.WWebJS.*` helper set (`getChat`, `sendMessage`, `getMessageModel`, `sendChatstate`, `toStickerData`, …) against the **live** WhatsApp app.

Because it retries instead of failing, injection can happen *before* login — scanning the QR does not require a reload.

**4. Inbound — hook the message collection**
`wa_boot.js` subscribes to `window.require('WAWebCollections').Msg.on('add')` (the same collection whatsapp-web.js hooks). For each event it:
- ignores non-new messages and `revoked` (deleted) ones,
- if the message is still `ciphertext` (not yet decrypted), waits for its `change:type` event and processes the decrypted version,
- serialises the message with `WWebJS.getMessageModel(msg)` and hands the JSON to Kotlin via `AndroidWA.onMessage(json)`.

`onMessage` runs on a **single-thread executor**, so messages are processed one at a time, in order.

**5. Filtering — what deserves a reply**
In Kotlin, `handleIncoming` reads the message id, `fromMe`, `remote` chat id, type, body, and timestamp, then drops:
- messages **you** sent (`fromMe`), empty ids, `status@broadcast`,
- group chats (unless enabled),
- **backlog**: anything timestamped before the app started (minus a 30 s grace) is history synced on connect, not a new message,
- **duplicates**: message ids are kept in a persisted, capped (1000-entry) `SharedPreferences` set (`SeenStore`), so reloading the WebView never re-replies to the same message.

Only `type == "chat"` (plain text) is answered; images/documents are ignored for now. A built-in `!ping` → `pong` check confirms the loop is alive.

**6. Think — the AI writes the words**
If auto-reply is on and an API key is set, Kotlin calls `Llm.reply(text)`, which POSTs an OpenAI-compatible `chat/completions` request (system prompt: *"reply naturally and briefly, plain text, no markdown"* + the user's message) with a 15 s connect / 60 s read timeout. Any OpenAI-shaped endpoint works — OpenAI, Groq, OpenRouter, NVIDIA NIM, or a local Ollama. The returned string is used verbatim. Blank key ⇒ the whole AI path is disabled and nothing is auto-sent.

**7. Send — back into WhatsApp**
Kotlin evaluates `window.__waSend(chatId, text, quotedId)`. That helper resolves the chat with `WWebJS.getChat`, sends through `WWebJS.sendMessage` (quoting the original message when possible, falling back to an unquoted send if the quote fails), and reports `onSent` / `onError` back to Kotlin. Optional helpers cover typing indicators (`sendChatstate`), presence, read receipts, and stickers (`toStickerData` + `sendMediaAsSticker`).

**8. Persistence — survive restarts and reboots**
The linked-device session lives in the WebView's cookies + DOM storage/IndexedDB. `CookieManager.flush()` is called in `onPause`, so the login is written to disk; on the next launch the same WhatsApp Web session resumes without re-scanning.

### Project layout

Source lives in [`android/`](android) — a standard, buildable Gradle project.

| File | Role |
|---|---|
| [`MainActivity.kt`](android/app/src/main/java/app/waweb/local/MainActivity.kt) | WebView host: settings, UA spoof, origin lock, injection, inbound filtering, dedupe, send, lifecycle |
| [`Llm.kt`](android/app/src/main/java/app/waweb/local/Llm.kt) | The swap-in AI seam — `reply(text): String?` against any OpenAI-compatible endpoint |
| [`wa_boot.js`](android/app/src/main/assets/wa_boot.js) | Waits for WhatsApp internals, defines `__waSend`, hooks `Msg.on('add')` + socket state |
| [`wa_utils.js`](android/app/src/main/assets/wa_utils.js) | Upstream `whatsapp-web.js` helpers (`window.WWebJS.*`), loaded into the page |
| [`AndroidManifest.xml`](android/app/src/main/AndroidManifest.xml) | Permissions, single-activity manifest |

### Why no Node / no server

`whatsapp-web.js` normally drives WhatsApp Web by remote-controlling a headless Chrome via Puppeteer. Here the browser is **already** on the phone (the system WebView), and the same helper library is injected straight into the page, so it talks to WhatsApp's live webpack modules directly. That removes Node, Puppeteer, a bundled Chromium, and the whole "run this on a PC" step — at the cost of depending on WhatsApp's internal module names, which occasionally change.

---

## 🔍 Search tags

`whatsapp automation` · `whatsapp auto-reply` · `whatsapp bot android` · `whatsapp web automation` · `ai whatsapp assistant` · `no-server android app` · `webview whatsapp` · `linked devices`

---

## 📱 Requirements

- Android 7.0+ for the published APK (the source project builds with minSdk 26)
- Up-to-date **Android System WebView** — WhatsApp Web needs a modern WebView
- Internet connection
- An AI API key/endpoint (OpenAI-compatible; NVIDIA NIM endpoint is built in)

---

## ⚠️ Honest notes

- **Unofficial client.** This drives the WhatsApp Web client, which Meta does not officially support for automation. Use a **spare number** — bans are possible. For a business, use the official WhatsApp Cloud API.
- **Background pauses.** Android may throttle a backgrounded WebView; replies are instant when the app is open.
- **WhatsApp changes.** WhatsApp sometimes renames internal page modules; if replies stop after an update, the injected helper needs a tweak.
- **Package name.** Build 1.0 uses `com.example` — a real package name is coming in the next release.

---

## 🔖 Version & package

- Version: **1.0** (versionCode `1`)
- Package: `com.example`
- minSdk 24 · targetSdk 36 · ~24 MB APK

---

## 📄 Third-party

The injected JavaScript helpers derive from [`whatsapp-web.js`](https://github.com/pedroslopez/whatsapp-web.js) (Apache-2.0). See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) and [`licenses/whatsapp-web.js-LICENSE`](licenses/whatsapp-web.js-LICENSE).

ClawLink WA is not affiliated with or endorsed by WhatsApp, Meta, or the whatsapp-web.js maintainers.

---

<p align="center">
<b>Star ⭐ this repo if you want to see WhatsApp auto-reply keep evolving.</b>
</p>
