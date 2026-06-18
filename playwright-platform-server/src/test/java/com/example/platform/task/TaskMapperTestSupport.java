package com.example.platform.task;

import com.example.platform.repository.mapper.TestRepositoryMapper;
import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.scene.mapper.SceneMapper;
import com.example.platform.scene.model.SceneEntity;
import com.example.platform.task.model.ArtifactEntity;
import com.example.platform.task.model.CaseResultEntity;
import com.example.platform.task.model.TaskEntity;
import com.example.platform.task.model.TaskStageLogEntity;
import java.time.LocalDateTime;

final class TaskMapperTestSupport {
    private TaskMapperTestSupport() {
    }

    static Long insertRepository(TestRepositoryMapper repositoryMapper, String name) {
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
        repositoryMapper.insert(entity);
        return entity.getId();
    }

    static Long insertScene(TestRepositoryMapper repositoryMapper, SceneMapper sceneMapper, String repoName, String sceneName) {
        Long repoId = insertRepository(repositoryMapper, repoName);
        SceneEntity entity = new SceneEntity();
        entity.setRepoId(repoId);
        entity.setName(sceneName);
        entity.setDescription("demo scene");
        entity.setBranch("main");
        entity.setTestSelectorType("SPEC");
        entity.setTestSelectorValue("tests/demo.spec.ts");
        entity.setProjectName("chromium");
        entity.setBrowser("chromium");
        entity.setEnvJson("{\"BASE_URL\":\"http://localhost\"}");
        entity.setRunCommand("npm run test:e2e -- tests/demo.spec.ts");
        entity.setScheduleEnabled(false);
        sceneMapper.insert(entity);
        return entity.getId();
    }

    static TaskEntity task(Long repoId, Long sceneId, String status, String branch, LocalDateTime queuedAt) {
        TaskEntity entity = new TaskEntity();
        entity.setRepoId(repoId);
        entity.setSceneId(sceneId);
        entity.setStatus(status);
        entity.setCurrentStage("queued");
        entity.setResultCode(null);
        entity.setResultMessage(null);
        entity.setCancelRequested(false);
        entity.setTriggerType("MANUAL");
        entity.setTriggerReason("mapper test");
        entity.setTriggerUser("tester");
        entity.setQueuedAt(queuedAt);
        entity.setBranch(branch);
        entity.setCommitSha("abc123");
        entity.setRunnerName("local-runner");
        entity.setLogUrl("s3://logs/task.log");
        entity.setResolvedBranch(branch);
        entity.setResolvedBrowser("chromium");
        entity.setResolvedEnvJson("{\"BASE_URL\":\"http://localhost\"}");
        entity.setResolvedMatchValue("tests/demo.spec.ts");
        entity.setResolvedTestRoot("tests");
        entity.setResolvedRunCommand("npm run test:e2e -- tests/demo.spec.ts");
        return entity;
    }

    static CaseResultEntity caseResult(Long taskId, String fullName, String status) {
        CaseResultEntity entity = new CaseResultEntity();
        entity.setTaskId(taskId);
        entity.setHistoryId("history-" + fullName);
        entity.setFullName(fullName);
        entity.setSuiteName("suite");
        entity.setStoryName("story");
        entity.setStatus(status);
        entity.setDurationMs(123L);
        entity.setOwnerName("owner");
        entity.setSeverity("normal");
        entity.setProjectName("chromium");
        return entity;
    }

    static ArtifactEntity artifact(Long taskId, Long caseResultId, String objectKey) {
        ArtifactEntity entity = new ArtifactEntity();
        entity.setTaskId(taskId);
        entity.setCaseResultId(caseResultId);
        entity.setArtifactType("TRACE");
        entity.setBucket("artifacts");
        entity.setObjectKey(objectKey);
        entity.setContentType("application/zip");
        entity.setSize(456L);
        entity.setUrl("https://artifacts.example/" + objectKey);
        return entity;
    }

    static TaskStageLogEntity stageLog(Long taskId, String stage, String objectKey) {
        TaskStageLogEntity entity = new TaskStageLogEntity();
        entity.setTaskId(taskId);
        entity.setStage(stage);
        entity.setStreamType("STDOUT");
        entity.setObjectKey(objectKey);
        entity.setContentType("text/plain");
        entity.setSize(789L);
        entity.setLineCount(12);
        entity.setPreviewText("install complete");
        return entity;
    }
}
