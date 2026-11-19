# Third-party notices

ClawLink WA redistributes one third-party component inside the APK and derives its injected
helper layer from it.

## whatsapp-web.js

- Project: https://github.com/pedroslopez/whatsapp-web.js
- License: Apache License 2.0 (full text in `licenses/whatsapp-web.js-LICENSE`)
- Component used: `src/util/Injected/Utils.js` (the `LoadUtils()` page script that defines
  `window.WWebJS.*`).
- Changes made: the file is wrapped in a function and injected into an Android `WebView` via
  `evaluateJavascript` instead of being evaluated by Puppeteer. No logic was rewritten; only the
  host environment differs.

ClawLink WA is an independent project and is not affiliated with or endorsed by WhatsApp, Meta,
or the whatsapp-web.js maintainers.
