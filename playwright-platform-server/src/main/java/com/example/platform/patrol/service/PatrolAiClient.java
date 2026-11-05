package com.example.platform.patrol.service;

import com.example.platform.common.CredentialCipher;
import com.example.platform.patrol.model.AiModelConfigEntity;
import com.example.platform.storage.service.ObjectStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * 巡页 LLM 客户端：按任务快照中的模型配置调用 OpenAI 兼容接口。
 *
 * <p>规划只需文本；判定可使用视觉模型（截图压缩为长边 ≤1568px 的 JPEG）。
 * 输出上限统一使用模型配置中的 maxTokens，不在各阶段单独指定；
 * 输出被截断或推理耗尽时不再放大重试，直接给出统一失败原因。
 */
@Component
public class PatrolAiClient {
    private static final Logger log = LoggerFactory.getLogger(PatrolAiClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_IMAGE_EDGE = 1568;
    private static final float JPEG_QUALITY = 0.8f;
    private static final int MAX_ATTEMPTS = 2;
    private static final int IMAGE_TOKEN_ESTIMATE = 1500;

    private final ObjectStorageService objectStorageService;
    private final CredentialCipher credentialCipher;
    private final String storageBucket;
    /** 流式读取时两段数据之间的空闲上限（秒），防止连接卡死。 */
    private final int idleTimeoutSeconds;
    /** 单次调用的总时长上限（秒），超时由任务时长预算兜底，不再用整段读超时。 */
    private final int maxCallSeconds;
    private final Map<String, WebClient> clients = new ConcurrentHashMap<>();

    public PatrolAiClient(ObjectStorageService objectStorageService,
                          CredentialCipher credentialCipher,
                          @Value("${platform.storage.bucket}") String storageBucket,
                          @Value("${platform.patrol.llm-timeout-seconds:120}") int idleTimeoutSeconds,
                          @Value("${platform.patrol.llm-max-call-seconds:1800}") int maxCallSeconds) {
        this.objectStorageService = objectStorageService;
        this.credentialCipher = credentialCipher;
        this.storageBucket = storageBucket;
        this.idleTimeoutSeconds = idleTimeoutSeconds <= 0 ? 120 : idleTimeoutSeconds;
        this.maxCallSeconds = maxCallSeconds <= 0 ? 1800 : maxCallSeconds;
    }

    public LlmResponse completeText(AiModelConfigEntity config, String purpose,
                                    String systemPrompt, String userPrompt) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(message("system", systemPrompt == null ? "" : systemPrompt));
        messages.add(message("user", userPrompt == null ? "" : userPrompt));
        return invoke(config, purpose, messages, outputLimit(config));
    }

