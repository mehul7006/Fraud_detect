package com.fraudshield.app

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.system.OsConstants
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

/** 12.2: which app sent a DNS question. Android 10+: the system tells us; older: /proc/net/udp(6). -1 = unknown. */
object UidResolver {
    private val labels = ConcurrentHashMap<Int, String>()

    fun uid(ctx: Context, q: DnsPacket.Query): Int = try {
        if (Build.VERSION.SDK_INT >= 29) {
            val cm = ctx.getSystemService(ConnectivityManager::class.java)
            cm.getConnectionOwnerUid(OsConstants.IPPROTO_UDP,
                InetSocketAddress(InetAddress.getByAddress(q.src), q.srcPort),
                InetSocketAddress(InetAddress.getByAddress(q.dst), q.dstPort))
        } else procNet(q)
    } catch (e: Exception) { -1 }

    /** /proc/net/udp lines: "sl local_address rem_address st ... uid": match our query's source port. */
    private fun procNet(q: DnsPacket.Query): Int {
        val port = "%04X".format(q.srcPort)
        for (f in listOf("/proc/net/udp", "/proc/net/udp6")) {
            val lines = try { File(f).readLines() } catch (e: Exception) { continue }
            for (l in lines.drop(1)) {
                val c = l.trim().split(Regex("\\s+"))
                if (c.size > 7 && c[1].endsWith(":$port")) return c[7].toIntOrNull() ?: -1
            }
        }
        return -1
    }

    /** "Chrome", "WhatsApp"... (several packages can share a UID: the first one's label). */
    fun label(ctx: Context, uid: Int): String? {
        if (uid < 0) return null
        labels[uid]?.let { return it }
        val pm = ctx.packageManager
        val pkg = pm.getPackagesForUid(uid)?.firstOrNull() ?: return when (uid) { 0 -> "Android system"; 1000 -> "Android system"; else -> null }
        val l = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
        labels[uid] = l
        return l
    }

    fun packageOf(ctx: Context, uid: Int): String? = if (uid < 0) null else ctx.packageManager.getPackagesForUid(uid)?.firstOrNull()
}
