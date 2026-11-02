package com.example.platform.patrol.service;

import com.example.platform.common.CredentialCipher;
import com.example.platform.common.PageResponse;
import com.example.platform.patrol.dto.PatrolSceneRequest;
import com.example.platform.patrol.dto.PatrolSceneResponse;
import com.example.platform.patrol.dto.PatrolTaskResponse;
import com.example.platform.patrol.dto.PatrolTaskSummaryResponse;
import com.example.platform.patrol.mapper.AiModelConfigMapper;
import com.example.platform.patrol.mapper.PatrolSceneMapper;
import com.example.platform.patrol.mapper.PatrolTaskMapper;
import com.example.platform.patrol.model.AiModelConfigEntity;
import com.example.platform.patrol.model.PatrolSceneEntity;
import com.example.platform.patrol.model.PatrolSceneTokenTotal;
import com.example.platform.patrol.model.PatrolTaskEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 巡页：CRUD、立即执行、执行历史与定时入口。 */
@Service
public class PatrolSceneService {
    private static final Logger log = LoggerFactory.getLogger(PatrolSceneService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> BROWSERS = List.of("chromium", "chrome", "firefox", "edge");
    private static final List<String> AUTH_TYPES = List.of("NONE", "COOKIE", "TOKEN", "LOCAL_STORAGE");

    private final PatrolSceneMapper sceneMapper;
    private final PatrolTaskMapper taskMapper;
    private final AiModelConfigMapper modelMapper;
    private final AiModelConfigService modelService;
    private final PatrolExecutionService executionService;
    private final PatrolRetentionService retentionService;
    private final PatrolCoverageService coverageService;
    private final CredentialCipher credentialCipher;
    private final int maxConcurrent;

    public PatrolSceneService(PatrolSceneMapper sceneMapper,
                              PatrolTaskMapper taskMapper,
                              AiModelConfigMapper modelMapper,
                              AiModelConfigService modelService,
                              PatrolExecutionService executionService,
                              PatrolRetentionService retentionService,
                              PatrolCoverageService coverageService,
                              CredentialCipher credentialCipher,
                              @Value("${platform.patrol.max-concurrent:2}") int maxConcurrent) {
        this.sceneMapper = sceneMapper;
        this.taskMapper = taskMapper;
        this.modelMapper = modelMapper;
        this.modelService = modelService;
        this.executionService = executionService;
        this.retentionService = retentionService;
        this.coverageService = coverageService;
        this.credentialCipher = credentialCipher;
        this.maxConcurrent = maxConcurrent <= 0 ? 2 : maxConcurrent;
    }

    public PageResponse<PatrolSceneResponse> list(Long spaceId, int page, int size) {
        int normalizedPage = Math.max(1, page);
        int normalizedSize = Math.min(50, Math.max(1, size));
        long total = sceneMapper.countBySpaceId(spaceId);
        List<PatrolSceneEntity> entities = sceneMapper.findPageBySpaceId(
                spaceId, normalizedSize, (normalizedPage - 1) * normalizedSize);
        Map<Long, AiModelConfigEntity> models = modelMap(spaceId);
        Map<Long, Long> tokenTotals = tokenTotals(spaceId);
        List<PatrolSceneResponse> items = entities.stream()
                .map(entity -> toResponse(entity, models, tokenTotals.getOrDefault(entity.getId(), 0L)))
                .toList();
        return PageResponse.of(items, total, normalizedPage, normalizedSize);
    }

    public PatrolSceneResponse get(Long spaceId, Long id) {
        PatrolSceneEntity entity = requireScene(spaceId, id);
        return toResponse(entity, modelMap(spaceId), tokenTotals(spaceId).getOrDefault(id, 0L));
    }

    public PatrolSceneResponse create(Long spaceId, PatrolSceneRequest request, String actor) {
        PatrolSceneEntity entity = new PatrolSceneEntity();
        entity.setSpaceId(spaceId);
        entity.setCreatedBy(actor);
        entity.setCreatedAt(LocalDateTime.now());
        apply(entity, request, true);
        sceneMapper.insert(entity);
        return toResponse(sceneMapper.findByIdAndSpaceId(entity.getId(), spaceId).orElse(entity), modelMap(spaceId), 0L);
    }

    public PatrolSceneResponse update(Long spaceId, Long id, PatrolSceneRequest request) {
        PatrolSceneEntity entity = requireScene(spaceId, id);
        apply(entity, request, false);
        sceneMapper.update(entity);
        return toResponse(sceneMapper.findByIdAndSpaceId(id, spaceId).orElse(entity), modelMap(spaceId),
                tokenTotals(spaceId).getOrDefault(id, 0L));
    }

    /** 复制巡页配置；副本默认关闭定时，避免复制后立即产生重复定时任务。 */
    public PatrolSceneResponse copy(Long spaceId, Long id, String actor) {
        PatrolSceneEntity source = requireScene(spaceId, id);
        PatrolSceneEntity copy = new PatrolSceneEntity();
        copy.setSpaceId(spaceId);
        copy.setName(uniqueCopyName(spaceId, source.getName()));
        copy.setDescription(source.getDescription());
        copy.setStartUrl(source.getStartUrl());
        copy.setBrowser(source.getBrowser());
        copy.setHeadless(source.getHeadless());
        copy.setAuthType(source.getAuthType());
        copy.setAuthCookieEncrypted(source.getAuthCookieEncrypted());
        copy.setAuthTokenEncrypted(source.getAuthTokenEncrypted());
        copy.setAuthTokenHeader(source.getAuthTokenHeader());
        copy.setAuthLocalStorageEncrypted(source.getAuthLocalStorageEncrypted());
        copy.setPlanModelId(source.getPlanModelId());
        copy.setJudgeModelId(source.getJudgeModelId());
        copy.setTaskBrief(source.getTaskBrief());
        copy.setBudgetDurationMinutes(source.getBudgetDurationMinutes());
        copy.setBudgetLlmTokens(source.getBudgetLlmTokens());
        copy.setVisibleTextLimit(source.getVisibleTextLimit());
        copy.setBudgetCases(source.getBudgetCases());
        copy.setScrollScan(source.getScrollScan());
        copy.setRecordVideo(source.getRecordVideo());
        copy.setAutoApproveWrite(source.getAutoApproveWrite());
        copy.setAutoApproveDestructive(source.getAutoApproveDestructive());
        copy.setScheduleEnabled(false);
        copy.setCronExpression(source.getCronExpression());
        copy.setNextRunAt(null);
        copy.setCreatedBy(actor);
        copy.setCreatedAt(LocalDateTime.now());
        sceneMapper.insert(copy);
        return toResponse(sceneMapper.findByIdAndSpaceId(copy.getId(), spaceId).orElse(copy), modelMap(spaceId), 0L);
    }

    public void delete(Long spaceId, Long id) {
        PatrolSceneEntity scene = requireScene(spaceId, id);
        if (taskMapper.existsActiveBySceneId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该巡页有任务在执行，无法删除");
        }
        for (PatrolTaskEntity task : taskMapper.findBySceneId(scene.getId())) {
            retentionService.purgeArtifacts(task);
        }
        sceneMapper.deleteByIdAndSpaceId(id, spaceId);
    }

    /** 手动执行：创建任务并异步启动。 */
    public PatrolTaskResponse run(Long spaceId, Long sceneId, String actor) {
        PatrolSceneEntity scene = requireScene(spaceId, sceneId);
        if (scene.getPlanModelId() == null || scene.getJudgeModelId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "该巡页尚未配置规划模型与判定模型，请先编辑巡页并选择模型");
        }
        if (taskMapper.existsActiveBySceneId(sceneId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该巡页已有任务在执行，请等待完成");
        }
        if (taskMapper.countActive() >= maxConcurrent) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "平台同时执行的巡页任务已达上限（" + maxConcurrent + "）");
        }
        PatrolTaskEntity task = createTask(scene, "MANUAL", actor);
        executionService.start(task.getId());
        return PatrolTaskResponse.from(task);
    }

    /** 定时执行入口：繁忙或未配置模型时跳过，不抛异常。 */
    public PatrolTaskEntity runScheduled(PatrolSceneEntity scene) {
        if (scene.getPlanModelId() == null || scene.getJudgeModelId() == null) {
            log.info("Patrol scene models not configured, scheduled run skipped. sceneId={}", scene.getId());
            return null;
        }
        if (taskMapper.existsActiveBySceneId(scene.getId())) {
            log.info("Patrol scene busy, scheduled run skipped. sceneId={}", scene.getId());
            return null;
        }
        if (taskMapper.countActive() >= maxConcurrent) {
            log.info("Patrol concurrency reached, scheduled run skipped. sceneId={}", scene.getId());
            return null;
        }
        PatrolTaskEntity task = createTask(scene, "SCHEDULED", "scheduler");
        executionService.start(task.getId());
        return task;
    }

    public PageResponse<PatrolTaskResponse> listTasks(Long spaceId, Long sceneId, int page, int size) {
        requireScene(spaceId, sceneId);
        int normalizedPage = Math.max(1, page);
        int normalizedSize = Math.min(50, Math.max(1, size));
        long total = taskMapper.countBySceneId(sceneId);
        return PageResponse.of(
                taskMapper.findPageBySceneId(sceneId, normalizedSize, (normalizedPage - 1) * normalizedSize)
                        .stream().map(this::taskResponse).toList(),
                total, normalizedPage, normalizedSize);
    }

    /** 执行历史汇总：仅统计已结束任务（存在耗时），避免运行中任务拉低平均值。 */
    public PatrolTaskSummaryResponse taskSummary(Long spaceId, Long sceneId) {
        requireScene(spaceId, sceneId);
        List<PatrolTaskEntity> finished = taskMapper.findBySceneId(sceneId).stream()
                .filter(task -> task.getDurationMs() != null)
                .toList();
        if (finished.isEmpty()) {
            return new PatrolTaskSummaryResponse(0, 0, 0d, 0d);
        }
        long totalTokens = 0;
        long totalDurationMs = 0;
        double totalCoverageRate = 0;
        for (PatrolTaskEntity task : finished) {
            totalTokens += task.getLlmTokenUsed() == null ? 0 : task.getLlmTokenUsed();
            totalDurationMs += task.getDurationMs();
            totalCoverageRate += coverageService.measure(task).rate();
        }
        int count = finished.size();
        return new PatrolTaskSummaryResponse(
                count,
                Math.round((double) totalTokens / count),
                round1(totalCoverageRate / count),
                round1((double) totalDurationMs / count / 60000d));
    }

    private PatrolTaskResponse taskResponse(PatrolTaskEntity task) {
        PatrolCoverage.Stats stats = coverageService.measure(task);
        return PatrolTaskResponse.from(task, stats.total(), stats.covered(), stats.rate());
    }

    private PatrolTaskEntity createTask(PatrolSceneEntity scene, String triggerType, String actor) {
        AiModelConfigEntity planModel = null;
        AiModelConfigEntity judgeModel = null;
        try {
            planModel = modelService.resolve(scene.getSpaceId(), scene.getPlanModelId(), false);
        } catch (Exception exception) {
            log.warn("Resolve plan model failed. sceneId={}, reason={}", scene.getId(), exception.getMessage());
        }
        try {
            judgeModel = modelService.resolve(scene.getSpaceId(), scene.getJudgeModelId(), true);
        } catch (Exception exception) {
            log.warn("Resolve judge model failed. sceneId={}, reason={}", scene.getId(), exception.getMessage());
        }
        PatrolTaskEntity task = new PatrolTaskEntity();
        task.setSpaceId(scene.getSpaceId());
        task.setSceneId(scene.getId());
        task.setSceneName(scene.getName());
        task.setTriggerType(triggerType);
        task.setStatus("QUEUED");
        task.setCurrentStage("QUEUED");
        task.setStartUrl(scene.getStartUrl());
        task.setTaskBrief(scene.getTaskBrief());
        task.setSceneSnapshotJson(PatrolExecutionService.sceneSnapshotJson(scene));
        task.setModelSnapshotJson(PatrolExecutionService.modelSnapshotJson(planModel, judgeModel));
        task.setBudgetDurationMinutes(scene.getBudgetDurationMinutes());
        task.setBudgetLlmTokens(scene.getBudgetLlmTokens());
        task.setBudgetCases(scene.getBudgetCases());
        task.setCreatedBy(actor);
        task.setCreatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        sceneMapper.updateLastTask(scene.getId(), LocalDateTime.now(), task.getId(), "QUEUED");
        return task;
    }

    private void apply(PatrolSceneEntity entity, PatrolSceneRequest request, boolean create) {
        String name = trim(request.name());
        if (name.isEmpty() || name.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "巡页名称不能为空且不超过 128 个字符");
        }
        if (sceneMapper.existsByName(entity.getSpaceId(), name, entity.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "同名巡页已存在");
        }
        entity.setName(name);
        entity.setDescription(truncate(request.description(), 512));
        entity.setStartUrl(validateUrl(request.startUrl()));
        String browser = trim(request.browser()).isEmpty()
                ? "chromium" : request.browser().trim().toLowerCase(Locale.ROOT);
        if (!BROWSERS.contains(browser)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "浏览器仅支持 chromium / chrome / firefox / edge");
        }
        entity.setBrowser(browser);
        entity.setHeadless(!Boolean.FALSE.equals(request.headless()));
        applyAuth(entity, request);
        entity.setPlanModelId(validateModel(entity.getSpaceId(), request.planModelId(), false));
        entity.setJudgeModelId(validateModel(entity.getSpaceId(), request.judgeModelId(), true));
        entity.setTaskBrief(truncate(request.taskBrief(), 1024));
        entity.setBudgetDurationMinutes(clamp(request.budgetDurationMinutes(), 15, 1, 60, "预算时长（分钟）"));
        entity.setBudgetLlmTokens(clamp(request.budgetLlmTokens(), 300000, 10000, 1000000, "Token 预算"));
        entity.setVisibleTextLimit(clamp(request.visibleTextLimit(), 1800, 100, 100000, "模型可见页面文本字数"));
        entity.setScrollScan(!Boolean.FALSE.equals(request.scrollScan()));
        entity.setRecordVideo(!Boolean.FALSE.equals(request.recordVideo()));
        entity.setAutoApproveWrite(Boolean.TRUE.equals(request.autoApproveWrite()));
        entity.setAutoApproveDestructive(Boolean.TRUE.equals(request.autoApproveDestructive()));
        boolean scheduleEnabled = Boolean.TRUE.equals(request.scheduleEnabled());
        entity.setScheduleEnabled(scheduleEnabled);
        String cron = trim(request.cronExpression());
        if (scheduleEnabled && cron.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "开启定时后必须填写 Cron 表达式");
        }
        if (!cron.isEmpty() && !CronExpression.isValidExpression(cron)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cron 表达式不合法");
        }
        entity.setCronExpression(cron.isEmpty() ? null : cron);
        entity.setNextRunAt(resolveNextRun(scheduleEnabled, entity.getCronExpression()));
    }

    private void applyAuth(PatrolSceneEntity entity, PatrolSceneRequest request) {
        String authType = trim(request.authType()).isEmpty()
                ? "NONE" : request.authType().trim().toUpperCase(Locale.ROOT);
        if (!AUTH_TYPES.contains(authType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "认证方式仅支持 NONE / COOKIE / TOKEN / LOCAL_STORAGE");
        }
        entity.setAuthType(authType);
        String cookie = trim(request.authCookie());
        if ("COOKIE".equals(authType)) {
            if (cookie.isEmpty() && entity.getAuthCookieEncrypted() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "选择 Cookie 认证时必须填写 Cookie");
            }
            if (!cookie.isEmpty()) {
                entity.setAuthCookieEncrypted(encrypt(cookie));
            }
        }
        String token = trim(request.authToken());
        if ("TOKEN".equals(authType)) {
            if (token.isEmpty() && entity.getAuthTokenEncrypted() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "选择 Token 认证时必须填写 Token");
            }
            if (!token.isEmpty()) {
                entity.setAuthTokenEncrypted(encrypt(token));
            }
            String header = trim(request.authTokenHeader());
            entity.setAuthTokenHeader(header.isEmpty() ? "Authorization" : header);
        }
        String localStorage = trim(request.authLocalStorage());
        if ("LOCAL_STORAGE".equals(authType)) {
            if (localStorage.isEmpty() && entity.getAuthLocalStorageEncrypted() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "选择 localStorage 注入时必须填写 JSON 键值对");
            }
            if (!localStorage.isEmpty()) {
                validateLocalStorage(localStorage);
                entity.setAuthLocalStorageEncrypted(encrypt(localStorage));
            }
        }
    }

    private Long validateModel(Long spaceId, Long modelId, boolean requireVision) {
        if (modelId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择模型配置");
        }
        AiModelConfigEntity entity = modelMapper.findByIdAndSpaceId(modelId, spaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型配置不存在或不可见"));
        if (requireVision && !Boolean.TRUE.equals(entity.getSupportsVision())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "判定模型必须支持视觉输入");
        }
        return modelId;
    }

    private LocalDateTime resolveNextRun(boolean enabled, String cron) {
        if (!enabled || cron == null || cron.isBlank()) {
            return null;
        }
        return CronExpression.parse(cron).next(LocalDateTime.now());
    }

    private PatrolSceneEntity requireScene(Long spaceId, Long id) {
        return sceneMapper.findByIdAndSpaceId(id, spaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "巡页不存在"));
    }

    private Map<Long, AiModelConfigEntity> modelMap(Long spaceId) {
        Map<Long, AiModelConfigEntity> map = new HashMap<>();
        for (AiModelConfigEntity entity : modelMapper.findVisibleBySpaceId(spaceId)) {
            map.put(entity.getId(), entity);
        }
        return map;
    }

    private Map<Long, Long> tokenTotals(Long spaceId) {
        Map<Long, Long> totals = new HashMap<>();
        for (PatrolSceneTokenTotal item : taskMapper.sumTokensBySpaceId(spaceId)) {
            totals.put(item.getSceneId(), item.getTotalTokens() == null ? 0L : item.getTotalTokens());
        }
        return totals;
    }

    private String uniqueCopyName(Long spaceId, String sourceName) {
        int attempt = 1;
        while (true) {
            String suffix = attempt == 1 ? " 副本" : " 副本 " + attempt;
            String candidate = clip(sourceName, 128 - suffix.length()) + suffix;
            if (!sceneMapper.existsByName(spaceId, candidate, null)) {
                return candidate;
            }
            attempt++;
        }
    }

    private static String clip(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static double round1(double value) {
        return Math.round(value * 10d) / 10d;
    }

    private PatrolSceneResponse toResponse(PatrolSceneEntity entity, Map<Long, AiModelConfigEntity> models) {
        return toResponse(entity, models, 0L);
    }

    private PatrolSceneResponse toResponse(PatrolSceneEntity entity, Map<Long, AiModelConfigEntity> models,
                                           long totalTokenUsed) {
        String planName = entity.getPlanModelId() == null ? null
                : Optional.ofNullable(models.get(entity.getPlanModelId()))
                .map(AiModelConfigEntity::getName).orElse("未配置");
        String judgeName = entity.getJudgeModelId() == null ? null
                : Optional.ofNullable(models.get(entity.getJudgeModelId()))
                .map(AiModelConfigEntity::getName).orElse("未配置");
        return PatrolSceneResponse.from(entity).withModelNames(planName, judgeName, totalTokenUsed);
    }

    private String validateUrl(String raw) {
        String value = trim(raw);
        if (value.isEmpty() || value.length() > 2048) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写合法的入口页 URL");
        }
        try {
            java.net.URI uri = new java.net.URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "入口页 URL 必须以 http:// 或 https:// 开头");
            }
        } catch (java.net.URISyntaxException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "入口页 URL 格式不合法");
        }
        return value;
    }

    private void validateLocalStorage(String raw) {
        if (raw.length() > 8000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "localStorage 配置过长");
        }
        try {
            JsonNode node = MAPPER.readTree(raw);
            if (!node.isObject() || node.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "localStorage 配置必须是形如 {\"key\":\"value\"} 的 JSON 对象");
            }
        } catch (java.io.IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "localStorage 配置不是合法 JSON");
        }
    }

    private String encrypt(String plaintext) {
        if (!credentialCipher.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "平台未配置 PLATFORM_CREDENTIAL_KEY，无法保存认证信息");
        }
        return credentialCipher.encrypt(plaintext);
    }

    private int clamp(Integer value, int fallback, int min, int max, String label) {
        int actual = value == null ? fallback : value;
        if (actual < min || actual > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    label + "需在 " + min + " 到 " + max + " 之间");
        }
        return actual;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.isEmpty()) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
