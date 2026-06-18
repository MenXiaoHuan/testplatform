package com.example.platform.repository.model;

import java.time.LocalDateTime;

public class TestRepositoryEntity {
    private Long id;

    private String name;

    private String gitUrl;

    private String defaultBranch;

    private String workingDirectory;

    private String installCommand;

    private String runCommandTemplate;

    private String testRoot;

    private String resultsIndexRelativePath = "test-results/.playwright-results.json";

    private String artifactRootRelativePath = ".playwright-artifacts";

    private Boolean enabled = true;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getGitUrl() { return gitUrl; }
    public void setGitUrl(String gitUrl) { this.gitUrl = gitUrl; }
    public String getDefaultBranch() { return defaultBranch; }
    public void setDefaultBranch(String defaultBranch) { this.defaultBranch = defaultBranch; }
    public String getWorkingDirectory() { return workingDirectory; }
    public void setWorkingDirectory(String workingDirectory) { this.workingDirectory = workingDirectory; }
    public String getInstallCommand() { return installCommand; }
    public void setInstallCommand(String installCommand) { this.installCommand = installCommand; }
    public String getRunCommandTemplate() { return runCommandTemplate; }
    public void setRunCommandTemplate(String runCommandTemplate) { this.runCommandTemplate = runCommandTemplate; }
    public String getTestRoot() { return testRoot; }
    public void setTestRoot(String testRoot) { this.testRoot = testRoot; }
    public String getResultsIndexRelativePath() { return resultsIndexRelativePath; }
    public void setResultsIndexRelativePath(String resultsIndexRelativePath) { this.resultsIndexRelativePath = resultsIndexRelativePath; }
    public String getArtifactRootRelativePath() { return artifactRootRelativePath; }
    public void setArtifactRootRelativePath(String artifactRootRelativePath) { this.artifactRootRelativePath = artifactRootRelativePath; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
