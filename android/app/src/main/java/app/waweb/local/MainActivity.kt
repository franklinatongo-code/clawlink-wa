package app.waweb.local

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Fully local WhatsApp Web host.
 *
 * The APK ships no WhatsApp code of its own: it loads the real web.whatsapp.com inside a
 * system WebView (the Chromium engine already on the phone), keeps the login in the WebView's
 * persistent storage, and injects the SAME helper library whatsapp-web.js uses
 * (`src/util/Injected/Utils.js`, shipped in assets/wa_utils.js).
 *
 * That gives you, with zero Node and zero external app:
 *   - `window.WWebJS.getChat / sendMessage / getMessageModel` (exact upstream code)
 *   - inbound messages via `window.require('WAWebCollections').Msg.on('add')`
 *
 * Login: scan the QR that web.whatsapp.com renders with the phone that owns the number.
 * The session then survives app restarts because DOM storage + cookies are persistent.
 */
class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var seen: SeenStore
    private val io = Executors.newSingleThreadExecutor()
    private val startedAtSec = System.currentTimeMillis() / 1000
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        seen = SeenStore(this)

        web = WebView(this)
        setContentView(web, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true          // localStorage + IndexedDB: the WhatsApp session lives here
            databaseEnabled = true
            allowFileAccess = false
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
            // WhatsApp serves a different page to a stock WebView UA; look like Chrome.
            userAgentString = userAgentString.replace("; wv", "").replace("Version/4.0 ", "")
        }
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)

        web.addJavascriptInterface(Bridge(), "AndroidWA")
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                // Only WhatsApp may run in this WebView; the bridge must not reach other origins.
                val host = request?.url?.host ?: return true
                return !(host == "web.whatsapp.com" || host.endsWith(".whatsapp.com") || host.endsWith(".whatsapp.net"))
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url != null && url.contains("web.whatsapp.com")) injectBridge()
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage?): Boolean {
                if (m != null) Log.d("WAJS", "${m.message()} (${m.lineNumber()})")
                return true
            }

            override fun onShowFileChooser(
                webView: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?,
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    startActivityForResult(params!!.createIntent(), FILE_REQ)
                    true
                } catch (e: Exception) {
                    fileCallback = null
                    false
                }
            }
        }

        web.loadUrl("https://web.whatsapp.com/")
    }

    /** Inject assets/wa_utils.js (upstream whatsapp-web.js helpers), then assets/wa_boot.js. */
    private fun injectBridge() {
        val utils = asset("wa_utils.js")
        val boot = asset("wa_boot.js")
        web.evaluateJavascript(utils) {
            web.evaluateJavascript(boot, null)
        }
    }

    private fun asset(name: String): String =
        assets.open(name).bufferedReader().use { it.readText() }

    private fun sendText(chatId: String, text: String, quotedId: String?) {
        val js = "window.__waSend && window.__waSend(${JSONObject.quote(chatId)}, ${JSONObject.quote(text)}, " +
            (quotedId?.let { JSONObject.quote(it) } ?: "null") + ")"
        runOnUiThread {
            try { web.evaluateJavascript(js, null) } catch (_: Exception) {}
        }
    }

    private fun handleIncoming(json: String) {
        val m = JSONObject(json)
        val idObj = m.optJSONObject("id") ?: return
        val id = idObj.optString("_serialized").ifEmpty { idObj.optString("\$1") }
        val fromMe = idObj.optBoolean("fromMe")
        val chatId = idObj.optString("remote")
        val type = m.optString("type")
        val body = m.optString("body")
        val sender = m.optString("from")

        if (fromMe || id.isEmpty() || chatId.isEmpty()) return
        if (chatId == "status@broadcast") return
        if (!REPLY_TO_GROUPS && chatId.endsWith("@g.us")) return
        if (IGNORE_BACKLOG) {
            val ts = m.optLong("timestamp", m.optLong("t"))
            if (ts > 0 && ts < startedAtSec - 30) return // history synced on connect, not new
        }
        if (seen.contains(id)) return
        seen.add(id)

        Log.d(TAG, "incoming $chatId ($sender) [$type]: $body")
        if (type != "chat") return // text only; extend for image/document here

        if (body.trim().equals("!ping", ignoreCase = true)) {
            sendText(chatId, "pong", id)
            return
        }
        if (!AUTO_REPLY_ALL || !Llm.isEnabled()) return

        val answer = Llm.reply(body.trim())
        if (answer.isNullOrBlank()) return
        sendText(chatId, answer, id)
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onMessage(json: String) {
            io.execute {
                try {
                    handleIncoming(json)
                } catch (e: Exception) {
                    Log.e(TAG, "onMessage failed", e)
                }
            }
        }

        @JavascriptInterface
        fun onSent(json: String) = Log.d(TAG, "sent $json")

        @JavascriptInterface
        fun onError(json: String) = Log.e(TAG, "send failed $json")

        @JavascriptInterface
        fun onState(json: String) = Log.d(TAG, "state $json")

        @JavascriptInterface
        fun log(msg: String) = Log.d("WAJS", msg)
    }

    /** Bounded "already handled" set persisted across restarts, so a reload does not re-reply. */
    private class SeenStore(ctx: Context) {
        private val prefs = ctx.getSharedPreferences("wa_seen", Context.MODE_PRIVATE)

        @Synchronized
        fun contains(id: String): Boolean = prefs.getStringSet(KEY, emptySet())!!.contains(id)

        @Synchronized
        fun add(id: String) {
            val next = prefs.getStringSet(KEY, emptySet())!!.toMutableSet()
            next.add(id)
            while (next.size > 1000) next.remove(next.first())
            prefs.edit().putStringSet(KEY, next).apply()
        }

        private companion object { const val KEY = "ids" }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_REQ) {
            val cb = fileCallback ?: return
            fileCallback = null
            cb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
        web.onPause()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onDestroy() {
        io.shutdown()
        web.destroy()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WALocal"
        private const val FILE_REQ = 1001

        /** Set true to send every incoming text to the LLM (see Llm.kt). */
        private const val AUTO_REPLY_ALL = false

        /** Set true to answer group chats too. */
        private const val REPLY_TO_GROUPS = false

        /** Ignore messages that are only history synced at connect time. */
        private const val IGNORE_BACKLOG = true
    }
}
