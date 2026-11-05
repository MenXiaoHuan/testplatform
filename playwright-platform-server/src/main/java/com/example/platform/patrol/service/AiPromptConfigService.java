package com.example.platform.patrol.service;

import com.example.platform.patrol.dto.AiPromptConfigRequest;
import com.example.platform.patrol.dto.AiPromptConfigResponse;
import com.example.platform.patrol.mapper.AiPromptConfigMapper;
import com.example.platform.patrol.model.AiPromptConfigEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 模型提示词配置：内置默认 + 空间覆盖 + 恢复默认。
 *
 * <p>同一空间同一标签只允许一条覆盖，标签不可修改；删除覆盖即恢复内置默认。
 */
@Service
public class AiPromptConfigService {
    private static final int MAX_CONTENT_LENGTH = 20000;

    private final AiPromptConfigMapper mapper;
    private final PatrolPromptService promptService;

    public AiPromptConfigService(AiPromptConfigMapper mapper, PatrolPromptService promptService) {
        this.mapper = mapper;
        this.promptService = promptService;
    }

    public List<AiPromptConfigResponse> list(Long spaceId) {
        Map<String, AiPromptConfigEntity> overrides = new LinkedHashMap<>();
        for (AiPromptConfigEntity entity : mapper.findBySpaceId(spaceId)) {
            overrides.put(entity.getLabel(), entity);
        }
        List<AiPromptConfigResponse> result = new ArrayList<>();
        for (String label : PatrolPromptService.labels()) {
            result.add(toResponse(label, overrides.get(label)));
        }
        return result;
    }

    public AiPromptConfigResponse create(Long spaceId, AiPromptConfigRequest request, String actor) {
        String label = requireLabel(request == null ? null : request.label());
        String content = requireContent(request == null ? null : request.content());
        if (mapper.countBySpaceIdAndLabel(spaceId, label) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该标签已配置提示词，请直接编辑");
        }
        AiPromptConfigEntity entity = new AiPromptConfigEntity();
        entity.setSpaceId(spaceId);
        entity.setLabel(label);
        entity.setContent(content);
        entity.setUpdatedBy(actor);
        mapper.insert(entity);
        promptService.invalidate(spaceId);
        return toResponse(label, mapper.findByIdAndSpaceId(entity.getId(), spaceId).orElse(null));
    }

    public AiPromptConfigResponse update(Long spaceId, Long id, AiPromptConfigRequest request, String actor) {
        AiPromptConfigEntity entity = requireOwned(spaceId, id);
        if (request != null && request.label() != null && !request.label().isBlank()
                && !requireLabel(request.label()).equals(entity.getLabel())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "提示词标签不可修改");
        }
        String content = requireContent(request == null ? null : request.content());
        mapper.update(entity.getId(), spaceId, content, actor);
        promptService.invalidate(spaceId);
        return toResponse(entity.getLabel(), mapper.findByIdAndSpaceId(id, spaceId).orElse(null));
    }

    /** 恢复默认：直接删除空间覆盖记录。 */
    public void reset(Long spaceId, Long id) {
        AiPromptConfigEntity entity = requireOwned(spaceId, id);
        mapper.deleteByIdAndSpaceId(entity.getId(), spaceId);
        promptService.invalidate(spaceId);
    }

    private AiPromptConfigEntity requireOwned(Long spaceId, Long id) {
        return mapper.findByIdAndSpaceId(id, spaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "提示词配置不存在或不可编辑"));
    }

    private static String requireLabel(String raw) {
        String label = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!PatrolPromptService.isKnownLabel(label)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择提示词标签：规划 / 补全 / 判定");
        }
        return label;
    }

    private static String requireContent(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "提示词内容不能为空");
        }
        String content = stripControlChars(raw).strip();
        if (content.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "提示词内容不能为空");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "提示词内容不能超过 " + MAX_CONTENT_LENGTH + " 字符");
        }
        return content;
    }

    /** 去掉不可见控制字符，保留换行与制表符；避免提示词里夹带零宽字符干扰阅读与审计。 */
    private static String stripControlChars(String raw) {
        StringBuilder builder = new StringBuilder(raw.length());
        for (int index = 0; index < raw.length(); index += 1) {
            char current = raw.charAt(index);
            if (current == '\n' || current == '\r' || current == '\t') {
                builder.append(current);
                continue;
            }
            if (current < 0x20 || current == 0x7F) {
                continue;
            }
            if ((current >= '\u200B' && current <= '\u200F')
                    || (current >= '\u202A' && current <= '\u202E')
                    || current == '\uFEFF') {
                continue;
            }
            builder.append(current);
        }
        return builder.toString();
    }

    private AiPromptConfigResponse toResponse(String label, AiPromptConfigEntity entity) {
        String defaultContent = promptService.defaultContent(label);
        if (entity == null) {
            return new AiPromptConfigResponse(null, label, PatrolPromptService.labelName(label),
                    defaultContent, defaultContent, false, null, null);
        }
        return new AiPromptConfigResponse(entity.getId(), label, PatrolPromptService.labelName(label),
                entity.getContent(), defaultContent, true, entity.getUpdatedBy(), entity.getUpdatedAt());
    }
}
