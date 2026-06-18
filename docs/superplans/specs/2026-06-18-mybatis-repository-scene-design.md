# 第六阶段设计：仓库与场景模块 MyBatis 迁移及覆盖率提升

## 一、背景

平台后端当前使用 Spring Data JPA 完成数据访问，数据库 schema 已由 Flyway 管理。JPA 的快速开发优势已经帮助项目完成前几阶段能力建设，但随着任务执行、场景调度、级联删除和审计能力增多，数据访问层逐渐需要更明确的 SQL 控制、查询边界和迁移节奏。

本阶段目标是启动 MyBatis 迁移，但不一次性改完整个持久层。用户确认第一批范围为“仓库+场景”，因此本阶段以 repository/scene/audit/schedule 相关持久层为主，同时补充关键服务测试，提升覆盖率并为后续 task 模块迁移建立模板。

## 二、范围

### 2.1 本阶段包含

- 引入 MyBatis 依赖和 mapper 扫描配置。
- 建立 Java 注解式 mapper 或 XML mapper 的项目约定。
- 迁移以下 JPA repository 对应能力：
  - `TestRepositoryJpaRepository`
  - `SceneJpaRepository`
  - `SceneScheduleStateJpaRepository`
  - `PlatformAuditLogJpaRepository`
- 改造仓库和场景相关 service，使其依赖 mapper 或持久层端口。
- 保留现有 entity 作为表映射对象和 service 领域对象，避免同时改 DTO/API。
- 增加或补齐以下测试：
  - `RepositoryServiceImpl`
  - `RepositoryCascadeDeleteServiceImpl`
  - `SceneServiceImpl`
  - `SceneScheduleLeaseServiceImpl`
  - 场景/仓库 MyBatis mapper 行为测试或等价测试
- 保持 Flyway schema 不变。
- 保持已有 API、前端调用和 Docker Compose 启动方式不变。

### 2.2 本阶段不包含

- 不迁移 task/artifact/case_result/task_stage_log 相关 repository。
- 不移除 `spring-boot-starter-data-jpa`，因为 task 模块仍依赖 JPA repository。
- 不重命名数据库表和列。
- 不引入 MyBatis Generator。
- 不引入复杂的多数据源或读写分离。
- 不改变当前任务执行、Runner、MinIO 归档逻辑。

## 三、当前目标与覆盖率观察

当前 JaCoCo 报告显示以下类覆盖较低或为 0：

- `SceneScheduleLeaseServiceImpl`：当前行覆盖为 0，是本阶段最直接的覆盖率提升点。
- `TaskCaseResultPersistenceServiceImpl`：覆盖为 0，但属于 task 模块，暂不纳入第一批 MyBatis 迁移。
- `RunnerWorkspaceServiceImpl`、`MinioObjectStorageService`：覆盖较低，但不是本次持久层迁移主线。
- `RepositoryServiceImpl`、`SceneServiceImpl` 已有测试，但迁移期间需要补充行为等价和边界用例。

本阶段覆盖率策略：

- 优先补“会保护迁移行为”的测试，而不是只为覆盖率数字测试 getter/setter。
- 对 service 层使用 Mockito 保护业务分支。
- 对 mapper 层使用 H2 + Flyway 进行轻量集成测试，验证 SQL 与 schema 一致。
- 暂不设置新的覆盖率硬阈值，避免迁移期间因存量低覆盖模块阻塞。

## 四、TARGETS 与 BUG_MAP

### TARGETS

```json
[
  {
    "file_path": "playwright-platform-server/src/main/java/com/example/platform/repository/service/RepositoryServiceImpl.java",
    "target_type": "class",
    "symbol": "RepositoryServiceImpl",
    "reason": "迁移仓库 CRUD、分页和重名校验时需要行为等价测试"
  },
  {
    "file_path": "playwright-platform-server/src/main/java/com/example/platform/repository/service/RepositoryCascadeDeleteServiceImpl.java",
    "target_type": "class",
    "symbol": "RepositoryCascadeDeleteServiceImpl",
    "reason": "迁移仓库删除依赖的场景查询和仓库删除时需要保护级联删除顺序"
  },
  {
    "file_path": "playwright-platform-server/src/main/java/com/example/platform/scene/service/SceneServiceImpl.java",
    "target_type": "class",
    "symbol": "SceneServiceImpl",
    "reason": "迁移场景 CRUD、分页、定时扫描和按 repo 删除时需要行为等价测试"
  },
  {
    "file_path": "playwright-platform-server/src/main/java/com/example/platform/scene/service/SceneScheduleLeaseServiceImpl.java",
    "target_type": "class",
    "symbol": "SceneScheduleLeaseServiceImpl",
    "reason": "当前覆盖率为 0，且迁移涉及 schedule_state 查找/保存语义"
  },
  {
    "file_path": "playwright-platform-server/src/main/java/com/example/platform/scene/service/SceneCascadeDeleteServiceImpl.java",
    "target_type": "class",
    "symbol": "SceneCascadeDeleteServiceImpl",
    "reason": "虽然 task repository 暂不迁移，但场景删除契约需要在迁移中保持稳定"
  }
]
```

