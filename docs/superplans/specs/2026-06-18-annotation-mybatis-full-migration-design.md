# 第六阶段修正设计：注解式 MyBatis 全量迁移与 JPA 删除

## 一、背景

上一版迁移采用 MyBatis XML mapper，并保留 task 模块 Spring Data JPA 作为阶段性共存方案。该方案与用户期望存在偏差：项目不需要 XML mapper，Mapper 接口应统一放在 mapper 包下并使用 `@Mapper` 与注解 SQL；同时本阶段应彻底删除 JPA 相关代码，而不是继续保留 JPA/MyBatis 共存。

本设计替代上一版 XML/JPA 共存方案。后续实施以注解式 MyBatis 为唯一持久层，删除 JPA 依赖、`JpaRepository` 接口和实体类中的 JPA 注解。

## 二、目标

- 使用 `@Mapper` + `@Select/@Insert/@Update/@Delete` 承载所有 SQL。
- 保持 mapper 接口统一包结构：
  - `com.example.platform.repository.mapper`
  - `com.example.platform.scene.mapper`
  - `com.example.platform.audit.mapper`
  - `com.example.platform.task.mapper`
- 删除所有 XML mapper 文件和 `mybatis.mapper-locations` 配置。
- 删除 `spring-boot-starter-data-jpa` 依赖和 `spring.jpa.*` 配置。
- 删除所有 `*JpaRepository.java`。
- 将实体类改成纯 POJO，删除 `jakarta.persistence.*` 注解。
- 全量迁移 task、artifact、case_result、task_stage_log 数据访问到 MyBatis。
- 保持现有 API、前端调用、Flyway schema、Docker Compose 行为不变。
- 保持并补齐测试，最终通过后端、前端和 Compose 验证。

## 三、不包含

- 不改数据库表名、列名和 Flyway migration。
- 不引入 MyBatis Generator。
- 不引入 XML mapper。
- 不引入多数据源、读写分离或复杂 repository port 抽象。
- 不重写业务流程或前端页面。

## 四、架构选择

采用方案 A：显式 `@Mapper` + 注解 SQL。

原因：

- 符合项目当前偏好，不需要 XML。
- SQL 与 mapper 方法就近维护，review 路径短。
- 删除 XML 后构建产物更简单。
- 显式 `@Mapper` 比仅依赖包扫描更直观，后续新增 mapper 时不易误解。

保留 `@MapperScan("com.example.platform.**.mapper")` 作为统一扫描兜底，同时每个 mapper 接口显式添加 `@Mapper`。

## 五、Mapper 设计

### 5.1 已有 mapper 改造

以下 mapper 从 XML 映射改成注解 SQL：

- `TestRepositoryMapper`
- `SceneMapper`
- `SceneScheduleStateMapper`
- `PlatformAuditLogMapper`

改造后删除：

- `src/main/resources/mapper/repository/TestRepositoryMapper.xml`
- `src/main/resources/mapper/scene/SceneMapper.xml`
- `src/main/resources/mapper/scene/SceneScheduleStateMapper.xml`
- `src/main/resources/mapper/audit/PlatformAuditLogMapper.xml`

### 5.2 新增 task mapper

新增：

- `TaskMapper`
- `ArtifactMapper`
- `CaseResultMapper`
- `TaskStageLogMapper`

这些 mapper 覆盖原 JPA repository 能力：

- 任务创建、更新、查询、分页、恢复查询、按 scene/repo 删除。
- 产物按 task/caseResult 查询、批量查询、批量删除、插入。
- 用例结果按 task 查询、批量删除、插入。
- 阶段日志插入、按 task 查询、批量查询、批量删除。

## 六、实体设计

实体类继续作为 service 与 mapper 的数据对象，但不再是 JPA Entity。

需要删除的注解包括：

- `@Entity`
- `@Table`
- `@Id`
- `@GeneratedValue`
- `@Column`
- `@Lob`
- `@Transient`
- 其他 `jakarta.persistence.*`

保留字段、getter/setter、默认值和业务方法。原先由 `@Transient` 表达的字段仅保留为普通 Java 字段；MyBatis SQL 显式选择列，不会写入非表字段。

## 七、分页与排序

JPA `Page` 查询统一改为 mapper 的 `count + list`：

- `countAll`
- `findPage(limit, offset)`
- `countBySceneId`
- `findPageBySceneId(sceneId, limit, offset)`

继续使用 `PageResponse.of(items, total, page, size)` 返回 API 响应。

排序保持现有语义：

- 仓库、场景：`updated_at desc, id desc`
- 任务列表：`created_at desc, id desc`
- 待恢复任务：`created_at asc, id asc`

## 八、事务与锁

`SceneMapper.findByIdForUpdate` 保留 `for update` SQL，并依赖现有 `@Transactional` 调用边界。

JPA `save` 语义拆分为明确方法：

- `insert`
- `update`
- `deleteById`
- `deleteAllBy...`

所有插入需要自增 ID 的表使用 `@Options(useGeneratedKeys = true, keyProperty = "id")`。

## 九、删除 JPA

删除依赖：

- `spring-boot-starter-data-jpa`

删除配置：

- `spring.jpa.hibernate.ddl-auto`
- `spring.jpa.open-in-view`

删除 repository 文件：

- `TestRepositoryJpaRepository`
- `SceneJpaRepository`
- `SceneScheduleStateJpaRepository`
- `PlatformAuditLogJpaRepository`
- `TaskJpaRepository`
- `ArtifactJpaRepository`
- `CaseResultJpaRepository`
- `TaskStageLogJpaRepository`

删除后全项目不应再出现：

- `JpaRepository`
- `org.springframework.data.jpa`
- `jakarta.persistence`

## 十、测试策略

- mapper 测试继续使用 H2 + Flyway，验证注解 SQL 与 schema 对齐。
- service 测试全部 mock mapper，不再 mock JPA repository。
- 保留已新增的覆盖率提升用例，尤其是 `SceneScheduleLeaseServiceImpl`。
- 对 task 模块补齐迁移保护测试，覆盖创建、执行状态更新、查询聚合、恢复、级联删除。

验证命令：

```bash
cd playwright-platform-server && mvn test
cd playwright-platform-web && npm test
cd playwright-platform-web && npm run build
docker compose config
```

## 十一、风险与缓解

- 注解 SQL 较长：使用 Java text block 保持可读性。
- JPA `save` 自动 insert/update 行为消失：在 service 中明确调用 `insert` 或 `update`，测试覆盖关键路径。
- JPA 实体注解删除后字段映射依赖 MyBatis：所有复杂字段使用显式列清单或 `@Results`。
- 分页 count/list 可能不一致：mapper 测试覆盖 count 和 list 查询。
- 批量删除顺序影响外键约束：保持现有 service 删除顺序，不下沉到单条复杂 SQL。

## 十二、验收标准

- 不存在 XML mapper 文件。
- mapper 接口均显式使用 `@Mapper`。
- 不存在 `spring-boot-starter-data-jpa`。
- 不存在 `*JpaRepository.java`。
- 主代码和测试代码不再引用 `JpaRepository`、`org.springframework.data.jpa`、`jakarta.persistence`。
- `mvn test` 通过。
- `npm test` 通过。
- `npm run build` 通过。
- `docker compose config` 通过。
