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
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * 巡页 LLM 客户端：按任务快照中的模型配置调用 OpenAI 兼容接口。
 *
 * <p>规划只需文本；判定可使用视觉模型（截图压缩为长边 ≤1568px 的 JPEG）。
 * 推理模型耗尽 max_tokens 时自动放大并重试。
 */
@Component
public class PatrolAiClient {
    private static final Logger log = LoggerFactory.getLogger(PatrolAiClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_IMAGE_EDGE = 1568;
    private static final float JPEG_QUALITY = 0.8f;
    private static final int MAX_ATTEMPTS = 2;
    private static final double ESCALATE_FACTOR = 1.8d;
    private static final int MAX_TOKEN_CAP = 8192;

    private final ObjectStorageService objectStorageService;
    private final CredentialCipher credentialCipher;
    private final String storageBucket;
    private final int timeoutSeconds;
    private final Map<String, WebClient> clients = new ConcurrentHashMap<>();

    public PatrolAiClient(ObjectStorageService objectStorageService,
                          CredentialCipher credentialCipher,
                          @Value("${platform.storage.bucket}") String storageBucket,
                          @Value("${platform.patrol.llm-timeout-seconds:90}") int timeoutSeconds) {
        this.objectStorageService = objectStorageService;
        this.credentialCipher = credentialCipher;
        this.storageBucket = storageBucket;
        this.timeoutSeconds = timeoutSeconds <= 0 ? 90 : timeoutSeconds;
    }

    public LlmResponse completeText(AiModelConfigEntity config, String purpose,
                                    String systemPrompt, String userPrompt, int maxTokens) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(message("system", systemPrompt == null ? "" : systemPrompt));
        messages.add(message("user", userPrompt == null ? "" : userPrompt));
        return invoke(config, purpose, messages, maxTokens);
    }

    public LlmResponse completeVision(AiModelConfigEntity config, String purpose,
                                      String systemPrompt, String userPrompt,
                                      List<String> screenshotKeys, int maxTokens) {
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
        return invoke(config, purpose, messages, maxTokens);
    }

    /** 连接测试：优先拉取 /models，失败时用最小 completion 探测。 */
    public TestOutcome test(AiModelConfigEntity config) {
        long startedAt = System.currentTimeMillis();
        try {
            String baseUrl = config.getBaseUrl().replaceAll("/+$", "");
            WebClient client = clientFor(config);
            String modelsPath = baseUrl.endsWith("/v1") ? "/models" : "/v1/models";
            String raw = client.get().uri(modelsPath).retrieve().bodyToMono(String.class)
                    .block(Duration.ofSeconds(Math.min(20, timeoutSeconds)));
            JsonNode root = MAPPER.readTree(raw == null ? "{}" : raw);
            int count = root.path("data").isArray() ? root.path("data").size() : 0;
            return new TestOutcome(true,
                    "连接成功，返回 " + count + " 个模型" + (count > 0 ? "（含 " + config.getModelId() + "）" : ""),
                    System.currentTimeMillis() - startedAt);
        } catch (Exception modelsError) {
            try {
                List<Map<String, Object>> messages = List.of(message("user", "ping"));
                LlmResponse response = invoke(config, "patrol.test", messages, 64);
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
        int attemptMaxTokens = maxTokens <= 0
                ? (config.getMaxTokens() == null ? 4096 : config.getMaxTokens())
                : maxTokens;
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt += 1) {
            long startedAt = System.currentTimeMillis();
            try {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("model", config.getModelId());
                body.put("messages", messages);
                body.put("temperature", config.getTemperature() == null
                        ? new BigDecimal("0.2") : config.getTemperature());
                body.put("stream", false);
                body.put("max_tokens", attemptMaxTokens);
                String raw = client.post().uri(path).bodyValue(body).retrieve()
                        .bodyToMono(String.class).block(Duration.ofSeconds(timeoutSeconds));
                long elapsed = System.currentTimeMillis() - startedAt;
                JsonNode response = MAPPER.readTree(raw == null ? "{}" : raw);
                JsonNode message = response.path("choices").path(0).path("message");
                JsonNode choice = response.path("choices").path(0);
                String content = extractContent(message);
                String finishReason = choice.path("finish_reason").asText("");
                if (content.isBlank()) {
                    boolean reasoningOnly = !message.path("reasoning_content").asText("").isBlank();
                    if (reasoningOnly && "length".equals(finishReason)) {
                        throw new ReasoningExhaustedException(
                                "推理过程耗尽 max_tokens=" + attemptMaxTokens + "，未产出正文");
                    }
                    throw new IllegalStateException("模型返回内容为空");
                }
                // JSON 规划/判定被 max_tokens 截断时（finish_reason=length），
                // 首次直接放大重试，避免把半截 JSON 交给解析器后静默降级。
                if ("length".equals(finishReason) && attempt < MAX_ATTEMPTS) {
                    throw new TruncatedResponseException(
                            "输出达到 max_tokens=" + attemptMaxTokens + " 被截断，准备放大重试");
                }
                int promptTokens = response.path("usage").path("prompt_tokens").asInt(0);
                int completionTokens = response.path("usage").path("completion_tokens").asInt(0);
                if (promptTokens <= 0) {
                    promptTokens = messages.stream().mapToInt(PatrolAiClient::estimateMessageTokens).sum();
                }
                if (completionTokens <= 0) {
                    completionTokens = estimateTokens(content);
                }
                log.info("Patrol LLM succeeded. purpose={}, model={}, promptTokens={}, completionTokens={}, finishReason={}, elapsedMs={}",
                        purpose, config.getModelId(), promptTokens, completionTokens, finishReason, elapsed);
                return new LlmResponse(content, promptTokens, completionTokens, elapsed, config.getModelId());
            } catch (Exception exception) {
                if (exception instanceof ReasoningExhaustedException
                        || exception instanceof TruncatedResponseException) {
                    int bumped = Math.min(MAX_TOKEN_CAP,
                            (int) Math.round(attemptMaxTokens * ESCALATE_FACTOR));
                    if (bumped > attemptMaxTokens) {
                        attemptMaxTokens = bumped;
                        log.info("Patrol LLM max_tokens escalated. purpose={}, nextMaxTokens={}",
                                purpose, bumped);
                    }
                }
                lastFailure = new IllegalStateException("LLM 调用失败(" + purpose + "): "
                        + shorten(exception.getMessage()), exception);
                log.warn("Patrol LLM failed. purpose={}, model={}, attempt={}, reason={}",
                        purpose, config.getModelId(), attempt, shorten(exception.getMessage()));
                if (attempt < MAX_ATTEMPTS) {
                    sleepQuietly(500L);
                }
            }
        }
        throw lastFailure == null ? new IllegalStateException("LLM 调用失败") : lastFailure;
    }

    private WebClient clientFor(AiModelConfigEntity config) {
        String key = config.getBaseUrl() + "|" + config.getApiKeyEncrypted();
        return clients.computeIfAbsent(key, ignored -> {
            HttpClient httpClient = HttpClient.create().responseTimeout(Duration.ofSeconds(timeoutSeconds));
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
                    if (imageUrl instanceof Map<?, ?> image && image.get("url") instanceof String url) {
                        total += Math.max(800, url.length() / 5);
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

    public record TestOutcome(boolean success, String message, long elapsedMs) {
    }

    public record LlmResponse(String content, int promptTokens, int completionTokens,
                              long durationMs, String model) {
        public int totalTokens() {
            return promptTokens + completionTokens;
        }
    }
}