    public LlmResponse completeVision(AiModelConfigEntity config, String purpose,
                                      String systemPrompt, String userPrompt,
                                      List<String> screenshotKeys) {
        if (!Boolean.TRUE.equals(config.getSupportsVision())) {
            throw new IllegalStateException("模型 " + config.getName() + " 未声明支持视觉输入");
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(message("system", systemPrompt == null ? "" : systemPrompt));
        List<Map<String, Object>> parts = new ArrayList<>();
        Map<String, Object> textPart = new LinkedHashMap<>();
        textPart.put("type", "text");
        textPart.put("text", userPrompt == null ? "" : userPrompt);
        parts.add(textPart);
        for (String key : screenshotKeys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            Map<String, Object> imageUrl = new LinkedHashMap<>();
            imageUrl.put("url", toDataUrl(key));
            Map<String, Object> imagePart = new LinkedHashMap<>();
            imagePart.put("type", "image_url");
            imagePart.put("image_url", imageUrl);
            parts.add(imagePart);
        }
        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", parts);
        messages.add(userMessage);
        return invoke(config, purpose, messages, outputLimit(config));
    }

    /** 全流程统一的输出上限：始终以模型配置中的 maxTokens 为准。 */
    public static int outputLimit(AiModelConfigEntity config) {
        int configured = config == null || config.getMaxTokens() == null ? 16384 : config.getMaxTokens();
        return configured <= 0 ? 16384 : configured;
    }

    /** 连接测试：优先拉取 /models，失败时用最小 completion 探测。 */
    public TestOutcome test(AiModelConfigEntity config) {
        long startedAt = System.currentTimeMillis();
        try {
            String baseUrl = config.getBaseUrl().replaceAll("/+$", "");
            WebClient client = clientFor(config);
            String modelsPath = baseUrl.endsWith("/v1") ? "/models" : "/v1/models";
            String raw = client.get().uri(modelsPath).retrieve().bodyToMono(String.class)
                    .block(Duration.ofSeconds(Math.min(20, idleTimeoutSeconds)));
            JsonNode root = MAPPER.readTree(raw == null ? "{}" : raw);
            int count = root.path("data").isArray() ? root.path("data").size() : 0;
            return new TestOutcome(true,
                    "连接成功，返回 " + count + " 个模型" + (count > 0 ? "（含 " + config.getModelId() + "）" : ""),
                    System.currentTimeMillis() - startedAt);
        } catch (Exception modelsError) {
            try {
                List<Map<String, Object>> messages = List.of(message("user", "ping"));
                LlmResponse response = invoke(config, "patrol.test", messages, Math.min(64, outputLimit(config)));
                return new TestOutcome(true,
                        "连接成功（/models 不可用，completion 正常，model=" + response.model() + "）",
                        System.currentTimeMillis() - startedAt);
            } catch (Exception completionError) {
                String message = completionError.getMessage() == null
                        ? modelsError.getMessage() : completionError.getMessage();
                return new TestOutcome(false, "连接失败：" + shorten(message),
                        System.currentTimeMillis() - startedAt);
            }
        }
    }

    private LlmResponse invoke(AiModelConfigEntity config, String purpose,
                               List<Map<String, Object>> messages, int maxTokens) {
        if (!"OPENAI_COMPATIBLE".equalsIgnoreCase(config.getProviderType())) {
            throw new IllegalStateException("暂仅支持 OPENAI_COMPATIBLE 协议，当前为 " + config.getProviderType());
        }
        WebClient client = clientFor(config);
        String path = chatCompletionsPath(config.getBaseUrl());
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt += 1) {
            long startedAt = System.currentTimeMillis();
            try {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("model", config.getModelId());
                body.put("messages", messages);
                body.put("temperature", config.getTemperature() == null
                        ? new BigDecimal("0.2") : config.getTemperature());
                body.put("stream", true);
                body.put("stream_options", Map.of("include_usage", true));
                body.put("max_tokens", maxTokens);
                StreamedCompletion completion = streamCompletion(client, path, body);
                long elapsed = System.currentTimeMillis() - startedAt;
                String content = completion.content();
                String finishReason = completion.finishReason();
                int promptTokens = completion.promptTokens();
                int completionTokens = completion.completionTokens();
                String reasoning = completion.reasoning();
                if (content.isBlank()) {
                    if (!reasoning.isBlank() && "length".equals(finishReason)) {
                        log.warn("Patrol LLM reasoning exhausted. purpose={}, model={}, maxTokens={}, "
                                        + "completionTokens={}, reasoningChars={}, elapsedMs={}",
                                purpose, config.getModelId(), maxTokens, completionTokens,
                                reasoning.length(), elapsed);
                        throw new ReasoningExhaustedException(
                                "推理过程耗尽 maxTokens（思考 " + reasoning.length() + " 字符 / 输出 "
                                        + completionTokens + " tokens），未产出正文；"
                                        + "请改用非推理模型或调大模型配置中的 maxTokens");
                    }
                    throw new IllegalStateException("模型返回内容为空（finish_reason="
                            + (finishReason.isBlank() ? "unknown" : finishReason) + "）");
                }
                // 截断的 JSON 交给解析器只会静默降级，这里直接失败并给出统一原因。
                if ("length".equals(finishReason)) {
                    throw new TruncatedResponseException(
                            "输出被截断（finish_reason=length）；请调大模型配置中的 maxTokens 或减少输入内容");
                }
                if (promptTokens <= 0) {
                    promptTokens = messages.stream().mapToInt(PatrolAiClient::estimateMessageTokens).sum();
                }
                if (completionTokens <= 0) {
                    completionTokens = estimateTokens(content);
                }
                log.info("Patrol LLM succeeded. purpose={}, model={}, promptTokens={}, completionTokens={}, finishReason={}, maxTokens={}, elapsedMs={}",
                        purpose, config.getModelId(), promptTokens, completionTokens, finishReason,
                        maxTokens, elapsed);
                return new LlmResponse(content, promptTokens, completionTokens, elapsed, config.getModelId());
            } catch (Exception exception) {
                RuntimeException failure = new IllegalStateException(
                        errorText(purpose, exception, maxTokens), exception);
                lastFailure = failure;
                log.warn("Patrol LLM failed. purpose={}, model={}, attempt={}, maxTokens={}, reason={}",
                        purpose, config.getModelId(), attempt, maxTokens, failure.getMessage());
                // 只有网络类失败重试；超时、截断、推理耗尽属于确定性失败，重试只会成倍拉长等待。
                if (!isRetryable(exception)) {
                    break;
                }
                if (attempt < MAX_ATTEMPTS) {
                    sleepQuietly(500L);
                }
            }
        }
        throw lastFailure == null
                ? new IllegalStateException(errorText(purpose, null, maxTokens))
                : lastFailure;
    }

