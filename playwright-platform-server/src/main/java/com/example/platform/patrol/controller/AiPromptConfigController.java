package com.example.platform.patrol.controller;

import com.example.platform.auth.context.AuthContext;
import com.example.platform.auth.context.AuthContextHolder;
import com.example.platform.common.ApiResponse;
import com.example.platform.patrol.dto.AiPromptConfigRequest;
import com.example.platform.patrol.dto.AiPromptConfigResponse;
import com.example.platform.patrol.service.AiPromptConfigService;
import com.example.platform.space.service.SpaceAuthorizationService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 空间级模型提示词配置：规划 / 补全 / 判定，仅超级管理员可访问。 */
@RestController
@RequestMapping("/api/spaces/{spaceId}/ai-prompts")
public class AiPromptConfigController {
    private final AiPromptConfigService promptConfigService;
    private final SpaceAuthorizationService spaceAuthorizationService;

    public AiPromptConfigController(AiPromptConfigService promptConfigService,
                                    SpaceAuthorizationService spaceAuthorizationService) {
        this.promptConfigService = promptConfigService;
        this.spaceAuthorizationService = spaceAuthorizationService;
    }

    @GetMapping
    public ApiResponse<List<AiPromptConfigResponse>> list(@PathVariable Long spaceId) {
        AuthContext actor = requireSuperAdmin();
        spaceAuthorizationService.requireReadableSpace(spaceId, actor);
        return ApiResponse.ok(promptConfigService.list(spaceId));
    }

    @PostMapping
    public ApiResponse<AiPromptConfigResponse> create(@PathVariable Long spaceId,
                                                     @RequestBody AiPromptConfigRequest request) {
        AuthContext actor = requireSuperAdmin();
        spaceAuthorizationService.requireOperableSpace(spaceId, actor);
        return ApiResponse.ok(promptConfigService.create(spaceId, request, actor.username()));
    }

    @PutMapping("/{id}")
    public ApiResponse<AiPromptConfigResponse> update(@PathVariable Long spaceId, @PathVariable Long id,
                                                     @RequestBody AiPromptConfigRequest request) {
        AuthContext actor = requireSuperAdmin();
        spaceAuthorizationService.requireOperableSpace(spaceId, actor);
        return ApiResponse.ok(promptConfigService.update(spaceId, id, request, actor.username()));
    }

    /** 恢复默认：删除空间覆盖。 */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@PathVariable Long spaceId, @PathVariable Long id) {
        AuthContext actor = requireSuperAdmin();
        spaceAuthorizationService.requireOperableSpace(spaceId, actor);
        promptConfigService.reset(spaceId, id);
    }

    private AuthContext requireSuperAdmin() {
        AuthContext context = AuthContextHolder.get();
        if (context == null || !context.superAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅超级管理员可配置巡页提示词");
        }
        return context;
    }
}
