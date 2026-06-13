package com.example.platform.repository.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "test_repository")
public class TestRepositoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "git_url", nullable = false, length = 512)
    private String gitUrl;

    @Column(name = "default_branch", nullable = false, length = 128)
    private String defaultBranch;

    @Column(name = "working_directory", length = 256)
    private String workingDirectory;

    @Column(name = "install_command", nullable = false, length = 256)
    private String installCommand;

    @Column(name = "run_command_template", nullable = false, length = 512)
    private String runCommandTemplate;

    @Column(name = "test_root", nullable = false, length = 256)
    private String testRoot;

    @Column(name = "report_relative_path", nullable = false, length = 256)
    private String reportRelativePath;

    @Column(name = "results_index_relative_path", nullable = false, length = 256)
    private String resultsIndexRelativePath = "test-results/.playwright-results.json";

    @Column(name = "artifact_root_relative_path", nullable = false, length = 256)
    private String artifactRootRelativePath = ".playwright-artifacts";

    @Column(nullable = false, columnDefinition = "tinyint(1)")
    private Boolean enabled = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
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
    public String getReportRelativePath() { return reportRelativePath; }
    public void setReportRelativePath(String reportRelativePath) { this.reportRelativePath = reportRelativePath; }
    public String getResultsIndexRelativePath() { return resultsIndexRelativePath; }
    public void setResultsIndexRelativePath(String resultsIndexRelativePath) { this.resultsIndexRelativePath = resultsIndexRelativePath; }
    public String getArtifactRootRelativePath() { return artifactRootRelativePath; }
    public void setArtifactRootRelativePath(String artifactRootRelativePath) { this.artifactRootRelativePath = artifactRootRelativePath; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
