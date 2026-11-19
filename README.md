# ClawLink WA

**AI WhatsApp auto-reply that runs entirely on your phone — one APK, no Node, no bundled Chromium.**

ClawLink WA loads the real [WhatsApp Web](https://web.whatsapp.com) inside Android's system WebView. That means it uses WhatsApp's own linked-device client (QR pairing, encryption, media, sync) and layers an AI reply engine on top — instead of re-implementing the WhatsApp protocol.

![ClawLink WA home](screenshots/01-home.png)
![ClawLink WA tools](screenshots/02-tools.png)

## Download

**First working version → [v1.0.0](https://github.com/franklinatongo-code/clawlink-wa/releases/tag/v1.0.0)**

APK: [`ClawLink-WA-1.0.apk`](https://github.com/franklinatongo-code/clawlink-wa/releases/download/v1.0.0/ClawLink-WA-1.0.apk)

## Features

- **Real WhatsApp linking** — QR / linked-device flow from the real web client, no protocol shortcuts.
- **Persistent session** — cookies + IndexedDB survive app restarts and reboots; no re-scan unless you switch accounts.
- **AI auto-reply** — incoming chats are answered by your AI. Multiple providers supported through an OpenAI-compatible endpoint.
- **Chats & AI replies view** — read the conversation and what the AI said.
- **Live WhatsApp Web** — inspect the real web client inside the app.
- **Switch account** — cleanly clears the session and shows a fresh QR.
- **Tools** — automation hooks, quiet hours, away replies, welcome messages, cooldowns, auto-react, and more.
- **Small engine** — WhatsApp layer is ~70 KB of injected JS; the browser is the one already on your phone.

## How it works (short)

```
web.whatsapp.com (system WebView)
   │  wa_utils.js  → window.WWebJS.*
   │  wa_boot.js   → Msg.on('add') / __waReply / typing / presence / stickers
   ▼
AndroidWA  (@JavascriptInterface)
   ▼
Kotlin engine → your AI → window.__waReply() → WhatsApp
```

- `wa_utils.js` is the injected helper layer derived from [`whatsapp-web.js`](https://github.com/pedroslopez/whatsapp-web.js) (Apache-2.0).
- The session is the WebView's own storage (cookies + IndexedDB), flushed on pause.
- Nothing is templated: the AI returns the reply text, and that text is sent.

## Install

1. Download `ClawLink-WA-1.0.apk` from **Releases**.
2. Allow "Install unknown apps" for your browser/file manager.
3. Open the app → **Link WhatsApp** → on your phone open **WhatsApp → Linked devices → Link a device** → scan the QR.
4. Configure your AI endpoint/key in the app.

Or over USB:

```bash
adb install ClawLink-WA-1.0.apk
```

## Requirements

- Android 7.0+ (minSdk 24), targetSdk 36
- A current **Android System WebView** (an old WebView cannot load WhatsApp Web reliably)
- Internet
- An AI API key / endpoint (OpenAI-compatible; NVIDIA NIM endpoint is wired in)

## Known limits

- **Unofficial.** It drives the WhatsApp Web client, which WhatsApp does not officially support for automation. Use a spare number — bans are possible. For commercial use, use the official WhatsApp Cloud API.
- **Background behavior.** Android may pause a backgrounded WebView; the connection survives, but do not expect instant replies while the app is fully backgrounded forever.
- **WhatsApp Web updates.** WhatsApp occasionally renames internal modules. If replies stop after an update, the injected layer needs a fix.
- **Placeholder package.** This 1.0 build uses `com.example` as the application ID; a future build should use the real package name.

## Version

- Version: `1.0` (versionCode `1`)
- Package: `com.example`
- minSdk 24 · targetSdk 36

## Third-party

The injected helpers are derived from `whatsapp-web.js`, licensed under Apache-2.0. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [licenses/whatsapp-web.js-LICENSE](licenses/whatsapp-web.js-LICENSE).
