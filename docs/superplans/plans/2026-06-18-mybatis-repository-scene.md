# MyBatis Repository Scene Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve backend test coverage while migrating the first batch of repository, scene, schedule-state, and audit persistence from Spring Data JPA calls to MyBatis.

**Architecture:** Keep JPA and MyBatis temporarily side by side because task, artifact, case-result, and stage-log modules still use JPA. Add XML-based MyBatis mappers for the first batch, switch repository and scene services to mapper dependencies, and protect behavior with service unit tests plus H2/Flyway mapper integration tests.

**Tech Stack:** Spring Boot 3.5, Java 21, Maven, MyBatis Spring Boot Starter 3.0.4, JUnit 5, Mockito, AssertJ, H2, Flyway, JaCoCo.

---

## File Structure

- Create `playwright-platform-server/src/main/java/com/example/platform/repository/mapper/TestRepositoryMapper.java`: MyBatis mapper for `test_repository` CRUD, pagination, duplicate checks, and delete.
- Create `playwright-platform-server/src/main/java/com/example/platform/scene/mapper/SceneMapper.java`: MyBatis mapper for `scene` CRUD, pagination, schedule queries, repo-scoped queries, and delete.
- Create `playwright-platform-server/src/main/java/com/example/platform/scene/mapper/SceneScheduleStateMapper.java`: MyBatis mapper for `scene_schedule_state` find, insert, and update.
- Create `playwright-platform-server/src/main/java/com/example/platform/audit/mapper/PlatformAuditLogMapper.java`: MyBatis mapper for `platform_audit_log` insert.
- Create `playwright-platform-server/src/main/resources/mapper/repository/TestRepositoryMapper.xml`: SQL for `TestRepositoryMapper`.
- Create `playwright-platform-server/src/main/resources/mapper/scene/SceneMapper.xml`: SQL for `SceneMapper`.
- Create `playwright-platform-server/src/main/resources/mapper/scene/SceneScheduleStateMapper.xml`: SQL for `SceneScheduleStateMapper`.
- Create `playwright-platform-server/src/main/resources/mapper/audit/PlatformAuditLogMapper.xml`: SQL for `PlatformAuditLogMapper`.
- Modify `playwright-platform-server/pom.xml`: add MyBatis starter.
- Modify `playwright-platform-server/src/main/resources/application.yml`: add MyBatis XML mapper location and underscore-to-camel mapping.
- Modify `playwright-platform-server/src/main/java/com/example/platform/PlatformApplication.java`: add `@MapperScan`.
- Modify `playwright-platform-server/src/main/java/com/example/platform/repository/service/RepositoryServiceImpl.java`: replace `TestRepositoryJpaRepository` with `TestRepositoryMapper`.
- Modify `playwright-platform-server/src/main/java/com/example/platform/repository/service/RepositoryCascadeDeleteServiceImpl.java`: replace repository/scene JPA dependencies with mapper dependencies.
- Modify `playwright-platform-server/src/main/java/com/example/platform/scene/service/SceneServiceImpl.java`: replace `SceneJpaRepository` and `TestRepositoryJpaRepository` with mapper dependencies.
- Modify `playwright-platform-server/src/main/java/com/example/platform/scene/service/SceneScheduleLeaseServiceImpl.java`: replace `SceneScheduleStateJpaRepository` with `SceneScheduleStateMapper`.
- Modify audit write service if it currently injects `PlatformAuditLogJpaRepository`: replace with `PlatformAuditLogMapper`.
- Modify `playwright-platform-server/src/test/java/com/example/platform/repository/RepositoryServiceImplTest.java`: switch mocks to mapper and expand pagination/not-found coverage.
- Create or modify `playwright-platform-server/src/test/java/com/example/platform/repository/RepositoryCascadeDeleteServiceImplTest.java`: protect repo cascade delete ordering and skip behavior.
- Create or modify `playwright-platform-server/src/test/java/com/example/platform/scene/SceneServiceImplTest.java`: protect scene mapper migration behavior.
- Create `playwright-platform-server/src/test/java/com/example/platform/scene/SceneScheduleLeaseServiceImplTest.java`: cover the previously uncovered lease service branches.
- Create `playwright-platform-server/src/test/java/com/example/platform/repository/TestRepositoryMapperTest.java`: verify repository SQL against H2/Flyway schema.
- Create `playwright-platform-server/src/test/java/com/example/platform/scene/SceneMapperTest.java`: verify scene SQL against H2/Flyway schema.
- Create `playwright-platform-server/src/test/java/com/example/platform/scene/SceneScheduleStateMapperTest.java`: verify schedule-state SQL against H2/Flyway schema.
- Create or modify `playwright-platform-server/src/test/java/com/example/platform/audit/PlatformAuditLogMapperTest.java`: verify audit insert SQL.

---

### Task 1: MyBatis Bootstrap

**Files:**
- Modify: `playwright-platform-server/pom.xml`
- Modify: `playwright-platform-server/src/main/resources/application.yml`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/PlatformApplication.java`

- [ ] **Step 1: Add MyBatis dependency**

In `playwright-platform-server/pom.xml`, add the dependency near the other Spring Boot starters:

```xml
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
    <version>3.0.4</version>
</dependency>
```

- [ ] **Step 2: Add MyBatis configuration**

In `playwright-platform-server/src/main/resources/application.yml`, add this top-level block:

```yaml
mybatis:
  mapper-locations: classpath*:mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true
```

- [ ] **Step 3: Enable mapper scanning**

Update `playwright-platform-server/src/main/java/com/example/platform/PlatformApplication.java`:

```java
package com.example.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.example.platform.**.mapper")
public class PlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }
}
```

- [ ] **Step 4: Verify bootstrap compiles**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn test -DskipTests
```

Expected: Maven compiles the project without MyBatis configuration or import errors.

- [ ] **Step 5: Commit bootstrap**

```bash
cd /Users/bytedance/test_platform
git add playwright-platform-server/pom.xml playwright-platform-server/src/main/resources/application.yml playwright-platform-server/src/main/java/com/example/platform/PlatformApplication.java
git commit -m "feat: add mybatis bootstrap"
```

