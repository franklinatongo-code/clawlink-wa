# HOW_IT_WORKS.md — a nerdy deep dive

> For people who want to know *exactly* what happens between "someone sends you a message"
> and "your AI replies". The friendly overview is in the [README](README.md); this is the
> engine room.

---

## 0. TL;DR

```
WhatsApp user
   │  (E2E-encrypted Signal message)
   ▼
Meta servers ──► your phone's linked-device socket
   │
   ▼
web.whatsapp.com  (the real web client, inside Android WebView)
   │   webpack runtime: window.require(...)
   │   collections:      WAWebCollections.Msg
   │   socket:           WAWebSocketModel.Socket
   ▼
wa_boot.js  ── Msg.on('add') ──► WWebJS.getMessageModel(msg) ──► JSON
   │
   ▼  @JavascriptInterface AndroidWA.onMessage(json)
Kotlin (single-thread executor)
   │   filter → dedupe(SeenStore) → Llm.reply(body)
   ▼
Llm.kt ── HTTP POST (OpenAI-compatible /chat/completions) ──► your model
   │
   ▼  reply text
web.evaluateJavascript("window.__waSend(chatId, text, quotedId)")
   │
   ▼
window.WWebJS.getChat → window.WWebJS.sendMessage → WhatsApp
```

Three languages, three trust boundaries, zero Node processes.

---

## 1. The Puppeteer-shaped hole

