package com.example.platform.evaluation.agent;

import com.example.platform.common.ApiResponse;
import com.example.platform.common.BusinessException;
import com.example.platform.evaluation.common.EvaluationErrorCode;
import com.example.platform.evaluation.common.TargetType;
import com.example.platform.evaluation.target.EvalTargetEntity;
import com.example.platform.evaluation.target.EvalTargetMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 外部 Agent 接入管理 —— 创建仅在线接入、发放 SDK 密钥、轮换、启停、列表。
 *
 * <p>外部 Agent 统一使用 HTTP_AGENT 类型：填写离线 Endpoint 后可参与离线评测，
 * 通过这里的连接密钥接入后同时接收在线 Trace；两者共用同一个 target_id。
 */
@RestController("evaluationAgentController")
@RequestMapping("/api/evaluation/agents")
public class AgentController {

    private final EvalTargetMapper targetMapper;
    private final EvalAgentConnectionMapper connectionMapper;
    private final AgentTokenService tokenService;
    private final String ingestHost;

    public AgentController(EvalTargetMapper targetMapper,
                           EvalAgentConnectionMapper connectionMapper,
                           AgentTokenService tokenService,
                           @Value("${platform.evaluation.agent.ingest-host:http://localhost:8080/api/evaluation/ingest}")
                           String ingestHost) {
        this.targetMapper = targetMapper;
        this.connectionMapper = connectionMapper;
        this.tokenService = tokenService;
        this.ingestHost = ingestHost;
    }

    /** 创建外部 Agent 接入，返回一次性密钥与 SDK 配置。 */
    @PostMapping
    @Transactional
    public ApiResponse<AgentCredentials> create(@RequestBody CreateAgentRequest request) {
        if (request.spaceId() == null) {
            throw new BusinessException(EvaluationErrorCode.BAD_REQUEST, "spaceId 不能为空");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new BusinessException(EvaluationErrorCode.BAD_REQUEST, "Agent 名称不能为空");
        }
        String name = request.name().trim();
        if (targetMapper.countBySpaceAndName(request.spaceId(), name) > 0) {
            throw new BusinessException("CONFLICT", "当前空间下已存在同名 Agent：" + name);
        }

        EvalTargetEntity target = new EvalTargetEntity();
        target.setSpaceId(request.spaceId());
        target.setName(name);
        // 在线 Trace 是外部 Agent 的能力分支，统一归档到 HTTP_AGENT；Endpoint 留空表示仅在线观测
        target.setTargetType(TargetType.HTTP_AGENT.name());
        target.setAdapterConfig("{}");
        target.setRuntimeOptions("{}");
        target.setLangfuseTracing("{\"mode\":\"SDK_CONNECTION\"}");
        target.setTags("[\"external-agent\"]");
        target.setStatus("ACTIVE");
        targetMapper.insert(target);

        AgentTokenService.KeyPair keys = tokenService.generate();
        EvalAgentConnectionEntity connection = new EvalAgentConnectionEntity();
        connection.setSpaceId(request.spaceId());
        connection.setTargetId(target.getId());
        connection.setAgentKey(tokenService.newAgentKey());
        connection.setPublicKey(keys.publicKey());
        connection.setSecretHash(tokenService.hash(keys.publicKey(), keys.secretKey()));
        connection.setStatus("CONNECTED");
        connectionMapper.insert(connection);

        return ApiResponse.ok(credentials(target.getId(), connection.getAgentKey(),
                keys.publicKey(), keys.secretKey(), connection.getStatus()));
    }