### BUG_MAP

```json
{
  "BUG_MAP": []
}
```

未发现满足闭环条件的存量业务缺陷。本阶段测试以“迁移行为等价 + 覆盖率提升”为主，不做缺陷探测型修复。

## 五、架构设计

### 5.1 持久层边界

引入 mapper 后，service 层直接依赖 mapper 还是依赖端口接口有两种选择：

- 直接依赖 MyBatis mapper：改动少，适合第一批迁移。
- 新增 repository port：可屏蔽 MyBatis/JPA 实现，但会增加类数量。

本阶段采用直接依赖 mapper，原因是当前 service 已经直接依赖 JPA repository；直接替换为 mapper 能保持改造面最小。后续如果需要支持多实现，再抽象 port。

### 5.2 包结构

新增 mapper 包：

```text
com.example.platform.repository.mapper
com.example.platform.scene.mapper
com.example.platform.audit.mapper
```

推荐文件：

- `TestRepositoryMapper`
- `SceneMapper`
- `SceneScheduleStateMapper`
- `PlatformAuditLogMapper`

如果使用 XML mapper，资源目录为：

```text
playwright-platform-server/src/main/resources/mapper/repository/TestRepositoryMapper.xml
playwright-platform-server/src/main/resources/mapper/scene/SceneMapper.xml
playwright-platform-server/src/main/resources/mapper/scene/SceneScheduleStateMapper.xml
playwright-platform-server/src/main/resources/mapper/audit/PlatformAuditLogMapper.xml
```

本阶段推荐 XML mapper，原因：

- SQL 更集中，便于 review。
- 分页、排序、大小写去重和调度查询更清楚。
- 后续 task 模块复杂查询更适合沿用 XML 风格。

### 5.3 MyBatis 配置

新增依赖：

```xml
<dependency>
  <groupId>org.mybatis.spring.boot</groupId>
  <artifactId>mybatis-spring-boot-starter</artifactId>
  <version>3.0.4</version>
</dependency>
```

新增配置：

```yaml
mybatis:
  mapper-locations: classpath*:mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true
```

在启动类或配置类上启用 mapper 扫描：

```java
@MapperScan("com.example.platform.**.mapper")
```

### 5.4 分页设计

当前 `RepositoryServiceImpl.list` 和 `SceneServiceImpl.listCards` 使用 Spring Data `Page`。迁移 MyBatis 后使用两个查询：

- `countAll()`
- `findPage(limit, offset)`

service 层继续返回 `PageResponse`，不改变 API。

分页规则保持现状：

- `page < 1` 归一到 1。
- `size < 1` 归一到 1。
- `size > 100` 归一到 100。
- 排序保持 `updated_at desc, id desc`。

### 5.5 大小写去重

JPA 方法：

- `existsByNameIgnoreCase`
- `existsByNameIgnoreCaseAndIdNot`

MyBatis SQL：

```sql
select count(1) > 0
from test_repository
where lower(name) = lower(#{name})
```

更新场景：

```sql
select count(1) > 0
from test_repository
where lower(name) = lower(#{name})
  and id <> #{id}
```

场景表同理。

### 5.6 调度 lease 设计

当前 `SceneScheduleLeaseServiceImpl.tryAcquire` 逻辑：

1. 根据 `sceneId` 查找 `SceneScheduleStateEntity`。
2. 不存在则创建新 state。
3. 如果 `plannedFireAt` 与 `lastPlannedFireAt` 相同，返回 false。
4. 否则更新 planned fire、lease owner、lease until 并保存。

迁移后 mapper 提供：

- `findBySceneId(Long sceneId)`
- `insert(SceneScheduleStateEntity state)`
- `update(SceneScheduleStateEntity state)`

service 层保持业务逻辑不变，先不引入数据库级 upsert。这样可以最大限度保持现有行为，后续再单独优化并发锁。

## 六、测试设计

### 6.1 Service 层测试

补充用例：

- `RepositoryServiceImpl`
  - list 对 page/size 做归一化，并按 mapper 返回总数组装 `PageResponse`。
  - create 时 trim name 后执行大小写去重校验。
  - update 时同名但同 id 不应误判重复。
  - get 不存在时抛出原有异常。

- `RepositoryCascadeDeleteServiceImpl`
  - 仓库不存在时不触发级联删除。
  - 仓库存在且有多个 scene 时按 scene id 调用 `deleteSceneGraph`。
  - 最后删除仓库本身。