    /** 只有网络类失败才重试；超时、截断、推理耗尽等确定性失败直接失败。 */
    private static boolean isRetryable(Exception exception) {
        if (exception instanceof ReasoningExhaustedException
                || exception instanceof TruncatedResponseException) {
            return false;
        }
        Throwable timeout = exception;
        while (timeout != null) {
            if (timeout instanceof java.util.concurrent.TimeoutException
                    || timeout instanceof java.net.SocketTimeoutException
                    || timeout.getClass().getName().startsWith("io.netty.handler.timeout.")
                    || (timeout instanceof IllegalStateException
                    && timeout.getMessage() != null
                    && timeout.getMessage().startsWith("Timeout on blocking read"))) {
                return false;
            }
            timeout = timeout.getCause();
        }
        Throwable network = exception;
        while (network != null) {
            if (network instanceof java.net.ConnectException
                    || network instanceof java.net.UnknownHostException
                    || network instanceof java.net.SocketException
                    || network instanceof java.io.IOException
                    || "reactor.netty.http.client.PrematureCloseException".equals(network.getClass().getName())) {
                return true;
            }
            network = network.getCause();
        }
        return false;
    }

    /** 各阶段统一的失败文案，避免同名问题在不同阶段各说各话。 */
    private static String errorText(String purpose, Exception cause, int maxTokens) {
        String reason = cause == null ? "" : shorten(cause.getMessage());
        if (reason == null || reason.isBlank()) {
            reason = "接口调用异常";
        }
        return "LLM 调用失败（" + stageLabel(purpose) + "）：" + reason + "（maxTokens=" + maxTokens + "）";
    }

    private static String stageLabel(String purpose) {
        if (purpose == null) {
            return "模型调用";
        }
        return switch (purpose) {
            case "patrol.plan" -> "用例规划";
            case "patrol.plan-coverage" -> "覆盖率补全";
            case "patrol.judge" -> "视觉判定";
            case "patrol.test" -> "连接测试";
            default -> purpose;
        };
    }

    private WebClient clientFor(AiModelConfigEntity config) {
        String key = config.getBaseUrl() + "|" + config.getApiKeyEncrypted();
        return clients.computeIfAbsent(key, ignored -> {
            HttpClient httpClient = HttpClient.create().responseTimeout(Duration.ofSeconds(idleTimeoutSeconds));
            String apiKey = credentialCipher.decrypt(config.getApiKeyEncrypted());
            return WebClient.builder()
                    .baseUrl(config.getBaseUrl())
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .codecs(configurer -> configurer.defaultCodecs()
                            .maxInMemorySize(32 * 1024 * 1024))
                    .build();
        });
    }

