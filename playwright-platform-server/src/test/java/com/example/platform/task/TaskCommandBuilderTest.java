package com.example.platform.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.scene.model.SceneEntity;
import com.example.platform.task.service.TaskCommandBuilderImpl;
import org.junit.jupiter.api.Test;

class TaskCommandBuilderTest {
    private final TaskCommandBuilderImpl builder = new TaskCommandBuilderImpl();

    @Test
    void shouldAppendQuotedTargetAndProjectForPlaywrightCli() {
        TestRepositoryEntity repository = repository("npx playwright test", "tests/e2e");
        SceneEntity scene = scene("login.spec.ts", "chromium");

        assertThat(builder.buildRunCommand(repository, scene))
                .isEqualTo("npx playwright test 'tests/e2e/login.spec.ts' --project 'chromium'");
    }

    @Test
    void shouldRenderPlaceholdersWithoutAppendingAgain() {
        TestRepositoryEntity repository = repository(
                "npx playwright test {target} --project {project}", "tests/e2e");
        SceneEntity scene = scene("login.spec.ts", "chromium");

        assertThat(builder.buildRunCommand(repository, scene))
                .isEqualTo("npx playwright test 'tests/e2e/login.spec.ts' --project 'chromium'");
    }

    @Test
    void shouldEscapeSingleQuotes() {
        TestRepositoryEntity repository = repository("npx playwright test", "");
        SceneEntity scene = scene("it's.spec.ts", "chromium");

        assertThat(builder.buildRunCommand(repository, scene))
                .isEqualTo("npx playwright test 'it'\\''s.spec.ts' --project 'chromium'");
    }

    @Test
    void shouldSupportTestRootPlaceholder() {
        TestRepositoryEntity repository = repository("cd {testRoot} && npx playwright test", "tests/e2e");
        SceneEntity scene = scene("", "");

        assertThat(builder.buildRunCommand(repository, scene))
                .isEqualTo("cd 'tests/e2e' && npx playwright test");
    }

    @Test
    void shouldNotDuplicateProjectOptionAlreadyPresentInTemplate() {
        TestRepositoryEntity repository = repository("npx playwright test --project=firefox", "");
        SceneEntity scene = scene("login.spec.ts", "chromium");

        assertThat(builder.buildRunCommand(repository, scene))
                .isEqualTo("npx playwright test --project=firefox 'login.spec.ts'");
    }

    @Test
    void shouldLetCustomCommandControlItsOwnArguments() {
        TestRepositoryEntity repository = repository("npm run test:e2e --", "tests/e2e");
        SceneEntity scene = scene("login.spec.ts", "chromium");

        assertThat(builder.buildRunCommand(repository, scene))
                .isEqualTo("npm run test:e2e --");
    }

    private TestRepositoryEntity repository(String command, String testRoot) {
        TestRepositoryEntity repository = new TestRepositoryEntity();
        repository.setRunCommandTemplate(command);
        repository.setTestRoot(testRoot);
        return repository;
    }

    private SceneEntity scene(String matchValue, String browser) {
        SceneEntity scene = new SceneEntity();
        scene.setMatchValue(matchValue);
        scene.setBrowser(browser);
        return scene;
    }
}
