package com.example.platform.patrol.service;

import com.example.platform.patrol.model.AiModelConfigEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 用例规划：基于入口页快照与任务描述，由规划模型产出带操作风险等级的用例。
 *
 * <p>LLM 失败/预算不足时退化为一条只读健康检查用例，并标记 degraded。
 */
@Service
public class PatrolPlannerService {
    private static final Logger log = LoggerFactory.getLogger(PatrolPlannerService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> ACTIONS = Set.of(
            "click", "dblclick", "rightclick", "tap",
            "fill", "type", "press", "clear",
            "check", "uncheck", "set_checked", "select",
            "hover", "focus", "blur",
            "scroll", "scroll_into_view", "drag_to",
            "goto", "go_back", "go_forward", "reload",
            "wait", "wait_for_selector", "wait_for_url", "wait_for_load_state",
            "assert_visible", "assert_hidden", "assert_text", "assert_value",
            "assert_checked", "assert_count", "assert_url", "assert_title", "assert_attribute");
    private static final Set<String> SELECTOR_REQUIRED = Set.of(
            "click", "dblclick", "rightclick", "tap", "fill", "type", "clear",
            "check", "uncheck", "set_checked", "select", "hover", "focus", "blur",
            "scroll_into_view", "drag_to", "wait_for_selector", "assert_visible",
            "assert_hidden", "assert_value", "assert_checked", "assert_count",
            "assert_attribute");
    private static final Set<String> MUTATING_ACTIONS = Set.of(
            "click", "dblclick", "rightclick", "tap", "fill", "type", "press", "clear",
            "check", "uncheck", "set_checked", "select", "drag_to");
    private static final Set<String> RISKS = Set.of("READ", "WRITE", "DESTRUCTIVE", "AUTH");
    private static final int MAX_STEPS = 8;
    private static final int DEFAULT_VISIBLE_TEXT_LIMIT = 1800;
    private static final int PLAN_MAX_TOKENS = 4500;

    private final PatrolAiClient aiClient;
    private final PatrolPromptService promptService;
    private final PatrolModelLogService modelLogService;

    public PatrolPlannerService(PatrolAiClient aiClient, PatrolPromptService promptService,
                                PatrolModelLogService modelLogService) {
        this.aiClient = aiClient;
        this.promptService = promptService;
        this.modelLogService = modelLogService;
    }

    /**
     * 覆盖率补全：针对入口页尚未被任何用例引用的可交互元素，追加一次规划。
     *
     * <p>只受 Token 与时长预算约束，调用一次避免无限补全。
     */
    public PlanResult planSupplement(Long taskId, String taskBrief, JsonNode snapshot, List<JsonNode> uncovered,
                                     List<String> existingNames, PatrolBudget budget,
                                     AiModelConfigEntity model) {
        if (model == null || uncovered == null || uncovered.isEmpty()) {
            return new PlanResult(List.of(), "LLM", 0, null, false, "");
        }
        String userPrompt = buildSupplementPrompt(taskBrief, uncovered, existingNames);
        String systemPrompt = promptService.supplement();
        int estimated = PatrolAiClient.estimateTokens(systemPrompt)
                + PatrolAiClient.estimateTokens(userPrompt) + 300;
        if (!budget.canAfford(estimated)) {
            budget.addStopReason("剩余 Token 不足以补齐未覆盖元素");
            modelLogService.record(taskId, null, null, PatrolModelLogService.STAGE_COVERAGE,
                    "patrol.plan-coverage", model, "SKIPPED", systemPrompt, userPrompt, null, 0, 0, 0,
                    "剩余 Token 不足以补齐未覆盖元素");
            return new PlanResult(List.of(), "LLM", 0, null, true, "Token 预算不足");
        }
        long startedAt = System.currentTimeMillis();
        try {
            PatrolAiClient.LlmResponse response =
                    aiClient.completeText(model, "patrol.plan-coverage", systemPrompt,
                            userPrompt, PLAN_MAX_TOKENS);
            budget.consumeTokens(response.totalTokens());
            List<PlannedCase> cases = parsePlan(response.content()).stream()
                    .filter(item -> existingNames.stream().noneMatch(name -> name.equals(item.name())))
                    .toList();
            log.info("Patrol coverage supplement response. cases={}, content={}",
                    cases.size(), truncate(response.content(), 800));
            if (cases.isEmpty()) {
                modelLogService.record(taskId, null, null, PatrolModelLogService.STAGE_COVERAGE,
                        "patrol.plan-coverage", model, "DEGRADED", systemPrompt, userPrompt,
                        response.content(), response.promptTokens(), response.completionTokens(),
                        System.currentTimeMillis() - startedAt, "未补齐未覆盖元素");
                return new PlanResult(List.of(), "LLM", response.totalTokens(), response.model(), true,
                        "未补齐未覆盖元素");
            }
            modelLogService.record(taskId, null, null, PatrolModelLogService.STAGE_COVERAGE,
                    "patrol.plan-coverage", model, "SUCCESS", systemPrompt, userPrompt,
                    response.content(), response.promptTokens(), response.completionTokens(),
                    System.currentTimeMillis() - startedAt, null);
            return new PlanResult(cases, "LLM", response.totalTokens(), response.model(), false, "");
        } catch (Exception exception) {
            log.warn("Patrol coverage supplement failed. reason={}", exception.getMessage());
            modelLogService.record(taskId, null, null, PatrolModelLogService.STAGE_COVERAGE,
                    "patrol.plan-coverage", model, "FAILED", systemPrompt, userPrompt, null, 0, 0,
                    System.currentTimeMillis() - startedAt, exception.getMessage());
            budget.addStopReason("覆盖率补全调用失败");
            return new PlanResult(List.of(), "LLM", 0, null, true, exception.getMessage());
        }
    }

    private String buildSupplementPrompt(String taskBrief, List<JsonNode> uncovered,
                                         List<String> existingNames) {
        StringBuilder builder = new StringBuilder();
        builder.append("任务描述：")
                .append(taskBrief == null || taskBrief.isBlank() ? "通用 Web 质量巡检" : taskBrief)
                .append('\n');
        builder.append("已有用例（不要重复）：")
                .append(existingNames.isEmpty() ? "无" : String.join("；", existingNames))
                .append('\n');
        builder.append("未覆盖的可交互元素（selector | frame | tag | role | text | 状态 | href）：\n");
        for (JsonNode element : uncovered) {
            appendElement(builder, element);
        }
        return builder.toString();
    }

    /** 输出采集到的元素：包含 frame 链、弹层深度与可见状态，供模型原样复制。 */
    private static void appendElement(StringBuilder builder, JsonNode element) {
        builder.append("- selector=").append(truncate(element.path("selector").asText(""), 500));
        JsonNode frame = element.path("frame");
        if (frame.isArray() && frame.size() > 0) {
            StringBuilder chain = new StringBuilder();
            for (JsonNode item : frame) {
                if (chain.length() > 0) {
                    chain.append(',');
                }
                chain.append('"').append(truncate(item.asText(""), 500)).append('"');
            }
            builder.append(" | frame=[").append(chain).append(']');
        }
        builder.append(" | tag=").append(element.path("tag").asText(""))
                .append(" | role=").append(element.path("role").asText(""))
                .append(" | text=").append(truncate(element.path("text").asText(""), 60))
                .append(" | visible=").append(element.path("visible").asBoolean(true))
                .append(" | enabled=").append(element.path("enabled").asBoolean(true));
        int layerDepth = element.path("layerDepth").asInt(0);
        if (layerDepth > 0) {
            builder.append(" | layer=").append(layerDepth).append('/')
                    .append(truncate(element.path("layerContainer").asText(""), 40));
        }
        String href = element.path("href").asText("");
        if (!href.isBlank()) {
            builder.append(" | href=").append(truncate(href, 160));
        }
        builder.append('\n');
    }

    public PlanResult plan(Long taskId, String taskBrief, JsonNode snapshot, PatrolBudget budget,
                           AiModelConfigEntity model, int visibleTextLimit) {
        String userPrompt = buildPrompt(taskBrief, snapshot, budget, visibleTextLimit);
        String systemPrompt = promptService.plan();
        int estimated = PatrolAiClient.estimateTokens(systemPrompt)
                + PatrolAiClient.estimateTokens(userPrompt) + 300;
        if (model == null) {
            budget.markDegraded();
            budget.addStopReason("未配置可用的规划模型，已降级为规则用例");
            modelLogService.record(taskId, null, null, PatrolModelLogService.STAGE_PLAN, "patrol.plan",
                    null, "SKIPPED", systemPrompt, userPrompt, null, 0, 0, 0,
                    "未配置可用的规划模型");
            return PlanResult.fallback(snapshot, "未配置规划模型");
        }
        if (!budget.canAfford(estimated)) {
            budget.markDegraded();
            budget.addStopReason("剩余 Token 不足以规划用例");
            modelLogService.record(taskId, null, null, PatrolModelLogService.STAGE_PLAN, "patrol.plan",
                    model, "SKIPPED", systemPrompt, userPrompt, null, 0, 0, 0,
                    "剩余 Token 不足以规划用例");
            return PlanResult.fallback(snapshot, "Token 预算不足");
        }
        long startedAt = System.currentTimeMillis();
        try {
            PatrolAiClient.LlmResponse response =
                    aiClient.completeText(model, "patrol.plan", systemPrompt, userPrompt, PLAN_MAX_TOKENS);
            budget.consumeTokens(response.totalTokens());
            List<PlannedCase> cases = parsePlan(response.content());
            if (cases.isEmpty()) {
                modelLogService.record(taskId, null, null, PatrolModelLogService.STAGE_PLAN, "patrol.plan",
                        model, "DEGRADED", systemPrompt, userPrompt, response.content(),
                        response.promptTokens(), response.completionTokens(),
                        System.currentTimeMillis() - startedAt, "未产出可执行用例");
                budget.markDegraded();
                budget.addStopReason("规划模型未产出可执行用例，已降级为规则用例");
                return PlanResult.fallback(snapshot, "未产出可执行用例");
            }
            modelLogService.record(taskId, null, null, PatrolModelLogService.STAGE_PLAN, "patrol.plan",
                    model, "SUCCESS", systemPrompt, userPrompt, response.content(),
                    response.promptTokens(), response.completionTokens(),
                    System.currentTimeMillis() - startedAt, null);
            return new PlanResult(cases, "LLM", response.totalTokens(), response.model(), false, "");
        } catch (Exception exception) {
            log.warn("Patrol plan failed, fallback to rules. reason={}", exception.getMessage());
            modelLogService.record(taskId, null, null, PatrolModelLogService.STAGE_PLAN, "patrol.plan",
                    model, "FAILED", systemPrompt, userPrompt, null, 0, 0,
                    System.currentTimeMillis() - startedAt, exception.getMessage());
            budget.markDegraded();
            budget.addStopReason("用例规划调用失败，已降级为规则用例");
            return PlanResult.fallback(snapshot, exception.getMessage());
        }
    }

    private String buildPrompt(String taskBrief, JsonNode snapshot, PatrolBudget budget, int visibleTextLimit) {
        StringBuilder builder = new StringBuilder();
        builder.append("任务描述：")
                .append(taskBrief == null || taskBrief.isBlank() ? "通用 Web 质量巡检" : taskBrief)
                .append('\n');
        builder.append("页面标题：").append(snapshot.path("title").asText("")).append('\n');
        builder.append("页面地址：").append(snapshot.path("url").asText("")).append('\n');
        builder.append("控制台错误数：").append(snapshot.path("consoleErrors").asInt(0)).append('\n');
        builder.append("剩余 Token 预算：").append(budget.getRemainingTokens()).append('\n');
        builder.append("可见文本（截断）：\n")
                .append(truncate(snapshot.path("visibleText").asText(""),
                        visibleTextLimit <= 0 ? DEFAULT_VISIBLE_TEXT_LIMIT : visibleTextLimit)).append('\n');
        builder.append("可交互元素（selector | frame | tag | role | 文案 | 状态 | 弹层 | 是否链接）：\n");
        JsonNode elements = snapshot.path("elements");
        int count = 0;
        if (elements.isArray()) {
            for (JsonNode element : elements) {
                appendElement(builder, element);
                count += 1;
            }
        }
        if (count == 0) {
            builder.append("（未采集到可交互元素）\n");
        }
        return builder.toString();
    }

    private List<PlannedCase> parsePlan(String content) {
        JsonNode root = extractJson(content);
        JsonNode casesNode = root.path("cases");
        if (!casesNode.isArray()) {
            return List.of();
        }
        List<PlannedCase> cases = new ArrayList<>();
        for (JsonNode caseNode : casesNode) {
            String name = truncate(caseNode.path("name").asText("").trim(), 200);
            if (name.isEmpty()) {
                continue;
            }
            List<PlannedStep> steps = new ArrayList<>();
            JsonNode stepsNode = caseNode.path("steps");
            if (stepsNode.isArray()) {
                for (JsonNode stepNode : stepsNode) {
                    if (steps.size() >= MAX_STEPS) {
                        break;
                    }
                    PlannedStep step = parseStep(stepNode);
                    if (step != null) {
                        steps.add(step);
                    }
                }
            }
            if (steps.isEmpty()) {
                continue;
            }
            String expected = truncate(caseNode.path("expected").asText("").trim(), 2000);
            if (expected.isEmpty()) {
                expected = "用例执行后页面保持可用且无异常提示";
            }
            cases.add(new PlannedCase(
                    name,
                    truncate(caseNode.path("intent").asText("").trim(), 1000),
                    expected,
                    normalizePriority(caseNode.path("priority").asText("")),
                    worstRisk(steps),
                    steps));
        }
        return cases;
    }

    private PlannedStep parseStep(JsonNode node) {
        String action = node.path("action").asText("").trim().toLowerCase(Locale.ROOT);
        if (!ACTIONS.contains(action)) {
            return null;
        }
        String selector = truncate(node.path("selector").asText("").trim(), 1000);
        String value = truncate(node.path("value").asText("").trim(), 1000);
        String target = truncate(node.path("target").asText("").trim(), 1000);
        String attribute = truncate(node.path("attribute").asText("").trim(), 200);
        List<String> frame = new ArrayList<>();
        JsonNode frameNode = node.path("frame");
        if (frameNode.isArray()) {
            for (JsonNode item : frameNode) {
                String part = truncate(item.asText("").trim(), 1000);
                if (!part.isEmpty() && frame.size() < 10) {
                    frame.add(part);
                }
            }
        }
        if (SELECTOR_REQUIRED.contains(action) && selector.isEmpty()) {
            return null;
        }
        if ("goto".equals(action) && value.isEmpty()) {
            return null;
        }
        if ("drag_to".equals(action) && (target.isEmpty() || selector.isEmpty())) {
            return null;
        }
        if (("assert_url".equals(action) || "assert_title".equals(action)) && value.isEmpty()) {
            return null;
        }
        if ("assert_attribute".equals(action) && (attribute.isEmpty() || selector.isEmpty())) {
            return null;
        }
        if ("assert_text".equals(action)) {
            String expected = value.isEmpty() ? node.path("label").asText("").trim() : value;
            if (expected.isEmpty()) {
                return null;
            }
            value = expected;
        }
        if ("assert_value".equals(action)) {
            String expected = value.isEmpty() ? node.path("label").asText("").trim() : value;
            if (expected.isEmpty()) {
                return null;
            }
            value = expected;
        }
        String operation = normalizeRisk(node.path("operation").asText(""), action);
        String risk = normalizeRisk(node.path("risk").asText(""), action);
        return new PlannedStep(
                action,
                selector.isEmpty() ? null : selector,
                truncate(node.path("label").asText("").trim(), 500),
                value.isEmpty() ? null : value,
                frame,
                target.isEmpty() ? null : target,
                attribute.isEmpty() ? null : attribute,
                operation,
                risk,
                truncate(node.path("reason").asText("").trim(), 500));
    }

    /** 未标注时按动作保守推断：会改变页面状态的动作默认 WRITE，其余 READ。 */
    private static String normalizeRisk(String raw, String action) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (RISKS.contains(value)) {
            return value;
        }
        if (MUTATING_ACTIONS.contains(action)) {
            return "WRITE";
        }
        return "READ";
    }

