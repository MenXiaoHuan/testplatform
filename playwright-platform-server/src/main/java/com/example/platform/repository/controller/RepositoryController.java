package com.example.platform.repository.controller;

import com.example.platform.common.ApiResponse;
import com.example.platform.common.PageResponse;
import com.example.platform.auth.context.AuthContextHolder;
import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.repository.service.RepositoryService;
import com.example.platform.space.service.SpaceAuthorizationService;
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
 * 仓库管理 HTTP 边界。
 *
 * <p>CRUD、事务边界、级联删除和详情缓存失效都在 {@link RepositoryService} 中；
 * 控制器只负责参数映射、空间权限校验和统一响应包装，避免业务逻辑回流到 Web 层。
 */
@RestController
@RequestMapping("/api/spaces/{spaceId}/repos")
public class RepositoryController {
    private final RepositoryService repositoryService;
    private final SpaceAuthorizationService spaceAuthorizationService;

    public RepositoryController(
            RepositoryService repositoryService,
            SpaceAuthorizationService spaceAuthorizationService) {
        this.repositoryService = repositoryService;
        this.spaceAuthorizationService = spaceAuthorizationService;
    }

    @PostMapping
    public ApiResponse<TestRepositoryEntity> create(@PathVariable Long spaceId, @RequestBody TestRepositoryEntity entity) {
        spaceAuthorizationService.requireOperableSpace(spaceId, AuthContextHolder.require());
        entity.setSpaceId(spaceId);
        return ApiResponse.ok(repositoryService.create(entity));
    }

    @GetMapping
    public ApiResponse<PageResponse<TestRepositoryEntity>> list(
            @PathVariable Long spaceId,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int size) {
        spaceAuthorizationService.requireReadableSpace(spaceId, AuthContextHolder.require());
        return ApiResponse.ok(repositoryService.list(spaceId, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<TestRepositoryEntity> get(@PathVariable Long spaceId, @PathVariable Long id) {
        spaceAuthorizationService.requireReadableSpace(spaceId, AuthContextHolder.require());
        return ApiResponse.ok(repositoryService.get(spaceId, id));
    }

    @PutMapping("/{id}")
    public ApiResponse<TestRepositoryEntity> update(@PathVariable Long spaceId, @PathVariable Long id, @RequestBody TestRepositoryEntity entity) {
        spaceAuthorizationService.requireOperableSpace(spaceId, AuthContextHolder.require());
        entity.setSpaceId(spaceId);
        return ApiResponse.ok(repositoryService.update(spaceId, id, entity));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long spaceId, @PathVariable Long id) {
        spaceAuthorizationService.requireOperableSpace(spaceId, AuthContextHolder.require());
        repositoryService.delete(spaceId, id);
    }
}