---

### Task 2: Repository Mapper

**Files:**
- Create: `playwright-platform-server/src/main/java/com/example/platform/repository/mapper/TestRepositoryMapper.java`
- Create: `playwright-platform-server/src/main/resources/mapper/repository/TestRepositoryMapper.xml`
- Create: `playwright-platform-server/src/test/java/com/example/platform/repository/TestRepositoryMapperTest.java`

- [ ] **Step 1: Create mapper interface**

Create `playwright-platform-server/src/main/java/com/example/platform/repository/mapper/TestRepositoryMapper.java`:

```java
package com.example.platform.repository.mapper;

import com.example.platform.repository.model.TestRepositoryEntity;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Param;

public interface TestRepositoryMapper {
    int insert(TestRepositoryEntity entity);

    int update(TestRepositoryEntity entity);

    Optional<TestRepositoryEntity> findById(@Param("id") Long id);

    List<TestRepositoryEntity> findPage(@Param("limit") int limit, @Param("offset") int offset);

    long countAll();

    boolean existsByNameIgnoreCase(@Param("name") String name);

    boolean existsByNameIgnoreCaseAndIdNot(@Param("name") String name, @Param("id") Long id);

    int deleteById(@Param("id") Long id);
}
```

- [ ] **Step 2: Create XML mapper**

