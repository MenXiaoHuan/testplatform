package com.example.platform.task;

import com.example.platform.task.parser.ParsedArtifactBinding;
import com.example.platform.task.parser.ParsedTaskResults;
import com.example.platform.task.service.TaskCaseResultParseService;
import com.example.platform.task.service.TaskCaseResultParseServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskCaseResultParseServiceTest {
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
}
