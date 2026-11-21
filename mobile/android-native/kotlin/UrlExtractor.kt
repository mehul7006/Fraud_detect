package com.fraudshield.app

import java.util.regex.Pattern

/**
 * Finds links in free text (notifications, messages). Pure Kotlin, no Android classes, so it can be unit-tested.
 * Matches: http(s)://..., www....., and bare domains such as `fleepkart.com/offer` or `bit.ly/abc`.
 * Does not match e-mail addresses. Trailing punctuation is trimmed.
 */
object UrlExtractor {
    private const val TLDS = "com|in|xyz|top|click|me|ly|co|info|net|org|apk|online|site|shop|icu|live|club|cc|tk|ml|gq|cf|" +
        "buzz|link|app|io|store|vip|pw|ru|cn|biz|us|uk|zip|cfd|monster|rest|fun|work"
    // characters that end a link: whitespace, quotes, angle brackets and Devanagari (Hindi text glued to a link)
    private const val STOP = "\\s<>\"'\\u0900-\\u097F"

    private val RE: Pattern = Pattern.compile(
        "(https?://[^$STOP]+" +
            "|\\bwww\\.[^$STOP]+" +
            "|(?<![@\\w.-])(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+(?:$TLDS)\\b(?![@\\w-])(?::\\d+)?(?:/[^$STOP]*)?)",
        Pattern.CASE_INSENSITIVE
    )
    private const val TRAILING = ".,;:!?)]}'\""

    fun extract(text: String): List<String> {
        val out = LinkedHashSet<String>()
        val m = RE.matcher(text)
        while (m.find()) {
            var s = m.group()
            while (s.isNotEmpty() && TRAILING.indexOf(s.last()) >= 0) s = s.dropLast(1)
            s = s.trimStart('(', '[', '{')
            if (s.length >= 4) out.add(s)
        }
        return out.toList()
    }
}
