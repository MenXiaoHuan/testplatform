package com.example.platform.config;

import com.example.platform.audit.model.PlatformAuditLogEntity;
import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.scene.model.SceneEntity;
import com.example.platform.scene.model.SceneScheduleStateEntity;
import com.example.platform.task.model.ArtifactEntity;
import com.example.platform.task.model.CaseResultEntity;
import com.example.platform.task.model.TaskEntity;
import com.example.platform.task.model.TaskStageLogEntity;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EntitySchemaMappingTest {
    private static final Path INIT_SCHEMA = Path.of("src/main/resources/db/migration/V1__init_schema.sql");
    private static final List<Class<?>> ENTITY_TYPES = List.of(
            TestRepositoryEntity.class,
            SceneEntity.class,
            TaskEntity.class,
            CaseResultEntity.class,
            ArtifactEntity.class,
            TaskStageLogEntity.class,
            SceneScheduleStateEntity.class,
            PlatformAuditLogEntity.class
    );

    @Test
    void shouldKeepEntitiesFreeOfJpaAnnotations() {
        for (Class<?> type : ENTITY_TYPES) {
            assertThat(hasJpaAnnotation(type.getAnnotations()))
                    .as("%s class annotations", type.getSimpleName())
                    .isFalse();
            for (Field field : type.getDeclaredFields()) {
                assertThat(hasJpaAnnotation(field.getAnnotations()))
                        .as("%s.%s field annotations", type.getSimpleName(), field.getName())
                        .isFalse();
            }
        }
    }

    @Test
    void shouldDefineRepositorySchemaInFlyway() throws Exception {
        String schema = normalizedSchema();

        assertThat(schema).contains("create table test_repository");
        assertThat(schema).contains("working_directory varchar(256) null");
        assertThat(schema).contains("enabled tinyint not null default 1");
        assertThat(fieldOf(TestRepositoryEntity.class, "workingDirectory").getType()).isEqualTo(String.class);
        assertThat(fieldOf(TestRepositoryEntity.class, "enabled").getType()).isEqualTo(Boolean.class);
    }

    @Test
    void shouldDefineSceneAndTaskSchemaInFlyway() throws Exception {
        String schema = normalizedSchema();

        assertThat(schema).contains("create table scene");
        assertThat(schema).contains("description varchar(512) null");
        assertThat(schema).contains("schedule_enabled tinyint not null default 0");
        assertThat(schema).contains("cron_expression varchar(64) null");
        assertThat(schema).contains("next_run_at datetime null");
        assertThat(schema).contains("last_run_at datetime null");
        assertThat(schema).contains("last_task_status varchar(32) null");
        assertThat(schema).contains("create table task");
        assertThat(schema).contains("created_at datetime not null default current_timestamp");
        assertThat(schema).contains("updated_at datetime not null default current_timestamp on update current_timestamp");
        assertThat(fieldOf(SceneEntity.class, "description").getType()).isEqualTo(String.class);
        assertThat(fieldOf(SceneEntity.class, "scheduleEnabled").getType()).isEqualTo(Boolean.class);
        assertThat(fieldOf(TaskEntity.class, "createdAt").getType().getSimpleName()).isEqualTo("LocalDateTime");
        assertThat(fieldOf(TaskEntity.class, "updatedAt").getType().getSimpleName()).isEqualTo("LocalDateTime");
    }

    @Test
    void shouldExposeTaskExecutionSnapshotFields() throws Exception {
        String schema = normalizedSchema();
        TaskEntity entity = new TaskEntity();

        entity.setResolvedBranch("main");
        entity.setResolvedBrowser("chromium");
        entity.setResolvedEnvJson("{\"BASE_URL\":\"https://example.com\"}");
        entity.setResolvedMatchValue("login.spec.ts");
        entity.setResolvedTestRoot("tests");
        entity.setResolvedRunCommand("node ./scripts/run-e2e.cjs --target login.spec.ts");

        assertThat(entity.getResolvedBranch()).isEqualTo("main");
        assertThat(entity.getResolvedBrowser()).isEqualTo("chromium");
        assertThat(entity.getResolvedEnvJson()).contains("BASE_URL");
        assertThat(entity.getResolvedMatchValue()).isEqualTo("login.spec.ts");
        assertThat(entity.getResolvedTestRoot()).isEqualTo("tests");
        assertThat(entity.getResolvedRunCommand()).contains("--target login.spec.ts");
        assertThat(schema).contains("resolved_branch varchar(128) null");
        assertThat(schema).contains("resolved_browser varchar(32) null");
        assertThat(schema).contains("resolved_env_json text null");
        assertThat(schema).contains("resolved_match_value varchar(256) null");
        assertThat(schema).contains("resolved_test_root varchar(256) null");
        assertThat(schema).contains("resolved_run_command varchar(1024) null");
        assertThat(fieldOf(TaskEntity.class, "resolvedBranch").getType()).isEqualTo(String.class);
        assertThat(fieldOf(TaskEntity.class, "resolvedBrowser").getType()).isEqualTo(String.class);
        assertThat(fieldOf(TaskEntity.class, "resolvedEnvJson").getType()).isEqualTo(String.class);
    }

    @Test
    void shouldDefineTaskRuntimeControlColumnsInFlyway() throws Exception {
        String schema = normalizedSchema();

        assertThat(schema).contains("queued_at datetime null");
        assertThat(schema).contains("current_stage varchar(32) null");
        assertThat(schema).contains("result_code varchar(32) null");
        assertThat(schema).contains("result_message varchar(1024) null");
        assertThat(schema).contains("cancel_requested tinyint(1) not null default 0");
        assertThat(schema).contains("cancel_requested_at datetime null");
        assertThat(schema).contains("cancel_requested_by varchar(64) null");
        assertThat(schema).contains("trigger_reason varchar(128) null");
        assertThat(fieldOf(TaskEntity.class, "cancelRequested").getType()).isEqualTo(Boolean.class);
        assertThat(fieldOf(TaskEntity.class, "currentStage").getType()).isEqualTo(String.class);
    }

    @Test
    void shouldDefineTaskStageLogTableInFlyway() throws Exception {
        String schema = normalizedSchema();

        assertThat(schema).contains("create table task_stage_log");
        assertThat(schema).contains("task_id bigint not null");
        assertThat(schema).contains("stage varchar(32) not null");
        assertThat(fieldOf(TaskStageLogEntity.class, "taskId").getType()).isEqualTo(Long.class);
    }

    @Test
    void shouldDefineSceneScheduleStateTableInFlyway() throws Exception {
        String schema = normalizedSchema();

        assertThat(schema).contains("create table scene_schedule_state");
        assertThat(schema).contains("scene_id bigint primary key");
        assertThat(schema).contains("version bigint not null default 0");
        assertThat(fieldOf(SceneScheduleStateEntity.class, "sceneId").getType()).isEqualTo(Long.class);
    }

    @Test
    void shouldDefinePlatformAuditLogTableInFlyway() throws Exception {
        String schema = normalizedSchema();

        assertThat(schema).contains("create table platform_audit_log");
        assertThat(schema).contains("entity_type varchar(32) not null");
        assertThat(schema).contains("detail_json json null");
        assertThat(fieldOf(PlatformAuditLogEntity.class, "detailJson").getType()).isEqualTo(String.class);
    }

    private String normalizedSchema() throws IOException {
        return Files.readString(INIT_SCHEMA).toLowerCase();
    }

    private Field fieldOf(Class<?> type, String fieldName) throws NoSuchFieldException {
        return type.getDeclaredField(fieldName);
    }

    private boolean hasJpaAnnotation(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation.annotationType().getPackageName().startsWith("jakarta" + ".persistence")) {
                return true;
            }
        }
        return false;
    }
}
