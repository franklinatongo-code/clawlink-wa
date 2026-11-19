# ClawLink WA

<p align="center">
<b>AI WhatsApp auto-reply on Android — one tap, one APK, zero servers.</b>
</p>

<p align="center">
<a href="https://github.com/franklinatongo-code/clawlink-wa/releases/latest"><img src="https://img.shields.io/github/v/release/franklinatongo-code/clawlink-wa?label=Latest%20Release&color=25D366&style=for-the-badge" alt="Latest Release"/></a>
<a href="#"><img src="https://img.shields.io/badge/Android-7%2B-3DDC84?logo=android&style=for-the-badge" alt="Android"/></a>
<a href="#"><img src="https://img.shields.io/badge/WhatsApp%20Web-Automation-25D366?logo=whatsapp&style=for-the-badge" alt="WhatsApp Web Automation"/></a>
<a href="#"><img src="https://img.shields.io/badge/AI%20Auto--Reply-Yes-7C4DFF?style=for-the-badge" alt="AI Auto Reply"/></a>
<a href="#"><img src="https://img.shields.io/badge/No%20Server-Yes-555&style=for-the-badge" alt="No Server"/></a>
<a href="#"><img src="https://img.shields.io/badge/License-Apache%202.0-green?style=for-the-badge" alt="License"/></a>
</p>

<p align="center">
<b>Install → Scan QR → Your AI answers your WhatsApp chats automatically.</b>
</p>

---

### What it does

ClawLink WA turns your Android phone into an **AI WhatsApp auto-reply machine**. It runs the real WhatsApp Web client inside your phone's own browser engine (WebView), pairs via QR just like WhatsApp Desktop, and lets an AI answer incoming chats for you — **24/7, hands-free.**

No computer. No Node.js. No self-hosted server. No re-inventing the WhatsApp protocol. Just install the APK on your phone, link your number once, and let the AI do the talking.

<p align="center">
<img src="screenshots/Screenshot_20261119-020024_ClawLink%20WA.png" width="260" alt="Home screen showing link status, chats, messages, AI replies"/>
<img src="screenshots/Screenshot_20261119-020159_ClawLink%20WA.png" width="260" alt="Tools screen with automation settings"/>
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
Enable “Install unknown apps” for your browser or file manager, then open the APK.  
Or from a computer:

```bash
adb install ClawLink-WA-1.0.apk
```

**3. Link WhatsApp**
Open ClawLink WA → **Link WhatsApp** → on your phone go to **WhatsApp → Linked devices → Link a device** → scan the QR shown in the app.

**4. Configure your AI**
Enter your AI endpoint/key (OpenAI-compatible or NVIDIA NIM). Your AI now answers incoming chats automatically.

---

## 📱 Requirements

- Android 7.0+ (minSdk 24)
- Up-to-date **Android System WebView** — WhatsApp Web needs a modern WebView
- Internet connection
- An AI API key/endpoint (OpenAI-compatible; NVIDIA NIM endpoint is built in)

---

## 🧠 How it works

```
WhatsApp Web (real, inside WebView)
   └─ hears a new message → AndroidWA bridge
                                   ↓
                             Kotlin engine
                                   ↓
                             Your AI writes the reply
                                   ↓
                            message sent back to the chat
```

ClawLink WA does not re-implement WhatsApp. It injects a tiny JavaScript helper into the real web client — the same layer `whatsapp-web.js` uses — so you get WhatsApp's own encryption, sockets, and media handling, with an AI layer on top.

---

## 🔍 What this is good for

- Auto-replying to WhatsApp messages while you sleep or work
- Running an AI concierge on a spare phone number
- Testing WhatsApp automation without a server or Node.js
- Keeping a WhatsApp session alive on a dedicated Android device

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
