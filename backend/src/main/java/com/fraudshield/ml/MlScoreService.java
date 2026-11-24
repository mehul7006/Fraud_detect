package com.fraudshield.ml;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudshield.lookalike.LookalikeService;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 11.4: the trained link model (ONNX, run by ONNX Runtime Java). Versioned: {@code <name>.onnx} + {@code <name>.json}
 * (feature names, threshold, metrics). If the model is missing, broken, or was trained on other features, the service
 * switches itself off and scans fall back to the rules - the app always starts.
 */
public final class MlScoreService implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(MlScoreService.class);

    private final FeatureExtractor features;
    private final OrtEnvironment env;
    private final OrtSession session;
    private final String input, version;
    private final double threshold;

    /** @param location "classpath:models/url-v1" or a file path without extension; null / missing = off */
    public MlScoreService(LookalikeService lookalike, String location) {
        this.features = new FeatureExtractor(lookalike);
        OrtEnvironment e = null;
        OrtSession s = null;
        String in = null, ver = null;
        double thr = 1.0;
        try {
            byte[] onnx = read(location, ".onnx");
            JsonNode meta = new ObjectMapper().readTree(read(location, ".json"));
            List<String> names = new ArrayList<>();
            meta.path("features").forEach(n -> names.add(n.asText()));
            if (!names.equals(FeatureExtractor.NAMES))
                throw new IllegalStateException("model was trained on other features (" + names.size() + " vs " + FeatureExtractor.NAMES.size() + ")");
            e = OrtEnvironment.getEnvironment();
            s = e.createSession(onnx, new OrtSession.SessionOptions());
            in = meta.path("input").asText("features");
            ver = meta.path("version").asText("?");
            thr = meta.path("threshold").asDouble(1.0);
            log.info("Link model {} loaded ({} features, threshold {})", ver, names.size(), thr);
        } catch (Exception | UnsatisfiedLinkError ex) {
            log.warn("Link model not used (rules only): {}", ex.toString());
            if (s != null) try { s.close(); } catch (Exception ignored) { /* closing */ }
            s = null;
        }
        this.env = e; this.session = s; this.input = in; this.version = ver; this.threshold = thr;
    }

    /** The shipped model, shared by plain-Java users (unit tests, old constructors). */
    public static MlScoreService defaults() { return Holder.INSTANCE; }
    private static final class Holder { static final MlScoreService INSTANCE = new MlScoreService(LookalikeService.defaults(), "classpath:models/url-v1"); }

    public boolean enabled() { return session != null; }
    public String version() { return version; }
    /** Probability above which the model calls a link phishing (false-positive rate <= 1 % on validation). */
    public double threshold() { return threshold; }

    /** Phishing probability 0..1, or null when the model is off or fails (the scan then uses the rules only). */
    public Double probability(String url) {
        if (session == null) return null;
        try {
            double[] f = features.extract(url);
            float[][] x = new float[1][f.length];
            for (int i = 0; i < f.length; i++) x[0][i] = (float) f[i];
            try (OnnxTensor t = OnnxTensor.createTensor(env, x); OrtSession.Result r = session.run(java.util.Map.of(input, t))) {
                for (var out : r) {
                    Object v = out.getValue().getValue();
                    if (v instanceof float[][] p && p[0].length == 2) return (double) p[0][1];
                }
            }
            return null;
        } catch (Exception e) {
            log.debug("model failed for one link: {}", e.toString());
            return null;
        }
    }

    private static byte[] read(String location, String ext) throws Exception {
        if (location == null || location.isBlank()) throw new IllegalStateException("no model configured");
        if (location.startsWith("classpath:")) {
            try (InputStream in = MlScoreService.class.getResourceAsStream("/" + location.substring(10) + ext)) {
                if (in == null) throw new IllegalStateException("model file missing: " + location + ext);
                return in.readAllBytes();
            }
        }
        return Files.readAllBytes(Path.of(location + ext));
    }

    @Override
    public void close() throws Exception { if (session != null) session.close(); }
}
