package com.example.platform.repository;

import com.example.platform.repository.mapper.TestRepositoryMapper;
import com.example.platform.repository.model.TestRepositoryEntity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
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
