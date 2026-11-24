package com.fraudshield.message;

import com.fraudshield.dto.Dtos.ScanResponse;
import com.fraudshield.service.UrlScanService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * 14.6: opt-in "Check with cloud AI" for one message. Privacy: the text is processed in memory only - it is never
 * stored, never logged (no threat record, no log line contains it), and the request is refused without the user's
 * explicit consent flag. Links inside are checked with the normal link pipeline (Phases 10-11).
 */
@RestController
@RequestMapping("/api/v1/scan/message")
public class MessageScanController {
    public record Request(@NotBlank @Size(max = 2000) String text, @NotNull Boolean consent) {}
    public record LinkVerdict(String host, String verdict, int score, String impersonatedBrand, String officialUrl) {}
    public record Response(String verdict, int score, String category, List<LinkVerdict> links, Map<String, String> explanation) {}

    private static final Pattern URL = Pattern.compile("(https?://[^\\s]+|www\\.[^\\s]+|\\b[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.(?:top|xyz|online|site|in|co|info|click|com|net|org|me|ly|shop)(?:/[^\\s]*)?)",
            Pattern.CASE_INSENSITIVE);

    private final MessageModel model = MessageModel.fromClasspath();
    private final UrlScanService urls;

    public MessageScanController(UrlScanService urls) { this.urls = urls; }

    @PostMapping
    public Response scan(@Valid @RequestBody Request r) {
        if (!Boolean.TRUE.equals(r.consent()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "consent required"); // never without the user's tap
        var m = model.classify(r.text());
        List<LinkVerdict> links = new ArrayList<>();
        var mt = URL.matcher(r.text());
        while (mt.find() && links.size() < 3) {
            ScanResponse s = urls.scan(mt.group(1));
            String host = mt.group(1).replaceFirst("^[a-z]+://", "").split("[/?#]")[0];
            links.add(new LinkVerdict(host, s.verdict().name(), s.score(), s.impersonatedBrand(), s.officialUrl()));
        }
        int score = (int) Math.round(m.scamProbability() * 100);
        for (var l : links) if (l.verdict().equals("MALICIOUS")) score = Math.max(score, 90);
        String verdict = score >= 70 ? "MALICIOUS" : score >= 40 ? "SUSPICIOUS" : "SAFE";
        String category = score >= 40 ? m.category() : "GENUINE";
        return new Response(verdict, score, category, links, Explanations.of(category));
    }

    /** Category explanations (en + hi), template text - the message itself is never put into a prompt or a log. */
    static final class Explanations {
        private static final Map<String, String[]> T = Map.ofEntries(
                Map.entry("BANK_KYC_BLOCK", new String[]{"Looks like a fake bank KYC / account-block message. Banks never ask you to update KYC through a link in an SMS.", "यह नकली बैंक KYC / खाता-बंद संदेश लगता है। बैंक कभी SMS के लिंक से KYC अपडेट नहीं करवाते।"}),
                Map.entry("LOTTERY_PRIZE", new String[]{"Looks like a fake prize or lottery. You cannot win a draw you never entered, and real prizes never need a fee.", "यह नकली इनाम या लॉटरी लगता है। जिस ड्रॉ में आपने भाग नहीं लिया उसे आप नहीं जीत सकते।"}),
                Map.entry("COURIER_PARCEL", new String[]{"Looks like a fake parcel / courier message asking for a small fee - do not pay or click.", "यह नकली पार्सल / कूरियर संदेश लगता है जो छोटा शुल्क माँगता है - भुगतान या क्लिक न करें।"}),
                Map.entry("ELECTRICITY_DISCONNECT", new String[]{"Looks like a fake electricity-disconnection message. Check your bill only in your electricity board's official app.", "यह नकली बिजली-कटौती संदेश लगता है। अपना बिल केवल बिजली विभाग के आधिकारिक ऐप में देखें।"}),
                Map.entry("JOB_TASK", new String[]{"Looks like a fake job or task scam - easy money for likes or ratings always ends in losses.", "यह नकली नौकरी या टास्क धोखा लगता है - लाइक या रेटिंग से आसान कमाई का अंत नुकसान में होता है।"}),
                Map.entry("LOAN_INSTANT", new String[]{"Looks like a fake instant loan. Real lenders never ask for a fee before the loan.", "यह नकली तुरंत लोन लगता है। असली लोन देने वाले पहले शुल्क नहीं माँगते।"}),
                Map.entry("DIGITAL_ARREST", new String[]{"This is the 'digital arrest' scam. Police and CBI never arrest or question anyone over a call or message - hang up and call 1930.", "यह 'डिजिटल अरेस्ट' धोखा है। पुलिस और CBI कभी कॉल या मैसेज पर गिरफ़्तार नहीं करते - कॉल काटें और 1930 पर बताएँ।"}),
                Map.entry("OTP_REQUEST", new String[]{"Someone is trying to get your OTP. Never share an OTP or PIN with anyone.", "कोई आपका OTP लेने की कोशिश कर रहा है। OTP या PIN कभी किसी को न बताएँ।"}),
                Map.entry("UPI_REFUND", new String[]{"This is a UPI refund / collect trick - entering your PIN SENDS money, it never receives it.", "यह UPI रिफंड / कलेक्ट की चाल है - PIN डालने से पैसा जाता है, आता नहीं।"}),
                Map.entry("INVESTMENT_TRADING", new String[]{"Looks like a fake investment group. Guaranteed high returns are always a scam.", "यह नकली निवेश ग्रुप लगता है। पक्के ऊँचे रिटर्न हमेशा धोखा होते हैं।"}),
                Map.entry("GENUINE", new String[]{"No signs of a scam found. Still, never share an OTP or PIN.", "धोखाधड़ी के संकेत नहीं मिले। फिर भी OTP या PIN कभी साझा न करें।"}));

        static Map<String, String> of(String category) {
            String[] t = T.getOrDefault(category, T.get("GENUINE"));
            return Map.of("en", t[0], "hi", t[1], "source", "template");
        }
    }
}
