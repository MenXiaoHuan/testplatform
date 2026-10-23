package com.example.platform.repository.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public class TestRepositoryEntity {
    private Long id;

    private Long spaceId;

    private String name;

    private String gitUrl;

    private String defaultBranch;

    private String workingDirectory;

    private String installCommand;

    private String runCommandTemplate;

    private String testRoot;

    private String resultsIndexRelativePath = "test-results/.playwright-results.json";

    private String artifactRootRelativePath = ".playwright-artifacts";

    /** PUBLIC / HTTPS_TOKEN / SSH_KEY；写入时为空表示使用默认值或保留原值。 */
    private String authType;

    /** HTTPS 认证用户名；SSH 模式忽略。 */
    private String credentialUsername;

    /**
     * 写入时是用户提交的明文凭据，落库前由 Service 加密；
     * 读取时永远不返回给前端。
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String credentialSecret;

    private LocalDateTime credentialUpdatedAt;

    private Boolean enabled = true;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long spaceId) { this.spaceId = spaceId; }
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
    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }
    public String getCredentialUsername() { return credentialUsername; }
    public void setCredentialUsername(String credentialUsername) { this.credentialUsername = credentialUsername; }
    public String getCredentialSecret() { return credentialSecret; }
    public void setCredentialSecret(String credentialSecret) { this.credentialSecret = credentialSecret; }
    public LocalDateTime getCredentialUpdatedAt() { return credentialUpdatedAt; }
    public void setCredentialUpdatedAt(LocalDateTime credentialUpdatedAt) { this.credentialUpdatedAt = credentialUpdatedAt; }

    /** 只暴露“是否配置”，不暴露密文或明文。 */
    public boolean isCredentialConfigured() {
        return credentialSecret != null && !credentialSecret.isBlank();
    }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
