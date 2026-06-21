package com.example.platform.scene.controller;

import com.example.platform.common.ApiResponse;
import com.example.platform.common.PageResponse;
import com.example.platform.scene.dto.SceneCardResponse;
import com.example.platform.scene.model.SceneEntity;
import com.example.platform.scene.service.SceneService;
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

/**
 * HTTP boundary for scene CRUD and scene card listing.
 *
 * <p>Schedule metadata updates, short write transactions, and cache invalidation
 * are handled by {@link SceneService}; this controller only translates HTTP
 * requests into service calls.
 */
@RestController
@RequestMapping("/api/scenes")
public class SceneController {
    private final SceneService sceneService;

    public SceneController(SceneService sceneService) {
        this.sceneService = sceneService;
    }

    @PostMapping
    public ApiResponse<SceneEntity> create(@RequestBody SceneEntity entity) {
        return ApiResponse.ok(sceneService.create(entity));
    }

    @GetMapping
    public ApiResponse<PageResponse<SceneCardResponse>> list(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(sceneService.listCards(page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<SceneEntity> get(@PathVariable Long id) {
        return ApiResponse.ok(sceneService.get(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<SceneEntity> update(@PathVariable Long id, @RequestBody SceneEntity entity) {
        return ApiResponse.ok(sceneService.update(id, entity));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        sceneService.delete(id);
    }
}
