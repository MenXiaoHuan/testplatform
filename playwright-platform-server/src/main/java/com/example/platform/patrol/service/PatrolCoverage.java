package com.example.platform.patrol.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 入口页元素覆盖率计算：采集到的可交互元素中被用例步骤引用的比例。
 *
 * <p>元素可能位于同源 iframe / 开放 Shadow DOM / 多层弹层中，因此用 {@code frame + selector}
 * 作为复合键，避免不同 iframe 内同名 selector 互相误判。
 */
public final class PatrolCoverage {
    private static final String SEPARATOR = "\u0001";

    private PatrolCoverage() {
    }

    /** 元素或步骤的复合定位键：frame 选择器链 + selector。 */
    public static String keyOf(String selector, List<String> frame) {
        String normalizedSelector = selector == null ? "" : selector.trim();
        if (normalizedSelector.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        if (frame != null) {
            for (String part : frame) {
                if (part != null && !part.isBlank()) {
                    builder.append(part.trim()).append(SEPARATOR);
                }
            }
        }
        return builder.append(normalizedSelector).toString();
    }

    public static String keyOf(JsonNode element) {
        List<String> frame = new ArrayList<>();
        JsonNode frameNode = element.path("frame");
        if (frameNode.isArray()) {
            for (JsonNode item : frameNode) {
                frame.add(item.asText(""));
            }
        }
        return keyOf(element.path("selector").asText(""), frame);
    }

    /**
     * 从页面快照中提取可被用例引用的元素：
     * 要求有 selector；用于覆盖率统计时只计可见且可用元素（隐藏弹层元素不拉低覆盖率）。
     */
    public static List<JsonNode> actionableElements(JsonNode snapshot) {
        JsonNode elements = snapshot == null ? null : snapshot.path("elements");
        if (elements == null || !elements.isArray()) {
            return List.of();
        }
        List<JsonNode> result = new ArrayList<>();
        for (JsonNode element : elements) {
            if (!element.path("visible").asBoolean(true) || !element.path("enabled").asBoolean(true)) {
                continue;
            }
            if (keyOf(element).isEmpty()) {
                continue;
            }
            result.add(element);
        }
        return result;
    }

    public static int totalElements(JsonNode snapshot) {
        return actionableElements(snapshot).size();
    }

    /** 用例步骤引用到的元素复合键集合。 */
    public static Set<String> keysOf(List<String> keys) {
        Set<String> result = new LinkedHashSet<>();
        if (keys == null) {
            return result;
        }
        for (String key : keys) {
            if (key != null && !key.isBlank()) {
                result.add(key.trim());
            }
        }
        return result;
    }

    /** 覆盖率统计结果：总数、已覆盖数与百分比（保留两位小数）。 */
    public record Stats(int total, int covered, double rate) {
        public static Stats empty() {
            return new Stats(0, 0, 0d);
        }
    }

    public static Stats measure(JsonNode snapshot, Set<String> keys) {
        int total = totalElements(snapshot);
        int covered = coveredElements(snapshot, keys);
        double rate = total == 0 ? 0d : Math.round((double) covered / total * 10000d) / 100d;
        return new Stats(total, covered, rate);
    }

    public static int coveredElements(JsonNode snapshot, Set<String> keys) {
        if (keys.isEmpty()) {
            return 0;
        }
        int covered = 0;
        for (JsonNode element : actionableElements(snapshot)) {
            if (keys.contains(keyOf(element))) {
                covered += 1;
            }
        }
        return covered;
    }

    public static List<JsonNode> uncoveredElements(JsonNode snapshot, Set<String> keys) {
        List<JsonNode> result = new ArrayList<>();
        for (JsonNode element : actionableElements(snapshot)) {
            if (!keys.contains(keyOf(element))) {
                result.add(element);
            }
        }
        return result;
    }

    public static String coverageText(JsonNode snapshot, Set<String> keys) {
        int total = totalElements(snapshot);
        int covered = coveredElements(snapshot, keys);
        double rate = total == 0 ? 0d : Math.round((double) covered / total * 10000d) / 100d;
        return "元素覆盖 " + covered + "/" + total + "（" + rate + "%）";
    }
}
