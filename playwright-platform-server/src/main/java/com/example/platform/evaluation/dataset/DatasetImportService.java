package com.example.platform.evaluation.dataset;

import com.example.platform.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** JSONL 导入 + 外部评测集白名单导入（C10）。 */
@Service
public class DatasetImportService {

    private static final Logger log = LoggerFactory.getLogger(DatasetImportService.class);
    private static final long MAX_FILE_BYTES = 50 * 1024 * 1024; // 50MB
    private static final List<String> URL_WHITELIST = List.of(
            "huggingface.co", "cdn-lfs.huggingface.co"
    );

    private final EvalDatasetMapper datasetMapper;
    private final EvalDatasetSampleMapper sampleMapper;
    private final ObjectMapper mapper = new ObjectMapper();

    public DatasetImportService(EvalDatasetMapper datasetMapper, EvalDatasetSampleMapper sampleMapper) {
        this.datasetMapper = datasetMapper;
        this.sampleMapper = sampleMapper;
    }

    public record ImportResult(int totalRows, int successRows, int failedRows, List<String> errors) {}

    /** 从 JSONL InputStream 导入。 */
    public ImportResult importJsonl(Long datasetId, InputStream is, long fileSize) {
        if (fileSize > MAX_FILE_BYTES) {
            throw new BusinessException("DATASET_IMPORT_FAILED", "文件超过 50MB，被安全策略拒绝");
        }
        // 重新导入语义：先清空旧样本
        sampleMapper.deleteByDataset(datasetId);

        List<EvalDatasetSampleEntity> batch = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int total = 0, success = 0, failed = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                total++;
                if (line.isBlank()) continue;
                try {
                    JsonNode node = mapper.readTree(line);
                    if (!node.has("input") || node.get("input").isNull()) {
                        errors.add("第 " + total + " 行: 缺少 input 字段");
                        failed++;
                        continue;
                    }
                    EvalDatasetSampleEntity sample = new EvalDatasetSampleEntity();
                    sample.setDatasetId(datasetId);
                    sample.setSampleIndex(total - 1);
                    sample.setInputText(node.get("input").asText());
                    sample.setGoldenAnswer(node.has("golden_answer") ? node.get("golden_answer").asText() : null);
                    sample.setKeywords(normalizeKeywords(node.get("keywords")));
                    sample.setReferenceContext(node.has("reference_context") ? node.get("reference_context").asText() : null);
                    sample.setMetadata(node.has("metadata") ? mapper.writeValueAsString(node.get("metadata")) : null);
                    batch.add(sample);
                    success++;
                    // 每 500 条 flush 一次
                    if (batch.size() >= 500) {
                        sampleMapper.batchInsert(datasetId, batch);
                        batch.clear();
                    }
                } catch (Exception e) {
                    errors.add("第 " + total + " 行: " + e.getMessage());
                    failed++;
                }
            }
            if (!batch.isEmpty()) {
                sampleMapper.batchInsert(datasetId, batch);
            }
            datasetMapper.updateCounts(datasetId, success, success);
        } catch (Exception e) {
            throw new BusinessException("DATASET_IMPORT_FAILED", "JSONL 导入失败: " + e.getMessage());
        }
        return new ImportResult(total, success, failed, errors);
    }

    /** 关键词支持 JSON 数组，或用逗号/空白分隔的字符串；统一存为 JSON 数组。 */
    private String normalizeKeywords(JsonNode node) throws Exception {
        if (node == null || node.isNull()) return null;
        List<String> list = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = item.asText("").trim();
                if (!value.isEmpty()) list.add(value);
            }
        } else {
            String raw = node.asText("").trim();
            if (!raw.isEmpty()) {
                for (String part : raw.split("[,，、\\s]+")) {
                    if (!part.isBlank()) list.add(part.trim());
                }
            }
        }
        return list.isEmpty() ? null : mapper.writeValueAsString(list);
    }

    /** 外部 URL 导入 —— 白名单校验。 */
    public void validateUrlWhitelist(String url) {
        try {
            java.net.URL u = new java.net.URL(url);
            String host = u.getHost().toLowerCase();
            boolean allowed = URL_WHITELIST.stream().anyMatch(host::endsWith);
            if (!allowed) {
                throw new BusinessException("DATASET_IMPORT_FAILED", "URL 不在白名单内: " + host);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("DATASET_IMPORT_FAILED", "URL 解析失败: " + e.getMessage());
        }
    }
}
