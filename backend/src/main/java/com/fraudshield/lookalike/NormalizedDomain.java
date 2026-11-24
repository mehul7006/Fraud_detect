package com.fraudshield.lookalike;

/**
 * 10.2 result. All names are lower case; {@code host}, {@code registrable}, {@code label} are in Unicode form
 * (punycode decoded), {@code asciiHost} is the punycode form that DNS uses.
 *
 * @param host           full host without "www." e.g. "login.flipkart.com.evil.xyz"
 * @param registrable    the part a person can buy, e.g. "evil.xyz", "flipkart.co.in", "my-shop.netlify.app"
 * @param publicSuffix   e.g. "xyz", "co.in", "netlify.app"
 * @param subdomain      everything in front of the registrable domain, "" if none
 * @param label          registrable without its suffix, e.g. "evil", "flipkart"
 * @param embeddedDomain a domain hidden in the sub-domain ("flipkart.com" in the example) or null
 * @param mixedScript    the label mixes Latin and other letters (Cyrillic "а" inside a Latin name)
 */
public record NormalizedDomain(String host, String asciiHost, String registrable, String publicSuffix,
                               String subdomain, String label, String embeddedDomain,
                               boolean ip, boolean idn, boolean mixedScript) {
    public int subdomainDepth() { return subdomain.isEmpty() ? 0 : subdomain.split("\\.").length; }
}