- `SceneServiceImpl`
  - listCards 使用 mapper 分页结果并正确计算 env count。
  - create 时校验 repo 存在且启用。
  - create/update 保持 selector/matchValue 互补逻辑。
  - triggerScheduledScenes 在没有 scheduler service 时调用 mapper 查询启用场景。

- `SceneScheduleLeaseServiceImpl`
  - state 不存在时插入并返回 true。
  - plannedFireAt 与上次相同返回 false，不更新。
  - state 存在且 plannedFireAt 不同时更新并返回 true。

### 6.2 Mapper 层测试

新增 mapper 集成测试建议使用 `@MybatisTest` 或 `@SpringBootTest` + H2 + Flyway。为了避免拉起完整业务上下文，优先使用 `@MybatisTest`，并显式导入 Flyway 初始化或使用测试 SQL。

测试目标：

- `TestRepositoryMapper`
  - insert 后 findById 可读。
  - existsByNameIgnoreCase 可识别大小写重复。
  - findPage 顺序为 `updated_at desc, id desc`。

- `SceneMapper`
  - findDueScheduledScenes 只返回 next_run_at <= now 的启用场景。
  - findAllByScheduleEnabledTrueAndNextRunAtIsNullOrderByIdAsc 排序正确。
  - deleteAllByRepoId 删除指定 repo 的场景。

- `SceneScheduleStateMapper`
  - insert/find/update 保持 scene_id 主键语义。

### 6.3 覆盖率目标

本阶段完成后预期：

- `SceneScheduleLeaseServiceImpl` 从 0 行覆盖提升到主要分支覆盖。
- `RepositoryServiceImpl` 分支覆盖提升，尤其分页和重名校验。
- `SceneServiceImpl` 补齐 listCards/triggerScheduledScenes/validateRepository 相关分支。
- 后端 `mvn test` 仍全部通过。

## 七、迁移步骤

1. 添加 MyBatis starter、配置和 mapper 扫描。
2. 定义第一批 mapper 接口和 XML SQL。
3. 为 mapper 编写 H2/Flyway 集成测试，先验证 SQL 行为。
4. 改造 `RepositoryServiceImpl` 依赖 `TestRepositoryMapper`。
5. 改造 `RepositoryCascadeDeleteServiceImpl` 依赖 `TestRepositoryMapper` 和 `SceneMapper`。
6. 改造 `SceneServiceImpl` 依赖 `SceneMapper` 和 `TestRepositoryMapper`。
7. 改造 `SceneScheduleLeaseServiceImpl` 依赖 `SceneScheduleStateMapper`。
8. 按需改造 audit 写入点使用 `PlatformAuditLogMapper`。
9. 保留未迁移的 JPA repository，避免 task 模块失效。
10. 跑 `mvn test`、`npm test`、`npm run build` 和 `docker compose config`。

## 八、风险与缓解

### 8.1 SQL 与实体字段不一致

风险：MyBatis 映射列名遗漏会导致字段为 null。

缓解：开启 `map-underscore-to-camel-case`，mapper 测试覆盖关键字段，复杂字段显式 resultMap。

### 8.2 分页总数与列表不一致

风险：`countAll` 和 `findPage` 条件不一致会导致 `PageResponse` 错误。

缓解：简单全量分页先不加条件；后续新增筛选时 count 和 list 同步测试。

### 8.3 大小写去重跨数据库差异

风险：MySQL/H2 对大小写和 collation 行为不同。

缓解：SQL 使用 `lower(name)=lower(#{name})`，避免依赖 collation。

### 8.4 调度 lease 并发语义变化

风险：JPA `save` 与 MyBatis insert/update 的行为不同。

缓解：本阶段保持 service 先查再写的现有语义，不新增数据库级 upsert；并用 service 测试锁定返回值和写入行为。

### 8.5 JPA 与 MyBatis 共存

风险：两套持久层并存期间容易误用旧 repository。

缓解：第一批 service 改为只注入 mapper；JPA repository 文件暂时保留但不被第一批 service 使用；后续 task 迁移完成后统一删除 JPA 依赖。

## 九、验收标准

- 设计范围内的 service 不再依赖以下 JPA repository：
  - `TestRepositoryJpaRepository`
  - `SceneJpaRepository`
  - `SceneScheduleStateJpaRepository`
  - `PlatformAuditLogJpaRepository`
- MyBatis mapper 测试覆盖第一批核心 SQL。
- `SceneScheduleLeaseServiceImpl` 分支行为有单元测试覆盖。
- `mvn test` 通过。
- `npm test` 通过。
- `npm run build` 通过。
- `docker compose config` 通过。
- README 或后续计划文档记录“JPA/MyBatis 暂时共存，task 模块后续迁移”的事实。