Create `playwright-platform-server/src/main/resources/mapper/repository/TestRepositoryMapper.xml`:

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.platform.repository.mapper.TestRepositoryMapper">
    <resultMap id="TestRepositoryResultMap" type="com.example.platform.repository.model.TestRepositoryEntity">
        <id property="id" column="id"/>
        <result property="name" column="name"/>
        <result property="gitUrl" column="git_url"/>
        <result property="defaultBranch" column="default_branch"/>
        <result property="workingDirectory" column="working_directory"/>
        <result property="installCommand" column="install_command"/>
        <result property="runCommandTemplate" column="run_command_template"/>
        <result property="testRoot" column="test_root"/>
        <result property="resultsIndexRelativePath" column="results_index_relative_path"/>
        <result property="artifactRootRelativePath" column="artifact_root_relative_path"/>
        <result property="enabled" column="enabled"/>
        <result property="createdAt" column="created_at"/>
        <result property="updatedAt" column="updated_at"/>
    </resultMap>

    <insert id="insert" parameterType="com.example.platform.repository.model.TestRepositoryEntity"
            useGeneratedKeys="true" keyProperty="id">
        insert into test_repository (
            name, git_url, default_branch, working_directory, install_command,
            run_command_template, test_root, results_index_relative_path,
            artifact_root_relative_path, enabled
        ) values (
            #{name}, #{gitUrl}, #{defaultBranch}, #{workingDirectory}, #{installCommand},
            #{runCommandTemplate}, #{testRoot}, #{resultsIndexRelativePath},
            #{artifactRootRelativePath}, #{enabled}
        )
    </insert>

    <update id="update" parameterType="com.example.platform.repository.model.TestRepositoryEntity">
        update test_repository
        set name = #{name},
            git_url = #{gitUrl},
            default_branch = #{defaultBranch},
            working_directory = #{workingDirectory},
            install_command = #{installCommand},
            run_command_template = #{runCommandTemplate},
            test_root = #{testRoot},
            results_index_relative_path = #{resultsIndexRelativePath},
            artifact_root_relative_path = #{artifactRootRelativePath},
            enabled = #{enabled}
        where id = #{id}
    </update>

    <select id="findById" resultMap="TestRepositoryResultMap">
        select *
        from test_repository
        where id = #{id}
    </select>

    <select id="findPage" resultMap="TestRepositoryResultMap">
        select *
        from test_repository
        order by updated_at desc, id desc
        limit #{limit} offset #{offset}
    </select>

    <select id="countAll" resultType="long">
        select count(1)
        from test_repository
    </select>

    <select id="existsByNameIgnoreCase" resultType="boolean">
        select count(1) > 0
        from test_repository
        where lower(name) = lower(#{name})
    </select>

    <select id="existsByNameIgnoreCaseAndIdNot" resultType="boolean">
        select count(1) > 0
        from test_repository
        where lower(name) = lower(#{name})
          and id &lt;&gt; #{id}
    </select>

    <delete id="deleteById">
        delete from test_repository
        where id = #{id}
    </delete>
</mapper>
```

- [ ] **Step 3: Write mapper integration test**

Create `playwright-platform-server/src/test/java/com/example/platform/repository/TestRepositoryMapperTest.java`:

```java
package com.example.platform.repository;

import com.example.platform.repository.mapper.TestRepositoryMapper;
import com.example.platform.repository.model.TestRepositoryEntity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@ActiveProfiles("test")
class TestRepositoryMapperTest {
    @Autowired
    private TestRepositoryMapper mapper;

    @Test
    void shouldInsertFindAndDetectDuplicateNameIgnoringCase() {
        TestRepositoryEntity entity = repository("Playwright Repo");

        mapper.insert(entity);

        assertThat(entity.getId()).isNotNull();
        assertThat(mapper.findById(entity.getId())).isPresent();
        assertThat(mapper.existsByNameIgnoreCase("playwright repo")).isTrue();
        assertThat(mapper.existsByNameIgnoreCaseAndIdNot("playwright repo", entity.getId())).isFalse();
    }

    @Test
    void shouldPageByUpdatedAtAndIdDescending() {
        TestRepositoryEntity first = repository("first-repo");
        TestRepositoryEntity second = repository("second-repo");
        mapper.insert(first);
        mapper.insert(second);

        List<TestRepositoryEntity> page = mapper.findPage(10, 0);

        assertThat(mapper.countAll()).isGreaterThanOrEqualTo(2);
        assertThat(page).extracting(TestRepositoryEntity::getId).contains(second.getId(), first.getId());
    }

    private TestRepositoryEntity repository(String name) {
        TestRepositoryEntity entity = new TestRepositoryEntity();
        entity.setName(name);
        entity.setGitUrl("https://github.com/demo/testframe.git");
        entity.setDefaultBranch("main");
        entity.setWorkingDirectory("playwright_framework");
        entity.setInstallCommand("npm ci");
        entity.setRunCommandTemplate("npm run test:e2e --");
        entity.setTestRoot("tests");
        entity.setResultsIndexRelativePath("test-results/.playwright-results.json");
        entity.setArtifactRootRelativePath(".playwright-artifacts");
        entity.setEnabled(true);
        return entity;
    }
}
```

- [ ] **Step 4: Run repository mapper test**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn test -Dtest=TestRepositoryMapperTest
```

Expected: PASS. If `@MybatisTest` is missing from dependencies, add `org.mybatis.spring.boot:mybatis-spring-boot-starter-test:3.0.4` with `test` scope and rerun.

- [ ] **Step 5: Commit repository mapper**

```bash
cd /Users/bytedance/test_platform
git add playwright-platform-server/src/main/java/com/example/platform/repository/mapper/TestRepositoryMapper.java playwright-platform-server/src/main/resources/mapper/repository/TestRepositoryMapper.xml playwright-platform-server/src/test/java/com/example/platform/repository/TestRepositoryMapperTest.java playwright-platform-server/pom.xml
git commit -m "feat: add repository mybatis mapper"
```

---

### Task 3: Scene And Schedule Mappers

**Files:**
- Create: `playwright-platform-server/src/main/java/com/example/platform/scene/mapper/SceneMapper.java`
- Create: `playwright-platform-server/src/main/java/com/example/platform/scene/mapper/SceneScheduleStateMapper.java`
- Create: `playwright-platform-server/src/main/resources/mapper/scene/SceneMapper.xml`
- Create: `playwright-platform-server/src/main/resources/mapper/scene/SceneScheduleStateMapper.xml`
- Create: `playwright-platform-server/src/test/java/com/example/platform/scene/SceneMapperTest.java`
- Create: `playwright-platform-server/src/test/java/com/example/platform/scene/SceneScheduleStateMapperTest.java`

- [ ] **Step 1: Create scene mapper interface**

Create `playwright-platform-server/src/main/java/com/example/platform/scene/mapper/SceneMapper.java`:

```java
package com.example.platform.scene.mapper;

import com.example.platform.scene.model.SceneEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Param;

public interface SceneMapper {
    int insert(SceneEntity entity);

    int update(SceneEntity entity);

    Optional<SceneEntity> findById(@Param("id") Long id);

    List<SceneEntity> findPage(@Param("limit") int limit, @Param("offset") int offset);

    long countAll();

    boolean existsByNameIgnoreCase(@Param("name") String name);

    boolean existsByNameIgnoreCaseAndIdNot(@Param("name") String name, @Param("id") Long id);

    List<SceneEntity> findAllByRepoId(@Param("repoId") Long repoId);

    List<SceneEntity> findAllByScheduleEnabledTrue();

    List<SceneEntity> findDueScheduledScenes(@Param("now") LocalDateTime now);

    List<SceneEntity> findAllByScheduleEnabledTrueAndNextRunAtIsNullOrderByIdAsc();

    int deleteById(@Param("id") Long id);

    int deleteAllByRepoId(@Param("repoId") Long repoId);
}
```

- [ ] **Step 2: Create schedule-state mapper interface**

Create `playwright-platform-server/src/main/java/com/example/platform/scene/mapper/SceneScheduleStateMapper.java`:

```java
package com.example.platform.scene.mapper;

import com.example.platform.scene.model.SceneScheduleStateEntity;
import java.util.Optional;
import org.apache.ibatis.annotations.Param;

public interface SceneScheduleStateMapper {
    Optional<SceneScheduleStateEntity> findBySceneId(@Param("sceneId") Long sceneId);

    int insert(SceneScheduleStateEntity entity);

    int update(SceneScheduleStateEntity entity);
}
```

- [ ] **Step 3: Create scene XML mapper**

Create `playwright-platform-server/src/main/resources/mapper/scene/SceneMapper.xml` with explicit field mapping and SQL:

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.platform.scene.mapper.SceneMapper">
    <resultMap id="SceneResultMap" type="com.example.platform.scene.model.SceneEntity">
        <id property="id" column="id"/>
        <result property="repoId" column="repo_id"/>
        <result property="name" column="name"/>
        <result property="description" column="description"/>
        <result property="branch" column="branch"/>
        <result property="testSelectorType" column="test_selector_type"/>
        <result property="testSelectorValue" column="test_selector_value"/>
        <result property="projectName" column="project_name"/>
        <result property="browser" column="browser"/>
        <result property="envJson" column="env_json"/>
        <result property="runCommand" column="run_command"/>
        <result property="scheduleEnabled" column="schedule_enabled"/>
        <result property="cronExpression" column="cron_expression"/>
        <result property="nextRunAt" column="next_run_at"/>
        <result property="lastRunAt" column="last_run_at"/>
        <result property="lastTaskStatus" column="last_task_status"/>
    </resultMap>

    <sql id="SceneColumns">
        id, repo_id, name, description, branch, test_selector_type, test_selector_value,
        project_name, browser, env_json, run_command, schedule_enabled, cron_expression,
        next_run_at, last_run_at, last_task_status
    </sql>

    <insert id="insert" parameterType="com.example.platform.scene.model.SceneEntity"
            useGeneratedKeys="true" keyProperty="id">
        insert into scene (
            repo_id, name, description, branch, test_selector_type, test_selector_value,
            project_name, browser, env_json, run_command, schedule_enabled, cron_expression,
            next_run_at, last_run_at, last_task_status
        ) values (
            #{repoId}, #{name}, #{description}, #{branch}, #{testSelectorType}, #{testSelectorValue},
            #{projectName}, #{browser}, #{envJson}, #{runCommand}, #{scheduleEnabled}, #{cronExpression},
            #{nextRunAt}, #{lastRunAt}, #{lastTaskStatus}
        )
    </insert>

    <update id="update" parameterType="com.example.platform.scene.model.SceneEntity">
        update scene
        set repo_id = #{repoId},
            name = #{name},
            description = #{description},
            branch = #{branch},
            test_selector_type = #{testSelectorType},
            test_selector_value = #{testSelectorValue},
            project_name = #{projectName},
            browser = #{browser},
            env_json = #{envJson},
            run_command = #{runCommand},
            schedule_enabled = #{scheduleEnabled},
            cron_expression = #{cronExpression},
            next_run_at = #{nextRunAt},
            last_run_at = #{lastRunAt},
            last_task_status = #{lastTaskStatus}
        where id = #{id}
    </update>

    <select id="findById" resultMap="SceneResultMap">
        select <include refid="SceneColumns"/>
        from scene
        where id = #{id}
    </select>

    <select id="findPage" resultMap="SceneResultMap">
        select <include refid="SceneColumns"/>
        from scene
        order by updated_at desc, id desc
        limit #{limit} offset #{offset}
    </select>

    <select id="countAll" resultType="long">
        select count(1)
        from scene
    </select>

    <select id="existsByNameIgnoreCase" resultType="boolean">
        select count(1) > 0
        from scene
        where lower(name) = lower(#{name})
    </select>

    <select id="existsByNameIgnoreCaseAndIdNot" resultType="boolean">
        select count(1) > 0
        from scene
        where lower(name) = lower(#{name})
          and id &lt;&gt; #{id}
    </select>

    <select id="findAllByRepoId" resultMap="SceneResultMap">
        select <include refid="SceneColumns"/>
        from scene
        where repo_id = #{repoId}
        order by id asc
    </select>

    <select id="findAllByScheduleEnabledTrue" resultMap="SceneResultMap">
        select <include refid="SceneColumns"/>
        from scene
        where schedule_enabled = true
        order by id asc
    </select>

    <select id="findDueScheduledScenes" resultMap="SceneResultMap">
        select <include refid="SceneColumns"/>
        from scene
        where schedule_enabled = true
          and cron_expression is not null
          and cron_expression &lt;&gt; ''
          and next_run_at is not null
          and next_run_at &lt;= #{now}
        order by next_run_at asc, id asc
    </select>

    <select id="findAllByScheduleEnabledTrueAndNextRunAtIsNullOrderByIdAsc" resultMap="SceneResultMap">
        select <include refid="SceneColumns"/>
        from scene
        where schedule_enabled = true
          and next_run_at is null
        order by id asc
    </select>

    <delete id="deleteById">
        delete from scene
        where id = #{id}
    </delete>

    <delete id="deleteAllByRepoId">
        delete from scene
        where repo_id = #{repoId}
    </delete>
</mapper>
```

- [ ] **Step 4: Create schedule-state XML mapper**

Create `playwright-platform-server/src/main/resources/mapper/scene/SceneScheduleStateMapper.xml`:

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.platform.scene.mapper.SceneScheduleStateMapper">
    <resultMap id="SceneScheduleStateResultMap" type="com.example.platform.scene.model.SceneScheduleStateEntity">
        <id property="sceneId" column="scene_id"/>
        <result property="lastPlannedFireAt" column="last_planned_fire_at"/>
        <result property="lastTriggeredAt" column="last_triggered_at"/>
        <result property="lastTaskId" column="last_task_id"/>
        <result property="leaseOwner" column="lease_owner"/>
        <result property="leaseUntil" column="lease_until"/>
        <result property="version" column="version"/>
        <result property="updatedAt" column="updated_at"/>
    </resultMap>

    <select id="findBySceneId" resultMap="SceneScheduleStateResultMap">
        select scene_id, last_planned_fire_at, last_triggered_at, last_task_id,
               lease_owner, lease_until, version, updated_at
        from scene_schedule_state
        where scene_id = #{sceneId}
    </select>

    <insert id="insert" parameterType="com.example.platform.scene.model.SceneScheduleStateEntity">
        insert into scene_schedule_state (
            scene_id, last_planned_fire_at, last_triggered_at, last_task_id,
            lease_owner, lease_until, version
        ) values (
            #{sceneId}, #{lastPlannedFireAt}, #{lastTriggeredAt}, #{lastTaskId},
            #{leaseOwner}, #{leaseUntil}, #{version}
        )
    </insert>

    <update id="update" parameterType="com.example.platform.scene.model.SceneScheduleStateEntity">
        update scene_schedule_state
        set last_planned_fire_at = #{lastPlannedFireAt},
            last_triggered_at = #{lastTriggeredAt},
            last_task_id = #{lastTaskId},
            lease_owner = #{leaseOwner},
            lease_until = #{leaseUntil},
            version = #{version}
        where scene_id = #{sceneId}
    </update>
</mapper>
```

- [ ] **Step 5: Write focused mapper tests**

Create `SceneMapperTest` to insert a repository via `TestRepositoryMapper`, insert scenes via `SceneMapper`, and assert due-schedule, repo-scope, and null-next-run queries. Create `SceneScheduleStateMapperTest` to insert, find, update, and refind a state row.

```java
@Test
void shouldFindDueScheduledScenesOnly() {
    LocalDateTime now = LocalDateTime.of(2026, 6, 18, 10, 0);
    SceneEntity due = scene("due-scene", true, "* * * * *", now.minusMinutes(1));
    SceneEntity future = scene("future-scene", true, "* * * * *", now.plusMinutes(1));
    SceneEntity disabled = scene("disabled-scene", false, "* * * * *", now.minusMinutes(1));
    sceneMapper.insert(due);
    sceneMapper.insert(future);
    sceneMapper.insert(disabled);

    List<SceneEntity> result = sceneMapper.findDueScheduledScenes(now);

    assertThat(result).extracting(SceneEntity::getName).containsExactly("due-scene");
}
```

```java
@Test
void shouldInsertFindAndUpdateScheduleState() {
    SceneScheduleStateEntity state = new SceneScheduleStateEntity();
    state.setSceneId(1L);
    state.setLastPlannedFireAt(LocalDateTime.of(2026, 6, 18, 10, 0));
    state.setLeaseOwner("local-scheduler");
    state.setLeaseUntil(LocalDateTime.of(2026, 6, 18, 10, 2));
    state.setVersion(0L);

    mapper.insert(state);
    SceneScheduleStateEntity existing = mapper.findBySceneId(1L).orElseThrow();
    existing.setLastTaskId(99L);
    existing.setVersion(1L);
    mapper.update(existing);

    assertThat(mapper.findBySceneId(1L).orElseThrow().getLastTaskId()).isEqualTo(99L);
}
```

- [ ] **Step 6: Run scene mapper tests**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn test -Dtest=SceneMapperTest,SceneScheduleStateMapperTest
```

Expected: PASS.

- [ ] **Step 7: Commit scene mappers**

```bash
cd /Users/bytedance/test_platform
git add playwright-platform-server/src/main/java/com/example/platform/scene/mapper playwright-platform-server/src/main/resources/mapper/scene playwright-platform-server/src/test/java/com/example/platform/scene/SceneMapperTest.java playwright-platform-server/src/test/java/com/example/platform/scene/SceneScheduleStateMapperTest.java
git commit -m "feat: add scene mybatis mappers"
```

---

### Task 4: Audit Mapper

**Files:**
- Create: `playwright-platform-server/src/main/java/com/example/platform/audit/mapper/PlatformAuditLogMapper.java`
- Create: `playwright-platform-server/src/main/resources/mapper/audit/PlatformAuditLogMapper.xml`
- Create or modify: `playwright-platform-server/src/test/java/com/example/platform/audit/PlatformAuditLogMapperTest.java`
- Modify: audit service that currently saves `PlatformAuditLogEntity`

- [ ] **Step 1: Locate audit write service**

Run:

```bash
cd /Users/bytedance/test_platform
rg "PlatformAuditLogJpaRepository|platformAuditLog|PlatformAuditLogEntity" playwright-platform-server/src/main/java/com/example/platform/audit playwright-platform-server/src/main/java/com/example/platform
```

Expected: identify the service or component that persists `PlatformAuditLogEntity`.

- [ ] **Step 2: Create mapper interface**

Create `playwright-platform-server/src/main/java/com/example/platform/audit/mapper/PlatformAuditLogMapper.java`:

```java
package com.example.platform.audit.mapper;

import com.example.platform.audit.model.PlatformAuditLogEntity;

public interface PlatformAuditLogMapper {
    int insert(PlatformAuditLogEntity entity);
}
```

- [ ] **Step 3: Create XML mapper**

Create `playwright-platform-server/src/main/resources/mapper/audit/PlatformAuditLogMapper.xml`:

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.platform.audit.mapper.PlatformAuditLogMapper">
    <insert id="insert" parameterType="com.example.platform.audit.model.PlatformAuditLogEntity"
            useGeneratedKeys="true" keyProperty="id">
        insert into platform_audit_log (
            entity_type, entity_id, action, operator_name, detail_json
        ) values (
            #{entityType}, #{entityId}, #{action}, #{operatorName}, #{detailJson}
        )
    </insert>
</mapper>
```

- [ ] **Step 4: Switch audit service to mapper**

Replace constructor injection of `PlatformAuditLogJpaRepository` with `PlatformAuditLogMapper`, and replace `repository.save(entity)` with `mapper.insert(entity)`. Keep method names, input validation, and public API unchanged.

```java
private final PlatformAuditLogMapper mapper;

public PlatformAuditLogServiceImpl(PlatformAuditLogMapper mapper) {
    this.mapper = mapper;
}

public PlatformAuditLogEntity record(PlatformAuditLogEntity entity) {
    mapper.insert(entity);
    return entity;
}
```

- [ ] **Step 5: Run audit-focused tests**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn test -Dtest=*Audit*
```

Expected: PASS. If no audit tests exist, add one mapper test that inserts one row and asserts generated `id` is not null.

- [ ] **Step 6: Commit audit mapper**

```bash
cd /Users/bytedance/test_platform
git add playwright-platform-server/src/main/java/com/example/platform/audit/mapper playwright-platform-server/src/main/resources/mapper/audit playwright-platform-server/src/main/java/com/example/platform/audit playwright-platform-server/src/test/java/com/example/platform/audit
git commit -m "feat: add audit mybatis mapper"
```

---

### Task 5: Repository Services Migration

**Files:**
- Modify: `playwright-platform-server/src/main/java/com/example/platform/repository/service/RepositoryServiceImpl.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/repository/service/RepositoryCascadeDeleteServiceImpl.java`
- Modify: `playwright-platform-server/src/test/java/com/example/platform/repository/RepositoryServiceImplTest.java`
- Create or modify: `playwright-platform-server/src/test/java/com/example/platform/repository/RepositoryCascadeDeleteServiceImplTest.java`

- [ ] **Step 1: Update repository service test to mapper mock**

Replace `TestRepositoryJpaRepository` mock with `TestRepositoryMapper` mock in `RepositoryServiceImplTest`, including these behaviors:

```java
TestRepositoryMapper repositoryMapper = Mockito.mock(TestRepositoryMapper.class);
RepositoryCascadeDeleteService repositoryCascadeDeleteService = Mockito.mock(RepositoryCascadeDeleteService.class);
RepositoryServiceImpl service = new RepositoryServiceImpl(repositoryMapper, repositoryCascadeDeleteService);

Mockito.when(repositoryMapper.insert(Mockito.any(TestRepositoryEntity.class))).thenAnswer(invocation -> {
    TestRepositoryEntity entity = invocation.getArgument(0);
    entity.setId(1L);
    return 1;
});
```

Add list and not-found tests:

```java
@Test
void shouldNormalizePaginationWhenListingRepositories() {
    TestRepositoryMapper repositoryMapper = Mockito.mock(TestRepositoryMapper.class);
    RepositoryServiceImpl service = new RepositoryServiceImpl(
            repositoryMapper,
            Mockito.mock(RepositoryCascadeDeleteService.class));
    Mockito.when(repositoryMapper.countAll()).thenReturn(1L);
    Mockito.when(repositoryMapper.findPage(100, 0)).thenReturn(List.of(new TestRepositoryEntity()));

    PageResponse<TestRepositoryEntity> response = service.list(0, 200);

    assertThat(response.getPage()).isEqualTo(1);
    assertThat(response.getSize()).isEqualTo(100);
    Mockito.verify(repositoryMapper).findPage(100, 0);
}

@Test
void shouldThrowWhenRepositoryNotFound() {
    TestRepositoryMapper repositoryMapper = Mockito.mock(TestRepositoryMapper.class);
    RepositoryServiceImpl service = new RepositoryServiceImpl(
            repositoryMapper,
            Mockito.mock(RepositoryCascadeDeleteService.class));
    Mockito.when(repositoryMapper.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(404L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Repository not found: 404");
}
```

- [ ] **Step 2: Run repository service test and verify failure**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn test -Dtest=RepositoryServiceImplTest
```

Expected: FAIL because `RepositoryServiceImpl` still expects `TestRepositoryJpaRepository`.

- [ ] **Step 3: Migrate `RepositoryServiceImpl`**

Update constructor and methods to use `TestRepositoryMapper`:

```java
private final TestRepositoryMapper repository;

public RepositoryServiceImpl(
        TestRepositoryMapper repository,
        RepositoryCascadeDeleteService repositoryCascadeDeleteService) {
    this.repository = repository;
    this.repositoryCascadeDeleteService = repositoryCascadeDeleteService;
}

@Override
public TestRepositoryEntity create(TestRepositoryEntity entity) {
    String normalizedName = normalizeName(entity.getName());
    validateUniqueName(normalizedName, null);
    entity.setName(normalizedName);
    repository.insert(entity);
    return entity;
}

@Override
public PageResponse<TestRepositoryEntity> list(int page, int size) {
    int normalizedPage = normalizePage(page);
    int normalizedSize = normalizeSize(size);
    int offset = (normalizedPage - 1) * normalizedSize;
    return PageResponse.of(
            repository.findPage(normalizedSize, offset),
            repository.countAll(),
            normalizedPage,
            normalizedSize);
}

@Override
public TestRepositoryEntity get(Long id) {
    return repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + id));
}

@Override
public TestRepositoryEntity update(Long id, TestRepositoryEntity entity) {
    TestRepositoryEntity existing = get(id);
    String normalizedName = normalizeName(entity.getName());
    validateUniqueName(normalizedName, id);
    existing.setName(normalizedName);
    existing.setGitUrl(entity.getGitUrl());
    existing.setDefaultBranch(entity.getDefaultBranch());
    existing.setWorkingDirectory(entity.getWorkingDirectory());
    existing.setInstallCommand(entity.getInstallCommand());
    existing.setRunCommandTemplate(entity.getRunCommandTemplate());
    existing.setTestRoot(entity.getTestRoot());
    existing.setResultsIndexRelativePath(entity.getResultsIndexRelativePath());
    existing.setArtifactRootRelativePath(entity.getArtifactRootRelativePath());
    existing.setEnabled(entity.getEnabled());
    repository.update(existing);
    return existing;
}
```

If `PageResponse.of` does not exist, add a static factory to `PageResponse` that accepts `List<T> records`, `long total`, `int page`, and `int size`, then calculates existing response metadata exactly like `PageResponse.from` did for Spring Data `Page`.

- [ ] **Step 4: Migrate cascade delete service**

In `RepositoryCascadeDeleteServiceImpl`, replace JPA calls with:

```java
Optional<TestRepositoryEntity> repository = testRepositoryMapper.findById(repoId);
if (repository.isEmpty()) {
    return;
}
sceneMapper.findAllByRepoId(repoId)
        .forEach(scene -> sceneCascadeDeleteService.deleteSceneGraph(scene.getId()));
testRepositoryMapper.deleteById(repoId);
```

- [ ] **Step 5: Run repository-focused tests**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn test -Dtest=RepositoryServiceImplTest,RepositoryCascadeDeleteServiceImplTest,TestRepositoryMapperTest
```

Expected: PASS.

- [ ] **Step 6: Commit repository service migration**

```bash
cd /Users/bytedance/test_platform
git add playwright-platform-server/src/main/java/com/example/platform/repository/service playwright-platform-server/src/test/java/com/example/platform/repository
git commit -m "feat: migrate repository services to mybatis"
```

---

### Task 6: Scene Services Migration

**Files:**
- Modify: `playwright-platform-server/src/main/java/com/example/platform/scene/service/SceneServiceImpl.java`
- Modify: `playwright-platform-server/src/main/java/com/example/platform/scene/service/SceneScheduleLeaseServiceImpl.java`
- Create or modify: `playwright-platform-server/src/test/java/com/example/platform/scene/SceneServiceImplTest.java`
- Create: `playwright-platform-server/src/test/java/com/example/platform/scene/SceneScheduleLeaseServiceImplTest.java`

- [ ] **Step 1: Write lease service tests before migration**

Create `playwright-platform-server/src/test/java/com/example/platform/scene/SceneScheduleLeaseServiceImplTest.java`:

```java
package com.example.platform.scene;

import com.example.platform.scene.mapper.SceneScheduleStateMapper;
import com.example.platform.scene.model.SceneScheduleStateEntity;
import com.example.platform.scene.service.SceneScheduleLeaseServiceImpl;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class SceneScheduleLeaseServiceImplTest {
    @Test
    void shouldInsertStateWhenMissing() {
        SceneScheduleStateMapper mapper = Mockito.mock(SceneScheduleStateMapper.class);
        LocalDateTime plannedFireAt = LocalDateTime.of(2026, 6, 18, 10, 0);
        Mockito.when(mapper.findBySceneId(1L)).thenReturn(Optional.empty());
        SceneScheduleLeaseServiceImpl service = new SceneScheduleLeaseServiceImpl(mapper);

        boolean acquired = service.tryAcquire(1L, plannedFireAt);

        ArgumentCaptor<SceneScheduleStateEntity> captor = ArgumentCaptor.forClass(SceneScheduleStateEntity.class);
        Mockito.verify(mapper).insert(captor.capture());
        assertThat(acquired).isTrue();
        assertThat(captor.getValue().getSceneId()).isEqualTo(1L);
        assertThat(captor.getValue().getLastPlannedFireAt()).isEqualTo(plannedFireAt);
        assertThat(captor.getValue().getLeaseOwner()).isEqualTo("local-scheduler");
        assertThat(captor.getValue().getLeaseUntil()).isAfter(LocalDateTime.now());
    }

    @Test
    void shouldReturnFalseWhenPlannedFireAlreadyHandled() {
        SceneScheduleStateMapper mapper = Mockito.mock(SceneScheduleStateMapper.class);
        LocalDateTime plannedFireAt = LocalDateTime.of(2026, 6, 18, 10, 0);
        SceneScheduleStateEntity state = new SceneScheduleStateEntity();
        state.setSceneId(1L);
        state.setLastPlannedFireAt(plannedFireAt);
        Mockito.when(mapper.findBySceneId(1L)).thenReturn(Optional.of(state));
        SceneScheduleLeaseServiceImpl service = new SceneScheduleLeaseServiceImpl(mapper);

        boolean acquired = service.tryAcquire(1L, plannedFireAt);

        assertThat(acquired).isFalse();
        Mockito.verify(mapper, Mockito.never()).insert(Mockito.any());
        Mockito.verify(mapper, Mockito.never()).update(Mockito.any());
    }

    @Test
    void shouldUpdateStateWhenExistingFireIsDifferent() {
        SceneScheduleStateMapper mapper = Mockito.mock(SceneScheduleStateMapper.class);
        SceneScheduleStateEntity state = new SceneScheduleStateEntity();
        state.setSceneId(1L);
        state.setLastPlannedFireAt(LocalDateTime.of(2026, 6, 18, 9, 0));
        LocalDateTime plannedFireAt = LocalDateTime.of(2026, 6, 18, 10, 0);
        Mockito.when(mapper.findBySceneId(1L)).thenReturn(Optional.of(state));
        SceneScheduleLeaseServiceImpl service = new SceneScheduleLeaseServiceImpl(mapper);

        boolean acquired = service.tryAcquire(1L, plannedFireAt);

        assertThat(acquired).isTrue();
        Mockito.verify(mapper).update(state);
        assertThat(state.getLastPlannedFireAt()).isEqualTo(plannedFireAt);
    }
}
```

- [ ] **Step 2: Run lease test and verify failure**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn test -Dtest=SceneScheduleLeaseServiceImplTest
```

Expected: FAIL because `SceneScheduleLeaseServiceImpl` still expects `SceneScheduleStateJpaRepository`.

- [ ] **Step 3: Migrate lease service**

Update `SceneScheduleLeaseServiceImpl`:

```java
private final SceneScheduleStateMapper repository;

public SceneScheduleLeaseServiceImpl(SceneScheduleStateMapper repository) {
    this.repository = repository;
}

@Override
public boolean tryAcquire(Long sceneId, LocalDateTime plannedFireAt) {
    Optional<SceneScheduleStateEntity> existing = repository.findBySceneId(sceneId);
    SceneScheduleStateEntity state = existing.orElseGet(() -> {
        SceneScheduleStateEntity created = new SceneScheduleStateEntity();
        created.setSceneId(sceneId);
        return created;
    });
    if (plannedFireAt != null && plannedFireAt.equals(state.getLastPlannedFireAt())) {
        return false;
    }
    state.setLastPlannedFireAt(plannedFireAt);
    state.setLeaseOwner("local-scheduler");
    state.setLeaseUntil(LocalDateTime.now().plusMinutes(2));
    if (existing.isPresent()) {
        repository.update(state);
    } else {
        repository.insert(state);
    }
    return true;
}
```

- [ ] **Step 4: Migrate scene service constructor and CRUD**

Replace fields and constructor parameters in `SceneServiceImpl`:

```java
private final SceneMapper repository;
private final TestRepositoryMapper repositoryMapper;

public SceneServiceImpl(
        SceneMapper repository,
        TestRepositoryMapper repositoryMapper,
        SceneCascadeDeleteService sceneCascadeDeleteService,
        ObjectMapper objectMapper,
        SceneSchedulerService sceneSchedulerService) {
    this.repository = repository;
    this.repositoryMapper = repositoryMapper;
    this.sceneCascadeDeleteService = sceneCascadeDeleteService;
    this.objectMapper = objectMapper;
    this.sceneSchedulerService = sceneSchedulerService;
}
```

Update data access calls:

```java
repository.insert(normalized);
return normalized;
```

```java
return PageResponse.of(
        repository.findPage(normalizedSize, (normalizedPage - 1) * normalizedSize),
        repository.countAll(),
        normalizedPage,
        normalizedSize).map(this::toCard);
```

```java
return repository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Scene not found: " + id));
```

```java
repository.update(existing);
return existing;
```

```java
repositoryMapper.findById(repoId)
        .orElseThrow(() -> new IllegalArgumentException("所属仓库不存在，请重新选择"));
```

- [ ] **Step 5: Update scene service tests**

Create or update `SceneServiceImplTest` with mapper mocks. Include these required cases:

```java
@Test
void shouldRejectDisabledRepositoryWhenCreatingScene() {
    SceneMapper sceneMapper = Mockito.mock(SceneMapper.class);
    TestRepositoryMapper repositoryMapper = Mockito.mock(TestRepositoryMapper.class);
    TestRepositoryEntity repository = new TestRepositoryEntity();
    repository.setId(1L);
    repository.setEnabled(false);
    Mockito.when(repositoryMapper.findById(1L)).thenReturn(Optional.of(repository));
    SceneServiceImpl service = new SceneServiceImpl(
            sceneMapper,
            repositoryMapper,
            Mockito.mock(SceneCascadeDeleteService.class),
            new ObjectMapper());
    SceneEntity payload = scene("demo-scene");
    payload.setRepoId(1L);

    assertThatThrownBy(() -> service.create(payload))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("所属仓库已停用，请先启用仓库");
}
```

```java
@Test
void shouldListCardsAndCountEnvironmentVariables() {
    SceneMapper sceneMapper = Mockito.mock(SceneMapper.class);
    TestRepositoryMapper repositoryMapper = Mockito.mock(TestRepositoryMapper.class);
    SceneEntity scene = scene("demo-scene");
    scene.setEnvJson("{\"BASE_URL\":\"http://localhost\",\"TOKEN\":\"secret\"}");
    Mockito.when(sceneMapper.countAll()).thenReturn(1L);
    Mockito.when(sceneMapper.findPage(20, 0)).thenReturn(List.of(scene));
    SceneServiceImpl service = new SceneServiceImpl(
            sceneMapper,
            repositoryMapper,
            Mockito.mock(SceneCascadeDeleteService.class),
            new ObjectMapper());

    PageResponse<SceneCardResponse> response = service.listCards(1, 20);

    assertThat(response.getItems()).hasSize(1);
    assertThat(response.getItems().get(0).envCount()).isEqualTo(2);
}
```

Use the actual accessor names from `PageResponse` and `SceneCardResponse` if they differ.

- [ ] **Step 6: Run scene-focused tests**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn test -Dtest=SceneServiceImplTest,SceneScheduleLeaseServiceImplTest,SceneMapperTest,SceneScheduleStateMapperTest
```

Expected: PASS.

- [ ] **Step 7: Commit scene service migration**

```bash
cd /Users/bytedance/test_platform
git add playwright-platform-server/src/main/java/com/example/platform/scene/service playwright-platform-server/src/test/java/com/example/platform/scene
git commit -m "feat: migrate scene services to mybatis"
```

---

### Task 7: Remove First-Batch JPA Usage From Services

**Files:**
- Search/modify: `playwright-platform-server/src/main/java/com/example/platform`
- Search/modify: `playwright-platform-server/src/test/java/com/example/platform`

- [ ] **Step 1: Search for first-batch JPA service usage**

Run:

```bash
cd /Users/bytedance/test_platform
rg "TestRepositoryJpaRepository|SceneJpaRepository|SceneScheduleStateJpaRepository|PlatformAuditLogJpaRepository" playwright-platform-server/src/main/java/com/example/platform playwright-platform-server/src/test/java/com/example/platform
```

Expected: no usage in migrated services. Remaining references may include the JPA repository interface files themselves and tests for not-yet-migrated task modules only if they legitimately still depend on those repositories.

- [ ] **Step 2: Keep repository interfaces until later phase**

Do not delete first-batch JPA repository interfaces in this task. Keeping them avoids accidental breakage if other modules still compile against them and keeps the migration reversible until task persistence is migrated.

- [ ] **Step 3: Run full backend tests**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn test
```

Expected: PASS.

- [ ] **Step 4: Commit cleanup**

```bash
cd /Users/bytedance/test_platform
git add playwright-platform-server/src/main/java/com/example/platform playwright-platform-server/src/test/java/com/example/platform
git commit -m "test: cover mybatis migration behavior"
```

---

### Task 8: Final Validation And Documentation

**Files:**
- Modify: `README.md`
- Validate: `.github/workflows/ci.yml`
- Validate: `docker-compose.yml`

- [ ] **Step 1: Document coexistence**

Add a short backend persistence note to `README.md`:

```markdown
### Persistence Layer

The backend currently uses Flyway-managed schema migrations. Repository, scene, schedule-state, and audit persistence use MyBatis XML mappers. Task, artifact, case-result, and task-stage-log persistence still use Spring Data JPA during the phased migration, so both `spring-boot-starter-data-jpa` and MyBatis remain enabled.
```

- [ ] **Step 2: Run backend validation**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn test
```

Expected: PASS and JaCoCo report generated under `playwright-platform-server/target/site/jacoco`.

- [ ] **Step 3: Run frontend validation**

Run:

```bash
cd /Users/bytedance/test_platform/playwright-platform-web
npm test
npm run build
```

Expected: PASS. Existing Rollup pure annotation warnings are acceptable if commands exit 0.

- [ ] **Step 4: Validate Compose config**

Run:

```bash
cd /Users/bytedance/test_platform
docker compose config
```

Expected: command exits 0 and prints resolved Compose config.

- [ ] **Step 5: Review coverage delta**

Open or inspect:

```bash
open /Users/bytedance/test_platform/playwright-platform-server/target/site/jacoco/index.html
```

Expected: `SceneScheduleLeaseServiceImpl` is no longer 0% line coverage, and repository/scene service coverage improves from the previous baseline.

- [ ] **Step 6: Commit final docs and validation**

```bash
cd /Users/bytedance/test_platform
git add README.md
git commit -m "docs: document mybatis migration state"
```

---

## Self-Review

- Spec coverage: covered MyBatis dependency, mapper scan, XML mappers, repository/scene/audit/schedule migration, service tests, mapper tests, JPA/MyBatis coexistence, and final validation.
- Scope check: task, artifact, case-result, and task-stage-log persistence remain out of scope and are explicitly preserved.
- Placeholder scan: the plan contains no deferred implementation placeholders; task steps include concrete paths, commands, and expected results.
- Type consistency: mapper method names are consistent across interface, XML, service migration steps, and test examples.
- Risk note: `PageResponse.of` may need to be added if absent; this is called out at the exact migration step because current `PageResponse.from` is Spring Data `Page`-based.

---

## Execution Handoff

Plan complete and saved to `docs/superplans/plans/2026-06-18-mybatis-repository-scene.md`. Two execution options:

1. **Subagent-Driven (recommended)** - Dispatch a fresh subagent per task, review between tasks, and commit each task boundary.
2. **Inline Execution** - Execute tasks in this session with batch checkpoints and local review.

Which approach?
