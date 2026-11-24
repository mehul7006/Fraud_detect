package com.fraudshield.app

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * 12.4: what the VPN does with one looked-up name. Pure Kotlin.
 *  1. local, instant: SHA-256 of the name and of each parent domain against the signed blocklist (12.5)
 *  2. known verdicts from earlier server checks (24 h)
 *  3. DNS-over-HTTPS hosts (12.3): block, so browsers fall back to normal (checked) DNS
 *  4. unknown: answer now, ask the server in the background (domain only, never in local-only mode)
 */
class DomainGuard(private val now: () -> Long = System::currentTimeMillis) {
    enum class Action { ALLOW, CHECK, BLOCK_LIST, BLOCK_VERDICT, BLOCK_DOH }

    data class Verdict(val malicious: Boolean, val brand: String?, val officialUrl: String?, val at: Long)

    @Volatile private var hashes: Set<String> = emptySet()
    @Volatile var version: Long = 0; private set
    private val verdicts = ConcurrentHashMap<String, Verdict>()
    @Volatile var blockDoh = true
    @Volatile var localOnly = false
    /** Names never sent to the server (our own backend, local names). */
    @Volatile var neverCheck: Set<String> = emptySet()

    fun blocklistSize() = hashes.size

    fun decide(rawName: String): Action {
        val name = rawName.lowercase().trimEnd('.')
        if (name.isEmpty() || !name.contains('.') || name.endsWith(".arpa") || name.endsWith(".local") ||
            name.endsWith(".lan") || name.endsWith(".home.arpa")) return Action.ALLOW
        val parents = parents(name)
        val h = hashes
        if (h.isNotEmpty() && parents.any { sha256(it) in h }) return Action.BLOCK_LIST
        for (p in parents) {
            val v = verdicts[p] ?: continue
            if (now() - v.at > VERDICT_MS) { verdicts.remove(p); continue }
            if (v.malicious) return Action.BLOCK_VERDICT
            return Action.ALLOW
        }
        if (blockDoh && DOH_HOSTS.any { name == it || name.endsWith(".$it") }) return Action.BLOCK_DOH
        if (localOnly || neverCheck.any { name == it || name.endsWith(".$it") }) return Action.ALLOW
        return Action.CHECK
    }

    fun verdictFor(name: String): Verdict? = parents(name.lowercase().trimEnd('.')).firstNotNullOfOrNull { verdicts[it] }

    fun remember(name: String, v: Verdict) { verdicts[name.lowercase().trimEnd('.')] = v; trim() }

    private fun trim() { if (verdicts.size > 5000) verdicts.keys.take(1000).forEach { verdicts.remove(it) } }

    /**
     * 12.5: applies a server delta after checking its signature. Returns false (list unchanged) for a bad signature.
     * [publicKey] = Base64 X.509 ECDSA P-256 key built into the app.
     */
    fun applyDelta(version: Long, full: Boolean, added: List<String>, removed: List<String>, signature: String,
                   publicKey: String): Boolean {
        if (!verify(canonical(version, full, added, removed), signature, publicKey)) return false
        val next = if (full) HashSet(added) else HashSet(hashes).apply { removeAll(removed.toSet()); addAll(added) }
        hashes = next
        this.version = version
        return true
    }

    /** Restores a list saved earlier (already verified when it was downloaded). */
    fun restore(version: Long, saved: Collection<String>) { hashes = HashSet(saved); this.version = version }

    fun snapshot(): Set<String> = hashes

    companion object {
        const val VERDICT_MS = 24 * 3600_000L

        /** Public DNS-over-HTTPS servers: an app using one would bypass the VPN's DNS check. */
        val DOH_HOSTS = setOf(
            "dns.google", "dns.google.com", "8888.google", "cloudflare-dns.com", "mozilla.cloudflare-dns.com",
            "chrome.cloudflare-dns.com", "one.one.one.one", "1dot1dot1dot1.cloudflare-dns.com", "dns.quad9.net",
            "dns9.quad9.net", "dns10.quad9.net", "dns11.quad9.net", "doh.opendns.com", "doh.familyshield.opendns.com",
            "dns.nextdns.io", "doh.cleanbrowsing.org", "dns.adguard.com", "dns.adguard-dns.com", "doh.dns.sb",
            "dns.alidns.com", "doh.pub", "doh.360.cn", "dns.mullvad.net", "doh.mullvad.net", "dns.controld.com",
            "freedns.controld.com", "doh.libredns.gr", "dns.switch.ch", "dns0.eu", "dns.twnic.tw")

        /** "a.b.evil.xyz" -> [a.b.evil.xyz, b.evil.xyz, evil.xyz] (at least two labels). */
        fun parents(name: String): List<String> {
            val parts = name.split('.')
            return (0..parts.size - 2).map { parts.subList(it, parts.size).joinToString(".") }
        }

        fun sha256(s: String): String =
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

        /** Same text the server signs (BlocklistSyncService.canonical). */
        fun canonical(version: Long, full: Boolean, added: List<String>, removed: List<String>) =
            "fraudshield-blocklist-v1\n$version\n$full\n${added.joinToString(",")}\n${removed.joinToString(",")}"

        fun verify(text: String, signature: String, publicKey: String): Boolean = try {
            val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)))
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(key); update(text.toByteArray(Charsets.UTF_8)); verify(Base64.getDecoder().decode(signature))
            }
        } catch (e: Exception) { false }

        /** 12.5: the server's blocklist signing key (public half; the private key never leaves the server). */
        const val BLOCKLIST_PUBLIC_KEY =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEg3hVsEYcJdCiflFqiDooA4Fv7g10JQ/+NPRtT6hYXfgu8nDSltKI9hNWTth+s6oSXi4LciJFbN+4VO8j7VJ2NA=="
    }
}
