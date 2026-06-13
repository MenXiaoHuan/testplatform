package com.example.platform.task.service;

import com.example.platform.repository.model.TestRepositoryEntity;
import com.example.platform.scene.model.SceneEntity;
import org.springframework.stereotype.Service;

@Service
public class TaskCommandBuilderImpl implements TaskCommandBuilder {
    @Override
    public String buildRunCommand(TestRepositoryEntity repository, SceneEntity scene) {
        String baseCommand = repository.getRunCommandTemplate();
        if (baseCommand == null || baseCommand.isBlank()) {
            baseCommand = scene.getRunCommand();
        }
        if (baseCommand == null) {
            baseCommand = "";
        }

        StringBuilder builder = new StringBuilder(baseCommand);
        boolean isPlaywrightNpxCommand = baseCommand.trim().startsWith("npx playwright test");
        String resolvedTarget = null;

        String matchValue = scene.getMatchValue();
        if (matchValue != null && !matchValue.isBlank()) {
            String normalizedRoot = repository.getTestRoot() == null
                    ? ""
                    : repository.getTestRoot().replaceAll("/+$", "");
            String normalizedMatch = matchValue.replaceAll("^/+", "");
            resolvedTarget = normalizedRoot.isBlank()
                    ? normalizedMatch
                    : normalizedRoot + "/" + normalizedMatch;

            if (isPlaywrightNpxCommand) {
                builder.append(" ").append(resolvedTarget);
            } else {
                builder.append(" --target ").append(resolvedTarget);
            }
        }

        if (scene.getBrowser() != null && !scene.getBrowser().isBlank()) {
            builder.append(" --project ").append(scene.getBrowser().trim());
        }

        return builder.toString();
    }
}
