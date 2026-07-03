package com.example.platform.scene;

import com.example.platform.scene.service.SchedulerInstanceIdProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulerInstanceIdProviderTest {
    @Test
    void shouldReturnConfiguredInstanceIdWhenProvided() {
        SchedulerInstanceIdProvider provider = new SchedulerInstanceIdProvider("scheduler-A");

        assertThat(provider.getInstanceId()).isEqualTo("scheduler-A");
    }

    @Test
    void shouldGenerateStableInstanceIdWhenConfigurationMissing() {
        SchedulerInstanceIdProvider provider = new SchedulerInstanceIdProvider(" ");

        assertThat(provider.getInstanceId()).isNotBlank();
        assertThat(provider.getInstanceId()).isEqualTo(provider.getInstanceId());
    }
}
