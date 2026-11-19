/*
 * Injected into web.whatsapp.com after the page loads.
 *
 * Mirrors what whatsapp-web.js does inside Puppeteer, but talks to Kotlin over the
 * `AndroidWA` JavascriptInterface instead of Puppeteer's evaluate():
 *   1. wait for WhatsApp's webpack module loader (`window.require`)
 *   2. run the upstream LoadUtils() to define window.WWebJS.* (see wa_utils.js)
 *   3. hook the same Msg collection whatsapp-web.js hooks for inbound messages
 *   4. expose window.__waSend(chatId, text, quotedId?) for outbound sends
 *
 * Loads before login too: it keeps retrying until the collections are usable, so the
 * QR scan does not need a reload.
 */
(function () {
  if (window.__waBooted) return;
  window.__waBooted = true;

  var sleep = function (ms) { return new Promise(function (r) { setTimeout(r, ms); }); };
  function log() {
    var a = [].slice.call(arguments).map(function (x) {
      return typeof x === 'string' ? x : (function () { try { return JSON.stringify(x); } catch (e) { return String(x); } })();
    });
    try { AndroidWA.log(a.join(' ')); } catch (e) {}
  }
  function emit(fn, obj) {
    try { AndroidWA[fn](JSON.stringify(obj)); } catch (e) { log('emit ' + fn + ': ' + e); }
  }

  function defineSend() {
    if (window.__waSend) return;
    window.__waSend = async function (chatId, text, quotedId) {
      try {
        var chat = await window.WWebJS.getChat(chatId, { getAsModel: false });
        if (!chat) throw new Error('chat not found: ' + chatId);
        var sent;
        try {
          sent = await window.WWebJS.sendMessage(chat, text, quotedId ? { quotedMessageId: quotedId } : {});
        } catch (e) {
          if (!quotedId) throw e;
          sent = await window.WWebJS.sendMessage(chat, text, {});
        }
        emit('onSent', { chatId: chatId, id: sent && sent.id && sent.id._serialized });
        return sent ? window.WWebJS.getMessageModel(sent) : null;
      } catch (e) {
        emit('onError', { chatId: chatId, error: String(e) });
        return null;
      }
    };
  }

  var stateHooked = false;
  function hookState() {
    if (stateHooked) return;
    try {
      var Sock = window.require('WAWebSocketModel').Socket;
      if (!Sock || typeof Sock.on !== 'function') return;
      Sock.on('change:state', function (_s, state) { emit('onState', { state: state }); });
      emit('onState', { state: Sock.state });
      stateHooked = true;
    } catch (e) {}
  }

  function push(msg) {
    try { emit('onMessage', window.WWebJS.getMessageModel(msg)); }
    catch (e) { log('model error: ' + e); }
  }

  var msgHooked = false;
  function hookMessages() {
    if (msgHooked) return;
    var Msg = window.require('WAWebCollections').Msg;
    if (!Msg || typeof Msg.on !== 'function') return;

    Msg.on('add', function (msg) {
      try {
        if (!msg.isNewMsg) return;
        if (msg.type === 'ciphertext') {
          msg.once('change:type', function (decrypted) {
            if (decrypted && decrypted.type !== 'revoked') push(decrypted);
          });
          return;
        }
        if (msg.type === 'revoked') return;
        push(msg);
      } catch (e) { log('msg hook: ' + e); }
    });
    msgHooked = true;
  }

  async function main() {
    log('boot: waiting for WhatsApp internals');
    for (;;) {
      try {
        if (typeof window.require !== 'function') { await sleep(500); continue; }

        // Wait until the core collection module exists so LoadUtils() patches a live app.
        var collections = null;
        try { collections = window.require('WAWebCollections'); } catch (e) {}
        if (!collections) { await sleep(1000); continue; }

        if (typeof window.WWebJS === 'undefined' && typeof window.__loadWWebUtils === 'function') {
          try { window.__loadWWebUtils(); } catch (e) { log('LoadUtils: ' + e); }
        }
        if (typeof window.WWebJS === 'undefined') { await sleep(1000); continue; }

        defineSend();
        hookState();
        hookMessages();

        if (msgHooked) { log('boot: listening for messages'); return; }
      } catch (e) {
        log('boot retry: ' + e);
      }
      await sleep(1000);
    }
  }

  main();
})();