    /**
     * 流式读取：SSE 边到边累计，只有“两段数据之间的空闲”受超时约束，
     * 整段读超时改由单次调用上限兜底，避免慢模型（推理模型）被固定时长误杀。
     */
    private StreamedCompletion streamCompletion(WebClient client, String path, Map<String, Object> body) {
        StreamAccumulator accumulator = new StreamAccumulator();
        client.post().uri(path)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(text -> new IllegalStateException(
                                "HTTP " + response.statusCode().value() + " " + shorten(text))))
                .bodyToFlux(DataBuffer.class)
                .timeout(Duration.ofSeconds(idleTimeoutSeconds))
                .doOnNext(buffer -> {
                    String chunk = buffer.toString(StandardCharsets.UTF_8);
                    DataBufferUtils.release(buffer);
                    accumulator.feed(chunk);
                })
                .blockLast(Duration.ofSeconds(maxCallSeconds));
        return accumulator.finish();
    }

    private String toDataUrl(String objectKey) {
        try (InputStream stream = objectStorageService.getObject(storageBucket, objectKey)) {
            BufferedImage image = ImageIO.read(stream);
            if (image == null) {
                throw new IllegalStateException("截图无法解析: " + objectKey);
            }
            BufferedImage scaled = scaleDown(image);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            writeJpeg(scaled, output);
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("截图读取失败: " + exception.getMessage(), exception);
        }
    }

    private static BufferedImage scaleDown(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int longest = Math.max(width, height);
        if (longest <= MAX_IMAGE_EDGE) {
            return toRgb(source, width, height);
        }
        double ratio = (double) MAX_IMAGE_EDGE / longest;
        int targetWidth = Math.max(1, (int) Math.round(width * ratio));
        int targetHeight = Math.max(1, (int) Math.round(height * ratio));
        BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static BufferedImage toRgb(BufferedImage source, int width, int height) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static void writeJpeg(BufferedImage image, ByteArrayOutputStream output) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY);
        try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    private static Map<String, Object> message(String role, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    /** 兼容 content 为字符串或 [{type:text,text:...}] 数组两类返回格式。 */
    private static String extractContent(JsonNode message) {
        JsonNode content = message.path("content");
        if (content.isTextual()) {
            return content.asText("");
        }
        if (content.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode part : content) {
                String text = part.path("text").asText("");
                if (!text.isEmpty()) {
                    builder.append(text);
                }
            }
            return builder.toString();
        }
        return "";
    }

    /** baseUrl 已带 /v1 时直接拼 /chat/completions，否则补全 /v1。 */
    static String chatCompletionsPath(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        return normalized.endsWith("/v1") ? "/chat/completions" : "/v1/chat/completions";
    }

    /** 粗略 token 估算：中文按 1 字 ≈ 1 token，英文按 4 字符 ≈ 1 token。 */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int cjk = 0;
        int other = 0;
        for (int index = 0; index < text.length(); index += 1) {
            char ch = text.charAt(index);
            if (ch >= 0x4E00 && ch <= 0x9FFF) {
                cjk += 1;
            } else {
                other += 1;
            }
        }
        return cjk + Math.max(1, other / 4);
    }

    private static int estimateMessageTokens(Map<String, Object> message) {
        Object content = message.get("content");
        if (content instanceof String text) {
            return estimateTokens(text);
        }
        if (content instanceof List<?> parts) {
            int total = 0;
            for (Object part : parts) {
                if (part instanceof Map<?, ?> map) {
                    Object text = map.get("text");
                    if (text instanceof String value) {
                        total += estimateTokens(value);
                    }
                    Object imageUrl = map.get("image_url");
                    if (imageUrl instanceof Map<?, ?> image && image.get("url") instanceof String) {
                        // 图片按视觉模型固定估算，不能按 base64 字符串长度（会严重高估）。
                        total += IMAGE_TOKEN_ESTIMATE;
                    }
                }
            }
            return total;
        }
        return 0;
    }

    private static String shorten(String value) {
        if (value == null) {
            return "";
        }
        String text = value.replace('\n', ' ').trim();
        return text.length() <= 300 ? text : text.substring(0, 300);
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class ReasoningExhaustedException extends IllegalStateException {
        ReasoningExhaustedException(String message) {
            super(message);
        }
    }

    /** 输出达到上限被截断（JSON 通常不完整）。 */
    private static final class TruncatedResponseException extends IllegalStateException {
        TruncatedResponseException(String message) {
            super(message);
        }
    }

    /** 单次流式调用的累计结果。 */
    private record StreamedCompletion(String content, String reasoning, String finishReason,
                                      int promptTokens, int completionTokens) {
    }

    /**
     * SSE 累计器：按事件块解析 data，累加正文、思考链、finish_reason 与 usage；
     * 服务端忽略 stream=true 直接返回整段 JSON 时，退化为普通 completion 解析。
     */
    private static final class StreamAccumulator {
        private final StringBuilder content = new StringBuilder();
        private final StringBuilder reasoning = new StringBuilder();
        private final StringBuilder pending = new StringBuilder();
        private final StringBuilder raw = new StringBuilder();
        private String finishReason = "";
        private int promptTokens;
        private int completionTokens;
        private int events;

        void feed(String chunk) {
            if (chunk == null || chunk.isEmpty()) {
                return;
            }
            raw.append(chunk);
            // 统一换行符后按空行切事件：一个事件可能在多次网络回调里被拆开。
            pending.append(chunk.replace("\r", ""));
            int boundary;
            while ((boundary = pending.indexOf("\n\n")) >= 0) {
                String block = pending.substring(0, boundary);
                pending.delete(0, boundary + 2);
                handleEvent(block);
            }
        }

        StreamedCompletion finish() {
            if (pending.length() > 0) {
                String block = pending.toString();
                pending.setLength(0);
                handleEvent(block);
            }
            if (events == 0) {
                parseWholeBody(raw.toString());
            }
            return new StreamedCompletion(content.toString(), reasoning.toString(),
                    finishReason, promptTokens, completionTokens);
        }

        private void handleEvent(String block) {
            StringBuilder data = new StringBuilder();
            for (String line : block.split("\n")) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String value = line.substring(5).trim();
                if (value.isEmpty()) {
                    continue;
                }
                if (data.length() > 0) {
                    data.append('\n');
                }
                data.append(value);
            }
            if (data.length() == 0) {
                return;
            }
            String payload = data.toString();
            if ("[DONE]".equals(payload)) {
                return;
            }
            JsonNode root;
            try {
                root = MAPPER.readTree(payload);
            } catch (Exception ignored) {
                return;
            }
            events += 1;
            accumulate(root);
        }

        private void accumulate(JsonNode root) {
            JsonNode usage = root.path("usage");
            if (usage.isObject()) {
                promptTokens = usage.path("prompt_tokens").asInt(promptTokens);
                completionTokens = usage.path("completion_tokens").asInt(completionTokens);
            }
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return;
            }
            JsonNode choice = choices.get(0);
            String finish = choice.path("finish_reason").asText("");
            if (!finish.isBlank()) {
                finishReason = finish;
            }
            JsonNode delta = choice.has("delta") ? choice.path("delta") : choice.path("message");
            appendText(content, delta.path("content"));
            appendText(reasoning, delta.path("reasoning_content"));
            if (reasoning.length() == 0) {
                appendText(reasoning, delta.path("reasoning"));
            }
        }

        private void parseWholeBody(String text) {
            String trimmed = text == null ? "" : text.trim();
            if (trimmed.isEmpty()) {
                return;
            }
            JsonNode root;
            try {
                root = MAPPER.readTree(trimmed);
            } catch (Exception ignored) {
                return;
            }
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return;
            }
            JsonNode choice = choices.get(0);
            JsonNode message = choice.path("message");
            content.append(extractContent(message));
            appendText(reasoning, message.path("reasoning_content"));
            finishReason = choice.path("finish_reason").asText(finishReason);
            JsonNode usage = root.path("usage");
            if (usage.isObject()) {
                promptTokens = usage.path("prompt_tokens").asInt(promptTokens);
                completionTokens = usage.path("completion_tokens").asInt(completionTokens);
            }
            events += 1;
        }

        private static void appendText(StringBuilder target, JsonNode node) {
            if (node == null || node.isMissingNode() || node.isNull()) {
                return;
            }
            if (node.isTextual()) {
                target.append(node.asText(""));
                return;
            }
            if (node.isArray()) {
                for (JsonNode part : node) {
                    target.append(part.isTextual() ? part.asText("") : part.path("text").asText(""));
                }
            }
        }
    }

    public record TestOutcome(boolean success, String message, long elapsedMs) {
    }

    public record LlmResponse(String content, int promptTokens, int completionTokens,
                              long durationMs, String model) {
        public int totalTokens() {
            return promptTokens + completionTokens;
        }
    }
}
