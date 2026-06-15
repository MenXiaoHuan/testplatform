package com.example.platform.config;

import com.example.platform.audit.model.PlatformAuditLogEntity;
import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.scene.model.SceneScheduleStateEntity;
import com.example.platform.scene.model.SceneEntity;
import com.example.platform.task.model.TaskEntity;
import com.example.platform.task.model.TaskStageLogEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EntitySchemaMappingTest {
    @Test
    void shouldMapRepositoryEnabledFlagToMysqlTinyintColumn() throws NoSuchFieldException {
        assertThat(columnDefinitionOf(TestRepositoryEntity.class, "enabled")).isEqualTo("tinyint(1)");
    }

    @Test
    void shouldExposeRepositoryWorkingDirectoryField() throws NoSuchFieldException {
        assertThat(columnOf(TestRepositoryEntity.class, "workingDirectory").name()).isEqualTo("working_directory");
        assertThat(columnOf(TestRepositoryEntity.class, "workingDirectory").length()).isEqualTo(256);
        assertThat(columnOf(TestRepositoryEntity.class, "workingDirectory").nullable()).isTrue();
    }

    @Test
    void shouldExposeSceneExecutionMvpFields() throws NoSuchFieldException {
        assertThat(columnOf(SceneEntity.class, "description").length()).isEqualTo(512);
        assertThat(columnOf(SceneEntity.class, "scheduleEnabled").name()).isEqualTo("schedule_enabled");
        assertThat(columnOf(SceneEntity.class, "scheduleEnabled").columnDefinition()).isEqualTo("tinyint(1)");
        assertThat(columnOf(SceneEntity.class, "scheduleEnabled").nullable()).isFalse();
        assertThat(columnOf(SceneEntity.class, "cronExpression").name()).isEqualTo("cron_expression");
        assertThat(columnOf(SceneEntity.class, "cronExpression").length()).isEqualTo(64);
        assertThat(columnOf(SceneEntity.class, "nextRunAt").name()).isEqualTo("next_run_at");
        assertThat(columnOf(SceneEntity.class, "lastRunAt").name()).isEqualTo("last_run_at");
        assertThat(columnOf(SceneEntity.class, "lastTaskStatus").name()).isEqualTo("last_task_status");
        assertThat(columnOf(SceneEntity.class, "lastTaskStatus").length()).isEqualTo(32);
        assertThat(columnOf(TaskEntity.class, "createdAt").name()).isEqualTo("created_at");
        assertThat(columnOf(TaskEntity.class, "createdAt").insertable()).isFalse();
        assertThat(columnOf(TaskEntity.class, "createdAt").updatable()).isFalse();
        assertThat(columnOf(TaskEntity.class, "updatedAt").name()).isEqualTo("updated_at");
        assertThat(columnOf(TaskEntity.class, "updatedAt").insertable()).isFalse();
        assertThat(columnOf(TaskEntity.class, "updatedAt").updatable()).isFalse();
    }

    @Test
    void shouldExposeTaskExecutionSnapshotFields() throws NoSuchFieldException {
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
        assertThat(columnOf(TaskEntity.class, "resolvedBranch").name()).isEqualTo("resolved_branch");
        assertThat(columnOf(TaskEntity.class, "resolvedBrowser").name()).isEqualTo("resolved_browser");
        assertThat(columnOf(TaskEntity.class, "resolvedEnvJson").name()).isEqualTo("resolved_env_json");
        assertThat(columnOf(TaskEntity.class, "resolvedMatchValue").name()).isEqualTo("resolved_match_value");
        assertThat(columnOf(TaskEntity.class, "resolvedTestRoot").name()).isEqualTo("resolved_test_root");
        assertThat(columnOf(TaskEntity.class, "resolvedRunCommand").name()).isEqualTo("resolved_run_command");
    }

    @Test
    void shouldMapTaskRuntimeControlColumns() throws NoSuchFieldException {
        assertThat(columnOf(TaskEntity.class, "queuedAt").name()).isEqualTo("queued_at");
        assertThat(columnOf(TaskEntity.class, "currentStage").name()).isEqualTo("current_stage");
        assertThat(columnOf(TaskEntity.class, "currentStage").length()).isEqualTo(32);
        assertThat(columnOf(TaskEntity.class, "resultCode").name()).isEqualTo("result_code");
        assertThat(columnOf(TaskEntity.class, "resultCode").length()).isEqualTo(32);
        assertThat(columnOf(TaskEntity.class, "resultMessage").name()).isEqualTo("result_message");
        assertThat(columnOf(TaskEntity.class, "resultMessage").length()).isEqualTo(1024);
        assertThat(columnOf(TaskEntity.class, "cancelRequested").name()).isEqualTo("cancel_requested");
        assertThat(columnOf(TaskEntity.class, "cancelRequested").columnDefinition()).isEqualTo("tinyint(1)");
        assertThat(columnOf(TaskEntity.class, "cancelRequestedAt").name()).isEqualTo("cancel_requested_at");
        assertThat(columnOf(TaskEntity.class, "cancelRequestedBy").name()).isEqualTo("cancel_requested_by");
        assertThat(columnOf(TaskEntity.class, "cancelRequestedBy").length()).isEqualTo(64);
        assertThat(columnOf(TaskEntity.class, "triggerReason").name()).isEqualTo("trigger_reason");
        assertThat(columnOf(TaskEntity.class, "triggerReason").length()).isEqualTo(128);
    }

    @Test
    void shouldMapTaskStageLogTable() {
        Table table = TaskStageLogEntity.class.getAnnotation(Table.class);
        assertThat(table).isNotNull();
        assertThat(table.name()).isEqualTo("task_stage_log");
    }

    @Test
    void shouldMapSceneScheduleStateTable() {
        Table table = SceneScheduleStateEntity.class.getAnnotation(Table.class);
        assertThat(table).isNotNull();
        assertThat(table.name()).isEqualTo("scene_schedule_state");
    }

    @Test
    void shouldMapPlatformAuditLogTable() {
        Table table = PlatformAuditLogEntity.class.getAnnotation(Table.class);
        assertThat(table).isNotNull();
        assertThat(table.name()).isEqualTo("platform_audit_log");
    }

    private String columnDefinitionOf(Class<?> type, String fieldName) throws NoSuchFieldException {
        return columnOf(type, fieldName).columnDefinition();
    }

    private Column columnOf(Class<?> type, String fieldName) throws NoSuchFieldException {
        Field field = type.getDeclaredField(fieldName);
        Column column = field.getAnnotation(Column.class);
        assertThat(column).isNotNull();
        return column;
    }
}
