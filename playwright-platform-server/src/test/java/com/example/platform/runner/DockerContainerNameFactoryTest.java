package com.example.platform.runner;

import com.example.platform.runner.service.DockerContainerNameFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DockerContainerNameFactoryTest {
    @Test
    void shouldCreateSafeContainerName() {
        DockerContainerNameFactory factory = new DockerContainerNameFactory();

        String name = factory.create(101L, "INSTALL");

        assertThat(name).startsWith("playwright-platform-task-101-install-");
        assertThat(name).matches("[a-z0-9][a-z0-9_.-]+");
        assertThat(name.length()).isLessThanOrEqualTo(128);
    }
}
