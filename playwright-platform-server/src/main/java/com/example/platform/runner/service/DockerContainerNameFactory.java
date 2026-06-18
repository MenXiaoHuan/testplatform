package com.example.platform.runner.service;

import java.security.SecureRandom;

public class DockerContainerNameFactory {
    private static final SecureRandom RANDOM = new SecureRandom();

    public String create(Long taskId, String stageName) {
        String stage = stageName == null || stageName.isBlank()
                ? "stage"
                : stageName.toLowerCase().replaceAll("[^a-z0-9_.-]", "-");
        String suffix = Long.toUnsignedString(RANDOM.nextLong(), 36);
        String name = "playwright-platform-task-" + taskId + "-" + stage + "-" + suffix;
        return name.length() <= 128 ? name : name.substring(0, 128);
    }
}
