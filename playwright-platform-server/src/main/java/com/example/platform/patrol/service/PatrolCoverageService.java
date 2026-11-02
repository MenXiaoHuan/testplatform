package com.example.platform.patrol.service;

import com.example.platform.patrol.mapper.PatrolCaseStepMapper;
import com.example.platform.patrol.model.PatrolCaseStepEntity;
import com.example.platform.patrol.model.PatrolTaskEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** 按任务口径计算入口页可交互元素覆盖率，供任务详情、历史列表与汇总复用。 */
@Service
public class PatrolCoverageService {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PatrolCaseStepMapper caseStepMapper;

    public PatrolCoverageService(PatrolCaseStepMapper caseStepMapper) {
        this.caseStepMapper = caseStepMapper;
    }

    public PatrolCoverage.Stats measure(PatrolTaskEntity task) {
        if (task.getSnapshotJson() == null || task.getSnapshotJson().isBlank()) {
            return PatrolCoverage.Stats.empty();
        }
        try {
            JsonNode snapshot = MAPPER.readTree(task.getSnapshotJson());
            Set<String> keys = caseStepMapper.findByTaskId(task.getId()).stream()
                    .map(step -> PatrolCoverage.keyOf(step.getTargetSelector(), frameOf(step.getLocatorJson())))
                    .filter(key -> !key.isEmpty())
                    .collect(Collectors.toSet());
            return PatrolCoverage.measure(snapshot, keys);
        } catch (Exception exception) {
            return PatrolCoverage.Stats.empty();
        }
    }

    private static List<String> frameOf(String locatorJson) {
        if (locatorJson == null || locatorJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode frame = MAPPER.readTree(locatorJson).path("frame");
            if (!frame.isArray()) {
                return List.of();
            }
            List<String> result = new ArrayList<>();
            for (JsonNode item : frame) {
                if (!item.asText("").isBlank()) {
                    result.add(item.asText(""));
                }
            }
            return result;
        } catch (Exception exception) {
            return List.of();
        }
    }
}
