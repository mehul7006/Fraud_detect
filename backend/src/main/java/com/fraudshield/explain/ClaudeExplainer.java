package com.fraudshield.explain;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;

/**
 * 11.6: Claude words the verdict for ordinary people, in English and Hindi. It runs in the background (never inside a
 * scan's 3 s budget); its answer is only used when {@link ExplainService#valid} accepts it.
 * Input = verdict + reason codes (with their fixed meaning) + brand: never the link or any page content.
 */
public final class ClaudeExplainer implements ExplainService.Explainer {
    static final String MODEL = "claude-opus-5-5";
    static final String SYSTEM = """
            You explain the result of a link safety check to ordinary people in India, many of them new to phones.
            You get the verdict, the reasons the checker found, and (sometimes) the brand the link imitates.
            Write at most two short, calm sentences in simple words: what the danger is, and what to do (usually: do not
            open it, do not enter OTP / PIN / password). Do not invent facts beyond the reasons. Never include links,
            website addresses, markup or emoji.
            Reply with JSON only: {"en": "<English>", "hi": "<simple Hindi in Devanagari>"}""";

    private final AnthropicClient client;
    private final Function<String, String> meaning;
    private static final ObjectMapper JSON = new ObjectMapper();

    /** @param meaning reason code -> its English template sentence (fixed text from explanations_en.properties) */
    public ClaudeExplainer(String apiKey, Function<String, String> meaning) {
        this.client = AnthropicOkHttpClient.builder().apiKey(apiKey).timeout(Duration.ofSeconds(20)).maxRetries(1).build();
        this.meaning = meaning;
    }

    /** The user message: only closed-list values. Package-private for the prompt-injection test. */
    String prompt(ExplainService.Request r) {
        StringBuilder b = new StringBuilder("Verdict: ").append(r.verdict()).append('\n');
        if (r.brand() != null) b.append("Imitated brand: ").append(r.brand()).append('\n');
        b.append("Reasons:\n");
        for (String c : r.codes()) b.append("- ").append(c).append(": ").append(meaning.apply(c)).append('\n');
        return b.toString();
    }

    @Override
    public Map<String, String> explain(ExplainService.Request r) throws Exception {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(2000L)
                .system(SYSTEM)
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                .addUserMessage(prompt(r))
                // refused requests are retried by Anthropic on a suitable model (server-side fallback, beta)
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .build();
        Message m = client.messages().create(params);
        if (m.stopReason().map(s -> s.toString().equalsIgnoreCase("refusal")).orElse(false)) return null;
        StringBuilder text = new StringBuilder();
        m.content().forEach(block -> block.text().ifPresent(t -> text.append(t.text())));
        String s = text.toString().strip();
        int a = s.indexOf('{'), z = s.lastIndexOf('}');
        if (a < 0 || z <= a) return null;
        JsonNode n = JSON.readTree(s.substring(a, z + 1));
        if (!n.hasNonNull("en") || !n.hasNonNull("hi")) return null;
        return Map.of("en", n.get("en").asText(), "hi", n.get("hi").asText());
    }
}
