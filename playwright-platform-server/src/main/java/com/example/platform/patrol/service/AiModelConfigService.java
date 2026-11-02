package com.example.platform.patrol.service;

import com.example.platform.common.CredentialCipher;
import com.example.platform.patrol.dto.AiModelConfigRequest;
import com.example.platform.patrol.dto.AiModelConfigResponse;
import com.example.platform.patrol.dto.AiModelOptionResponse;
import com.example.platform.patrol.dto.AiModelTestResponse;
import com.example.platform.patrol.mapper.AiModelConfigMapper;
import com.example.platform.patrol.model.AiModelConfigEntity;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * AI 模型配置服务：空间自行新建模型，规划与判定可分别选择。
 */
@Service
public class AiModelConfigService {
    private static final String PROVIDER_OPENAI = "OPENAI_COMPATIBLE";

    private final AiModelConfigMapper mapper;
    private final CredentialCipher credentialCipher;
    private final PatrolAiClient client;

    public AiModelConfigService(
            AiModelConfigMapper mapper,
            CredentialCipher credentialCipher,
            PatrolAiClient client) {
        this.mapper = mapper;
        this.credentialCipher = credentialCipher;
        this.client = client;
    }

    public List<AiModelConfigResponse> list(Long spaceId) {
        return mapper.findVisibleBySpaceId(spaceId).stream().map(AiModelConfigResponse::from).toList();
    }

    public List<AiModelOptionResponse> options(Long spaceId) {
        return mapper.findVisibleBySpaceId(spaceId).stream()
                .filter(entity -> !Boolean.FALSE.equals(entity.getEnabled()))
                .map(AiModelOptionResponse::from)
                .toList();
    }

    public AiModelConfigResponse create(Long spaceId, AiModelConfigRequest request, String actor) {
        AiModelConfigEntity entity = new AiModelConfigEntity();
        entity.setSpaceId(spaceId);
        apply(entity, request, true);
        entity.setCreatedBy(actor);
        entity.setCreatedAt(LocalDateTime.now());
        mapper.insert(entity);
        return AiModelConfigResponse.from(mapper.findById(entity.getId()).orElse(entity));
    }

    public AiModelConfigResponse update(Long spaceId, Long id, AiModelConfigRequest request) {
        AiModelConfigEntity entity = requireOwned(spaceId, id);
        apply(entity, request, false);
        mapper.update(entity);
        return AiModelConfigResponse.from(mapper.findById(id).orElse(entity));
    }

    public void delete(Long spaceId, Long id) {
        requireOwned(spaceId, id);
        mapper.deleteByIdAndSpaceId(id, spaceId);
    }

    public AiModelTestResponse test(Long spaceId, Long id) {
        AiModelConfigEntity entity = requireOwned(spaceId, id);
        PatrolAiClient.TestOutcome outcome = client.test(entity);
        mapper.updateTestResult(entity.getId(), outcome.success() ? "SUCCESS" : "FAILED", outcome.message());
        return new AiModelTestResponse(outcome.success(), outcome.message(), outcome.elapsedMs());
    }

    /** 按模型 ID 解析本空间的模型；未选择时返回 null（由调用方降级）。 */
    public AiModelConfigEntity resolve(Long spaceId, Long modelId, boolean requireVision) {
        if (modelId == null) {
            return null;
        }
        AiModelConfigEntity entity = mapper.findByIdAndSpaceId(modelId, spaceId)
                .orElseThrow(() -> new IllegalStateException("模型配置不存在或不属于当前空间"));
        if (Boolean.FALSE.equals(entity.getEnabled())) {
            throw new IllegalStateException("模型配置已停用：" + entity.getName());
        }
        if (requireVision && !Boolean.TRUE.equals(entity.getSupportsVision())) {
            throw new IllegalStateException("判定模型必须支持视觉输入：" + entity.getName());
        }
        return entity;
    }

    private AiModelConfigEntity requireOwned(Long spaceId, Long id) {
        return mapper.findByIdAndSpaceId(id, spaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "模型配置不存在或不可编辑"));
    }

    private void apply(AiModelConfigEntity entity, AiModelConfigRequest request, boolean create) {
        String name = trim(request.name());
        if (name.isEmpty() || name.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型名称不能为空且不超过 128 个字符");
        }
        if (mapper.existsByName(entity.getSpaceId(), name, entity.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "同名模型配置已存在");
        }
        entity.setName(name);
        String provider = trim(request.providerType()).isEmpty()
                ? PROVIDER_OPENAI : request.providerType().trim().toUpperCase(Locale.ROOT);
        if (!PROVIDER_OPENAI.equals(provider)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前仅支持 OPENAI_COMPATIBLE 协议");
        }
        entity.setProviderType(provider);
        entity.setBaseUrl(validateBaseUrl(request.baseUrl()));
        String apiKey = trim(request.apiKey());
        if (create && apiKey.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写 API Key");
        }
        if (!apiKey.isEmpty()) {
            if (!credentialCipher.isConfigured()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "平台未配置 PLATFORM_CREDENTIAL_KEY，无法保存 API Key");
            }
            entity.setApiKeyEncrypted(credentialCipher.encrypt(apiKey));
        }
        String modelId = trim(request.modelId());
        if (modelId.isEmpty() || modelId.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写模型 ID（不超过 128 个字符）");
        }
        entity.setModelId(modelId);
        entity.setSupportsVision(Boolean.TRUE.equals(request.supportsVision()));
        entity.setSupportsTools(Boolean.TRUE.equals(request.supportsTools()));
        entity.setSupportsJson(!Boolean.FALSE.equals(request.supportsJson()));
        int maxTokens = request.maxTokens() == null ? 4096 : request.maxTokens();
        if (maxTokens < 256 || maxTokens > 32768) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "maxTokens 需在 256 到 32768 之间");
        }
        entity.setMaxTokens(maxTokens);
        BigDecimal temperature = request.temperature() == null
                ? new BigDecimal("0.20") : request.temperature();
        if (temperature.compareTo(BigDecimal.ZERO) < 0 || temperature.compareTo(new BigDecimal("1.5")) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "temperature 需在 0 到 1.5 之间");
        }
        entity.setTemperature(temperature.setScale(2, java.math.RoundingMode.HALF_UP));
        entity.setEnabled(!Boolean.FALSE.equals(request.enabled()));
    }

    private String validateBaseUrl(String raw) {
        String value = trim(raw).replaceAll("/+$", "");
        if (value.isEmpty() || value.length() > 512) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写合法的 Base URL");
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Base URL 必须以 http:// 或 https:// 开头");
            }
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (host.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Base URL 缺少主机名");
            }
            if (host.equals("169.254.169.254") || host.equals("metadata.google.internal")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Base URL 不允许指向云元数据地址");
            }
            return value;
        } catch (java.net.URISyntaxException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Base URL 格式不合法");
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