[`whatsapp-web.js`](https://github.com/pedroslopez/whatsapp-web.js) does something clever and
ugly: it launches a headless Chrome, navigates to `web.whatsapp.com`, and then *injects
JavaScript into the page* to drive the client. It never talks to WhatsApp's servers at the
protocol level — it just puppeteers the official web app.

That means the hard parts (Signal encryption, socket auth, device linking, sync) are already
done by WhatsApp's own code. The library only needs two things:

1. a browser that has a copy of the WhatsApp web app, and
2. a way to run JS in that page and get values back out.

On a phone, **we already have the browser** — Android's system WebView. So we can delete the
entire Node + Puppeteer + bundled-Chromium half and replace it with:

| Puppeteer world | This app |
|---|---|
| `puppeteer.launch()` | `WebView(this)` |
| `page.goto('https://web.whatsapp.com')` | `web.loadUrl(...)` |
| `page.evaluate(js)` | `web.evaluateJavascript(js, cb)` |
| `page.exposeFunction(...)` | `@JavascriptInterface` (`AndroidWA`) |
| `Utils.js` injected | same `Utils.js`, shipped as `assets/wa_utils.js` |
| Node process on a PC | Kotlin on the phone |

Same helper library, different transport.

---

## 2. What WhatsApp Web actually is

The web client is a single-page app bundled by webpack. A few facts make the whole trick work:

- **`window.require` is a live module registry.** Every internal module (collections, socket,
  models, RPCs) is addressable by name at runtime, e.g.
  `window.require('WAWebCollections')`. There is no import statement needed; the bundle
  exposes its own module system globally.
- **Collections are Backbone-style stores.** `WAWebCollections.Msg` is an event-emitting
  collection of `Msg` models. `.on('add', cb)` fires for every message the client ingests.
  `Chat`, `Contact`, `Msg`, `GroupMetadata` etc. all live here.
- **The socket is also a module.** `window.require('WAWebSocketModel').Socket` is the live
  connection; `Sock.state` and `change:state` tell you when the device is
  `CONNECTED` / `OPENING` / `PAIRING` etc.
- **Messages arrive encrypted.** New messages can first appear with `type === 'ciphertext'`
  and are replaced/updated once decryption completes, firing `change:type`. Deleted messages
  arrive as `type === 'revoked'`.
- **Models are objects, not plain JSON.** `msg.id` is an object whose string form is in
  `id._serialized` (older builds: `id.$1`); `msg.remote` is the chat id
  (`…@c.us` for a person, `…@g.us` for a group); `msg.from` is the sender.
- **The session is browser storage.** Device credentials, keys, and sync state live in
  cookies + `localStorage` + `IndexedDB` (and the service worker cache). Keep that storage and
  you stay linked; wipe it and you must re-scan the QR.

We do **not** reimplement any of the above. We observe and drive it.

---

## 3. The three layers

| Layer | Language | Runs in | Job |
|---|---|---|---|
| Shell | Kotlin/Java | Android app process | Own the WebView, security policy, lifecycle, UI |
| Helper | JavaScript | the WhatsApp page (V8) | Read/emit WhatsApp models, send messages |
| Host | Kotlin | Android app process | Filter, dedupe, call the AI, trigger sends |

The only channel between the page layer and the app layer is the `AndroidWA`
`@JavascriptInterface`. The only channel back into the page is `evaluateJavascript`.

---

## 4. Boot sequence (timeline)

**`MainActivity.onCreate`**

```kotlin
WebView(this)                                   // system WebView = phone's Chromium
settings.javaScriptEnabled = true
settings.domStorageEnabled = true               // localStorage + IndexedDB
settings.databaseEnabled = true
settings.allowFileAccess = false                // page can't touch local files
settings.userAgentString = userAgentString
    .replace("; wv", "")                        // hide "WebView"
    .replace("Version/4.0 ", "")                // look like desktop Chrome
CookieManager.getInstance().apply {
    setAcceptCookie(true)
    setAcceptThirdPartyCookies(web, true)
}
web.addJavascriptInterface(Bridge(), "AndroidWA")
web.loadUrl("https://web.whatsapp.com/")
```

Why the UA surgery? WhatsApp sniffs the user agent. A stock WebView UA gets a stripped
"download the app" landing page; a desktop-Chrome-looking UA gets the full client. Strip
`; wv` and `Version/4.0 ` and you pass.

**`onPageFinished(url)`** — if the URL is `web.whatsapp.com`, inject:

```kotlin
evaluateJavascript(assets["wa_utils.js"]) {   // 1. define __loadWWebUtils
    evaluateJavascript(assets["wa_boot.js"])  // 2. start the boot loop
}
```

Order matters: `wa_boot.js` calls `window.__loadWWebUtils()`, which only exists after
`wa_utils.js` has run.

**`wa_boot.js` boot loop** (retries forever, 0.5–1 s apart):

```
typeof window.require === 'function'?          no → sleep, retry
window.require('WAWebCollections') exists?     no → sleep, retry
window.WWebJS undefined?                       → window.__loadWWebUtils()
                                               (patches the LIVE app, defines WWebJS.*)
defineSend()      // window.__waSend(...)
hookState()       // Sock.on('change:state')
hookMessages()    // Msg.on('add')
msgHooked?         yes → "listening for messages", stop
```

Because it retries rather than fails, it is safe to inject **before login**. You can have the
QR screen up, scan, and the hooks attach as soon as the client comes alive — no reload needed.

`wa_utils.js` is the upstream `whatsapp-web.js` `Utils.js`, wrapped so it only installs once.
It defines a large `window.WWebJS.*` surface, including (this build):

```
getChat, getChats, getChatModel, getContact, getContacts, getMessageModel,
sendMessage, sendSeen, sendChatstate, editMessage, forwardMessage,
getProfilePicThumbToBase, toStickerData, processStickerData, processMediaData,
compareWwebVersions, getStatusModel, getAllStatuses, rejectCall, …
```

---

## 5. Inbound path — from socket to AI

### In the page (`wa_boot.js`)

```js
Msg.on('add', function (msg) {
  if (!msg.isNewMsg) return;                      // ignore renders/reorders
  if (msg.type === 'ciphertext') {                // not decrypted yet
    msg.once('change:type', function (decrypted) {
      if (decrypted && decrypted.type !== 'revoked') push(decrypted);
    });
    return;
  }
  if (msg.type === 'revoked') return;            // deleted message
  push(msg);
});

function push(msg) {
  AndroidWA.onMessage(JSON.stringify(WWebJS.getMessageModel(msg)));
}
```

`getMessageModel` normalises the internal model into something JSON-safe: serialized id,
remote chat id, sender, type, body, timestamp, `isNewMsg`, etc.

### Across the bridge

`Bridge.onMessage(json)` immediately hands off to a **single-thread executor**:

```kotlin
@JavascriptInterface fun onMessage(json: String) {
    io.execute { handleIncoming(json) }   // one at a time, in order
}
```

Keeping `@JavascriptInterface` methods fast matters: they block the page's JS thread while
they run. Doing the slow work (filters, HTTP to the AI) off-thread avoids janking WhatsApp Web.

### In Kotlin (`handleIncoming`)

```
parse JSONObject
id        = id._serialized  ?:  id.$1
fromMe    = id.fromMe
chatId    = id.remote
type      = type
body      = body
sender    = from
timestamp = timestamp ?: t

drop if fromMe                      → never reply to yourself
drop if id or chatId empty          → malformed
drop if chatId == status@broadcast  → status updates
drop if !REPLY_TO_GROUPS and chatId endsWith @g.us
drop if IGNORE_BACKLOG and ts < startedAtSec - 30   → old history synced on connect
drop if seen.contains(id)           → already handled (persisted)
seen.add(id)
drop if type != "chat"              → text only

if body == "!ping" → reply "pong" and return
if !AUTO_REPLY_ALL or !Llm.isEnabled() → return
answer = Llm.reply(body.trim())
if answer blank → return
sendText(chatId, answer, quotedId = id)
```

The **30-second grace** in the backlog check exists because when the client connects it
replays recent history through the same `add` events. Anything timestamped before the app
started is history, not a new message; the grace window covers clock skew and messages that
landed right as we launched.

The **`SeenStore`** is a bounded `SharedPreferences` string set (cap 1000, oldest evicted).
It survives process death, so reloading the WebView or rotating the device never triggers a
duplicate reply.

---

## 6. Outbound path — from AI text to WhatsApp

`sendText` serialises arguments and calls into the page **on the UI thread** (WebView APIs
must run there):

```kotlin
val js = "window.__waSend(${quote(chatId)}, ${quote(text)}, ${quotedId?.let(::quote) ?: "null"})"
runOnUiThread { web.evaluateJavascript(js, null) }
```

`__waSend` does the real work:

```js
window.__waSend = async function (chatId, text, quotedId) {
  var chat = await window.WWebJS.getChat(chatId, { getAsModel: false });
  var sent;
  try {
    sent = await window.WWebJS.sendMessage(chat, text,
             quotedId ? { quotedMessageId: quotedId } : {});
  } catch (e) {
    if (!quotedId) throw e;
    sent = await window.WWebJS.sendMessage(chat, text, {});   // quote-unsupported fallback
  }
  AndroidWA.onSent({ chatId, id: sent?.id?._serialized });
  return window.WWebJS.getMessageModel(sent);
};
```

Key details:

- **`getAsModel: false`** returns the *live* model, which is what `sendMessage` needs. The
  model version is for serialising, not for sending.
- **Quote fallback**: if quoting the original message fails (e.g. it was deleted or the
  message type can't be quoted), it retries without the quote rather than dropping the reply.
- `onSent` / `onError` come back over the bridge purely for logging/telemetry.

Typing indicators, read receipts, presence, and stickers are available in the same helper
layer (`sendChatstate`, `sendSeen`, `toStickerData` + `sendMediaAsSticker`); the shipped build
wires them into the reply flow (typing on → think → typing off → send).

---

## 7. Session persistence — why you scan once

The linked-device session is **browser storage**, not something the app manages:

```
WebView data dir
├── Cookies                         ← auth/session cookies
├── app_webview/Local Storage       ← localStorage
├── app_webview/IndexedDB           ← raw keys, sync state, message cache
└── Service Worker/CacheStorage     ← web app assets
```

Two things keep it alive:

1. `domStorageEnabled = true` + `databaseEnabled = true` + cookies accepted (settings above),
2. `CookieManager.getInstance().flush()` in `onPause()` — writes the in-memory cookie jar to
   disk so a kill/reboot doesn't lose the login.

Result: relaunch the app and the same linked-device session resumes. "Switch account" simply
clears that storage, which is why it lands you back on the QR screen.

---

## 8. Security model / threat surface

The dangerous part of any WebView app is the JS bridge: `@JavascriptInterface` methods are
callable by *whatever page is loaded*. This app narrows that to one origin.

```kotlin
override fun shouldOverrideUrlLoading(view, request): Boolean {
    val host = request?.url?.host ?: return true
    return !(host == "web.whatsapp.com" ||
             host.endsWith(".whatsapp.com") ||
             host.endsWith(".whatsapp.net"))
}
```

- **Origin allow-list.** Any navigation to a different host is cancelled, so the bridge is
  only ever reachable from WhatsApp's own pages.
- **Injection is host-gated.** `injectBridge()` runs only when
  `url.contains("web.whatsapp.com")`.
- **No local file access.** `allowFileAccess = false` stops `file://` reads.
- **No secrets in the APK.** `Llm.kt` ships with a blank `API_KEY`; no tokens are embedded.
- **Debugging off in release.** `setWebContentsDebuggingEnabled` is gated on `BuildConfig.DEBUG`.

Residual risks (be honest): the bridge trusts WhatsApp's page contents; a hypothetical XSS in
the web client could call the exposed methods. The exposed surface is deliberately small
(`onMessage`, `onSent`, `onError`, `onState`, `log`) and does not expose arbitrary file/network
primitives.

---

## 9. The AI seam

`Llm.kt` is intentionally tiny — a single function:

```kotlin
fun reply(userText: String): String?
```

It POSTs an OpenAI-shaped body:

```json
{
  "model": "<MODEL>",
  "messages": [
    {"role": "system", "content": "You are replying to a WhatsApp message ... plain text only ..."},
    {"role": "user",   "content": "<the incoming message>"}
  ]
}
```

with `Content-Type: application/json`, `Authorization: Bearer <API_KEY>`, a 15 s connect and
60 s read timeout, then reads `choices[0].message.content`. Blank key ⇒ `isEnabled()` is false
and the AI path is skipped entirely.

Because it's just HTTP, any OpenAI-compatible endpoint works: OpenAI, Groq, OpenRouter,
NVIDIA NIM, or a LAN Ollama (`http://192.168.x.x:11434/v1/chat/completions`, key = anything).

The shipped APK adds a UI/automation layer on top of the same loop — per-chat history
(rolling window of recent turns), reply chunking (~4000 chars, quoting the first chunk),
quiet hours, away/vacation and welcome messages, keyword auto-replies, auto-react cooldowns,
and a dashboard counting chats/messages/AI replies.

> **Nothing is templated.** The string the model returns is the string that gets sent.

---

## 10. Keeping the helper in sync

`wa_utils.js` is generated from a checked-out `whatsapp-web.js`, not hand-edited:

```bash
npm ci                                   # at the OpenWA/whatsapp-web.js repo root
./android/sync-wwebjs-utils.sh           # regenerates android/app/src/main/assets/wa_utils.js
node --check android/app/src/main/assets/wa_utils.js
```

The script wraps upstream `src/util/Injected/Utils.js` in an idempotent
`__loadWWebUtils()` and syntax-checks the result. Upgrade the npm package, re-run, rebuild.

---

## 11. Failure modes & war stories

| Symptom | Cause | Fix |
|---|---|---|
| "Download WhatsApp for Android" page | Stock WebView UA | Strip `; wv` / `Version/4.0` from UA |
| Hooks never attach | `window.require`/`WAWebCollections` not ready yet | The boot loop retries; don't "fix" it by reloading early |
| Replies to old messages on launch | History replay through `Msg.on('add')` | `IGNORE_BACKLOG` + 30 s grace |
| Same message answered twice | Page reload / process restart re-emits | `SeenStore` dedupe (persisted, capped 1000) |
| Empty bodies for some messages | `ciphertext` not yet decrypted, or non-`chat` type | Wait `change:type`; only answer `type == "chat"` |
| Replies stop after a WhatsApp update | Internal module names drift (`WAWeb*`) | Re-sync `Utils.js`; adjust the module name |
| Delayed replies when backgrounded | Android throttles background WebViews/Doze | Keep the app foreground; replies are instant when open |
| Breaks on group badge counts | `@g.us` chat ids, participant senders | `REPLY_TO_GROUPS` is off by default |

---

## 12. What this is *not*

- **Not a protocol client.** It does not speak Signal or connect to WhatsApp servers itself.
  It is a **linked device** using WhatsApp's own web app, exactly like WhatsApp Desktop —
  which is also why you scan a QR.
- **Not a way around E2E.** Messages are decrypted by WhatsApp's own client code in the page.
- **Not sanctioned for automation.** Meta doesn't officially support driving WhatsApp Web
  this way. Use a spare number; bans are possible. For production, use the WhatsApp Cloud API.

---

## 13. Nerd knobs (in `MainActivity.kt`)

| Constant | Default | Effect |
|---|---|---|
| `AUTO_REPLY_ALL` | `false` | Send every incoming text to the LLM |
| `REPLY_TO_GROUPS` | `false` | Answer `@g.us` group chats too |
| `IGNORE_BACKLOG` | `true` | Skip history synced at connect |
| backlog grace | 30 s | Timestamp window before "now" treated as history |
| `SeenStore` cap | 1000 ids | Dedupe memory bound |
| LLM connect/read timeout | 15 s / 60 s | Network patience |

---

## 14. Glossary

- **WebView** — Android's embeddable Chromium; shares the phone's Chromium engine.
- **webpack runtime / `window.require`** — WhatsApp Web's module loader, exposed globally.
- **Collection** — Backbone-style store (`Msg`, `Chat`, `Contact`) that emits events.
- **Model** — an object instance inside a collection (`Msg` model, `Chat` model).
- **`_serialized`** — the stable string form of a WhatsApp id (`true_234…@c.us_ABCD…`).
- **ciphertext message** — an inbound message object before decryption updates its type.
- **linked device** — a secondary device paired to your account (what you create by QR).
- **JavascriptInterface** — the Android API that lets page JS call Kotlin methods.

---

## 15. Further reading

- [`whatsapp-web.js`](https://github.com/pedroslopez/whatsapp-web.js) — Apache-2.0; source of the injected helpers.
- Android [`WebView`](https://developer.android.com/reference/android/webkit/WebView) and
  [`addJavascriptInterface`](https://developer.android.com/reference/android/webkit/WebView#addJavascriptInterface(java.lang.Object,%20java.lang.String)).
- [`CookieManager`](https://developer.android.com/reference/android/webkit/CookieManager) and
  [`flush()`](https://developer.android.com/reference/android/webkit/CookieManager#flush()).
