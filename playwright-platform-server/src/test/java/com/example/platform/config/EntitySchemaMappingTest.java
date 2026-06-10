package com.example.platform.config;

import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.scene.model.SceneEntity;
import jakarta.persistence.Column;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EntitySchemaMappingTest {
    @Test
    void shouldMapEnabledFlagsToMysqlTinyintColumns() throws NoSuchFieldException {
        assertThat(columnDefinitionOf(TestRepositoryEntity.class, "enabled")).isEqualTo("tinyint(1)");
        assertThat(columnDefinitionOf(SceneEntity.class, "enabled")).isEqualTo("tinyint(1)");
    }

    private String columnDefinitionOf(Class<?> type, String fieldName) throws NoSuchFieldException {
        Field field = type.getDeclaredField(fieldName);
        Column column = field.getAnnotation(Column.class);
        assertThat(column).isNotNull();
        return column.columnDefinition();
    }
}
