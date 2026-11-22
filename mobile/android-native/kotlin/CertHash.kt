package com.fraudshield.app

import java.security.MessageDigest

/** 6.2: SHA-256 of signing certificates, as lowercase hex, comma separated (matches backend AppTrust). */
object CertHash {
    const val MAX_CERTS = 6 // 6 x 64 hex + commas fits the backend's 400-character limit

    fun sha256Hex(cert: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(cert).joinToString("") { "%02x".format(it) }

    fun join(certs: List<ByteArray>): String? =
        if (certs.isEmpty()) null else certs.take(MAX_CERTS).map { sha256Hex(it) }.distinct().joinToString(",")
}
