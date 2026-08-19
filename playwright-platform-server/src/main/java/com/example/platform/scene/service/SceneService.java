package com.example.platform.scene.service;

import com.example.platform.common.PageResponse;
import com.example.platform.scene.dto.SceneCardResponse;
import com.example.platform.scene.model.SceneEntity;

/**
 * 场景服务接口 —— 定义场景 CRUD、卡片列表与按仓库删除的业务方法。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@link #create} —— 创建场景（含选择器规范化、名称唯一性校验、cron 表达式解析）</li>
 *   <li>{@link #listCards} —— 分页查询场景卡片列表（用于前端场景列表页）</li>
 *   <li>{@link #get} —— 获取单个场景详情</li>
 *   <li>{@link #update} —— 更新场景</li>
 *   <li>{@link #delete} —— 删除场景（级联删除）</li>
 *   <li>{@link #deleteAllByRepoId} —— 按仓库 ID 删除所有关联场景</li>
 * </ul>
 */
public interface SceneService {
    SceneEntity create(SceneEntity entity);
    PageResponse<SceneCardResponse> listCards(Long spaceId, int page, int size);
    SceneEntity get(Long spaceId, Long id);
    SceneEntity update(Long spaceId, Long id, SceneEntity entity);
    void delete(Long spaceId, Long id);
    void deleteAllByRepoId(Long repoId);
}
