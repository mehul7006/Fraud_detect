package com.fraudshield.siteintel;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * 10.7: what a fetched page shows and asks for - parsed with jsoup, no scripts run.
 *
 * @param title            page title (trimmed, max 200 chars)
 * @param text             visible text, lower case, max 20k chars (only used in memory for the checks)
 * @param faviconUrl       absolute favicon address
 * @param forms            every form: where it sends data and which secret fields it has
 * @param externalScriptHosts hosts of scripts loaded from other sites
 * @param metaRefreshUrl   target of {@code <meta http-equiv=refresh>}, or null
 */
public record PageFeatures(String title, String text, String faviconUrl, List<Form> forms,
                           Set<String> externalScriptHosts, String metaRefreshUrl) {

    /** {@code actionUrl} absolute (page address when the form has no action). */
    public record Form(String actionUrl, String method, boolean password, boolean otp, boolean card, boolean idNumber) {
        public boolean asksSecrets() { return password || otp || card || idNumber; }
    }

    private static final Pattern OTP = Pattern.compile("otp|one.?time|verification.?code|\\bpin\\b|mpin|upi.?pin|passcode");
    private static final Pattern CARD = Pattern.compile("card.?(no|num)|cardnumber|\\bcc.?num|cvv|cvc|expiry|exp.?date|\\bccv");
    private static final Pattern ID = Pattern.compile("aadha+r|\\bpan\\b|pan.?(no|num|card)|ifsc|account.?(no|num)|acc.?no|dob|date.?of.?birth|net.?banking.?(id|user)|customer.?id");

    public boolean asksSecrets() { return forms.stream().anyMatch(Form::asksSecrets); }

    public static PageFeatures parse(byte[] body, String contentType, String pageUrl) {
        Charset cs = StandardCharsets.UTF_8;
        if (contentType != null) {
            var m = Pattern.compile("charset=([\\w-]+)", Pattern.CASE_INSENSITIVE).matcher(contentType);
            if (m.find()) try { cs = Charset.forName(m.group(1)); } catch (Exception ignored) { /* keep UTF-8 */ }
        }
        Document doc = Jsoup.parse(new String(body, cs), pageUrl);
        String title = doc.title().strip();
        if (title.length() > 200) title = title.substring(0, 200);
        String text = doc.body() == null ? "" : doc.body().text().toLowerCase(Locale.ROOT);
        if (text.length() > 20_000) text = text.substring(0, 20_000);

        Element icon = doc.selectFirst("link[rel~=(?i)^(shortcut icon|icon|apple-touch-icon)$], link[rel~=(?i)icon]");
        String favicon = icon != null && !icon.absUrl("href").isEmpty() ? icon.absUrl("href") : root(pageUrl) + "/favicon.ico";

        List<Form> forms = new ArrayList<>();
        for (Element f : doc.select("form")) {
            String action = f.absUrl("action");
            if (action.isEmpty()) action = pageUrl;
            boolean pw = false, otp = false, card = false, id = false;
            for (Element in : f.select("input, select, textarea")) {
                String type = in.attr("type").toLowerCase(Locale.ROOT);
                String hint = (in.attr("name") + " " + in.id() + " " + in.attr("placeholder") + " " + in.attr("aria-label")
                        + " " + in.attr("autocomplete")).toLowerCase(Locale.ROOT);
                if (type.equals("password")) pw = true;
                if (OTP.matcher(hint).find() || in.attr("autocomplete").equalsIgnoreCase("one-time-code")) otp = true;
                if (CARD.matcher(hint).find() || hint.contains("cc-number") || hint.contains("cc-csc")) card = true;
                if (ID.matcher(hint).find()) id = true;
            }
            forms.add(new Form(action, f.attr("method").isEmpty() ? "get" : f.attr("method").toLowerCase(Locale.ROOT), pw, otp, card, id));
        }
        // password boxes outside a <form> (sent by script): count as a form posting to the page itself
        if (forms.stream().noneMatch(Form::password) && !doc.select("input[type=password]").isEmpty())
            forms.add(new Form(pageUrl, "post", true, false, false, false));

        String pageHost = host(pageUrl);
        Set<String> ext = new TreeSet<>();
        for (Element s : doc.select("script[src]")) {
            String h = host(s.absUrl("src"));
            if (h != null && !h.equals(pageHost)) ext.add(h);
        }
        String refresh = null;
        Element meta = doc.selectFirst("meta[http-equiv~=(?i)refresh]");
        if (meta != null) {
            var m = Pattern.compile("url\\s*=\\s*['\"]?([^'\";]+)", Pattern.CASE_INSENSITIVE).matcher(meta.attr("content"));
            if (m.find()) {
                try { refresh = java.net.URI.create(pageUrl).resolve(m.group(1).strip()).toString(); } catch (Exception ignored) { /* bad url */ }
            }
        }
        return new PageFeatures(title, text, favicon, List.copyOf(forms), Collections.unmodifiableSet(ext), refresh);
    }

    static String host(String url) {
        try { var h = java.net.URI.create(url).getHost(); return h == null ? null : h.toLowerCase(Locale.ROOT); }
        catch (Exception e) { return null; }
    }

    private static String root(String url) {
        try { var u = java.net.URI.create(url); return u.getScheme() + "://" + u.getRawAuthority(); }
        catch (Exception e) { return url; }
    }
}
