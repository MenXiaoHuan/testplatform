package com.example.platform.scene.controller;

import com.example.platform.common.ApiResponse;
import com.example.platform.common.PageResponse;
import com.example.platform.auth.context.AuthContextHolder;
import com.example.platform.scene.dto.SceneCardResponse;
import com.example.platform.scene.model.SceneEntity;
import com.example.platform.scene.service.SceneService;
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
 * 场景控制器 —— 对外暴露场景 CRUD 与场景卡片列表的 HTTP 接口。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@code POST /api/spaces/{spaceId}/scenes} —— 创建场景</li>
 *   <li>{@code GET /api/spaces/{spaceId}/scenes} —— 分页查询场景卡片列表</li>
 *   <li>{@code GET /api/spaces/{spaceId}/scenes/{id}} —— 获取单个场景详情</li>
 *   <li>{@code PUT /api/spaces/{spaceId}/scenes/{id}} —— 更新场景</li>
 *   <li>{@code DELETE /api/spaces/{spaceId}/scenes/{id}} —— 删除场景（级联删除关联数据）</li>
 * </ul>
 *
 * <p>调度元数据更新、短事务写入与缓存失效均由 {@link SceneService} 处理，
 * 本控制器仅负责 HTTP 请求到服务调用的转换。
 *
 * <p>依赖：{@link SceneService}、{@link SpaceAuthorizationService}（空间权限校验）。
 */
@RestController
@RequestMapping("/api/spaces/{spaceId}/scenes")
public class SceneController {
    private final SceneService sceneService;
    private final SpaceAuthorizationService spaceAuthorizationService;

    public SceneController(
            SceneService sceneService,
            SpaceAuthorizationService spaceAuthorizationService) {
        this.sceneService = sceneService;
        this.spaceAuthorizationService = spaceAuthorizationService;
    }

    /** 创建场景，校验空间权限与仓库有效性后持久化。 */
    @PostMapping
    public ApiResponse<SceneEntity> create(@PathVariable Long spaceId, @RequestBody SceneEntity entity) {
        spaceAuthorizationService.requireOperableSpace(spaceId, AuthContextHolder.require());
        entity.setSpaceId(spaceId);
        return ApiResponse.ok(sceneService.create(entity));
    }

    /** 分页查询当前空间下的场景卡片列表。 */
    @GetMapping
    public ApiResponse<PageResponse<SceneCardResponse>> list(
            @PathVariable Long spaceId,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int size) {
        spaceAuthorizationService.requireReadableSpace(spaceId, AuthContextHolder.require());
        return ApiResponse.ok(sceneService.listCards(spaceId, page, size));
    }

    /** 根据 ID 获取单个场景详情。 */
    @GetMapping("/{id}")
    public ApiResponse<SceneEntity> get(@PathVariable Long spaceId, @PathVariable Long id) {
        spaceAuthorizationService.requireReadableSpace(spaceId, AuthContextHolder.require());
        return ApiResponse.ok(sceneService.get(spaceId, id));
    }

    /** 更新场景信息，包含选择器规范化、名称唯一性校验等。 */
    @PutMapping("/{id}")
    public ApiResponse<SceneEntity> update(@PathVariable Long spaceId, @PathVariable Long id, @RequestBody SceneEntity entity) {
        spaceAuthorizationService.requireOperableSpace(spaceId, AuthContextHolder.require());
        entity.setSpaceId(spaceId);
        return ApiResponse.ok(sceneService.update(spaceId, id, entity));
    }

    /** 删除指定场景，级联删除关联的任务、调度事件、制品等数据。 */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long spaceId, @PathVariable Long id) {
        spaceAuthorizationService.requireOperableSpace(spaceId, AuthContextHolder.require());
        sceneService.delete(spaceId, id);
    }
}
