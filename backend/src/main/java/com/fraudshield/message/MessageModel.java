package com.fraudshield.message;

import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 14.6: the same scam-message model the phone runs (ml/messages/train_msg.py -> models/msg-v1.bin), for the opt-in
 * cloud check. Feature code mirrors train_msg.py and MessageClassifier.kt (parity test on 100 messages).
 */
public final class MessageModel {
    public record Result(double scamProbability, String category, double categoryProbability) {}

    private static final Pattern URL = Pattern.compile(
            "(https?://\\S+|www\\.\\S+|\\b[\\w-]+\\.(?:top|xyz|online|site|in|co|info|click|com|net|org|me|ly)\\b\\S*)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS | Pattern.UNICODE_CASE);
    private static final Pattern DIGITS = Pattern.compile("\\d+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern SPACES = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private final int buckets;
    private final List<String> classes;
    private final float[] bias;
    private final float[][] w;

    private MessageModel(int buckets, List<String> classes, float[] bias, float[][] w) {
        this.buckets = buckets; this.classes = classes; this.bias = bias; this.w = w;
    }

    public static MessageModel fromClasspath() {
        try (InputStream in = MessageModel.class.getResourceAsStream("/models/msg-v1.bin")) {
            if (in == null) throw new IllegalStateException("models/msg-v1.bin missing");
            return load(in);
        } catch (Exception e) {
            throw new IllegalStateException("message model unreadable", e);
        }
    }

    static MessageModel load(InputStream input) throws Exception {
        DataInputStream d = new DataInputStream(new java.io.BufferedInputStream(input));
        byte[] magic = new byte[6];
        d.readFully(magic);
        if (!"FSMSG1".equals(new String(magic, StandardCharsets.US_ASCII))) throw new IllegalStateException("not a message model");
        int buckets = u32(d), n = u32(d);
        List<String> classes = new ArrayList<>();
        for (int i = 0; i < n; i++) { byte[] b = new byte[d.readUnsignedByte()]; d.readFully(b); classes.add(new String(b, StandardCharsets.UTF_8)); }
        float[] bias = floats(d, n);
        float[][] w = new float[n][];
        for (int i = 0; i < n; i++) w[i] = floats(d, buckets);
        return new MessageModel(buckets, List.copyOf(classes), bias, w);
    }

    private static int u32(DataInputStream d) throws Exception {
        byte[] b = new byte[4]; d.readFully(b); return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private static float[] floats(DataInputStream d, int count) throws Exception {
        byte[] b = new byte[count * 4]; d.readFully(b);
        float[] f = new float[count];
        ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(f);
        return f;
    }

    public List<String> classes() { return classes; }

    public Result classify(String text) {
        Map<Integer, Double> f = features(text, buckets);
        double[] logit = new double[classes.size()];
        for (int c = 0; c < logit.length; c++) {
            double s = bias[c];
            for (var e : f.entrySet()) s += w[c][e.getKey()] * e.getValue();
            logit[c] = s;
        }
        double max = Arrays.stream(logit).max().orElse(0), sum = 0;
        double[] p = new double[logit.length];
        for (int i = 0; i < p.length; i++) { p[i] = Math.exp(logit[i] - max); sum += p[i]; }
        for (int i = 0; i < p.length; i++) p[i] /= sum;
        int g = classes.indexOf("GENUINE"), best = g == 0 ? 1 : 0;
        for (int i = 0; i < p.length; i++) if (i != g && p[i] > p[best]) best = i;
        return new Result(1 - p[g], classes.get(best), p[best]);
    }

    static long fnv(String s) {
        long h = 0x811C9DC5L;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) { h ^= (b & 0xFF); h = (h * 0x01000193L) & 0xFFFFFFFFL; }
        return h;
    }

    static String normalise(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        t = URL.matcher(t).replaceAll(" zzurl ");
        t = DIGITS.matcher(t).replaceAll("0");
        return SPACES.matcher(t).replaceAll(" ").strip();
    }

    static Map<Integer, Double> features(String text, int buckets) {
        String t = normalise(text);
        int[] cps = (" " + t + " ").codePoints().toArray();
        LinkedHashSet<Integer> idx = new LinkedHashSet<>();
        for (int n = 3; n <= 5; n++)
            for (int i = 0; i + n <= cps.length; i++) idx.add((int) (fnv("c:" + new String(cps, i, n)) % buckets));
        String[] words = t.split(" ");
        for (String wd : words) if (!wd.isEmpty()) idx.add((int) (fnv("w:" + wd) % buckets));
        for (int i = 0; i + 1 < words.length; i++) idx.add((int) (fnv("b:" + words[i] + " " + words[i + 1]) % buckets));
        double v = 1.0 / Math.sqrt(Math.max(1, idx.size()));
        Map<Integer, Double> out = new LinkedHashMap<>();
        for (int k : idx) out.put(k, v);
        return out;
    }
}
