package com.example.platform.evaluation.dataset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 内置评测集官方全集同步：
 * 优先通过 HuggingFace datasets-server rows API 拉取全部行；该地址在受限网络下不可用时，
 * 自动回退到镜像站下载官方 Parquet 文件并本地解析（默认 https://hf-mirror.com）。
 * 同步由平台自动触发（启动时 + 每日定时），在后台线程执行，失败时保留现有样本并记录错误状态。
 */
@Service
public class BuiltinDatasetSyncService {

    private static final Logger log = LoggerFactory.getLogger(BuiltinDatasetSyncService.class);
    private static final int PAGE_SIZE = 100;
    private static final int MAX_ROWS = 200_000;
    private static final long CONNECT_TIMEOUT_SECONDS = 10;
    private static final long REQUEST_TIMEOUT_SECONDS = 120;

    private final EvalDatasetMapper datasetMapper;
    private final EvalDatasetSampleMapper sampleMapper;
    private final ObjectMapper objectMapper;
    private final Set<Long> syncing = ConcurrentHashMap.newKeySet();
    /** 连接失败后在此时段内直接走镜像，避免每个数据集重复等待超时；到期后自动重试官方源。 */
    private volatile long primaryRetryAfterMillis = 0;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            // 镜像站下载地址会 302 跳转到 CDN，必须跟随重定向
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** datasets-server 地址可配置，便于在受限网络下通过代理/镜像转发。 */
    private final String apiBaseUrl;
    private final int maxRetries;
    /** 自动同步周期：离线超过该小时数才会在服务启动时重新拉取全集。 */
    private final long refreshHours;
    /** 镜像站地址：datasets-server 不可达时，改从镜像下载 Parquet。 */
    private final String mirrorBaseUrl;
    private final boolean mirrorFallbackEnabled;

    private final Map<String, HfSource> sources = Map.of(
            "gsm8k", new HfSource("openai/gsm8k", "main", "train"),
            "mmlu", new HfSource("cais/mmlu", "all", "test"),
            "humaneval", new HfSource("openai/openai_humaneval", "openai_humaneval", "test"),
            "ceval", new HfSource("ceval/ceval-exam", "all", "test"),
            "truthfulqa", new HfSource("truthfulqa/truthful_qa", "generation", "validation"),
            "mbpp", new HfSource("google-research-datasets/mbpp", "sanitized", "test")
    );

    public BuiltinDatasetSyncService(EvalDatasetMapper datasetMapper,
                                     EvalDatasetSampleMapper sampleMapper,
                                     ObjectMapper objectMapper,
                                     @Value("${platform.evaluation.builtin-sync.base-url:https://datasets-server.huggingface.co}")
                                     String apiBaseUrl,
                                     @Value("${platform.evaluation.builtin-sync.max-retries:3}")
                                     int maxRetries,
                                     @Value("${platform.evaluation.builtin-sync.refresh-hours:24}")
                                     long refreshHours,
                                     @Value("${platform.evaluation.builtin-sync.mirror-base-url:https://hf-mirror.com}")
                                     String mirrorBaseUrl,
                                     @Value("${platform.evaluation.builtin-sync.mirror-fallback-enabled:true}")
                                     boolean mirrorFallbackEnabled) {
        this.datasetMapper = datasetMapper;
        this.sampleMapper = sampleMapper;
        this.objectMapper = objectMapper;
        this.apiBaseUrl = apiBaseUrl == null || apiBaseUrl.isBlank()
                ? "https://datasets-server.huggingface.co"
                : apiBaseUrl.trim();
        this.maxRetries = Math.max(1, maxRetries);
        this.refreshHours = Math.max(1, refreshHours);
        this.mirrorBaseUrl = mirrorBaseUrl == null || mirrorBaseUrl.isBlank()
                ? "https://hf-mirror.com"
                : mirrorBaseUrl.trim();
        this.mirrorFallbackEnabled = mirrorFallbackEnabled;
    }

