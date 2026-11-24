package com.fraudshield.app

import android.content.Context
import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.regex.Pattern
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * 14.3: on-device scam-message model (trained by ml/messages/train_msg.py). The text never leaves the phone.
 * Hashed character / word n-grams -> multinomial logistic regression. Feature code mirrors train_msg.py exactly
 * (Unicode-aware regexes, code points not UTF-16 units) - MessageClassifierTest checks 100 messages against Python.
 * The model file ships in the app (assets/models/msg-v1.bin); a newer signed one from the server replaces it.
 */
class MessageClassifier private constructor(private val buckets: Int, val classes: List<String>,
                                           private val bias: FloatArray, private val w: Array<FloatArray>) {

    data class Result(val scamProbability: Double, val category: String, val categoryProbability: Double, val ms: Double)

    fun classify(text: String): Result {
        val t0 = System.nanoTime()
        val f = features(text, buckets)
        val logits = DoubleArray(classes.size) { c -> var s = bias[c].toDouble(); for ((k, v) in f) s += w[c][k] * v; s }
        val max = logits.max()
        val e = logits.map { exp(it - max) }
        val sum = e.sum()
        val p = e.map { it / sum }
        val g = classes.indexOf("GENUINE")
        var best = 0
        for (i in p.indices) if (i != g && (best == g || p[i] > p[best])) best = i
        return Result(1 - p[g], classes[best], p[best], (System.nanoTime() - t0) / 1e6)
    }

    fun probabilityGenuine(text: String): Double = 1 - classify(text).scamProbability

    companion object {
        /** Android (ICU) rejects UNICODE_CHARACTER_CLASS but its \d \s \w \b are Unicode already - same result as the
         *  desktop JVM with the flag (which the parity tests run on). Without this fallback the app crashed on a phone. */
        private fun unicode(p: String, flags: Int = 0): Pattern = try {
            Pattern.compile(p, flags or Pattern.UNICODE_CHARACTER_CLASS)
        } catch (e: IllegalArgumentException) {
            Pattern.compile(p, flags)
        }
        private val URL = unicode("(https?://\\S+|www\\.\\S+|\\b[\\w-]+\\.(?:top|xyz|online|site|in|co|info|click|com|net|org|me|ly)\\b\\S*)",
            Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
        private val DIGITS = unicode("\\d+")
        private val SPACES = unicode("\\s+")

        fun fnv(s: String): Long {
            var h = 0x811C9DC5L
            for (b in s.toByteArray(Charsets.UTF_8)) { h = h xor (b.toLong() and 0xFF); h = (h * 0x01000193L) and 0xFFFFFFFFL }
            return h
        }

        fun normalise(text: String): String {
            var t = text.lowercase()
            t = URL.matcher(t).replaceAll(" zzurl ")
            t = DIGITS.matcher(t).replaceAll("0")
            return SPACES.matcher(t).replaceAll(" ").trim()
        }

        /** Same features as train_msg.features(): bucket -> value, L2-normalised. */
        fun features(text: String, buckets: Int): Map<Int, Double> {
            val t = normalise(text)
            val cps = " $t ".codePoints().toArray()
            val idx = LinkedHashSet<Int>()
            for (n in 3..5) for (i in 0..cps.size - n) idx += (fnv("c:" + String(cps, i, n)) % buckets).toInt()
            val words = t.split(" ")
            for (wd in words) if (wd.isNotEmpty()) idx += (fnv("w:$wd") % buckets).toInt()
            for (i in 0 until words.size - 1) idx += (fnv("b:" + words[i] + " " + words[i + 1]) % buckets).toInt()
            val v = 1.0 / sqrt(idx.size.toDouble().coerceAtLeast(1.0))
            return idx.associateWith { v }
        }

        fun load(input: InputStream): MessageClassifier {
            val d = DataInputStream(input.buffered())
            val magic = ByteArray(6); d.readFully(magic)
            require(String(magic) == "FSMSG1") { "not a FraudShield message model" }
            fun u32(): Int { val b = ByteArray(4); d.readFully(b); return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).int }
            val buckets = u32(); val n = u32()
            val classes = (0 until n).map { val len = d.readUnsignedByte(); val b = ByteArray(len); d.readFully(b); String(b, Charsets.UTF_8) }
            fun floats(count: Int): FloatArray {
                val b = ByteArray(count * 4); d.readFully(b)
                val fb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                return FloatArray(count).also { fb.get(it) }
            }
            val bias = floats(n)
            val w = Array(n) { floats(buckets) }
            return MessageClassifier(buckets, classes, bias, w)
        }

        @Volatile private var shared: MessageClassifier? = null

        /** The updated model (filesDir, already signature-checked when downloaded) or the one shipped in the app. */
        fun get(ctx: Context): MessageClassifier = shared ?: synchronized(this) {
            shared ?: run {
                val updated = java.io.File(ctx.filesDir, "msg-model.bin")
                val m = if (updated.exists()) try { updated.inputStream().use(::load) } catch (e: Exception) { null } else null
                (m ?: ctx.assets.open("flutter_assets/assets/models/msg-v1.bin").use(::load)).also { shared = it }
            }
        }

        fun reset() { shared = null }
    }
}
