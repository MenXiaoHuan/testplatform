package com.example.platform.patrol.controller;

import com.example.platform.auth.context.AuthContextHolder;
import com.example.platform.common.ApiResponse;
import com.example.platform.patrol.dto.AiModelConfigRequest;
import com.example.platform.patrol.dto.AiModelConfigResponse;
import com.example.platform.patrol.dto.AiModelOptionResponse;
import com.example.platform.patrol.dto.AiModelTestResponse;
import com.example.platform.patrol.service.AiModelConfigService;
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

/** 空间级模型配置：模型由空间自行新建与维护。 */
@RestController
@RequestMapping("/api/spaces/{spaceId}/ai-models")
public class AiModelConfigController {
    private final AiModelConfigService modelService;
    private final SpaceAuthorizationService spaceAuthorizationService;

    public AiModelConfigController(AiModelConfigService modelService,
                                   SpaceAuthorizationService spaceAuthorizationService) {
        this.modelService = modelService;
        this.spaceAuthorizationService = spaceAuthorizationService;
    }

    @GetMapping
    public ApiResponse<List<AiModelConfigResponse>> list(@PathVariable Long spaceId) {
        spaceAuthorizationService.requireReadableSpace(spaceId, AuthContextHolder.require());
        return ApiResponse.ok(modelService.list(spaceId));
    }

    @GetMapping("/options")
    public ApiResponse<List<AiModelOptionResponse>> options(@PathVariable Long spaceId) {
        spaceAuthorizationService.requireReadableSpace(spaceId, AuthContextHolder.require());
        return ApiResponse.ok(modelService.options(spaceId));
    }

    @PostMapping
    public ApiResponse<AiModelConfigResponse> create(@PathVariable Long spaceId,
                                                     @RequestBody AiModelConfigRequest request) {
        var actor = AuthContextHolder.require();
        spaceAuthorizationService.requireOperableSpace(spaceId, actor);
        return ApiResponse.ok(modelService.create(spaceId, request, actor.username()));
    }

    @PutMapping("/{id}")
    public ApiResponse<AiModelConfigResponse> update(@PathVariable Long spaceId, @PathVariable Long id,
                                                     @RequestBody AiModelConfigRequest request) {
        spaceAuthorizationService.requireOperableSpace(spaceId, AuthContextHolder.require());
        return ApiResponse.ok(modelService.update(spaceId, id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long spaceId, @PathVariable Long id) {
        spaceAuthorizationService.requireOperableSpace(spaceId, AuthContextHolder.require());
        modelService.delete(spaceId, id);
    }

    @PostMapping("/{id}/test")
    public ApiResponse<AiModelTestResponse> test(@PathVariable Long spaceId, @PathVariable Long id) {
        spaceAuthorizationService.requireReadableSpace(spaceId, AuthContextHolder.require());
        return ApiResponse.ok(modelService.test(spaceId, id));
    }
}