    /** 自动同步入口：未同步、上次失败或超过刷新周期时，后台拉取官方全集。 */
    public void autoSyncIfNeeded(EvalDatasetEntity dataset) {
        if (!Boolean.TRUE.equals(dataset.getIsBuiltin()) || sources.get(dataset.getBuiltinKey()) == null) {
            return;
        }
        String status = dataset.getSyncStatus();
        boolean needsSync = status == null || "IDLE".equals(status) || "ERROR".equals(status)
                || dataset.getLastSyncedAt() == null
                || dataset.getLastSyncedAt().isBefore(LocalDateTime.now().minusHours(refreshHours));
        if (!needsSync) return;
        try {
            syncAsync(dataset.getId());
        } catch (Exception e) {
            log.warn("Auto sync builtin dataset failed: dataset={}, error={}", dataset.getId(), e.getMessage());
        }
    }

    /** 异步同步；同一内置评测集不会并发同步。 */
    public void syncAsync(Long datasetId) {
        EvalDatasetEntity dataset = datasetMapper.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("评测集不存在: " + datasetId));
        if (!Boolean.TRUE.equals(dataset.getIsBuiltin())) {
            throw new IllegalArgumentException("仅内置评测集支持同步官方全集");
        }
        if (!syncing.add(datasetId)) return;

