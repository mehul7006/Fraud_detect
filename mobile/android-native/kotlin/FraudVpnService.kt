package com.fraudshield.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Phase 12: on-device DNS-only VPN. Only the virtual DNS address (10.111.222.1 / fdfd:fdfd::1) is routed into the
 * VPN, so all other traffic goes to the internet as before - no extra hop, no packet copying, little battery.
 * Every name an app looks up passes [DomainGuard]: listed or known-fake domains get "does not exist" (the page
 * cannot load) and a full-screen alert; unknown ones are answered at once and checked on the server in the
 * background (domain only; never in local-only mode). FraudShield itself is excluded from its own VPN.
 */
class FraudVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    @Volatile private var running = false
    private var reader: Thread? = null
    private val pool = Executors.newFixedThreadPool(8)
    private val checks = Executors.newFixedThreadPool(3)
    private val sched = Executors.newSingleThreadScheduledExecutor()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val lastAlert = ConcurrentHashMap<String, Long>()
    private lateinit var out: FileOutputStream
    private lateinit var traffic: TrafficStore

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopVpn(userStopped = true); return START_NOT_STICKY }
        if (!running) startVpn()
        return START_STICKY // also covers Always-on VPN and restarts by the system (12.9)
    }

    override fun onRevoke() { // the user switched us off in Android settings, or another VPN took over (12.10)
        prefs(this).edit().putString(KEY_LAST_STOP, "REVOKED").apply()
        stopVpn(userStopped = false)
    }

    override fun onDestroy() { stopVpn(userStopped = false); pool.shutdownNow(); checks.shutdownNow(); sched.shutdownNow(); super.onDestroy() }

    private fun startVpn() {
        traffic = TrafficStore.get(this)
        applyOptions(this)
        loadBlocklist(this)
        AdBlocker.init(this, BuildConfig.FS_FLAVOUR) // 18.3: ad / tracker / malware list (Play build: no ads)
        guard.neverCheck = BackgroundSession.load(this)?.apiBase?.let { setOfNotNull(android.net.Uri.parse(it).host) } ?: emptySet()
        val b = Builder().setSession("FraudShield")
            .addAddress(VPN_ADDR4, 32).addDnsServer(DNS4).addRoute(DNS4, 32)
            .addAddress(VPN_ADDR6, 128).addDnsServer(DNS6).addRoute(DNS6, 128)
            .setMtu(1500).setBlocking(true)
            .setConfigureIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        try { b.addDisallowedApplication(packageName) } catch (_: Exception) { }
        if (Build.VERSION.SDK_INT >= 29) b.setMetered(false)
        val fd = try { b.establish() } catch (e: Exception) { null }
        if (fd == null) { // not allowed (no consent) or another always-on VPN
            prefs(this).edit().putString(KEY_LAST_STOP, "NOT_ALLOWED").apply()
            stopSelf(); return
        }
        tun = fd
        out = FileOutputStream(fd.fileDescriptor)
        running = true
        instance = this
        startedAt = System.currentTimeMillis()
        prefs(this).edit().putBoolean(KEY_ENABLED, true).remove(KEY_LAST_STOP).apply()
        foreground()
        reader = Thread({ readLoop(FileInputStream(fd.fileDescriptor)) }, "fs-vpn-reader").apply { start() }
        // each sync on its own: one failing must not stop the others (an exception here used to skip the 18.x list)
        sched.scheduleWithFixedDelay({
            try { syncBlocklist(this) } catch (e: Throwable) { android.util.Log.w("FraudShieldVpn", "blocklist sync failed: ${e.javaClass.simpleName}") }
            try { AppNetworkAnalyzer.sync(this) } catch (e: Throwable) { android.util.Log.w("FraudShieldVpn", "domain categories sync failed: ${e.javaClass.simpleName}") }
            // 17.1: files an app saved into shared folders without its own name (DCIM/Camera, Pictures) are only known
            // through the index - refresh it every 6 h, not once a day (device run: photos taken after the daily refresh)
            try { LeftoverIndex.get(this).refreshIfStale(this, 6L * 3600 * 1000) } catch (e: Throwable) { android.util.Log.w("FraudShieldVpn", "leftover index refresh failed: ${e.javaClass.simpleName}") }
            try {
                val ok = AdBlocker.sync(this)
                android.util.Log.i("FraudShieldVpn", "ad/tracker list sync ok=$ok version=${AdBlocker.policy.list.version} size=${AdBlocker.policy.list.size}")
            } catch (e: Throwable) { android.util.Log.w("FraudShieldVpn", "ad/tracker list sync failed: ${e.javaClass.simpleName}: ${e.message?.take(80)}") }
        }, 0, 6, TimeUnit.HOURS)
        AppFirewall.refresh(this)
        InstallWatcher.register(this)                                                       // 13.11
        sched.scheduleWithFixedDelay({ try { WarnScheduler.tick(this) } catch (_: Exception) { }
            // 18.3: no list yet (no session at start, server down): retry every 15 min instead of waiting 6 h
            if (AdBlocker.policy.list.size == 0) try {
                android.util.Log.i("FraudShieldVpn", "ad/tracker list retry ok=${AdBlocker.sync(this)} size=${AdBlocker.policy.list.size}")
            } catch (e: Throwable) { android.util.Log.w("FraudShieldVpn", "ad/tracker list retry failed: ${e.javaClass.simpleName}") }
        }, 1, 15, TimeUnit.MINUTES) // 13.10
        sched.scheduleWithFixedDelay({ traffic.flush(); try { BlockStats.get(this).flush() } catch (_: Exception) { }; updateNotification(); logStats() }, 30, 30, TimeUnit.SECONDS)
    }

    private fun stopVpn(userStopped: Boolean) {
        if (userStopped) prefs(this).edit().putBoolean(KEY_ENABLED, false).putString(KEY_LAST_STOP, "USER").apply()
        running = false
        if (instance === this) instance = null
        try { tun?.close() } catch (_: Exception) { }
        tun = null
        reader?.interrupt()
        if (::traffic.isInitialized) traffic.flush()
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) { }
        stopSelf()
    }

    private fun readLoop(input: FileInputStream) {
        val buf = ByteArray(32767)
        while (running) {
            val n = try { input.read(buf) } catch (e: Exception) { if (running) continue else break }
            if (n <= 0) continue
            val q = DnsPacket.parse(buf, n) ?: continue   // only DNS reaches us; TCP/853 (DoT probes) is dropped
            pool.execute { handle(q, System.nanoTime()) }
        }
    }

    private fun handle(q: DnsPacket.Query, t0: Long) {
        queries.incrementAndGet()
        val uid = UidResolver.uid(this, q)
        traffic.lookup(uid, q.name)
        if (AppFirewall.isBlocked(uid)) { // 13.9: this app's internet is blocked by the user
            write(DnsPacket.reply(q, DnsPacket.nxdomain(q))); firewallBlocked.incrementAndGet(); return
        }
        val action = guard.decide(q.name)
        if (action == DomainGuard.Action.BLOCK_LIST || action == DomainGuard.Action.BLOCK_VERDICT || action == DomainGuard.Action.BLOCK_DOH) {
            write(DnsPacket.reply(q, DnsPacket.nxdomain(q)))
            if (action != DomainGuard.Action.BLOCK_DOH) onBlocked(q.name, uid, guard.verdictFor(q.name))
            else dohBlocked.incrementAndGet()
            return
        }
        // 18.3: ads / trackers / malware from the phone-wide list - silent "does not exist", counted on the phone only
        val cat = AdBlocker.policy.decide(q.name, uid, System.currentTimeMillis())
        if (cat != 0) {
            write(DnsPacket.reply(q, DnsPacket.nxdomain(q)))
            BlockStats.get(this).record(uid, cat, q.name.lowercase().trimEnd('.'))
            return
        }
        val cached = cache.get(q.name, q.qtype, q.id)
        if (cached != null) { write(DnsPacket.reply(q, cached)); overhead(t0, 0); return }
        val tUp = System.nanoTime()
        val answer = forward(q.payload) ?: return // no answer: the app retries
        val upstreamNs = System.nanoTime() - tUp
        cache.put(q.name, q.qtype, answer, ttlCap = if (action == DomainGuard.Action.CHECK) 30 else 3600)
        write(DnsPacket.reply(q, answer))
        overhead(t0, upstreamNs)
        if (action == DomainGuard.Action.CHECK && q.qtype in CHECK_TYPES) check(q.name, uid)
    }

    /** 12.3: our own time per lookup (total minus the upstream server's answer time). */
    private fun overhead(t0: Long, upstreamNs: Long) {
        val us = (System.nanoTime() - t0 - upstreamNs) / 1000
        overheadMicros.addAndGet(us); overheadCount.incrementAndGet()
    }

    @Synchronized private fun write(p: ByteArray) { try { out.write(p) } catch (_: Exception) { } }

    /** Plain UDP to the chosen upstream DNS (FraudShield is outside its own VPN, and the socket is protected). */
    private fun forward(payload: ByteArray): ByteArray? {
        for (server in upstreams(this)) {
            try {
                DatagramSocket().use { s ->
                    protect(s)
                    s.soTimeout = 1500 // then the next server (device test: some upstream answers never came)
                    s.send(DatagramPacket(payload, payload.size, InetSocketAddress(server, 53)))
                    val resp = DatagramPacket(ByteArray(4096), 4096)
                    s.receive(resp)
                    return resp.data.copyOf(resp.length)
                }
            } catch (_: Exception) { upstreamErrors.incrementAndGet() }
        }
        return null
    }

    /** 12.4 / 12.6: background server check of one unknown name (domain only, nothing else). */
    private fun check(name: String, uid: Int) {
        if (!inFlight.add(name)) return
        serverChecks.incrementAndGet()
        checks.execute {
            try {
                val (code, body) = BackgroundScanner.request(this, "/scan/domain", JSONObject().put("domain", name))
                if (code == 200 && body != null) {
                    val j = JSONObject(body)
                    val bad = j.optString("verdict") == "MALICIOUS"
                    val v = DomainGuard.Verdict(bad, j.optString("impersonatedBrand").ifBlank { null },
                        j.optString("officialUrl").ifBlank { null }, System.currentTimeMillis())
                    guard.remember(name, v)
                    if (bad) { cache.clear(); onBlocked(name, uid, v) }
                }
            } finally { inFlight.remove(name) }
        }
    }

    private fun onBlocked(name: String, uid: Int, v: DomainGuard.Verdict?) {
        blocked.incrementAndGet()
        val now = System.currentTimeMillis()
        if ((lastAlert[name] ?: 0) > now - 60_000) return // one alert per site per minute
        lastAlert[name] = now
        val app = UidResolver.label(this, uid)
        val title = if (v?.brand != null) "Blocked fake ${v.brand} site" else "Blocked dangerous site"
        val body = buildString {
            append(name)
            if (v?.brand != null) append(" looks like ${v.brand} but is not ${v.brand}.")
            else append(" is on the list of fake / scam sites.")
            if (v?.officialUrl != null) append(" Real site: ${v.officialUrl}")
            if (app != null) append("\nOpened by: $app")
        }
        AlertNotifier.show(this, title, body, name.hashCode())
        recordBlocked(this, name, v, app)
    }

    private fun foreground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannel(NotificationChannel(CH, "Fake-website protection", NotificationManager.IMPORTANCE_LOW))
        val n = notification()
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
            else startForeground(NOTIF_ID, n)
        } catch (e: Exception) {
            android.util.Log.w("FraudShield", "VPN runs without foreground notification: ${e.javaClass.simpleName}")
        }
    }

    private fun notification(): Notification = NotificationCompat.Builder(this, CH)
        .setSmallIcon(android.R.drawable.ic_lock_lock)
        .setContentTitle("Fake-website protection is on")
        .setContentText(if (blocked.get() == 0L) "Checks the sites your apps open. Sees site names only."
                        else "${blocked.get()} fake site(s) blocked")
        .setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        .build()

    /** Counters only - never a domain or an app name. */
    private fun logStats() {
        val n = overheadCount.get()
        android.util.Log.i("FraudShieldVpn", "stats queries=${queries.get()} blocked=${blocked.get()} doh=${dohBlocked.get()} " +
            "serverChecks=${serverChecks.get()} cacheHits=${cache.hits} overheadMs=${"%.2f".format(if (n == 0L) 0.0 else overheadMicros.get() / 1000.0 / n)} " +
            "upstreamErrors=${upstreamErrors.get()} list=${guard.blocklistSize()} localOnly=${guard.localOnly}")
    }

    private fun updateNotification() {
        if (!running) return
        try { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification()) } catch (_: Exception) { }
    }

    companion object {
        const val ACTION_START = "com.fraudshield.app.VPN_START"
        const val ACTION_STOP = "com.fraudshield.app.VPN_STOP"
        const val VPN_ADDR4 = "10.111.222.2"
        const val DNS4 = "10.111.222.1"
        const val VPN_ADDR6 = "fdfd:fdfd::2"
        const val DNS6 = "fdfd:fdfd::1"
        private const val CH = "fraudshield_vpn"
        private const val NOTIF_ID = 7012
        const val PREFS = "fraudshield_vpn"
        const val KEY_ENABLED = "enabled"
        const val KEY_LAST_STOP = "last_stop"
        private val CHECK_TYPES = setOf(DnsPacket.TYPE_A, DnsPacket.TYPE_AAAA, DnsPacket.TYPE_HTTPS)

        val guard = DomainGuard()
        val cache = DnsCache()
        @Volatile var instance: FraudVpnService? = null
        @Volatile var startedAt = 0L
        val serverChecks = AtomicLong(); val firewallBlocked = AtomicLong()
        val queries = AtomicLong(); val blocked = AtomicLong(); val dohBlocked = AtomicLong(); val upstreamErrors = AtomicLong()
        val overheadMicros = AtomicLong(); val overheadCount = AtomicLong()

        fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        fun isRunning() = instance?.running == true

        fun start(ctx: Context) {
            val i = Intent(ctx, FraudVpnService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun stop(ctx: Context) { ctx.startService(Intent(ctx, FraudVpnService::class.java).setAction(ACTION_STOP)) }

        /** 12.7 options: local-only mode, DoH handling, upstream DNS ("system", "cloudflare", "google", "quad9"). */
        fun applyOptions(ctx: Context) {
            val p = prefs(ctx)
            guard.localOnly = p.getBoolean("local_only", false)
            guard.blockDoh = p.getBoolean("block_doh", true)
        }

        fun upstreams(ctx: Context): List<InetAddress> {
            val chosen = when (prefs(ctx).getString("upstream", "system")) {
                "cloudflare" -> listOf("1.1.1.1", "1.0.0.1")
                "google" -> listOf("8.8.8.8", "8.8.4.4")
                "quad9" -> listOf("9.9.9.9", "149.112.112.112")
                else -> emptyList()
            }.map { InetAddress.getByName(it) }
            if (chosen.isNotEmpty()) return chosen
            // the real network's DNS servers (FraudShield is outside its VPN, so this is the underlying network)
            val cm = ctx.getSystemService(ConnectivityManager::class.java)
            val sys = cm.activeNetwork?.let { cm.getLinkProperties(it)?.dnsServers }.orEmpty()
                .filter { it.hostAddress != DNS4 && !it.hostAddress.orEmpty().startsWith("fdfd:fdfd") }
            // IPv4 first (IPv6 paths are often slow/broken on mobile), and a public fallback at the end
            return (sys.sortedBy { if (it is java.net.Inet4Address) 0 else 1 } + InetAddress.getByName("1.1.1.1")).distinct()
        }

        // ---- 12.5 blocklist: signed deltas, saved to a file
        private fun blocklistFile(ctx: Context) = java.io.File(ctx.filesDir, "vpn-blocklist.txt")

        fun loadBlocklist(ctx: Context) {
            val f = blocklistFile(ctx)
            if (!f.exists() || guard.version > 0) return
            val lines = f.readLines()
            val v = lines.firstOrNull()?.toLongOrNull() ?: return
            guard.restore(v, lines.drop(1).filter { it.length == 64 })
        }

        fun syncBlocklist(ctx: Context): Boolean {
            val (code, body) = BackgroundScanner.request(ctx, "/config/blocklist?since=${guard.version}", null)
            if (code != 200 || body == null) return false
            val j = JSONObject(body)
            fun list(a: JSONArray?) = (0 until (a?.length() ?: 0)).map { a!!.getString(it) }
            val ok = guard.applyDelta(j.getLong("version"), j.getBoolean("full"), list(j.optJSONArray("added")),
                list(j.optJSONArray("removed")), j.getString("signature"), DomainGuard.BLOCKLIST_PUBLIC_KEY)
            if (!ok) { android.util.Log.w("FraudShield", "blocklist signature invalid - ignored"); return false }
            blocklistFile(ctx).writeText(guard.version.toString() + "\n" + guard.snapshot().joinToString("\n"))
            prefs(ctx).edit().putLong("blocklist_synced", System.currentTimeMillis()).apply()
            return true
        }

        // ---- recent blocks, shown by the app
        @Synchronized fun recordBlocked(ctx: Context, name: String, v: DomainGuard.Verdict?, app: String?) {
            val p = prefs(ctx)
            val arr = JSONArray(p.getString("recent_blocks", "[]"))
            val next = JSONArray().put(JSONObject().put("domain", name).put("brand", v?.brand ?: "").put("officialUrl", v?.officialUrl ?: "")
                .put("app", app ?: "").put("at", System.currentTimeMillis()))
            for (i in 0 until minOf(arr.length(), 49)) next.put(arr.get(i))
            p.edit().putString("recent_blocks", next.toString()).apply()
        }

        fun status(ctx: Context): Map<String, Any?> {
            val p = prefs(ctx)
            val n = overheadCount.get()
            return mapOf(
                "running" to isRunning(), "prepared" to (prepare(ctx) == null), "enabled" to p.getBoolean(KEY_ENABLED, false),
                "lastStop" to p.getString(KEY_LAST_STOP, null), "localOnly" to guard.localOnly, "blockDoh" to guard.blockDoh,
                "upstream" to p.getString("upstream", "system"), "blocklistSize" to guard.blocklistSize(),
                "blocklistVersion" to guard.version, "blocklistSynced" to p.getLong("blocklist_synced", 0),
                "queries" to queries.get(), "blocked" to blocked.get(), "dohBlocked" to dohBlocked.get(),
                "cacheHits" to cache.hits, "avgOverheadMs" to if (n == 0L) 0.0 else overheadMicros.get() / 1000.0 / n,
                "upstreamErrors" to upstreamErrors.get(), "since" to startedAt, "serverChecks" to serverChecks.get(), "firewallBlocked" to firewallBlocked.get(),
                "adBlocked" to BlockStats.total.get(), // 18.5

                "recentBlocks" to p.getString("recent_blocks", "[]"),
            ) + VpnState.check(ctx)
        }
    }
}
