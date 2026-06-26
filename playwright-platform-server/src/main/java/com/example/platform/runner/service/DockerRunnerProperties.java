package com.example.platform.runner.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.runner.docker")
public class DockerRunnerProperties {
    private String image = "mcr.microsoft.com/playwright:v1.44.0-jammy";
    private String network = "bridge";
    private String memory = "2g";
    private String cpus = "2";
    private String containerWorkspaceRoot = "/workspace/task";
    private boolean removeContainer = true;
    private long imagePullTimeoutSeconds = 1800;

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getNetwork() {
        return network;
    }

    public void setNetwork(String network) {
        this.network = network;
    }

    public String getMemory() {
        return memory;
    }

    public void setMemory(String memory) {
        this.memory = memory;
    }

    public String getCpus() {
        return cpus;
    }

    public void setCpus(String cpus) {
        this.cpus = cpus;
    }

    public String getContainerWorkspaceRoot() {
        return containerWorkspaceRoot;
    }

    public void setContainerWorkspaceRoot(String containerWorkspaceRoot) {
        this.containerWorkspaceRoot = containerWorkspaceRoot;
    }

    public boolean isRemoveContainer() {
        return removeContainer;
    }

    public void setRemoveContainer(boolean removeContainer) {
        this.removeContainer = removeContainer;
    }

    public long getImagePullTimeoutSeconds() {
        return imagePullTimeoutSeconds;
    }

    public void setImagePullTimeoutSeconds(long imagePullTimeoutSeconds) {
        this.imagePullTimeoutSeconds = imagePullTimeoutSeconds;
    }
}
