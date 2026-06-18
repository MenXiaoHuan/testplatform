package com.example.platform.audit;

import com.example.platform.audit.mapper.PlatformAuditLogMapper;
import com.example.platform.audit.model.PlatformAuditLogEntity;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest(properties = "mybatis.mapper-locations=classpath*:mapper/**/*.xml")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class PlatformAuditLogMapperTest {
    @Autowired
    private PlatformAuditLogMapper mapper;

    @Test
    void shouldInsertAuditLogAndPopulateGeneratedId() {
        PlatformAuditLogEntity entity = new PlatformAuditLogEntity();
        entity.setEntityType("SCENE");
        entity.setEntityId(101L);
        entity.setAction("CREATE");
        entity.setOperatorName("system");
        entity.setDetailJson("{\"name\":\"demo-scene\"}");

        int affectedRows = mapper.insert(entity);

        assertThat(affectedRows).isEqualTo(1);
        assertThat(entity.getId()).isNotNull();
    }
}
