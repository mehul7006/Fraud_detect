package com.fraudshield.app

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** 14.2 / 14.3: the Kotlin model gives the same answers as training (Python) on 100 held-out messages, fast. */
class MessageClassifierTest {
    private val model by lazy {
        val f = listOf("../../assets/models/msg-v1.bin", "../assets/models/msg-v1.bin", "assets/models/msg-v1.bin").map(::File).first { it.exists() }
        f.inputStream().use(MessageClassifier::load)
    }
    private val vectors by lazy {
        listOf("../../android-native/test/msg-vectors.tsv", "../android-native/test/msg-vectors.tsv").map(::File).first { it.exists() }
            .readLines().filter { it.isNotBlank() }.map { it.split("\t", limit = 3) }
    }

    @Test fun same_probabilities_as_training() {
        assertEquals(100, vectors.size)
        for ((top, pGenuine, text) in vectors) {
            val r = model.classify(text)
            assertEquals("P(genuine) for: $text", pGenuine.toDouble(), 1 - r.scamProbability, 1e-4)
            if (top != "GENUINE") assertEquals(top, r.category)
        }
    }

    @Test fun hindi_hinglish_and_english_scams_and_a_real_otp() {
        assertTrue(model.classify("आपका SBI खाता आज बंद हो जाएगा, तुरंत KYC अपडेट करें http://sbi-kyc.top/").scamProbability > 0.5)
        assertTrue(model.classify("Bhai galti se OTP chala gaya tumhare number pe, code bhej do jaldi").scamProbability > 0.5)
        assertTrue(model.classify("Your electricity will be disconnected tonight at 9:30 PM. Call officer 98XXXXX123").scamProbability > 0.5)
        assertTrue(model.classify("482913 is your OTP for Rs 1,250 at Amazon. Do not share this OTP with anyone. - HDFC Bank").scamProbability < 0.5)
    }

    @Test fun fast_enough_for_a_phone() {
        repeat(20) { model.classify(vectors[it][2]) }
        val ms = vectors.map { model.classify(it[2]).ms }.average()
        assertTrue("average $ms ms", ms < 20)
    }

    @Test fun unicode_normalisation_matches_python() {
        assertEquals("zzurl verify 0", MessageClassifier.normalise("HTTPS://x.top/a  Verify ४५६"))
    }

    /** 14.9: Hindi + Hinglish scams from the held-out templates (never seen in training) are detected on the phone. */
    @Test fun hindi_and_hinglish_held_out_scams() {
        // the corpus is at the repository root (ml/messages); a missing file must fail, not silently pass
        val corpus = listOf("../../../ml/messages/corpus.csv", "../../ml/messages/corpus.csv").map(::File).firstOrNull { it.exists() }
            ?: error("ml/messages/corpus.csv not found from ${File(".").absolutePath}")
        val rows = corpus.readLines().drop(1).mapNotNull { l ->
            val m = Regex("^\"?(.*?)\"?,(SCAM|GENUINE),(\\w+),(en|hi|hinglish),[^,]*,(train|test)$").find(l) ?: return@mapNotNull null
            m.groupValues
        }
        val samples = rows.filter { it[2] == "SCAM" && it[4] != "en" && it[5] == "test" }
        val caught = samples.count { model.classify(it[1].replace("\"\"", "\"")).scamProbability >= 0.5 }
        System.err.println("14.9 Hindi/Hinglish held-out scams: ${samples.size}, detected $caught")
        assertTrue("need at least 30 samples, got ${samples.size}", samples.size >= 30)
        assertTrue("detected $caught of ${samples.size}", caught >= 30)
    }

    /** 14.10: genuine bank / OTP / delivery messages stay SAFE (the app shows >= 0.40 as at least "suspicious"). */
    @Test fun genuine_messages_stay_safe() {
        val corpus = listOf("../../../ml/messages/corpus.csv", "../../ml/messages/corpus.csv").map(::File).firstOrNull { it.exists() }
            ?: error("corpus not found")
        val rows = corpus.readLines().drop(1).mapNotNull { l ->
            Regex("^\"?(.*?)\"?,(SCAM|GENUINE),(\\w+),(en|hi|hinglish),[^,]*,(train|test)$").find(l)?.groupValues
        }.filter { it[2] == "GENUINE" }
        fun fp(rs: List<List<String>>) = rs.count { model.classify(it[1].replace("\"\"", "\"")).scamProbability >= 0.40 }
        val held = rows.filter { it[5] == "test" }
        System.err.println("14.10 genuine held-out: ${held.size}, flagged ${fp(held)}; all genuine: ${rows.size}, flagged ${fp(rows)}")
        assertTrue(rows.size >= 500)
        assertTrue("held-out FP ${fp(held)} of ${held.size}", fp(held) * 100.0 / held.size < 2.0)
        assertTrue("all FP ${fp(rows)} of ${rows.size}", fp(rows) * 100.0 / rows.size < 2.0)
    }
}
