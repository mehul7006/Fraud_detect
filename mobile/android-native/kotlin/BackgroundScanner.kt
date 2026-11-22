package com.fraudshield.app

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * 7.11: checks links (and reports new-device notices) straight from the notification listener, so protection works
 * while FraudShield is closed or swiped away. Android keeps an enabled NotificationListenerService running on its
 * own; no extra always-on service is needed. Network work runs on one background thread.
 * Anything that cannot be done now (offline, logged out, server error) goes to the normal event queue, which the app
 * processes on its next run - nothing is dropped.
 */
object BackgroundScanner {
    private val worker = Executors.newSingleThreadExecutor()

    /** @return true if the link was taken over by the background path (caller must not queue it again). */
    fun onLink(ctx: Context, url: String, pkg: String, fallbackQueue: (JSONObject) -> Unit): Boolean {
        appContext = ctx.applicationContext
        val session = BackgroundSession.load(ctx)
        val action = BackgroundPolicy.forLink(BackgroundPolicy.hostOf(url), pkg, session?.disabled ?: emptySet(),
            session?.trusted ?: emptyList(), BackgroundSession.appInForeground, session != null)
        when (action) {
            BackgroundPolicy.Action.SKIP -> return true
            BackgroundPolicy.Action.QUEUE -> return false
            BackgroundPolicy.Action.SCAN -> Unit
        }
        val s = session!!
        worker.execute {
            val (status, body) = post(s, "/scan/url", JSONObject().put("url", url).put("sourceApp", pkg))
            if (status in 200..299 && body != null) {
                val r = JSONObject(body)
                val reasons = r.optJSONArray("reasons")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
                BackgroundPolicy.alertFor(r.optString("verdict"), url, reasons)?.let { (title, text) ->
                    AlertNotifier.show(ctx, title, text, url.hashCode())
                }
                android.util.Log.i("FraudShield", "background link scan: ${r.optString("verdict")}")
            } else if (BackgroundPolicy.queueOnFailure(status)) {
                fallbackQueue(JSONObject().put("kind", "LINK").put("pkg", pkg).put("url", url))
            }
        }
        return true
    }

    /** New-device / linked-device notice while the app is closed: alert now and tell the server (feeds 7.2). */
    fun onNewDevice(ctx: Context, app: String, fallbackQueue: (JSONObject) -> Unit): Boolean {
        appContext = ctx.applicationContext
        if (BackgroundSession.appInForeground) return false
        val s = BackgroundSession.load(ctx) ?: return false
        val name = if (app == "telegram") "Telegram" else "WhatsApp"
        AlertNotifier.show(ctx, "$name: new device activity",
            "A new-device or linked-device notice appeared. Open $name and log out all other devices now.", 77)
        worker.execute {
            val (status, _) = post(s, "/threats", JSONObject()
                .put("type", if (app == "telegram") "TELEGRAM_TAKEOVER" else "WHATSAPP_TAKEOVER")
                .put("severity", "CRITICAL").put("title", "$name new device / session notice")
                .put("detail", "Seen while FraudShield was closed").put("sourceApp", app))
            if (BackgroundPolicy.queueOnFailure(status)) fallbackQueue(JSONObject().put("kind", "NEW_DEVICE").put("app", app))
        }
        return true
    }

    /** Sends; on 401 renews the scanner's own token once with its refresh token (8.2) and sends again. */
    private fun post(s: BackgroundSession.Session, path: String, json: JSONObject): Pair<Int, String?> {
        val first = send(s.apiBase, path, json, s.token)
        if (first.first != 401 || s.refresh == null) return first
        val ctx = appContext ?: return first
        val (code, body) = send(s.apiBase, "/auth/refresh", JSONObject().put("refreshToken", s.refresh), null)
        if (code != 200 || body == null) {
            if (code == 401) BackgroundSession.clear(ctx) // refused: wait for the app to hand over a new session
            return first
        }
        val j = JSONObject(body)
        val token = j.getString("token")
        BackgroundSession.updateTokens(ctx, token, j.optString("refreshToken").ifBlank { null })
        return send(s.apiBase, path, json, token)
    }

    @Volatile private var appContext: Context? = null

    private fun send(apiBase: String, path: String, json: JSONObject, token: String?): Pair<Int, String?> = try {
        val c = URL(apiBase.trimEnd('/') + path).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.connectTimeout = 10_000; c.readTimeout = 20_000; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
        c.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
        val code = c.responseCode
        val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }
        c.disconnect()
        code to body
    } catch (e: Exception) {
        android.util.Log.w("FraudShield", "background request failed: ${e.javaClass.simpleName}")
        0 to null
    }
}