    /**
     * 给已有外部 Agent（HTTP_AGENT）挂载在线 Trace 能力（不新增 Agent 记录，复用同一个 targetId）。
     * 这样离线评测与在线监控共享同一个 target_id。
     */
    @PostMapping("/{targetId}/connection")
    @Transactional
    public ApiResponse<AgentCredentials> attach(@PathVariable Long targetId,
                                                 @RequestParam Long spaceId) {
        EvalTargetEntity target = requireTarget(targetId, spaceId);
        if (!TargetType.HTTP_AGENT.name().equalsIgnoreCase(target.getTargetType())) {
            throw new BusinessException(EvaluationErrorCode.BAD_REQUEST,
                    "仅外部 Agent 可以挂载在线 Trace（当前类型: " + target.getTargetType() + "）");
        }
        if (connectionMapper.findByTargetId(targetId).isPresent()) {
            throw new BusinessException("CONFLICT", "该 Agent 已接入在线 Trace，请使用轮换密钥");
        }

        AgentTokenService.KeyPair keys = tokenService.generate();
        EvalAgentConnectionEntity connection = new EvalAgentConnectionEntity();
        connection.setSpaceId(spaceId);
        connection.setTargetId(targetId);
        connection.setAgentKey(tokenService.newAgentKey());
        connection.setPublicKey(keys.publicKey());
        connection.setSecretHash(tokenService.hash(keys.publicKey(), keys.secretKey()));
        connection.setStatus("CONNECTED");
        connectionMapper.insert(connection);

        return ApiResponse.ok(credentials(targetId, connection.getAgentKey(),
                keys.publicKey(), keys.secretKey(), connection.getStatus()));
    }

    /** 当前空间下所有外部 Agent 接入状态。 */
    @GetMapping
    public ApiResponse<List<AgentSummary>> list(@RequestParam Long spaceId) {
        return ApiResponse.ok(connectionMapper.listBySpace(spaceId));
    }

    /** 轮换密钥：旧密钥立即失效，新密钥只返回一次。 */
    @PostMapping("/{targetId}/rotate")
    @Transactional
    public ApiResponse<AgentCredentials> rotate(@PathVariable Long targetId,
                                                @RequestParam Long spaceId) {
        EvalTargetEntity target = requireTarget(targetId, spaceId);
        EvalAgentConnectionEntity connection = connectionMapper.findByTargetId(targetId)
                .orElseThrow(() -> new BusinessException("AGENT_NOT_FOUND", "Agent 接入记录不存在"));

        AgentTokenService.KeyPair keys = tokenService.generate();
        connectionMapper.rotate(targetId, keys.publicKey(),
                tokenService.hash(keys.publicKey(), keys.secretKey()));

        return ApiResponse.ok(credentials(targetId, connection.getAgentKey(),
                keys.publicKey(), keys.secretKey(), connection.getStatus()));
    }

    /** 启用 / 停用接入。status 只允许 CONNECTED / DISABLED。 */
    @PostMapping("/{targetId}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long targetId,
                                          @RequestParam Long spaceId,
                                          @RequestParam String status) {
        requireTarget(targetId, spaceId);
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!"CONNECTED".equals(normalized) && !"DISABLED".equals(normalized)) {
            throw new BusinessException(EvaluationErrorCode.BAD_REQUEST, "status 只允许 CONNECTED / DISABLED");
        }
        connectionMapper.updateStatus(targetId, normalized);
        return ApiResponse.ok(null);
    }

    private EvalTargetEntity requireTarget(Long targetId, Long spaceId) {
        EvalTargetEntity target = targetMapper.findById(targetId)
                .orElseThrow(() -> new BusinessException(
                        EvaluationErrorCode.TARGET_NOT_FOUND, "Target id=" + targetId + " 不存在"));
        if (!target.getSpaceId().equals(spaceId)) {
            throw new BusinessException(EvaluationErrorCode.TARGET_NOT_FOUND, "跨 Space 访问被拒绝");
        }
        return target;
    }

    private AgentCredentials credentials(Long targetId, String agentKey,
                                         String publicKey, String secretKey, String status) {
        Map<String, String> sdkEnv = new LinkedHashMap<>();
        sdkEnv.put("LANGFUSE_HOST", ingestHost);
        sdkEnv.put("LANGFUSE_PUBLIC_KEY", publicKey);
        sdkEnv.put("LANGFUSE_SECRET_KEY", secretKey);
        return new AgentCredentials(targetId, agentKey, publicKey, secretKey,
                status, ingestHost, sdkEnv);
    }

    public record CreateAgentRequest(Long spaceId, String name, String description) {}

    /**
     * 接入凭证。
     *
     * @param secretKey 只在此响应中出现一次，平台只保存 hash
     */
    public record AgentCredentials(
            Long targetId,
            String agentKey,
            String publicKey,
            String secretKey,
            String status,
            String ingestHost,
            Map<String, String> sdkEnv) {}
}
