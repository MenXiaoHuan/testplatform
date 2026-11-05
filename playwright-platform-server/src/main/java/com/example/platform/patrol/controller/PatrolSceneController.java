package com.example.platform.patrol.controller;

import com.example.platform.auth.context.AuthContextHolder;
import com.example.platform.common.ApiResponse;
import com.example.platform.common.PageResponse;
import com.example.platform.patrol.dto.PatrolSceneRequest;
import com.example.platform.patrol.dto.PatrolSceneResponse;
import com.example.platform.patrol.dto.PatrolSceneSupplementDraft;
import com.example.platform.patrol.dto.PatrolTaskResponse;
import com.example.platform.patrol.dto.PatrolTaskSummaryResponse;
import com.example.platform.patrol.service.PatrolSceneService;
import com.example.platform.patrol.service.PatrolSupplementResolver;
import com.example.platform.space.service.SpaceAuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 巡页场景：CRUD、立即执行与执行历史。 */
@RestController
@RequestMapping("/api/spaces/{spaceId}/patrol-scenes")
public class PatrolSceneController {
    private final PatrolSceneService sceneService;
    private final SpaceAuthorizationService spaceAuthorizationService;

    public PatrolSceneController(PatrolSceneService sceneService,
                                 SpaceAuthorizationService spaceAuthorizationService) {
        this.sceneService = sceneService;
        this.spaceAuthorizationService = spaceAuthorizationService;
    }

    @GetMapping
    public ApiResponse<PageResponse<PatrolSceneResponse>> list(
            @PathVariable Long spaceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        spaceAuthorizationService.requireReadableSpace(spaceId, AuthContextHolder.require());
        return ApiResponse.ok(sceneService.list(spaceId, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<PatrolSceneResponse> get(@PathVariable Long spaceId, @PathVariable Long id) {
        spaceAuthorizationService.requireReadableSpace(spaceId, AuthContextHolder.require());
        return ApiResponse.ok(sceneService.get(spaceId, id));
    }

    @PostMapping
    public ApiResponse<PatrolSceneResponse> create(@PathVariable Long spaceId,
                                                   @RequestBody PatrolSceneRequest request) {
        var actor = AuthContextHolder.require();
        spaceAuthorizationService.requireOperableSpace(spaceId, actor);
        return ApiResponse.ok(sceneService.create(spaceId, request, actor.username()));
    }

    @PutMapping("/{id}")
    public ApiResponse<PatrolSceneResponse> update(@PathVariable Long spaceId, @PathVariable Long id,
                                                   @RequestBody PatrolSceneRequest request) {
        spaceAuthorizationService.requireOperableSpace(spaceId, AuthContextHolder.require());
        return ApiResponse.ok(sceneService.update(spaceId, id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long spaceId, @PathVariable Long id) {
        spaceAuthorizationService.requireOperableSpace(spaceId, AuthContextHolder.require());
        sceneService.delete(spaceId, id);
    }

    @PostMapping("/{id}/supplements/resolve")
    public ApiResponse<PatrolSupplementResolver.ResolveResult> resolveSupplement(
            @PathVariable Long spaceId, @PathVariable Long id,
            @RequestBody PatrolSceneSupplementDraft draft) {
        spaceAuthorizationService.requireOperableSpace(spaceId, AuthContextHolder.require());
        return ApiResponse.ok(sceneService.resolveSupplement(spaceId, id, draft));
    }

    @PostMapping("/{id}/copy")
    public ApiResponse<PatrolSceneResponse> copy(@PathVariable Long spaceId, @PathVariable Long id) {
        var actor = AuthContextHolder.require();
        spaceAuthorizationService.requireOperableSpace(spaceId, actor);
        return ApiResponse.ok(sceneService.copy(spaceId, id, actor.username()));
    }

    @PostMapping("/{id}/run")
    public ApiResponse<PatrolTaskResponse> run(@PathVariable Long spaceId, @PathVariable Long id) {
        var actor = AuthContextHolder.require();
        spaceAuthorizationService.requireOperableSpace(spaceId, actor);
        return ApiResponse.ok(sceneService.run(spaceId, id, actor.username()));
    }

    @GetMapping("/{id}/tasks")
    public ApiResponse<PageResponse<PatrolTaskResponse>> tasks(
            @PathVariable Long spaceId,
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        spaceAuthorizationService.requireReadableSpace(spaceId, AuthContextHolder.require());
        return ApiResponse.ok(sceneService.listTasks(spaceId, id, page, size));
    }

    @GetMapping("/{id}/tasks/summary")
    public ApiResponse<PatrolTaskSummaryResponse> taskSummary(@PathVariable Long spaceId,
                                                              @PathVariable Long id) {
        spaceAuthorizationService.requireReadableSpace(spaceId, AuthContextHolder.require());
        return ApiResponse.ok(sceneService.taskSummary(spaceId, id));
    }
}
