package com.fraudshield.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** Roadmap 4.6: 20 sample strings. Keep in sync with test/url_extractor_test.dart (Dart port). */
class UrlExtractorTest {
    private fun check(text: String, vararg expected: String) = assertEquals(text, expected.toList(), UrlExtractor.extract(text))

    @Test fun t01_http() = check("go to http://example.com now", "http://example.com")
    @Test fun t02_httpsWithQuery() = check("https://shop.example.com/a?x=1&y=2 ok", "https://shop.example.com/a?x=1&y=2")
    @Test fun t03_www() = check("visit www.example.com today", "www.example.com")
    @Test fun t04_bareDomain() = check("open flipkart.com", "flipkart.com")
    @Test fun t05_bareDomainWithPath() = check("claim at fleepkart.com/offer", "fleepkart.com/offer")
    @Test fun t06_shortener() = check("click bit.ly/abc123", "bit.ly/abc123")
    @Test fun t07_apkLink() = check("install http://evil.xyz/app.apk", "http://evil.xyz/app.apk")
    @Test fun t08_trailingPeriod() = check("See https://example.com/page.", "https://example.com/page")
    @Test fun t09_trailingCommaAndBang() = check("go www.example.com, or a.example.org!", "www.example.com", "a.example.org")
    @Test fun t10_inParentheses() = check("(https://example.com/x)", "https://example.com/x")
    @Test fun t11_inSquareBrackets() = check("link [www.example.com/a] here", "www.example.com/a")
    @Test fun t12_hindiAroundLink() = check("अपना खाता देखें http://sbi-kyc.top/login अभी", "http://sbi-kyc.top/login")
    @Test fun t13_hindiGluedBefore() = check("देखें:http://x-offer.click/a", "http://x-offer.click/a")
    @Test fun t14_twoLinks() = check("http://a.com/1 and https://b.in/2", "http://a.com/1", "https://b.in/2")
    @Test fun t15_noLink() = check("Your OTP is 123456, do not share it")
    @Test fun t16_emailIsNotALink() = check("mail me at john.smith@example.com please")
    @Test fun t17_duplicatesCollapse() = check("www.example.com and www.example.com", "www.example.com")
    @Test fun t18_uppercaseScheme() = check("HTTPS://EXAMPLE.COM/Path", "HTTPS://EXAMPLE.COM/Path")
    @Test fun t19_ipWithPortAndPath() = check("http://192.168.1.5:8080/login", "http://192.168.1.5:8080/login")
    @Test fun t20_bareDomainWithPort() = check("try evil-site.xyz:8080/x now", "evil-site.xyz:8080/x")
}
