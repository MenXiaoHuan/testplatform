package com.example.platform.task;

import com.example.platform.task.parser.ParsedArtifactBinding;
import com.example.platform.task.parser.ParsedTaskResults;
import com.example.platform.task.service.TaskCaseResultParseService;
import com.example.platform.task.service.TaskCaseResultParseServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class TaskCaseResultParseServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void shouldParseCaseResultsAndArtifactBindings() {
        TaskCaseResultParseService service = new TaskCaseResultParseServiceImpl(new ObjectMapper());

        ParsedTaskResults parsed = service.parse(
                101L,
                Path.of("src/test/resources/playwright-results/minimal-results.json"),
                Path.of("."));

        assertThat(parsed.caseResults()).hasSize(1);
        assertThat(parsed.caseResults().getFirst().taskId()).isEqualTo(101L);
        assertThat(parsed.caseResults().getFirst().status()).isEqualTo("PASSED");
        assertThat(parsed.caseResults().getFirst().projectName()).isEqualTo("chromium");
        assertThat(parsed.artifactBindings()).hasSize(2);
        assertThat(parsed.artifactBindings())
                .extracting(ParsedArtifactBinding::artifactType)
                .containsExactlyInAnyOrder("TRACE", "SCREENSHOT");
    }

    @Test
    void shouldParseNestedSuitesFromPlaywrightResults() {
        TaskCaseResultParseService service = new TaskCaseResultParseServiceImpl(new ObjectMapper());

        ParsedTaskResults parsed = service.parse(
                102L,
                Path.of("src/test/resources/playwright-results/nested-results.json"),
                Path.of("."));

        assertThat(parsed.caseResults()).hasSize(1);
        assertThat(parsed.caseResults().getFirst().taskId()).isEqualTo(102L);
        assertThat(parsed.caseResults().getFirst().suiteName()).isEqualTo("interview_agent/login/login.spec.ts / 登录模块");
        assertThat(parsed.caseResults().getFirst().storyName()).isEqualTo("输入正确的账号和密码");
        assertThat(parsed.caseResults().getFirst().status()).isEqualTo("FAILED");
        assertThat(parsed.artifactBindings()).hasSize(2);
        assertThat(parsed.artifactBindings())
                .extracting(ParsedArtifactBinding::relativePath)
                .containsExactlyInAnyOrder(
                        ".playwright-artifacts/interview_agent/login/trace.zip",
                        ".playwright-artifacts/interview_agent/login/error.png");
    }

    @Test
    void shouldMapContainerWorkspaceAttachmentsBackToWorkspaceRelativePaths() throws Exception {
        TaskCaseResultParseService service = new TaskCaseResultParseServiceImpl(new ObjectMapper());
        Path resultsFile = tempDir.resolve("container-results.json");
        Files.writeString(
                resultsFile,
                """
                        {
                          "suites": [
                            {
                              "title": "interview_agent/login/login.spec.ts",
                              "specs": [],
                              "suites": [
                                {
                                  "title": "登录模块",
                                  "specs": [
                                    {
                                      "title": "输入正确的账号和密码",
                                      "tests": [
                                        {
                                          "projectName": "chromium",
                                          "results": [
                                            {
                                              "status": "failed",
                                              "duration": 60,
                                              "attachments": [
                                                {
                                                  "name": "trace",
                                                  "contentType": "application/zip",
                                                  "path": "/workspace/task/.playwright-artifacts/interview_agent/login/trace.zip"
                                                },
                                                {
                                                  "name": "screenshot",
                                                  "contentType": "image/png",
                                                  "path": "/workspace/task/.playwright-artifacts/interview_agent/login/error.png"
                                                }
                                              ]
                                            }
                                          ]
                                        }
                                      ]
                                    }
                                  ]
                                }
                              ]
                            }
                          ]
                        }
                        """);

        ParsedTaskResults parsed = service.parse(103L, resultsFile, Path.of("/runner-workspaces/13"));

        assertThat(parsed.artifactBindings())
                .extracting(ParsedArtifactBinding::relativePath)
                .containsExactlyInAnyOrder(
                        ".playwright-artifacts/interview_agent/login/trace.zip",
                        ".playwright-artifacts/interview_agent/login/error.png");
    }
}