        datasetMapper.updateSyncStatus(datasetId, "SYNCING", dataset.getLastSyncedAt(), null);
        executor.submit(() -> {
            try {
                syncNow(dataset);
            } catch (Exception e) {
                log.warn("Builtin dataset sync failed: dataset={}, key={}, error={}",
                        datasetId, dataset.getBuiltinKey(), e.getMessage());
                datasetMapper.updateSyncStatus(datasetId, "ERROR",
                        dataset.getLastSyncedAt(), truncate(e.getMessage(), 1000));
            } finally {
                syncing.remove(datasetId);
            }
        });
    }

    private void syncNow(EvalDatasetEntity dataset) throws Exception {
        HfSource source = sources.get(dataset.getBuiltinKey());
        if (source == null) {
            throw new IllegalStateException("该内置评测集暂未配置官方同步源");
        }
        List<EvalDatasetSampleEntity> samples;
        Exception primaryError = null;
        try {
            if (System.currentTimeMillis() < primaryRetryAfterMillis) {
                throw new IllegalStateException("datasets-server 暂时不可达，直接使用镜像");
            }
            samples = fetchAll(dataset, source);
        } catch (Exception error) {
            primaryError = error;
            if (isConnectivityError(error)) {
                primaryRetryAfterMillis = System.currentTimeMillis() + 10 * 60_000L;
            }
            if (!mirrorFallbackEnabled) throw error;
            log.warn("Datasets-server unavailable, fallback to mirror parquet: dataset={}, key={}, error={}",
                    dataset.getId(), dataset.getBuiltinKey(), error.getMessage());
            try {
                samples = fetchViaMirror(dataset, source);
            } catch (Exception mirrorError) {
                mirrorError.addSuppressed(primaryError);
                throw new IllegalStateException("官方全集同步失败：数据源不可用（"
                        + primaryError.getMessage() + "），镜像回退也失败（"
                        + mirrorError.getMessage() + "）", mirrorError);
            }
        }
        if (samples.isEmpty()) {
            throw new IllegalStateException("官方数据集返回空结果");
        }

        // 全部数据拉取成功后再替换，网络失败不会清空现有样本
        sampleMapper.deleteBuiltinSamples(dataset.getId());
        int startIndex = sampleMapper.maxSampleIndex(dataset.getId()) + 1;
        List<EvalDatasetSampleEntity> batch = new ArrayList<>();
        int index = startIndex;
        for (EvalDatasetSampleEntity sample : samples) {
            sample.setDatasetId(dataset.getId());
            sample.setSampleIndex(index++);
            batch.add(sample);
            if (batch.size() >= 500) {
                sampleMapper.batchInsert(dataset.getId(), batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            sampleMapper.batchInsert(dataset.getId(), batch);
        }
        int total = sampleMapper.countByDataset(dataset.getId());
        datasetMapper.updateCounts(dataset.getId(), total, total);
        datasetMapper.updateSyncStatus(dataset.getId(), "READY", LocalDateTime.now(), null);
        log.info("Builtin dataset synced: dataset={}, key={}, total={}",
                dataset.getId(), dataset.getBuiltinKey(), total);
    }

    private List<EvalDatasetSampleEntity> fetchAll(EvalDatasetEntity dataset, HfSource source) throws Exception {
        List<EvalDatasetSampleEntity> result = new ArrayList<>();
        int offset = 0;
        int total = -1;
        while (offset < MAX_ROWS) {
            String url = apiBaseUrl + "/rows"
                    + "?dataset=" + encode(source.dataset())
                    + "&config=" + encode(source.config())
                    + "&split=" + encode(source.split())
                    + "&offset=" + offset
                    + "&length=" + PAGE_SIZE;
            JsonNode root = fetchPage(dataset, url, offset);
            if (total < 0) total = root.path("num_rows_total").asInt(-1);
            JsonNode rows = root.path("rows");
            if (!rows.isArray() || rows.isEmpty()) break;

            for (JsonNode entry : rows) {
                EvalDatasetSampleEntity sample = mapSample(dataset, entry.path("row"));
                if (sample != null) result.add(sample);
            }
            offset += rows.size();
            if (offset >= total && total > 0) break;
            if (rows.size() < PAGE_SIZE) break;
        }
        return result;
    }

    /**
     * 分页拉取并带重试：网络抖动、超时、429/5xx 时按退避重试，避免整套同步因单页失败中断。
     */
    private JsonNode fetchPage(EvalDatasetEntity dataset, String url, int offset) throws Exception {
        Exception lastError = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                        .header("Accept-Encoding", "identity")
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int code = response.statusCode();
                if (code == 200) {
                    return objectMapper.readTree(response.body());
                }
                if (code != 429 && code < 500) {
                    throw new IllegalStateException("HuggingFace 请求失败: HTTP " + code
                            + " " + truncate(response.body(), 300));
                }
                lastError = new IllegalStateException("HuggingFace 请求失败: HTTP " + code
                        + " " + truncate(response.body(), 300));
            } catch (IOException | IllegalStateException e) {
                lastError = e;
            }
            if (attempt < maxRetries) {
                log.warn("Builtin dataset sync retry: dataset={}, offset={}, attempt={}, error={}",
                        dataset.getId(), offset, attempt, lastError.getMessage());
                Thread.sleep(1000L * attempt);
            }
        }
        throw new IllegalStateException("官方数据源请求失败（offset=" + offset + "，已重试 "
                + maxRetries + " 次）：" + (lastError == null ? "未知错误" : lastError.getMessage())
                + "；请确认服务器可访问 " + apiBaseUrl, lastError);
    }

    /**
     * 镜像站回退：按仓库元数据定位官方 Parquet 文件，逐个下载并本地解析。
     * 适用于 datasets-server 被网络拦截、但镜像站可访问的部署环境。
     */
    private List<EvalDatasetSampleEntity> fetchViaMirror(EvalDatasetEntity dataset, HfSource source) throws Exception {
        List<String> files = resolveParquetFiles(source);
        if (files.isEmpty()) {
            throw new IllegalStateException("镜像站未找到 Parquet 数据文件: " + source.dataset());
        }
        List<EvalDatasetSampleEntity> result = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:")) {
            for (String file : files) {
                Path temp = null;
                try {
                    temp = downloadParquet(source, file);
                    readParquet(conn, dataset, temp, result);
                } finally {
                    if (temp != null) Files.deleteIfExists(temp);
                }
                if (result.size() >= MAX_ROWS) break;
            }
        }
        return result;
    }

    /** 读取仓库元数据，匹配 config/split 对应的 Parquet 文件；无聚合 config 时合并所有子配置。 */
    private List<String> resolveParquetFiles(HfSource source) throws Exception {
        String metaUrl = mirrorBaseUrl + "/api/datasets/" + encodePath(source.dataset());
        JsonNode root = fetchJson(metaUrl, "镜像站数据集元数据");
        List<String> matched = new ArrayList<>();
        List<String> splitFiles = new ArrayList<>();
        for (JsonNode sibling : root.path("siblings")) {
            String name = sibling.path("rfilename").asText("");
            if (!name.endsWith(".parquet")) continue;
            int slash = name.lastIndexOf('/');
            String leaf = slash >= 0 ? name.substring(slash + 1) : name;
            if (leaf.startsWith(source.split() + "-")) {
                splitFiles.add(name);
                if (name.startsWith(source.config() + "/")) {
                    matched.add(name);
                }
            }
        }
        if (!matched.isEmpty()) {
            matched.sort(String::compareTo);
            return matched;
        }
        splitFiles.sort(String::compareTo);
        return splitFiles;
    }

    private Path downloadParquet(HfSource source, String file) throws Exception {
        String url = mirrorBaseUrl + "/datasets/" + encodePath(source.dataset())
                + "/resolve/main/" + encodePath(file);
        Exception lastError = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            Path temp = Files.createTempFile("builtin-sync-", ".parquet");
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofMinutes(5))
                        .GET()
                        .build();
                HttpResponse<InputStream> response = httpClient.send(
                        request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() == 200) {
                    try (InputStream body = response.body()) {
                        Files.copy(body, temp, StandardCopyOption.REPLACE_EXISTING);
                    }
                    return temp;
                }
                response.body().close();
                lastError = new IllegalStateException("镜像下载失败: HTTP " + response.statusCode() + " " + file);
            } catch (IOException e) {
                lastError = e;
            }
            Files.deleteIfExists(temp);
            if (attempt < maxRetries) {
                log.warn("Builtin dataset mirror retry: file={}, attempt={}, error={}",
                        file, attempt, lastError == null ? "unknown" : lastError.getMessage());
                Thread.sleep(1000L * attempt);
            }
        }
        throw new IllegalStateException("从镜像下载 Parquet 失败（" + file + "，已重试 "
                + maxRetries + " 次）：" + (lastError == null ? "未知错误" : lastError.getMessage())
                + "；请确认服务器可访问 " + mirrorBaseUrl, lastError);
    }

    private JsonNode fetchJson(String url, String context) throws Exception {
        Exception lastError = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(60))
                        .header("Accept-Encoding", "identity")
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() == 200) {
                    return objectMapper.readTree(response.body());
                }
                lastError = new IllegalStateException(context + " 请求失败: HTTP " + response.statusCode());
                if (response.statusCode() != 429 && response.statusCode() < 500) break;
            } catch (IOException e) {
                lastError = e;
            }
            if (attempt < maxRetries) Thread.sleep(1000L * attempt);
        }
        throw new IllegalStateException(context + " 请求失败（已重试 " + maxRetries + " 次）："
                + (lastError == null ? "未知错误" : lastError.getMessage())
                + "；请确认服务器可访问 " + mirrorBaseUrl, lastError);
    }

    /** 使用内置 DuckDB 读取本地 Parquet，把每行转换成 JSON 后复用统一的样本映射逻辑。 */
    private void readParquet(Connection conn, EvalDatasetEntity dataset, Path file,
                             List<EvalDatasetSampleEntity> out) throws Exception {
        String path = file.toAbsolutePath().toString().replace('\\', '/').replace("'", "''");
        String sql = "SELECT * FROM read_parquet('" + path + "')";
        try (Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            int columns = meta.getColumnCount();
            while (rs.next()) {
                ObjectNode row = objectMapper.createObjectNode();
                for (int i = 1; i <= columns; i++) {
                    row.set(meta.getColumnName(i), toJsonNode(rs.getObject(i)));
                }
                EvalDatasetSampleEntity sample = mapSample(dataset, row);
                if (sample != null) {
                    out.add(sample);
                    if (out.size() >= MAX_ROWS) return;
                }
            }
        }
    }

    private JsonNode toJsonNode(Object value) throws Exception {
        if (value == null) return objectMapper.nullNode();
        if (value instanceof ArrayNode node) return node;
        if (value instanceof java.sql.Array array) {
            ArrayNode node = objectMapper.createArrayNode();
            Object items = array.getArray();
            if (items instanceof Object[] arr) {
                for (Object item : arr) node.add(toJsonNode(item));
            } else if (items != null) {
                node.add(toJsonNode(items));
            }
            array.free();
            return node;
        }
        if (value instanceof Object[] arr) {
            ArrayNode node = objectMapper.createArrayNode();
            for (Object item : arr) node.add(toJsonNode(item));
            return node;
        }
        if (value instanceof List<?> list) {
            ArrayNode node = objectMapper.createArrayNode();
            for (Object item : list) node.add(toJsonNode(item));
            return node;
        }
        if (value instanceof Boolean bool) return BooleanNode.valueOf(bool);
        if (value instanceof Integer || value instanceof Long
                || value instanceof Short || value instanceof Byte) {
            return LongNode.valueOf(((Number) value).longValue());
        }
        if (value instanceof Float || value instanceof Double) {
            return DoubleNode.valueOf(((Number) value).doubleValue());
        }
        if (value instanceof byte[] bytes) return TextNode.valueOf(new String(bytes, StandardCharsets.UTF_8));
        return TextNode.valueOf(String.valueOf(value));
    }

    private EvalDatasetSampleEntity mapSample(EvalDatasetEntity dataset, JsonNode row) {
        String input;
        String golden;
        switch (dataset.getBuiltinKey()) {
            case "gsm8k" -> {
                input = text(row, "question");
                golden = text(row, "answer");
            }
            case "mmlu" -> {
                List<String> choices = choices(row);
                input = withChoices(text(row, "question"), choices);
                golden = answerLetter(row.path("answer"), choices);
            }
            case "humaneval" -> {
                input = text(row, "prompt");
                golden = text(row, "canonical_solution");
            }
            case "ceval" -> {
                List<String> choices = choices(row);
                input = withChoices(text(row, "question"), choices);
                golden = answerLetter(row.path("answer"), choices);
            }
            case "truthfulqa" -> {
                input = text(row, "question");
                golden = text(row, "best_answer");
            }
            case "mbpp" -> {
                // sanitized 版本字段名为 prompt，full 版本为 text
                input = text(row, "text");
                if (input == null) input = text(row, "prompt");
                golden = text(row, "code");
            }
            default -> {
                return null;
            }
        }
        if (input == null || input.isBlank()) return null;

        EvalDatasetSampleEntity sample = new EvalDatasetSampleEntity();
        sample.setInputText(input);
        sample.setGoldenAnswer(golden);
        sample.setMetadata("{\"benchmark\":\"" + dataset.getBuiltinKey()
                + "\",\"builtin\":true,\"source\":\"huggingface\"}");
        sample.setScenarioTags(dataset.getScenarioTags());
        return sample;
    }

    private static List<String> choices(JsonNode row) {
        List<String> result = new ArrayList<>();
        JsonNode choices = row.path("choices");
        if (choices.isArray()) {
            choices.forEach(item -> result.add(item.asText()));
        }
        if (result.isEmpty()) {
            for (String field : List.of("A", "B", "C", "D")) {
                String value = text(row, field);
                if (value != null) result.add(value);
            }
        }
        return result;
    }

    private static String withChoices(String question, List<String> choices) {
        if (question == null || choices.isEmpty()) return question;
        StringBuilder value = new StringBuilder(question);
        for (int i = 0; i < choices.size(); i++) {
            value.append('\n').append((char) ('A' + i)).append(". ").append(choices.get(i));
        }
        return value.toString();
    }

    private static String answerLetter(JsonNode answer, List<String> choices) {
        if (answer == null || answer.isNull() || answer.isMissingNode()) return null;
        if (answer.isInt()) {
            int index = answer.asInt();
            return index >= 0 && index < 26 ? String.valueOf((char) ('A' + index)) : null;
        }
        String raw = answer.asText("").trim();
        if (raw.isEmpty()) return null;
        if (raw.length() == 1 && Character.isLetter(raw.charAt(0))) return raw.toUpperCase();
        try {
            int index = Integer.parseInt(raw);
            return index >= 0 && index < 26 ? String.valueOf((char) ('A' + index)) : null;
        } catch (NumberFormatException ignored) {
            return raw;
        }
    }

    private static String text(JsonNode row, String field) {
        JsonNode node = row.path(field);
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** 按路径段编码，保留 "/" 分隔符，用于镜像站文件路径。 */
    private static String encodePath(String path) {
        String[] parts = path.split("/", -1);
        StringBuilder encoded = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) encoded.append('/');
            encoded.append(encode(parts[i]).replace("+", "%20"));
        }
        return encoded.toString();
    }

    /** 判断是否为网络连通类失败（超时/连接拒绝），用于暂时跳过官方源直连。 */
    private static boolean isConnectivityError(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof java.net.http.HttpTimeoutException
                    || current instanceof java.net.ConnectException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && (message.contains("timed out") || message.contains("Connection refused"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private record HfSource(String dataset, String config, String split) {}
}
