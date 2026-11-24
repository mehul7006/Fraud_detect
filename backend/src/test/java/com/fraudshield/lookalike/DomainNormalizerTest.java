package com.fraudshield.lookalike;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 10.2: 30 cases - registrable domain, suffix, sub-domain, embedded domain. "-" = empty / null. */
class DomainNormalizerTest {
    static final DomainNormalizer N = DomainNormalizer.fromClasspath();

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        // input                                   registrable           suffix        subdomain              embedded
        "https://www.flipkart.com/ | flipkart.com | com | - | -",
        "flipkart.com | flipkart.com | com | - | -",
        "WWW.FLIPKART.COM. | flipkart.com | com | - | -",
        "https://login.flipkart.com.evil.xyz/a | evil.xyz | xyz | login.flipkart.com | flipkart.com",
        "http://secure.sbi.co.in.verify-kyc.top | verify-kyc.top | top | secure.sbi.co.in | sbi.co.in",
        "https://www.onlinesbi.sbi/ | onlinesbi.sbi | sbi | - | -",
        "https://retail.onlinesbi.sbi/x | onlinesbi.sbi | sbi | retail | -",
        "https://www.sbi.co.in | sbi.co.in | co.in | - | -",
        "https://hdfc.bank.in/login | hdfc.bank.in | bank.in | - | -",
        "https://uidai.gov.in | uidai.gov.in | gov.in | - | -",
        "https://myaadhaar.uidai.gov.in/x | uidai.gov.in | gov.in | myaadhaar | -",
        "https://flipkart-sale.netlify.app/ | flipkart-sale.netlify.app | netlify.app | - | -",
        "https://abc.github.io/paytm | abc.github.io | github.io | - | -",
        "https://x.y.blogspot.com | y.blogspot.com | blogspot.com | x | -",
        "https://my-shop.web.app | my-shop.web.app | web.app | - | -",
        "https://foo.firebaseapp.com | foo.firebaseapp.com | firebaseapp.com | - | -",
        "https://sites.google.com/view/x | google.com | com | sites | -",
        "https://paytm.com@evil.xyz/login | evil.xyz | xyz | - | -",
        "https://user:pass@evil.co.uk:8443/x | evil.co.uk | co.uk | - | -",
        "https://evil.xyz:8080?q=flipkart.com | evil.xyz | xyz | - | -",
        "https://a.b.c.d.e.evil.in | evil.in | in | a.b.c.d.e | -",
        "https://flipkart.com.in | flipkart.com.in | com.in | - | -",
        "https://foo.co.in | foo.co.in | co.in | - | -",
        "https://amazon.in.offer-today.shop | offer-today.shop | shop | amazon.in | amazon.in",
        "http://whatsapp.com-verify.xyz | com-verify.xyz | xyz | whatsapp | -",
        "https://foo.unknowntld | foo.unknowntld | unknowntld | - | -",
        "https://www.bbc.co.uk | bbc.co.uk | co.uk | - | -",
        "https://www.gov.uk/x | www.gov.uk | gov.uk | - | -",
    })
    void cuts_domains(String in, String reg, String suf, String sub, String emb) {
        var d = N.normalize(in);
        assertNotNull(d, in);
        assertEquals(reg, d.registrable(), "registrable of " + in);
        assertEquals(suf, d.publicSuffix(), "suffix of " + in);
        assertEquals(sub.equals("-") ? "" : sub, d.subdomain(), "subdomain of " + in);
        assertEquals(emb.equals("-") ? null : emb, d.embeddedDomain(), "embedded domain of " + in);
    }

    @Test
    void decodes_punycode_and_spots_mixed_scripts() {
        var d = N.normalize("https://xn--mazon-3ve.com/"); // Cyrillic "а" + "mazon"
        assertTrue(d.idn());
        assertEquals("аmazon.com", d.registrable());
        assertEquals("аmazon", d.label());
        assertTrue(d.mixedScript());
        assertEquals("xn--mazon-3ve.com", d.asciiHost());
        var u = N.normalize("http://аmazon.com"); // typed with the Cyrillic letter directly
        assertEquals(d.registrable(), u.registrable());
        assertFalse(N.normalize("https://flipkart.com").idn());
    }

    @Test
    void ip_and_empty_inputs() {
        assertTrue(N.normalize("http://192.168.4.20/sbi-kyc").ip());
        assertTrue(N.normalize("http://[2001:db8::1]/x").ip());
        assertNull(N.normalize(""));
        assertNull(N.normalize(null));
        assertNull(N.normalize("https:///nohost"));
    }
}