    private static String worstRisk(List<PlannedStep> steps) {
        boolean destructive = false;
        boolean write = false;
        boolean auth = false;
        for (PlannedStep step : steps) {
            switch (step.risk()) {
                case "DESTRUCTIVE" -> destructive = true;
                case "WRITE" -> write = true;
                case "AUTH" -> auth = true;
                default -> {
                }
            }
        }
        if (destructive) {
            return "DESTRUCTIVE";
        }
        if (auth) {
            return "AUTH";
        }
        return write ? "WRITE" : "READ";
    }

    private static JsonNode extractJson(String content) {
        if (content == null) {
            return MAPPER.createObjectNode();
        }
        String text = content.trim();
        if (text.startsWith("```")) {
            int firstNewLine = text.indexOf('\n');
            text = firstNewLine >= 0 ? text.substring(firstNewLine + 1) : text;
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return MAPPER.createObjectNode();
        }
        try {
            return MAPPER.readTree(text.substring(start, end + 1));
        } catch (IOException exception) {
            return MAPPER.createObjectNode();
        }
    }

    private static String normalizePriority(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        return "P0".equals(value) || "P2".equals(value) ? value : "P1";
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record PlannedCase(String name, String intent, String expected, String priority,
                              String worstRisk, List<PlannedStep> steps) {
    }

    public record PlannedStep(String action, String selector, String label, String value,
                              List<String> frame, String target, String attribute,
                              String operation, String risk, String reason) {
    }

    public record PlanResult(List<PlannedCase> cases, String source, int tokens, String modelId,
                             boolean degraded, String reason) {
        public static PlanResult fallback(JsonNode snapshot, String reason) {
            String title = snapshot.path("title").asText("");
            return new PlanResult(
                    List.of(new PlannedCase(
                            "页面可达性与基础健康检查",
                            "确认入口页可正常加载且无控制台错误",
                            "页面成功加载、标题非空、控制台无错误",
                            "P1",
                            "READ",
                            List.of())),
                    "RULE",
                    0,
                    null,
                    true,
                    reason == null ? "规则兜底" : reason);
        }
    }
}
