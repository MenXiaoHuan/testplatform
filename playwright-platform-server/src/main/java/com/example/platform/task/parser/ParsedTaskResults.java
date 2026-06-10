package com.example.platform.task.parser;

import java.util.List;

public record ParsedTaskResults(
        List<ParsedCaseResult> caseResults,
        List<ParsedArtifactBinding> artifactBindings) {
}
